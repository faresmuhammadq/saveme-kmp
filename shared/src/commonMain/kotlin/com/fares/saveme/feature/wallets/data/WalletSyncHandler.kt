package com.fares.saveme.feature.wallets.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.sync.BatchItem
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.wallets.data.dto.VersionOnlyRequest
import com.fares.saveme.feature.wallets.data.dto.WalletDto
import com.fares.saveme.feature.wallets.data.dto.WalletWriteRequest
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletField
import kotlinx.serialization.json.JsonElement

/**
 * A wallet's way through the sync engine (research R-04): `PUT` to create or save, then the
 * archive call when the draft asks for it, or `DELETE` with a JSON body. Deletion is final, so
 * [canRestore] is false. Only ids and outcomes are logged by the engine; nothing here logs.
 */
class WalletSyncHandler(
    private val repository: WalletRepository,
    private val api: WalletApi,
    private val accountId: () -> String? = { null },
) : SyncHandler {

    override val recordType: String = RECORD_TYPE

    override val canRestore: Boolean = false

    private fun writeRequest(draft: WalletDraft, version: Long) = WalletWriteRequest(
        type = draft.type, name = draft.name, currency = draft.currency,
        openingBalanceMinor = draft.openingBalanceMinor, icon = draft.icon, color = draft.color,
        version = version,
    )

    /** `PUT` / `DELETE` as an item; an archive has its own endpoint, so it goes per record (spec 009 FR-018). */
    override suspend fun batchItem(change: PendingChange): BatchItem? {
        if (change.isDelete) {
            return BatchItem(RECORD_TYPE, BatchOp.Delete, change.recordId, null, AppJson.encodeToJsonElement(VersionOnlyRequest.serializer(), VersionOnlyRequest(change.baseVersion)))
        }
        val draft = AppJson.decodeFromString(WalletDraft.serializer(), change.payloadJson)
        if (draft.archive) return null
        return BatchItem(RECORD_TYPE, BatchOp.Upsert, change.recordId, null, writeRequest(draft, change.baseVersion).toJson())
    }

    override suspend fun send(change: PendingChange): ApiResult<JsonElement> {
        if (change.isDelete) return api.delete(change.recordId, change.baseVersion).toJson()
        val draft = AppJson.decodeFromString(WalletDraft.serializer(), change.payloadJson)
        val stored = repository.get(change.recordId)

        // An archive-only change has nothing to PUT; sending the unchanged fields would only add a round trip.
        val skipPut = draft.archive && change.baseVersion > 0 && stored != null && stored.matches(draft)
        var version = change.baseVersion
        var last: WalletDto? = null
        if (!skipPut) {
            when (val put = api.put(change.recordId, writeRequest(draft, change.baseVersion))) {
                is ApiResult.Success -> {
                    last = put.value
                    version = put.value.version
                }
                else -> return put.toJson()
            }
        }
        val alreadyArchived = last?.archivedAt != null
        if (draft.archive && !alreadyArchived) return api.archive(change.recordId, version).toJson()
        // Reaching here means the PUT ran (a skipped PUT always archives above).
        return ApiResult.Success(AppJson.encodeToJsonElement(WalletDto.serializer(), checkNotNull(last)))
    }

    override suspend fun applyServerCopy(json: JsonElement) {
        repository.storeServerCopy(AppJson.decodeFromJsonElement(WalletDto.serializer(), json))
    }

    /**
     * A wallet that never reached the server has no copy to fall back to: it disappears. One the person may
     * have lost while this change waited (no live membership) is read again from the start, which removes it.
     */
    override suspend fun onDiscarded(recordId: String) {
        repository.removePlaceholder(recordId)
        repository.reconsiderAfterDiscard(recordId, accountId().orEmpty())
    }

    override fun refusalFields(problem: ProblemDetails): Map<String, String> =
        problem.errors.orEmpty()
            .mapNotNull { e -> WalletField.fromPointer(e.pointer)?.let { it.name to e.code } }
            .toMap()

    private fun com.fares.saveme.feature.wallets.data.local.WalletEntity.matches(draft: WalletDraft) =
        type == draft.type && name == draft.name && currency == draft.currency &&
            openingBalanceMinor == draft.openingBalanceMinor && icon == draft.icon && color == draft.color

    private fun ApiResult<WalletDto>.toJson(): ApiResult<JsonElement> = when (this) {
        is ApiResult.Success -> ApiResult.Success(AppJson.encodeToJsonElement(WalletDto.serializer(), value))
        is ApiResult.Failure -> this
        ApiResult.Offline -> ApiResult.Offline
        ApiResult.Timeout -> ApiResult.Timeout
    }

    companion object {
        const val RECORD_TYPE = "wallet"
    }
}
