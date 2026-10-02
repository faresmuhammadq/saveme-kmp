package com.fares.saveme.core.sync

import co.touchlab.kermit.Logger
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.RetryClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The one path from the phone to the server for every synced record (spec 004).
 *
 * Edits are saved here first and sent later (P-04.2) through each record type's [SyncHandler].
 * Every send carries the version the person last saw (P-04.4); a clash waits for the person and
 * is never merged (backend ADR-0008 rule 9). Only rows made by the signed-in account are ever
 * sent (FR-018). Payloads are never logged (P-09.2).
 */
class SyncEngine(
    private val dao: PendingChangeDao,
    private val scopes: com.fares.saveme.core.database.SyncScopeDao,
    handlers: List<SyncHandler>,
    private val scheduler: SyncScheduler,
    private val currentAccountId: () -> String?,
    private val loadAccount: suspend () -> Unit = {},
    private val clock: Clock = Clock.System,
    private val backoff: Backoff = Backoff(),
) {
    private val handlers = handlers.associateBy { it.recordType }

    /** Guards every read-modify-write of a row. */
    private val rows = Mutex()

    /** One drain at a time, so `Sending` rows found at the start are always leftovers. */
    private val draining = Mutex()

    val status: Flow<SyncStatus> = combine(
        dao.observeWaitingCount(),
        dao.observeNeedsDecisionCount(),
        scopes.observeLastPushed(),
    ) { waiting, decision, last ->
        SyncStatus(waiting, decision, last?.let { runCatching { Instant.parse(it) }.getOrNull() })
    }

    fun observe(recordType: String, recordId: String): Flow<PendingChange?> =
        dao.observe(recordType, recordId).map { it?.toDomain() }

    suspend fun get(recordType: String, recordId: String): PendingChange? = dao.get(recordType, recordId)?.toDomain()

    /**
     * Saves a change locally. A record already waiting keeps its oldest base version and takes the
     * new content (FR-003); an edit after a refusal or clash makes it waiting again.
     */
    suspend fun enqueue(recordType: String, recordId: String, scope: SyncScope, baseVersion: Long, payloadJson: String) {
        write(recordType, recordId, scope, baseVersion, payloadJson, isDelete = false)
        scheduler.enqueue()
    }

    /** Queues a deletion (P-04.5). A record that never reached the server leaves nothing to send (FR-004). */
    suspend fun enqueueDelete(recordType: String, recordId: String, scope: SyncScope, baseVersion: Long) {
        val queued = rows.withLock {
            val existing = dao.get(recordType, recordId)
            if ((existing?.baseVersion ?: baseVersion) == 0L) {
                dao.delete(recordType, recordId)
                false
            } else {
                true
            }
        }
        if (!queued) return
        write(recordType, recordId, scope, baseVersion, dao.get(recordType, recordId)?.payloadJson ?: "{}", isDelete = true)
        scheduler.enqueue()
    }

    private suspend fun write(
        recordType: String,
        recordId: String,
        scope: SyncScope,
        baseVersion: Long,
        payloadJson: String,
        isDelete: Boolean,
    ) = rows.withLock {
        val account = checkNotNull(currentAccountId()) { "a change can only be made while signed in" }
        val now = clock.now().toString()
        val existing = dao.get(recordType, recordId)
        val row = existing?.copy(
            payloadJson = payloadJson,
            isDelete = isDelete,
            restore = existing.restore && !isDelete,
            revision = existing.revision + 1,
            state = ChangeState.Waiting.name,
            attempts = 0,
            nextAttemptAt = null,
            clashJson = null,
            refusalJson = null,
            updatedAt = now,
        ) ?: PendingChangeEntity(
            recordType = recordType,
            recordId = recordId,
            accountId = account,
            scopeKind = scope.kind.wire,
            scopeId = scope.id,
            baseVersion = baseVersion,
            payloadJson = payloadJson,
            isDelete = isDelete,
            restore = false,
            revision = 0,
            createdOrder = dao.maxCreatedOrder() + 1,
            state = ChangeState.Waiting.name,
            attempts = 0,
            nextAttemptAt = null,
            clashJson = null,
            refusalJson = null,
            updatedAt = now,
        )
        dao.upsert(row)
        log.d { "queued $recordType/$recordId (revision ${row.revision})" }
    }

    /**
     * Sends every due change of the signed-in account, oldest first (FR-006, FR-007). Called by
     * the scheduler; safe to call at any time.
     */
    suspend fun drain() = draining.withLock {
        loadAccount()
        val account = currentAccountId() ?: return@withLock
        dao.resetSending()
        for (row in dao.due(account, clock.now().stamp())) {
            val handler = handlers[row.recordType]
            if (handler == null) {
                log.w { "no handler for ${row.recordType}; left waiting" }
                continue
            }
            rows.withLock { dao.upsert(row.copy(state = ChangeState.Sending.name)) }
            val result = handler.send(row.toDomain())
            val stop = rows.withLock { settle(row, handler, result) }
            if (stop) return@withLock
        }
        // A run can start before a row's wait is over (the scheduler's timing is approximate, and a
        // row can be rescheduled while this run was queued). Without this the row would wait until
        // the app is next opened. Rows due now but unsent (no handler) are not rescheduled, or this
        // would loop.
        val next = dao.nextDueAt(account)?.takeIf { it.isNotEmpty() }?.let { Instant.parse(it) } ?: return@withLock
        scheduler.enqueue((next - clock.now()).coerceAtLeast(Duration.ZERO))
    }

    /** Applies one send's answer; returns true when nothing else can be sent now (no connection). */
    private suspend fun settle(sent: PendingChangeEntity, handler: SyncHandler, result: ApiResult<JsonElement>): Boolean {
        val now = clock.now()
        // The row as it is now: the person may have edited or discarded it while it was in flight.
        val current = dao.get(sent.recordType, sent.recordId)
        val editedMeanwhile = current != null && current.revision != sent.revision
        when (result) {
            is ApiResult.Success -> {
                handler.applyServerCopy(result.value)
                scopes.markPushed(SyncScope(ScopeKind.fromWire(sent.scopeKind), sent.scopeId).key, now.stamp())
                when {
                    current == null -> Unit
                    editedMeanwhile -> {
                        // The newer edit goes next, on top of the version this send produced (R-02).
                        dao.upsert(
                            current.copy(
                                baseVersion = result.value.version() ?: current.baseVersion,
                                state = ChangeState.Waiting.name,
                                attempts = 0,
                                nextAttemptAt = null,
                                restore = false,
                            ),
                        )
                        scheduler.enqueue()
                    }
                    else -> dao.delete(sent.recordType, sent.recordId)
                }
                log.d { "sent ${sent.recordType}/${sent.recordId}" }
                return false
            }
            is ApiResult.Failure -> {
                val row = current ?: return false
                val clash = result.problem.current
                when {
                    result.code == ApiErrorCode.VERSION_CONFLICT && clash != null -> {
                        dao.upsert(row.copy(state = ChangeState.Clash.name, clashJson = clash.toString(), attempts = 0, nextAttemptAt = null))
                        log.i { "clash on ${sent.recordType}/${sent.recordId} (request ${result.problem.requestId})" }
                        return false
                    }
                    result.code == ApiErrorCode.UNAUTHENTICATED -> {
                        // The session-ended flow signs out, and sign-out empties the queue.
                        dao.upsert(row.copy(state = ChangeState.Waiting.name))
                        return true
                    }
                    result.code.retryClass == RetryClass.Automatic || result.code == ApiErrorCode.VERSION_CONFLICT -> {
                        val attempts = row.attempts + 1
                        val wait = backoff.delayFor(attempts, result.retryAfterSeconds)
                        dao.upsert(row.copy(state = ChangeState.Waiting.name, attempts = attempts, nextAttemptAt = (now + wait).stamp()))
                        scheduler.enqueue(wait)
                        log.i { "retrying ${sent.recordType}/${sent.recordId} in $wait (${result.problem.code}, request ${result.problem.requestId})" }
                        return false
                    }
                    editedMeanwhile -> {
                        // The refusal was for content the person has already changed; send the new one.
                        dao.upsert(row.copy(state = ChangeState.Waiting.name))
                        scheduler.enqueue()
                        return false
                    }
                    else -> {
                        val refusal = Refusal(result.problem.code, handler.refusalFields(result.problem), result.problem.requestId)
                        dao.upsert(
                            row.copy(
                                state = ChangeState.Refused.name,
                                refusalJson = AppJson.encodeToString(Refusal.serializer(), refusal),
                                nextAttemptAt = null,
                            ),
                        )
                        log.i { "refused ${sent.recordType}/${sent.recordId}: ${result.problem.code} (request ${result.problem.requestId})" }
                        return false
                    }
                }
            }
            ApiResult.Offline, ApiResult.Timeout -> {
                val attempts = (current?.attempts ?: sent.attempts) + 1
                // The first failure retries as soon as a connection exists (SC-003). Repeated ones
                // mean "connected but unreachable" (captive portal, server down): the scheduler's
                // connectivity check passes at once, so without a wait this would loop nonstop.
                val wait = backoff.offlineDelay(attempts)
                current?.let {
                    dao.upsert(
                        it.copy(
                            state = ChangeState.Waiting.name,
                            attempts = attempts,
                            nextAttemptAt = if (wait == Duration.ZERO) null else (now + wait).stamp(),
                        ),
                    )
                }
                scheduler.enqueue(wait)
                return true
            }
        }
    }

    /** "Keep mine": resend on top of the other device's version, restoring it if it was deleted (R-05). */
    suspend fun keepMine(recordType: String, recordId: String) {
        rows.withLock {
            val row = dao.get(recordType, recordId) ?: return
            if (row.state != ChangeState.Clash.name) return
            val clash = row.clashJson?.let { AppJson.parseToJsonElement(it) } ?: return
            // A final deletion can't be undone: the only way out is "Accept the deletion" (useTheirs).
            if (clash.isTombstone() && handlers[recordType]?.canRestore == false) return
            dao.upsert(
                row.copy(
                    baseVersion = clash.version() ?: row.baseVersion,
                    restore = clash.isTombstone() && !row.isDelete,
                    state = ChangeState.Waiting.name,
                    clashJson = null,
                    attempts = 0,
                    nextAttemptAt = null,
                ),
            )
        }
        scheduler.enqueue()
    }

    /** "Use the other device's" / "Accept the deletion": the server copy wins, the change is dropped. */
    suspend fun useTheirs(recordType: String, recordId: String) = rows.withLock {
        val row = dao.get(recordType, recordId) ?: return@withLock
        val clash = row.clashJson?.let { AppJson.parseToJsonElement(it) } ?: return@withLock
        val handler = handlers[recordType]
        handler?.applyServerCopy(clash)
        dao.delete(recordType, recordId)
        handler?.onDiscarded(recordId)
    }

    /** Drops a change in any state; the server copy shows again. */
    suspend fun discard(recordType: String, recordId: String) = rows.withLock {
        dao.get(recordType, recordId) ?: return@withLock
        dao.delete(recordType, recordId)
        handlers[recordType]?.onDiscarded(recordId)
    }

    /** "Sync now": every waiting change is due at once (FR-010). */
    suspend fun syncNow() {
        dao.clearWaits()
        scheduler.enqueue(Duration.ZERO)
    }

    private companion object {
        val log = Logger.withTag("sync")

        /**
         * A fixed-width UTC instant (millisecond precision), so stored times compare as strings in
         * the DAO. `Instant.toString()` drops a zero fraction ("…:50Z" sorts after "…:50.1Z").
         */
        fun Instant.stamp(): String {
            val text = Instant.fromEpochMilliseconds(toEpochMilliseconds()).toString()
            return if ('.' in text) text else text.removeSuffix("Z") + ".000Z"
        }

        fun JsonElement.version(): Long? = (this as? JsonObject)?.get("version")?.jsonPrimitive?.longOrNull

        fun JsonElement.isTombstone(): Boolean {
            val deletedAt = (this as? JsonObject)?.get("deleted_at") ?: return false
            return deletedAt != JsonNull
        }
    }
}
