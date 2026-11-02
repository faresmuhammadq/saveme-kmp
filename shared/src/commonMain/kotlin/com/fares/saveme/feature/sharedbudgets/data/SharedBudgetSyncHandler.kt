package com.fares.saveme.feature.sharedbudgets.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.sync.BatchItem
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetVersionRequest
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetWriteRequest
import kotlinx.serialization.json.JsonElement

/**
 * A Shared Budget's way through the sync engine: a `PUT` of the whole writable budget to create or save, or a `DELETE` with a
 * JSON body. Both go in a batch. Deletion is final, so [canRestore] is false. The currency of a budget the server already has
 * is always the stored one (immutable member). Archive, lines, members and invitations are not queued: they are direct calls.
 * Nothing here logs an amount or a name.
 */
class SharedBudgetSyncHandler(
    private val repository: SharedBudgetRepository,
    private val api: SharedBudgetApi,
) : SyncHandler {

    override val recordType: String = RECORD_TYPE

    override val canRestore: Boolean = false

    override suspend fun batchItem(change: PendingChange): BatchItem {
        if (change.isDelete) {
            return BatchItem(RECORD_TYPE, BatchOp.Delete, change.recordId, null, AppJson.encodeToJsonElement(SharedBudgetVersionRequest.serializer(), SharedBudgetVersionRequest(change.baseVersion)))
        }
        return BatchItem(RECORD_TYPE, BatchOp.Upsert, change.recordId, null, writeRequest(change).toJson())
    }

    private suspend fun writeRequest(change: PendingChange): SharedBudgetWriteRequest {
        val payload = AppJson.decodeFromString(SharedBudgetPayload.serializer(), change.payloadJson)
        val server = repository.get(change.recordId)?.takeIf { it.version > 0 }
        return SharedBudgetWriteRequest(payload.name, server?.currency ?: payload.currency, payload.startDay, payload.limitMinor, change.baseVersion)
    }

    override suspend fun send(change: PendingChange): ApiResult<JsonElement> =
        if (change.isDelete) api.delete(change.recordId, change.baseVersion).toJson() else api.put(change.recordId, writeRequest(change)).toJson()

    override suspend fun applyServerCopy(json: JsonElement) {
        repository.storeServerCopy(AppJson.decodeFromJsonElement(SharedBudgetDto.serializer(), json))
    }

    /** A budget that never reached the server has no copy to fall back to: it disappears. */
    override suspend fun onDiscarded(recordId: String) {
        repository.removePlaceholder(recordId)
    }

    override fun refusalFields(problem: ProblemDetails): Map<String, String> =
        problem.errors.orEmpty().mapNotNull { e -> FIELD_POINTERS[e.pointer]?.let { it to e.code } }.toMap()

    private fun ApiResult<SharedBudgetDto>.toJson(): ApiResult<JsonElement> = when (this) {
        is ApiResult.Success -> ApiResult.Success(AppJson.encodeToJsonElement(SharedBudgetDto.serializer(), value))
        is ApiResult.Failure -> this
        ApiResult.Offline -> ApiResult.Offline
        ApiResult.Timeout -> ApiResult.Timeout
    }

    companion object {
        const val RECORD_TYPE = "shared_budget"

        private val FIELD_POINTERS = mapOf("/name" to "name", "/currency" to "currency", "/start_day" to "start_day", "/limit_minor" to "limit")
    }
}
