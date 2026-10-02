package com.fares.saveme.core.sync

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What a caller wants read (spec 009 research R-10). */
sealed interface ReadTarget {
    /** The person's scope and every followed wallet: sign-in, foreground, the wallet list. */
    data object Everything : ReadTarget

    /** Only the person's scope: the categories, account and sessions screens. */
    data object User : ReadTarget

    /** One wallet: its transactions, detail or members. */
    data class Wallet(val id: String) : ReadTarget
}

/** How the reads a caller asked for ended. `core` never returns a feature's outcome type (P-10.7); services map this. */
enum class ReadOutcome { Done, Offline, Failed }

/**
 * Serializes sync runs: upload first, then reads (spec 009 FR-026). Triggers that arrive during a
 * run are merged into the next one, not lost; every caller gets the outcome of the run that served
 * it. Floors, outcomes and the order of work live here, so the features' services keep their
 * signatures and screens do not change (SC-008).
 */
interface SyncRunner {
    /** Uploads what waits, then reads [target]; without [force] a scope read within the last minute is skipped. */
    suspend fun refresh(target: ReadTarget, force: Boolean): ReadOutcome

    /** Uploads what waits and reads the scopes that upload wrote to; returns when that run is done. */
    suspend fun upload()

    /** Starts an [upload] run without waiting for it (a save with the app open is sent at once). */
    fun requestUpload()

    /** The background worker's entry: [upload]. */
    suspend fun runBackground()

    /** Sign-out: stops the run in progress and answers everyone waiting as failed. */
    fun cancel()
}

/** Stops the sync run at sign-out; the database cleaner empties the queue and the positions itself. */
class SyncSignOutCleaner(private val runner: () -> SyncRunner) : com.fares.saveme.core.session.UserDataCleaner {
    override suspend fun clear() = runner().cancel()
}

class DefaultSyncRunner(
    private val engine: SyncEngine,
    private val puller: FeedPuller,
    private val walletScopes: () -> WalletScopes?,
    private val currentAccountId: () -> String?,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val floor: Duration = 60.seconds,
) : SyncRunner {

    private class Request(val target: ReadTarget?, val force: Boolean) {
        val result = CompletableDeferred<ReadOutcome>()
    }

    private val state = Mutex()
    private val pending = mutableListOf<Request>()
    private var running: Job? = null
    private var inFlight: List<Request> = emptyList()
    private val lastRead = mutableMapOf<String, Instant>()

    override suspend fun refresh(target: ReadTarget, force: Boolean): ReadOutcome = submit(Request(target, force)).result.await()

    override suspend fun upload() {
        submit(Request(null, false)).result.await()
    }

    override suspend fun runBackground() = upload()

    override fun requestUpload() {
        scope.launch { submit(Request(null, false)) }
    }

    override fun cancel() {
        running?.cancel()
    }

    private suspend fun submit(request: Request): Request {
        state.withLock {
            pending += request
            if (running?.isActive != true) running = scope.launch { loop() }
        }
        return request
    }

    private suspend fun loop() {
        val self = currentCoroutineContext()[Job]
        try {
            while (true) {
                val batch = state.withLock {
                    if (pending.isEmpty()) {
                        if (running === self) running = null
                        return
                    }
                    pending.toList().also { pending.clear() }
                }
                inFlight = batch
                try {
                    run(batch)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.e { "sync run failed (${e::class.simpleName})" }
                }
                batch.forEach { it.result.complete(ReadOutcome.Failed) }
                inFlight = emptyList()
            }
        } catch (e: CancellationException) {
            // Cancelled (sign-out): nobody keeps waiting.
            withContext(NonCancellable) {
                val abandoned = state.withLock {
                    if (running === self) running = null
                    (inFlight + pending).also { pending.clear() }
                }
                inFlight = emptyList()
                abandoned.forEach { it.result.complete(ReadOutcome.Failed) }
            }
            throw e
        }
    }

    /** One run: upload, then reads (FR-026). Completes the batch's requests with their outcomes. */
    private suspend fun run(batch: List<Request>) {
        val account = currentAccountId()
        if (account == null) {
            batch.forEach { it.result.complete(ReadOutcome.Failed) }
            return
        }
        val report = engine.drain()
        if (report.offline) {
            // A run whose upload found no connection skips its reads and reports offline (FR-026).
            batch.forEach { it.result.complete(ReadOutcome.Offline) }
            return
        }
        val reads = batch.filter { it.target != null }
        val wantsEverything = reads.any { it.target == ReadTarget.Everything }
        val userScope = SyncScope(ScopeKind.User, account)
        val readUser = wantsEverything || reads.any { it.target == ReadTarget.User } || userScope in report.written
        val results = linkedMapOf<String, ScopeResult>()
        val readRun = ReadRun(account)

        // The scopes to read and whether each is forced: what an upload wrote to is always read.
        val walletForce = linkedMapOf<String, Boolean>()
        fun want(id: String, force: Boolean) {
            walletForce[id] = (walletForce[id] ?: false) || force
        }
        reads.forEach { request -> (request.target as? ReadTarget.Wallet)?.let { want(it.id, request.force) } }
        report.written.filter { it.kind == ScopeKind.Wallet }.forEach { want(it.id, true) }

        var stopped = false
        if (readUser) {
            val force = userScope in report.written || reads.any { (it.target == ReadTarget.Everything || it.target == ReadTarget.User) && it.force }
            val read = readScope(userScope, force, readRun)
            results[userScope.key] = read.result
            stopped = read.result == ScopeResult.Offline
            val completed = read.read as? ScopeRead.Done
            // Wallets held whose own membership a complete read did not return: their own feeds decide (FR-011).
            completed?.seenWalletIds?.let { seen -> walletScopes()?.unconfirmed(seen)?.forEach { want(it, true) } }
        }
        if (wantsEverything && !stopped) {
            val everythingForced = reads.any { it.target == ReadTarget.Everything && it.force }
            walletScopes()?.followed()?.forEach { want(it, everythingForced) }
        }
        val readWallets = linkedSetOf<String>()
        for ((id, force) in walletForce) {
            val key = SyncScope(ScopeKind.Wallet, id).key
            if (stopped) {
                results[key] = ScopeResult.Offline
                continue
            }
            val wallets = walletScopes()
            if (wallets != null && !wallets.isAccepted(id)) {
                // A wallet made on this phone has no feed yet (spec 009 edge case).
                results[key] = ScopeResult.Done
                continue
            }
            val read = readScope(SyncScope(ScopeKind.Wallet, id), force, readRun)
            results[key] = read.result
            readWallets += id
            if (read.result == ScopeResult.Offline) stopped = true
        }

        val followedForEverything = if (wantsEverything) walletForce.keys else emptySet()
        batch.forEach { request ->
            val outcome = when (val target = request.target) {
                null -> ReadOutcome.Done
                ReadTarget.User -> results[userScope.key].toOutcome()
                is ReadTarget.Wallet -> results[SyncScope(ScopeKind.Wallet, target.id).key].toOutcome()
                ReadTarget.Everything -> combine(
                    listOf(results[userScope.key]) + followedForEverything.map { results[SyncScope(ScopeKind.Wallet, it).key] },
                )
            }
            request.result.complete(outcome)
        }
    }

    private enum class ScopeResult { Done, Offline, Failed }

    private class Read(val result: ScopeResult, val read: ScopeRead?)

    private suspend fun readScope(scope: SyncScope, force: Boolean, readRun: ReadRun): Read {
        val last = lastRead[scope.key]
        if (!force && last != null && clock.now() - last < floor) return Read(ScopeResult.Done, null)
        return when (val read = puller.read(scope, readRun)) {
            is ScopeRead.Done -> {
                lastRead[scope.key] = clock.now()
                Read(ScopeResult.Done, read)
            }
            ScopeRead.Offline -> Read(ScopeResult.Offline, read)
            ScopeRead.Failed -> Read(ScopeResult.Failed, read)
        }
    }

    private fun ScopeResult?.toOutcome(): ReadOutcome = when (this) {
        ScopeResult.Offline -> ReadOutcome.Offline
        ScopeResult.Failed -> ReadOutcome.Failed
        ScopeResult.Done, null -> ReadOutcome.Done
    }

    /** Done when every read completed, offline when any could not reach the server, failed otherwise (the spec 007 rule, FR-015). */
    private fun combine(results: List<ScopeResult?>): ReadOutcome = when {
        results.any { it == ScopeResult.Offline } -> ReadOutcome.Offline
        results.any { it == ScopeResult.Failed } -> ReadOutcome.Failed
        else -> ReadOutcome.Done
    }

    private companion object {
        val log = Logger.withTag("sync")
    }
}
