package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.feature.account.FakeSyncScopeDao
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A handler that can say "as a batch item" (or "per record only" for the ids in [perRecordOnly]), with
 * scripted prerequisites; its per-record `send` is answered by [answer].
 */
class BatchHandler(
    override val recordType: String = "note",
    override val canRestore: Boolean = true,
    var answer: (PendingChange) -> ApiResult<JsonElement> = { ApiResult.Offline },
    val log: MutableList<String> = mutableListOf(),
) : SyncHandler {
    val perRecordOnly = mutableSetOf<String>()
    val prerequisitesOf = mutableMapOf<String, List<RecordKey>>()
    val sent = mutableListOf<PendingChange>()
    val applied = mutableListOf<JsonElement>()
    val discarded = mutableListOf<String>()
    var accepts: (PendingChange, JsonElement) -> Boolean = { _, _ -> false }

    override suspend fun acceptsClash(change: PendingChange, current: JsonElement): Boolean = accepts(change, current)

    override suspend fun batchItem(change: PendingChange): BatchItem? {
        if (change.recordId in perRecordOnly) return null
        val data = JsonObject(mapOf("payload" to JsonPrimitive(change.payloadJson), "version" to JsonPrimitive(change.baseVersion)))
        return BatchItem(
            recordType, if (change.isDelete) BatchOp.Delete else BatchOp.Upsert, change.recordId,
            walletId = if (recordType == "transaction") change.scope.id else null, data = data,
        )
    }

    override suspend fun prerequisites(change: PendingChange): List<RecordKey> = prerequisitesOf[change.recordId].orEmpty()

    override suspend fun send(change: PendingChange): ApiResult<JsonElement> {
        sent += change
        log += "send ${change.recordId}"
        return answer(change)
    }

    override suspend fun applyServerCopy(json: JsonElement) { applied += json }

    override suspend fun onDiscarded(recordId: String) { discarded += recordId }

    override fun refusalFields(problem: ProblemDetails): Map<String, String> =
        problem.errors.orEmpty().associate { it.pointer.orEmpty().removePrefix("/") to it.code }
}

/** The engine over fakes with the batch upload on: handlers for the given record types, one signed-in account. */
class BatchHarness(vararg types: String = arrayOf("note")) {
    val dao = FakePendingChangeDao()
    val scopes = FakeSyncScopeDao()
    val scheduler = FakeSyncScheduler()
    val clock = FakeClock()
    val api = FakeSyncApi()
    val log = mutableListOf<String>()
    val handlers = types.map { BatchHandler(it, log = log) }
    val handler get() = handlers.first()
    var account: String? = "u1"
    val engine = SyncEngine(dao, scopes, handlers, scheduler, currentAccountId = { account }, clock = clock, syncApi = api)
    val user = SyncScope(ScopeKind.User, "u1")

    init {
        api.batchAnswer = { items ->
            log += "batch ${items.size}: ${items.joinToString { it.id }}"
            ApiResult.Success(items.map { FakeSyncApi.appliedResult(it, version = it.data.let { d -> (d as JsonObject)["version"].let { v -> (v as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L } } + 1) })
        }
    }

    suspend fun save(id: String, payload: String = "{}", base: Long = 1, type: String = "note", scope: SyncScope = user) =
        engine.enqueue(type, id, scope, base, payload)

    fun row(id: String, type: String = "note") = dao.rows.value[type to id]

    fun record(version: Long, deleted: Boolean = false): JsonElement = EngineHarness.record(version, deleted)

    companion object {
        val NULL: JsonElement = JsonNull
    }
}
