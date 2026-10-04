package com.fares.saveme.feature.budgets.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.sync.BatchItem
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.RecordKey
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.budgets.data.dto.BudgetDto
import com.fares.saveme.feature.budgets.data.dto.BudgetVersionRequest
import com.fares.saveme.feature.budgets.data.dto.BudgetWriteRequest
import com.fares.saveme.feature.budgets.data.local.BudgetEntity
import kotlinx.serialization.json.JsonElement

/**
 * A budget's way through the sync engine (research R-04): a `PUT` to create or save, then the archive call when asked, or
 * `DELETE` with a JSON body. Deletion is final, so [canRestore] is false. Category, currency and period type of a budget
 * the server already has are always the stored ones (immutable members, FR-005). The wire type is a local constant
 * (P-10.4). Nothing here logs an amount.
 */
class BudgetSyncHandler(
    private val repository: BudgetRepository,
    private val api: BudgetApi,
) : SyncHandler {

    override val recordType: String = RECORD_TYPE

    override val canRestore: Boolean = false

    /** `PUT` / `DELETE` as an item without a wallet; an archive has its own endpoint, so it goes per record (spec 009 FR-018). */
    override suspend fun batchItem(change: PendingChange): BatchItem? {
        if (change.isDelete) {
            return BatchItem(RECORD_TYPE, BatchOp.Delete, change.recordId, null, AppJson.encodeToJsonElement(BudgetVersionRequest.serializer(), BudgetVersionRequest(change.baseVersion)))
        }
        val payload = AppJson.decodeFromString(BudgetPayload.serializer(), change.payloadJson)
        if (payload.archive) return null
        val stored = repository.get(change.recordId)
        return BatchItem(RECORD_TYPE, BatchOp.Upsert, change.recordId, null, writeRequest(payload, stored, change.baseVersion).toJson())
    }

    private fun writeRequest(payload: BudgetPayload, stored: BudgetEntity?, version: Long): BudgetWriteRequest {
        val server = stored?.takeIf { it.version > 0 }
        return BudgetWriteRequest(
            categoryId = if (server != null) server.categoryId else payload.categoryId,
            periodType = server?.periodType ?: BudgetRepository.MONTHLY,
            amountMinor = payload.amountMinor,
            currency = server?.currency ?: payload.currency,
            version = version,
        )
    }

    override suspend fun send(change: PendingChange): ApiResult<JsonElement> {
        if (change.isDelete) return api.delete(change.recordId, change.baseVersion).toJson()
        val payload = AppJson.decodeFromString(BudgetPayload.serializer(), change.payloadJson)
        val stored = repository.get(change.recordId)

        // An archive-only change has nothing to PUT; sending the unchanged limit would only add a round trip.
        val skipPut = payload.archive && change.baseVersion > 0 && stored != null && stored.amountMinor == payload.amountMinor
        var version = change.baseVersion
        var last: BudgetDto? = null
        if (!skipPut) {
            when (val put = api.put(change.recordId, writeRequest(payload, stored, change.baseVersion))) {
                is ApiResult.Success -> {
                    last = put.value
                    version = put.value.version
                }
                else -> return put.toJson()
            }
        }
        val alreadyArchived = last?.archivedAt != null
        if (payload.archive && !alreadyArchived) return api.archive(change.recordId, version).toJson()
        // Reaching here means the PUT ran (a skipped PUT always archives above).
        return ApiResult.Success(AppJson.encodeToJsonElement(BudgetDto.serializer(), checkNotNull(last)))
    }

    override suspend fun applyServerCopy(json: JsonElement) {
        repository.storeServerCopy(AppJson.decodeFromJsonElement(BudgetDto.serializer(), json))
    }

    /** A category made on this phone and not yet accepted holds the budget that names it (spec 009 FR-022). */
    override suspend fun prerequisites(change: PendingChange): List<RecordKey> {
        if (change.isDelete) return emptyList()
        val categoryId = runCatching { AppJson.decodeFromString(BudgetPayload.serializer(), change.payloadJson) }.getOrNull()?.categoryId
        return listOfNotNull(categoryId?.let { RecordKey(CATEGORY_TYPE, it) })
    }

    /** A budget that never reached the server has no copy to fall back to: it disappears. */
    override suspend fun onDiscarded(recordId: String) {
        repository.removePlaceholder(recordId)
    }

    override fun refusalFields(problem: ProblemDetails): Map<String, String> =
        problem.errors.orEmpty()
            .mapNotNull { e -> FIELD_POINTERS[e.pointer]?.let { it to e.code } }
            .toMap()

    private fun ApiResult<BudgetDto>.toJson(): ApiResult<JsonElement> = when (this) {
        is ApiResult.Success -> ApiResult.Success(AppJson.encodeToJsonElement(BudgetDto.serializer(), value))
        is ApiResult.Failure -> this
        ApiResult.Offline -> ApiResult.Offline
        ApiResult.Timeout -> ApiResult.Timeout
    }

    companion object {
        const val RECORD_TYPE = "budget"

        /** The record type of the categories a budget may name; a local constant, not an import of that feature. */
        private const val CATEGORY_TYPE = "category"

        /** Validation pointers the editor can show next to a field, as plain field names. */
        private val FIELD_POINTERS = mapOf(
            "/category_id" to "category", "/amount_minor" to "limit", "/currency" to "currency", "/period_type" to "period",
        )
    }
}
