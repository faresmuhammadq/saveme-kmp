package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.sync.BatchItem
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.RecordKey
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.transactions.data.dto.TransactionDto
import com.fares.saveme.feature.transactions.data.dto.TransactionFields
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.data.dto.TransactionRestoreRequest
import com.fares.saveme.feature.transactions.data.dto.TransactionWriteRequest
import com.fares.saveme.feature.transactions.data.dto.VersionOnlyRequest
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import kotlinx.serialization.json.JsonElement

/**
 * A transaction's way through the sync engine (research R-04): `PUT` to create or save, `DELETE`
 * with a JSON body, and for "Restore with my change" a `PATCH` followed by a `PUT`. The kind, a
 * transfer's destination and (for an existing transaction) the currency sent are always the stored
 * ones (FR-012). Nothing here logs amounts, notes or payloads (P-09.2).
 *
 * @param walletOnPhone whether the person still has the wallet on this phone; the path uses the
 *   source wallet unless it is gone, then the destination (ADR-0015 rule 5).
 */
class TransactionSyncHandler(
    private val repository: TransactionRepository,
    private val api: TransactionApi,
    private val walletOnPhone: suspend (String) -> Boolean,
) : SyncHandler {

    override val recordType: String = RECORD_TYPE

    override val canRestore: Boolean = true

    /** The wallet a write is addressed to: the source unless it is gone from the phone, then the destination (ADR-0015 rule 5). */
    private suspend fun pathWallet(change: PendingChange, stored: TransactionEntity?): String {
        val source = change.scope.id
        return if (walletOnPhone(source)) source else stored?.counterWalletId ?: source
    }

    /**
     * `PUT` / `DELETE` as an item, with the wallet it is addressed to (spec 009 FR-017, FR-024). "Restore with my
     * change" has no single item: [batchItems] sends it as a pair, and the per-record `PATCH` then `PUT` stays the
     * fallback.
     */
    override suspend fun batchItem(change: PendingChange): BatchItem? {
        if (change.restore && !change.isDelete) return null
        val stored = repository.get(change.recordId)
        val wallet = pathWallet(change, stored)
        return if (change.isDelete) {
            BatchItem(RECORD_TYPE, BatchOp.Delete, change.recordId, wallet, AppJson.encodeToJsonElement(VersionOnlyRequest.serializer(), VersionOnlyRequest(change.baseVersion)))
        } else {
            BatchItem(RECORD_TYPE, BatchOp.Upsert, change.recordId, wallet, writeRequest(change, stored, change.baseVersion).toJson())
        }
    }

    /**
     * A restore with the person's change is `restore` (version n) then `upsert` (version n + 1), both addressed to the
     * per-record path wallet and settled as one answer (spec 011 FR-014, R-09). The upsert carries the stored kind,
     * currency and counter wallet (spec 008 FR-012); a change whose content cannot be read sends the restore alone.
     */
    override suspend fun batchItems(change: PendingChange): List<BatchItem>? {
        if (!change.restore || change.isDelete) return batchItem(change)?.let(::listOf)
        val stored = repository.get(change.recordId)
        val wallet = pathWallet(change, stored)
        val restore = BatchItem(
            RECORD_TYPE, BatchOp.Restore, change.recordId, wallet,
            TransactionRestoreRequest(change.baseVersion).toJson(),
        )
        if (decode(change) == null) return listOf(restore)
        val upsert = BatchItem(RECORD_TYPE, BatchOp.Upsert, change.recordId, wallet, writeRequest(change, stored, change.baseVersion + 1).toJson())
        return listOf(restore, upsert)
    }

    /** The source wallet, a transfer's destination and the category must exist on the server first (spec 009 FR-022). */
    override suspend fun prerequisites(change: PendingChange): List<RecordKey> {
        if (change.isDelete) return emptyList()
        val payload = decode(change)
        val stored = repository.get(change.recordId)?.takeIf { it.version > 0 }
        return listOfNotNull(
            RecordKey(WALLET_TYPE, change.scope.id),
            (if (stored != null) stored.counterWalletId else payload?.counterWalletId)?.let { RecordKey(WALLET_TYPE, it) },
            payload?.categoryId?.let { RecordKey(CATEGORY_TYPE, it) },
        )
    }

    override suspend fun send(change: PendingChange): ApiResult<JsonElement> {
        val id = change.recordId
        val stored = repository.get(id)
        val pathWallet = pathWallet(change, stored)

        val result: ApiResult<TransactionDto> = when {
            change.isDelete -> api.delete(pathWallet, id, change.baseVersion)
            change.restore -> restoreThenSave(change, pathWallet)
            else -> api.put(pathWallet, id, writeRequest(change, stored, change.baseVersion))
        }
        return result.toJson()
    }

    private suspend fun restoreThenSave(change: PendingChange, pathWallet: String): ApiResult<TransactionDto> {
        val restored = when (val patch = api.restore(pathWallet, change.recordId, change.baseVersion)) {
            is ApiResult.Success -> patch.value
            else -> return patch
        }
        val payload = decode(change)
        if (payload == null || restored.matches(payload)) return ApiResult.Success(restored)
        val request = TransactionWriteRequest(
            kind = restored.kind,
            date = payload.date,
            amountMinor = payload.amountMinor,
            currency = restored.currency,
            categoryId = payload.categoryId,
            counterWalletId = restored.counterWalletId,
            description = payload.description,
            memo = payload.memo,
            counterparty = payload.counterparty,
            cleared = payload.cleared,
            version = restored.version,
        )
        // A failure here leaves the transaction live on the server; the engine shows it on the person's change (R-04).
        return api.put(pathWallet, change.recordId, request)
    }

    private fun writeRequest(change: PendingChange, stored: TransactionEntity?, version: Long): TransactionWriteRequest {
        val payload = checkNotNull(decode(change)) { "a transaction change without content" }
        // The kind and a transfer's wallets never change after creation; the stored ones win (FR-012).
        val existing = stored?.takeIf { it.version > 0 }
        return TransactionWriteRequest(
            kind = existing?.kind ?: payload.kind,
            date = payload.date,
            amountMinor = payload.amountMinor,
            currency = existing?.currency ?: payload.currency,
            categoryId = payload.categoryId,
            counterWalletId = if (existing != null) existing.counterWalletId else payload.counterWalletId,
            description = payload.description,
            memo = payload.memo,
            counterparty = payload.counterparty,
            cleared = payload.cleared,
            version = version,
        )
    }

    /** A copy that is not this phone's own answer: no carry, and a deletion removes the row at once (analysis A4). */
    override suspend fun applyServerCopy(json: JsonElement) {
        repository.storeServerCopy(AppJson.decodeFromJsonElement(TransactionDto.serializer(), json), own = false)
    }

    /** This phone's own answer, per record or as a batch item: its effect joins the row's carry until a wallet read confirms it (R-04, R-09). */
    override suspend fun applySentAnswer(json: JsonElement) {
        repository.storeServerCopy(AppJson.decodeFromJsonElement(TransactionDto.serializer(), json), own = true)
    }

    /** A transaction that never reached the server has no copy to fall back to: it disappears. */
    override suspend fun onDiscarded(recordId: String) {
        repository.removePlaceholder(recordId)
    }

    override fun refusalFields(problem: ProblemDetails): Map<String, String> =
        problem.errors.orEmpty()
            .mapNotNull { e -> TransactionFields.byPointer[e.pointer]?.let { it to e.code } }
            .toMap()

    private fun decode(change: PendingChange): TransactionPayload? =
        runCatching { AppJson.decodeFromString(TransactionPayload.serializer(), change.payloadJson) }.getOrNull()

    private fun TransactionDto.matches(payload: TransactionPayload) =
        date == payload.date && amountMinor == payload.amountMinor && categoryId == payload.categoryId &&
            description == payload.description && memo == payload.memo && counterparty == payload.counterparty &&
            cleared == payload.cleared

    private fun ApiResult<TransactionDto>.toJson(): ApiResult<JsonElement> = when (this) {
        is ApiResult.Success -> ApiResult.Success(AppJson.encodeToJsonElement(TransactionDto.serializer(), value))
        is ApiResult.Failure -> this
        ApiResult.Offline -> ApiResult.Offline
        ApiResult.Timeout -> ApiResult.Timeout
    }

    companion object {
        const val RECORD_TYPE = "transaction"

        /** The record types of the wallet and category handlers; `data` imports no other feature (P-10.6). */
        private const val WALLET_TYPE = "wallet"
        private const val CATEGORY_TYPE = "category"
    }
}
