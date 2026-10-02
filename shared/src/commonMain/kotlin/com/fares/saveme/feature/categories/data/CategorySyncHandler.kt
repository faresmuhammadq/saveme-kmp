package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.sync.BatchItem
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.categories.data.dto.CategoryDto
import com.fares.saveme.feature.categories.data.dto.CategoryPayload
import com.fares.saveme.feature.categories.data.dto.CategoryVersionRequest
import com.fares.saveme.feature.categories.data.dto.CategoryWriteRequest
import com.fares.saveme.feature.categories.data.local.CategoryEntity
import kotlinx.serialization.json.JsonElement

/**
 * A category's way through the sync engine (research R-04): `PUT` to create or save, then the
 * archive call when asked, or `DELETE` with a JSON body. Deletion is final, so [canRestore] is
 * false. The kind sent is always the stored one (FR-008). Nothing here logs.
 */
class CategorySyncHandler(
    private val repository: CategoryRepository,
    private val api: CategoryApi,
) : SyncHandler {

    override val recordType: String = RECORD_TYPE

    override val canRestore: Boolean = false

    /** `PUT` / `DELETE` as an item with the stored kind; an archive has its own endpoint, so it goes per record (spec 009 FR-018). */
    override suspend fun batchItem(change: PendingChange): BatchItem? {
        if (change.isDelete) {
            return BatchItem(RECORD_TYPE, BatchOp.Delete, change.recordId, null, AppJson.encodeToJsonElement(CategoryVersionRequest.serializer(), CategoryVersionRequest(change.baseVersion)))
        }
        val payload = AppJson.decodeFromString(CategoryPayload.serializer(), change.payloadJson)
        if (payload.archive) return null
        val stored = repository.get(change.recordId)
        return BatchItem(RECORD_TYPE, BatchOp.Upsert, change.recordId, null, writeRequest(payload, stored, change.baseVersion).toJson())
    }

    private fun writeRequest(payload: CategoryPayload, stored: CategoryEntity?, version: Long) = CategoryWriteRequest(
        kind = stored?.kind ?: payload.kind,
        name = payload.name,
        icon = payload.icon,
        color = payload.color,
        version = version,
    )

    override suspend fun send(change: PendingChange): ApiResult<JsonElement> {
        if (change.isDelete) return api.delete(change.recordId, change.baseVersion).toJson()
        val payload = AppJson.decodeFromString(CategoryPayload.serializer(), change.payloadJson)
        val stored = repository.get(change.recordId)

        // An archive-only change has nothing to PUT; sending the unchanged fields would only add a round trip.
        val skipPut = payload.archive && change.baseVersion > 0 && stored != null && stored.matches(payload)
        var version = change.baseVersion
        var last: CategoryDto? = null
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
        return ApiResult.Success(AppJson.encodeToJsonElement(CategoryDto.serializer(), checkNotNull(last)))
    }

    override suspend fun applyServerCopy(json: JsonElement) {
        repository.storeServerCopy(AppJson.decodeFromJsonElement(CategoryDto.serializer(), json))
    }

    /** A category that never reached the server has no copy to fall back to: it disappears. */
    override suspend fun onDiscarded(recordId: String) {
        repository.removePlaceholder(recordId)
    }

    override fun refusalFields(problem: ProblemDetails): Map<String, String> =
        problem.errors.orEmpty()
            .mapNotNull { e -> FIELD_POINTERS[e.pointer]?.let { it to e.code } }
            .toMap()

    private fun CategoryEntity.matches(payload: CategoryPayload) =
        name == payload.name && icon == payload.icon && color == payload.color

    private fun ApiResult<CategoryDto>.toJson(): ApiResult<JsonElement> = when (this) {
        is ApiResult.Success -> ApiResult.Success(AppJson.encodeToJsonElement(CategoryDto.serializer(), value))
        is ApiResult.Failure -> this
        ApiResult.Offline -> ApiResult.Offline
        ApiResult.Timeout -> ApiResult.Timeout
    }

    companion object {
        const val RECORD_TYPE = "category"

        /** Validation pointers the editor can show next to a field, as plain field names. */
        private val FIELD_POINTERS = mapOf("/name" to "name", "/kind" to "kind", "/icon" to "icon", "/color" to "color")
    }
}
