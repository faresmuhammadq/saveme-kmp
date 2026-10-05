package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.ProblemDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** In-memory stand-in that follows PendingChangeDao's SQL (the SQL itself: PendingChangeDaoTest). */
class FakePendingChangeDao : PendingChangeDao {
    val rows = MutableStateFlow<Map<Pair<String, String>, PendingChangeEntity>>(emptyMap())

    override suspend fun upsert(entity: PendingChangeEntity) {
        rows.value = rows.value + ((entity.recordType to entity.recordId) to entity)
    }

    override suspend fun get(type: String, id: String) = rows.value[type to id]

    override fun observe(type: String, id: String): Flow<PendingChangeEntity?> = rows.map { it[type to id] }

    override suspend fun forScope(kind: String, id: String) = rows.value.values
        .filter { it.scopeKind == kind && it.scopeId == id }
        .sortedBy { it.createdOrder }

    override fun observeByType(type: String): Flow<List<PendingChangeEntity>> =
        rows.map { r -> r.values.filter { it.recordType == type }.sortedBy { it.createdOrder } }

    override suspend fun due(accountId: String, now: String) = rows.value.values
        .filter { it.accountId == accountId && it.state == "Waiting" && (it.nextAttemptAt == null || it.nextAttemptAt <= now) }
        .sortedBy { it.createdOrder }

    override suspend fun nextDueAt(accountId: String) = rows.value.values
        .filter { it.accountId == accountId && it.state == "Waiting" }
        .minOfOrNull { it.nextAttemptAt.orEmpty() }

    override fun observeWaitingCount(): Flow<Int> = rows.map { r -> r.values.count { it.state == "Waiting" || it.state == "Sending" } }

    override fun observeNeedsDecisionCount(): Flow<Int> = rows.map { r -> r.values.count { it.state == "Clash" || it.state == "Refused" } }

    override suspend fun maxCreatedOrder() = rows.value.values.maxOfOrNull { it.createdOrder } ?: 0L

    override suspend fun resetSending() {
        rows.value = rows.value.mapValues { (_, r) -> if (r.state == "Sending") r.copy(state = "Waiting") else r }
    }

    override suspend fun clearWaits() {
        rows.value = rows.value.mapValues { (_, r) -> if (r.state == "Waiting") r.copy(nextAttemptAt = null) else r }
    }

    override suspend fun delete(type: String, id: String) {
        rows.value = rows.value - (type to id)
    }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }
}

/** Records what was scheduled; nothing runs on its own. */
class FakeSyncScheduler : SyncScheduler {
    val enqueued = mutableListOf<Duration>()
    var cancelled = 0
    override fun enqueue(delay: Duration) { enqueued += delay }
    override fun cancel() { cancelled++ }
}

class FakeClock(var now: Instant = Instant.parse("2026-10-02T10:00:00Z")) : Clock {
    override fun now(): Instant = now
}

/** A handler whose answers are scripted per send. */
class FakeHandler(
    override val recordType: String = "note",
    override val canRestore: Boolean = true,
    var answer: (PendingChange) -> ApiResult<JsonElement> = { ApiResult.Offline },
) : SyncHandler {
    val sent = mutableListOf<PendingChange>()
    val applied = mutableListOf<JsonElement>()
    val discarded = mutableListOf<String>()
    var duringSend: suspend () -> Unit = {}
    var accepts: (PendingChange, JsonElement) -> Boolean = { _, _ -> false }

    override suspend fun acceptsClash(change: PendingChange, current: JsonElement): Boolean = accepts(change, current)

    override suspend fun send(change: PendingChange): ApiResult<JsonElement> {
        sent += change
        duringSend()
        return answer(change)
    }

    override suspend fun applyServerCopy(json: JsonElement) { applied += json }

    override suspend fun onDiscarded(recordId: String) { discarded += recordId }

    override fun refusalFields(problem: ProblemDetails): Map<String, String> =
        problem.errors.orEmpty().associate { it.pointer.orEmpty().removePrefix("/") to it.code }
}
