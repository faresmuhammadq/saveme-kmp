package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.transactions.data.dto.TransactionDto
import com.fares.saveme.feature.transactions.data.dto.TransactionFields
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.data.dto.TransactionWriteRequest
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Ids whose send succeeded and whose answer the engine applies next: the carry belongs to those (R-04). */
    private val ownAnswers = mutableSetOf<String>()
    private val ownLock = Mutex()

    override suspend fun send(change: PendingChange): ApiResult<JsonElement> {
        val id = change.recordId
        val stored = repository.get(id)
        val source = change.scope.id
        val pathWallet = if (walletOnPhone(source)) source else stored?.counterWalletId ?: source

        val result: ApiResult<TransactionDto> = when {
            change.isDelete -> api.delete(pathWallet, id, change.baseVersion)
            change.restore -> restoreThenSave(change, pathWallet)
            else -> api.put(pathWallet, id, writeRequest(change, stored, change.baseVersion))
        }
        if (result is ApiResult.Success) ownLock.withLock { ownAnswers += id }
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

    override suspend fun applyServerCopy(json: JsonElement) {
        val dto = AppJson.decodeFromJsonElement(TransactionDto.serializer(), json)
        val own = ownLock.withLock { ownAnswers.remove(dto.id) }
        repository.storeServerCopy(dto, own)
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
    }
}
