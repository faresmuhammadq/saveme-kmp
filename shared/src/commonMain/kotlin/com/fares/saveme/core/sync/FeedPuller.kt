package com.fares.saveme.core.sync

import co.touchlab.kermit.Logger
import com.fares.saveme.core.database.SyncScopeDao
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant

/** How one scope's read ended (spec 009 data-model §3). */
sealed interface ScopeRead {
    /**
     * Every page was applied. [seenWalletIds] is set after a complete read of the person's scope
     * that began without a position: the wallets of the own memberships it returned (FR-011).
     * [lost] is true when the wallet turned out to be gone (its feed answered `404`).
     */
    data class Done(
        val startedAt: Instant,
        val seenWalletIds: Set<String>? = null,
        val lost: Boolean = false,
    ) : ScopeRead

    /** No answer: the position stays at the last applied page. */
    data object Offline : ScopeRead

    data object Failed : ScopeRead
}

/** What one sync run knows across its scope reads: the account it started for, and which scopes had an invalid position (FR-009). */
class ReadRun(val accountId: String?) {
    internal val invalidCursors = mutableSetOf<String>()
}

/**
 * Reads one scope's change feed into the features' tables (spec 009 FR-001, FR-003, FR-004,
 * FR-006–FR-009, FR-011). Rows go to the [FeedApplier] registered for their (scope kind, type); a
 * type nobody registered is skipped and counted. Nothing here knows a feature's table; a feature
 * that makes sense of wallets implements [WalletScopes].
 *
 * The position of a scope is stored after the rows of a page are applied (a crash in between
 * re-reads the page and the appliers' version guard makes that harmless). A read that began
 * without a position stores it only after its last page: its sweep needs the ids of every page, so
 * an interrupted one starts again from the beginning (research R-04).
 */
class FeedPuller(
    private val api: SyncApi,
    private val scopes: SyncScopeDao,
    private val appliers: () -> List<FeedApplier>,
    private val walletScopes: () -> WalletScopes?,
    private val lock: SyncLock,
    private val currentAccountId: () -> String?,
    private val clock: Clock = Clock.System,
) {
    suspend fun read(scope: SyncScope, run: ReadRun = ReadRun(currentAccountId())): ScopeRead =
        lock.withLock { readWithRestart(scope, run) }

    private suspend fun readWithRestart(scope: SyncScope, run: ReadRun): ScopeRead {
        var restarts = 0
        while (true) {
            when (val outcome = readOnce(scope, run)) {
                is Outcome.Finished -> return outcome.read
                Outcome.Restart -> {
                    // A position the server refused is forgotten and the scope is read from the start, once.
                    if (restarts++ >= 1) return ScopeRead.Failed
                    scopes.clearFeed(scope.key)
                }
            }
        }
    }

    private sealed interface Outcome {
        data class Finished(val read: ScopeRead) : Outcome
        data object Restart : Outcome
    }

    private fun finished(read: ScopeRead): Outcome = Outcome.Finished(read)

    private suspend fun readOnce(scope: SyncScope, run: ReadRun): Outcome {
        val key = scope.key
        val startedAt = clock.now()
        val storedPosition = scopes.feedCursor(key)
        val fromStart = storedPosition == null
        val byType = appliers().filter { it.scopeKind == scope.kind }.associateBy { it.rowType }
        val seen = mutableMapOf<String, MutableSet<String>>()
        val seenWalletIds = mutableSetOf<String>()
        val skipped = mutableMapOf<String, Int>()
        var rowCount = 0
        var pageCount = 0
        var sentPosition = storedPosition
        while (true) {
            val answer = when (scope.kind) {
                ScopeKind.User -> api.userChanges(sentPosition)
                ScopeKind.Wallet -> api.walletChanges(scope.id, sentPosition)
            }
            if (!stillSignedIn(run)) {
                log.i { "read of ${scope.kind.wire} scope dropped: the account changed" }
                return finished(ScopeRead.Failed)
            }
            when (answer) {
                ApiResult.Offline, ApiResult.Timeout -> {
                    log.i { "read of ${scope.kind.wire} scope: no answer after $pageCount pages, $rowCount rows" }
                    return finished(ScopeRead.Offline)
                }
                is ApiResult.Failure -> return failure(scope, run, answer, hadPosition = sentPosition != null, fromStart = fromStart)
                is ApiResult.Success -> {
                    val page = answer.value
                    if (page.meta.syncCursor.isEmpty()) {
                        log.w { "read of ${scope.kind.wire} scope: a page without a position" }
                        return finished(ScopeRead.Failed)
                    }
                    try {
                        applyPage(scope, page.data, byType, seen, seenWalletIds, skipped, collect = fromStart)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The position stays where it was: the page is read again and the version guard makes a repeat harmless.
                        log.e { "read of ${scope.kind.wire} scope: applying a page failed (${e::class.simpleName})" }
                        return finished(ScopeRead.Failed)
                    }
                    if (!stillSignedIn(run)) return finished(ScopeRead.Failed)
                    pageCount++
                    rowCount += page.data.size
                    if (page.meta.hasMore) {
                        if (!fromStart) scopes.storeFeedPage(key, page.meta.syncCursor)
                        sentPosition = page.meta.nextCursor ?: page.meta.syncCursor
                        continue
                    }
                    try {
                        if (fromStart) {
                            // Sweep first: a failure leaves the position empty, so the next run starts again and sweeps then.
                            appliers().filter { it.scopeKind == scope.kind }.forEach { it.sweep(scope, seen[it.rowType].orEmpty()) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.e { "read of ${scope.kind.wire} scope: the sweep failed (${e::class.simpleName})" }
                        return finished(ScopeRead.Failed)
                    }
                    if (!stillSignedIn(run)) return finished(ScopeRead.Failed)
                    scopes.storeFeedPage(key, page.meta.syncCursor)
                    scopes.markFeedRead(key, startedAt.syncStamp())
                    log.i {
                        "read of ${scope.kind.wire} scope: $pageCount pages, $rowCount rows" +
                            (if (skipped.isEmpty()) "" else ", skipped ${skipped.entries.joinToString { "${it.key} x${it.value}" }}") +
                            (if (fromStart) ", from the start" else "")
                    }
                    return finished(ScopeRead.Done(startedAt, seenWalletIds.takeIf { fromStart && scope.kind == ScopeKind.User }))
                }
            }
        }
    }

    private suspend fun failure(scope: SyncScope, run: ReadRun, answer: ApiResult.Failure, hadPosition: Boolean, fromStart: Boolean): Outcome {
        val where = "${scope.kind.wire} scope (request ${answer.problem.requestId})"
        return when {
            answer.code == ApiErrorCode.SYNC_CURSOR_EXPIRED && hadPosition && fromStart -> {
                // The position came from this very read: starting again would meet the same answer at the same page
                // (the live backend expires a from-the-start read's own next page behind its purge horizon, qa-report.md).
                log.w { "position of $where expired in the middle of a read from the start" }
                finished(ScopeRead.Failed)
            }
            answer.code == ApiErrorCode.SYNC_CURSOR_EXPIRED && hadPosition -> {
                log.i { "position of $where expired; reading from the start" }
                Outcome.Restart
            }
            answer.code == ApiErrorCode.INVALID_CURSOR && hadPosition -> {
                if (run.invalidCursors.add(scope.key)) {
                    log.i { "position of $where refused; reading from the start" }
                    Outcome.Restart
                } else {
                    log.w { "position of $where refused twice in one run" }
                    finished(ScopeRead.Failed)
                }
            }
            answer.code == ApiErrorCode.NOT_FOUND && scope.kind == ScopeKind.Wallet -> {
                val wallets = walletScopes()
                if (wallets == null) {
                    log.w { "wallet feed not found, nobody to ask ($where)" }
                    finished(ScopeRead.Failed)
                } else {
                    log.i { "wallet feed not found: access lost ($where)" }
                    wallets.accessLost(scope.id)
                    finished(ScopeRead.Done(clock.now(), lost = true))
                }
            }
            else -> {
                log.w { "read of $where refused: ${answer.problem.code.ifEmpty { "status ${answer.problem.status}" }}" }
                finished(ScopeRead.Failed)
            }
        }
    }

    /** Rows go to their appliers in feed order, consecutive rows of one type together (research R-02). */
    private suspend fun applyPage(
        scope: SyncScope,
        page: List<ChangeRowDto>,
        byType: Map<String, FeedApplier>,
        seen: MutableMap<String, MutableSet<String>>,
        seenWalletIds: MutableSet<String>,
        skipped: MutableMap<String, Int>,
        collect: Boolean,
    ) {
        var start = 0
        while (start < page.size) {
            val type = page[start].type
            var end = start
            while (end < page.size && page[end].type == type) end++
            val group = page.subList(start, end).map { it.record }
            val applier = byType[type]
            if (applier == null) {
                skipped[type] = (skipped[type] ?: 0) + group.size
            } else {
                applier.apply(scope, group)
                if (collect) {
                    val ids = seen.getOrPut(type) { mutableSetOf() }
                    group.forEach { record ->
                        record.stringMember("id")?.let { ids += it }
                        if (scope.kind == ScopeKind.User && type == OWN_MEMBERSHIP) record.stringMember("wallet_id")?.let { seenWalletIds += it }
                    }
                }
            }
            start = end
        }
    }

    private fun stillSignedIn(run: ReadRun): Boolean = run.accountId != null && currentAccountId() == run.accountId

    private fun JsonElement.stringMember(name: String): String? = ((this as? JsonObject)?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        val log = Logger.withTag("sync")

        /** The user feed's `wallet_member` rows are the person's own memberships (backend ADR-0017). */
        const val OWN_MEMBERSHIP = "wallet_member"
    }
}
