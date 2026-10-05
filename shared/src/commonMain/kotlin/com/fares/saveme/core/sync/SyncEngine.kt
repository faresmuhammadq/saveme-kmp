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
    /** The batch upload (spec 009); without it every change is sent per record, as in spec 004. */
    private val syncApi: SyncApi? = null,
    /** Shared with the feed puller; one drain at a time, so `Sending` rows found at the start are always leftovers. */
    private val lock: SyncLock = SyncLock(),
) {
    private val handlers = handlers.associateBy { it.recordType }

    /** Guards every read-modify-write of a row. */
    private val rows = Mutex()

    val status: Flow<SyncStatus> = combine(
        dao.observeWaitingCount(),
        dao.observeNeedsDecisionCount(),
        scopes.observeLastPushed(),
    ) { waiting, decision, last ->
        SyncStatus(waiting, decision, last?.let { runCatching { Instant.parse(it) }.getOrNull() })
    }

    fun observe(recordType: String, recordId: String): Flow<PendingChange?> =
        dao.observe(recordType, recordId).map { it?.toDomain() }

    /** Every waiting, sent-but-unanswered, clashed or refused change of one record type. */
    fun observeAll(recordType: String): Flow<List<PendingChange>> =
        dao.observeByType(recordType).map { rows -> rows.map { it.toDomain() } }

    suspend fun get(recordType: String, recordId: String): PendingChange? = dao.get(recordType, recordId)?.toDomain()

    /** Every change of one scope in any state, oldest first; read-only (spec 013: what leaving a wallet would discard). */
    suspend fun pendingIn(scope: SyncScope): List<PendingChange> = dao.forScope(scope.kind.wire, scope.id).map { it.toDomain() }

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
     * Sends every due change of the signed-in account, oldest first (FR-006, FR-007): through the
     * batch upload, up to 50 per request, each item answered on its own (spec 009 FR-017, FR-019).
     * A change the batch cannot carry is sent per record at its place in the order (FR-018). Called
     * by the runner; safe to call at any time. Returns the scopes written to.
     */
    suspend fun drain(): DrainReport = lock.withLock {
        loadAccount()
        val account = currentAccountId() ?: return@withLock DrainReport()
        dao.resetSending()
        val run = DrainRun(account)
        for (row in dao.due(account, clock.now().syncStamp())) {
            if (currentAccountId() != account) return@withLock run.report()
            val handler = handlers[row.recordType]
            if (handler == null) {
                log.w { "no handler for ${row.recordType}; left waiting" }
                continue
            }
            val change = row.toDomain()
            val prerequisites = handler.prerequisites(change)
            val items = if (syncApi != null) handler.batchItems(change).orEmpty() else emptyList()
            if (items.isEmpty()) {
                if (flush(run)) return@withLock run.report()
                if (isHeld(prerequisites, emptySet())) {
                    run.held = true
                    continue
                }
                if (sendOne(run, Entry(row, handler, emptyList(), prerequisites))) return@withLock run.report()
            } else {
                // The items of one change travel together: never split across two requests (spec 011 FR-014).
                if (run.itemCount + items.size > MAX_BATCH && flush(run)) return@withLock run.report()
                if (isHeld(prerequisites, run.keys)) {
                    run.held = true
                    continue
                }
                run.entries += Entry(row, handler, items, prerequisites)
                run.keys += items.first().key
                if (run.itemCount >= MAX_BATCH && flush(run)) return@withLock run.report()
            }
        }
        if (flush(run)) return@withLock run.report()
        // A prerequisite accepted in this run lets what it held go next run (FR-022).
        if (run.appliedAny && run.held) scheduler.enqueue()
        // A run can start before a row's wait is over (the scheduler's timing is approximate, and a
        // row can be rescheduled while this run was queued). Without this the row would wait until
        // the app is next opened. Rows due now but unsent (no handler, or held) are not rescheduled,
        // or this would loop.
        dao.nextDueAt(account)?.takeIf { it.isNotEmpty() }?.let { next ->
            scheduler.enqueue((Instant.parse(next) - clock.now()).coerceAtLeast(Duration.ZERO))
        }
        run.report()
    }

    /** What one drain has done so far. */
    private class DrainRun(val account: String) {
        val entries = mutableListOf<Entry>()
        val keys = mutableSetOf<RecordKey>()
        val written = linkedSetOf<SyncScope>()
        var appliedAny = false
        var held = false
        var offline = false

        /** Items waiting to be sent: a restore pair counts two (spec 011). */
        val itemCount: Int get() = entries.sumOf { it.items.size }

        fun report() = DrainReport(written, appliedAny, offline)
    }

    /**
     * A due row with its handler, its batch items (empty = per record; a transaction restore has two, settled as one
     * answer, spec 011 R-09) and what it needs first.
     */
    private class Entry(
        val row: PendingChangeEntity,
        val handler: SyncHandler,
        val items: List<BatchItem>,
        val prerequisites: List<RecordKey>,
    )

    /**
     * A prerequisite blocks only while its own create (base version 0) still waits on this phone
     * and is not an item earlier in the same request (spec 009 R-07).
     */
    private suspend fun isHeld(prerequisites: List<RecordKey>, inRequest: Set<RecordKey>): Boolean =
        prerequisites.any { key ->
            if (key.untilSettled) {
                dao.get(key.recordType, key.recordId) != null
            } else {
                key !in inRequest && dao.get(key.recordType, key.recordId)?.baseVersion == 0L
            }
        }

    /** Sends the batch built so far; true when nothing else can be sent now. */
    private suspend fun flush(run: DrainRun): Boolean {
        val entries = run.entries.toList()
        run.entries.clear()
        run.keys.clear()
        if (entries.isEmpty()) return false
        val api = checkNotNull(syncApi) { "batch entries without a batch client" }
        rows.withLock { entries.forEach { dao.upsert(it.row.copy(state = ChangeState.Sending.name)) } }
        val itemCount = entries.sumOf { it.items.size }
        val answer = api.batch(entries.flatMap { it.items }.map { it.toDto() })
        // Signed out while the request was in flight: sign-out emptied the queue and the tables, so nothing is applied (FR-028).
        if (currentAccountId() != run.account) return true
        return when {
            answer is ApiResult.Success && answerMatches(entries, answer.value) -> {
                log.i { "batch of $itemCount items answered" }
                settleItems(run, entries, answer.value)
            }
            answer is ApiResult.Success -> {
                log.w { "batch answer does not match its $itemCount items; sending per record" }
                sendEach(run, entries)
            }
            answer is ApiResult.Failure && answer.isShapeOrEndpointRefusal() -> {
                log.w { "batch refused as a whole (${answer.problem.code.ifEmpty { "no code" }}, status ${answer.problem.status}, request ${answer.problem.requestId}); sending per record" }
                sendEach(run, entries)
            }
            else -> {
                // A request-level answer is every item's answer, as a single write would have had it (FR-020).
                val failure = answer as ApiResult<Nothing>
                log.i {
                    "batch of $itemCount items not answered: " +
                        (if (answer is ApiResult.Failure) "${answer.problem.code} (request ${answer.problem.requestId})" else answer::class.simpleName)
                }
                if (answer is ApiResult.Offline || answer is ApiResult.Timeout) run.offline = true
                var stop = false
                for (entry in entries) stop = rows.withLock { settle(entry.row, entry.handler, failure) } || stop
                stop
            }
        }
    }

    /** Each item's answer through [settle]; a dependent of an item that was not accepted waits instead (FR-022). */
    private suspend fun settleItems(run: DrainRun, entries: List<Entry>, results: List<BatchItemResultDto>): Boolean {
        val notAccepted = mutableSetOf<RecordKey>()
        var stop = false
        var next = 0
        for (entry in entries) {
            val slice = results.subList(next, next + entry.items.size)
            next += entry.items.size
            val key = entry.items.first().key
            if (entry.prerequisites.any { it in notAccepted }) {
                returnToWaiting(entry.row)
                notAccepted += key
                continue
            }
            val answer = reduce(slice.map { it.toApiResult() })
            stop = settleOne(run, entry, answer) || stop
            if (answer !is ApiResult.Success) notAccepted += key
        }
        return stop
    }

    /** The answers of one change's items as one: the first that was not accepted, else the last (spec 011 R-09). */
    private fun reduce(answers: List<ApiResult<JsonElement>>): ApiResult<JsonElement> =
        answers.firstOrNull { it !is ApiResult.Success } ?: answers.last()

    /** The per-record fallback for a batch (FR-021): the same rows, in the same order, one request each. */
    private suspend fun sendEach(run: DrainRun, entries: List<Entry>): Boolean {
        val notAccepted = mutableSetOf<RecordKey>()
        for (entry in entries) {
            val key = entry.items.firstOrNull()?.key ?: RecordKey(entry.row.recordType, entry.row.recordId)
            if (entry.prerequisites.any { it in notAccepted }) {
                returnToWaiting(entry.row)
                notAccepted += key
                continue
            }
            if (currentAccountId() != run.account) return true
            val answer = entry.handler.send(entry.row.toDomain())
            if (currentAccountId() != run.account) return true
            val stop = settleOne(run, entry, answer)
            if (answer !is ApiResult.Success) notAccepted += key
            if (stop) return true
        }
        return false
    }

    /** One change on its own request (spec 004 path). */
    private suspend fun sendOne(run: DrainRun, entry: Entry): Boolean {
        rows.withLock { dao.upsert(entry.row.copy(state = ChangeState.Sending.name)) }
        val answer = entry.handler.send(entry.row.toDomain())
        if (currentAccountId() != run.account) return true
        return settleOne(run, entry, answer)
    }

    private suspend fun settleOne(run: DrainRun, entry: Entry, answer: ApiResult<JsonElement>): Boolean {
        val stop = rows.withLock { settle(entry.row, entry.handler, answer) }
        when (answer) {
            is ApiResult.Success -> {
                run.appliedAny = true
                run.written += SyncScope(ScopeKind.fromWire(entry.row.scopeKind), entry.row.scopeId)
            }
            ApiResult.Offline, ApiResult.Timeout -> run.offline = true
            is ApiResult.Failure -> Unit
        }
        return stop
    }

    /** The change goes back to waiting with nothing counted: its prerequisite was not accepted. */
    private suspend fun returnToWaiting(row: PendingChangeEntity) = rows.withLock {
        val current = dao.get(row.recordType, row.recordId) ?: return@withLock
        if (current.state == ChangeState.Sending.name) dao.upsert(current.copy(state = ChangeState.Waiting.name))
    }

    private fun answerMatches(entries: List<Entry>, results: List<BatchItemResultDto>): Boolean =
        results.size == entries.sumOf { it.items.size } && entries.flatMap { it.items }.zip(results).all { (item, result) ->
            result.id == item.id && result.type == item.type && when (result.outcome) {
                OUTCOME_APPLIED -> result.record != null
                OUTCOME_CONFLICT, OUTCOME_REJECTED -> result.problem != null
                else -> false
            }
        }

    private fun BatchItemResultDto.toApiResult(): ApiResult<JsonElement> = when (outcome) {
        OUTCOME_APPLIED -> ApiResult.Success(checkNotNull(record))
        else -> {
            val problem = checkNotNull(problem)
            ApiResult.Failure(problem, ApiErrorCode.from(problem.code), problem.retryAfterSeconds)
        }
    }

    private fun BatchItem.toDto() = BatchItemDto(type = type, op = op.wire, id = id, walletId = walletId, data = data)

    /**
     * Refused for a reason that is not about the changes: the request shape, the endpoint, the
     * negotiated type, or an answer that could not be read (spec 009 FR-021).
     */
    private fun ApiResult.Failure.isShapeOrEndpointRefusal(): Boolean = problem.status == 200 ||
        code in FALLBACK_CODES || (code == ApiErrorCode.UNKNOWN && problem.status in FALLBACK_STATUSES)

    /** Applies one send's answer; returns true when nothing else can be sent now (no connection). */
    private suspend fun settle(sent: PendingChangeEntity, handler: SyncHandler, result: ApiResult<JsonElement>): Boolean {
        val now = clock.now()
        // The row as it is now: the person may have edited or discarded it while it was in flight.
        val current = dao.get(sent.recordType, sent.recordId)
        val editedMeanwhile = current != null && current.revision != sent.revision
        when (result) {
            is ApiResult.Success -> {
                handler.applySentAnswer(result.value)
                scopes.markPushed(SyncScope(ScopeKind.fromWire(sent.scopeKind), sent.scopeId).key, now.syncStamp())
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
                    result.code == ApiErrorCode.VERSION_CONFLICT && clash != null &&
                        handler.acceptsClash(row.toDomain(), clash) -> {
                        handler.applyServerCopy(clash)
                        dao.delete(sent.recordType, sent.recordId)
                        handler.onDiscarded(sent.recordId)
                        log.i { "clash accepted on ${sent.recordType}/${sent.recordId} (request ${result.problem.requestId})" }
                        return false
                    }
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
                        dao.upsert(row.copy(state = ChangeState.Waiting.name, attempts = attempts, nextAttemptAt = (now + wait).syncStamp()))
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
                        val refusal = Refusal(result.problem.code, handler.refusalFields(result.problem), result.problem.requestId, result.problem.feature)
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
                            nextAttemptAt = if (wait == Duration.ZERO) null else (now + wait).syncStamp(),
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

        const val MAX_BATCH = 50
        const val OUTCOME_APPLIED = "applied"
        const val OUTCOME_CONFLICT = "conflict"
        const val OUTCOME_REJECTED = "rejected"

        val FALLBACK_CODES = setOf(
            ApiErrorCode.VALIDATION_FAILED, ApiErrorCode.MALFORMED_JSON, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE,
            ApiErrorCode.NOT_ACCEPTABLE, ApiErrorCode.NOT_FOUND,
        )
        val FALLBACK_STATUSES = setOf(404, 405, 406, 415)

        fun JsonElement.version(): Long? = (this as? JsonObject)?.get("version")?.jsonPrimitive?.longOrNull

        fun JsonElement.isTombstone(): Boolean {
            val deletedAt = (this as? JsonObject)?.get("deleted_at") ?: return false
            return deletedAt != JsonNull
        }
    }
}

/** What one upload run did: the scopes it wrote to, whether anything was accepted, and whether the connection failed. */
data class DrainReport(
    val written: Set<SyncScope> = emptySet(),
    val appliedAny: Boolean = false,
    val offline: Boolean = false,
)
