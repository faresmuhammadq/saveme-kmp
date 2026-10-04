package com.fares.saveme.feature.recurring.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.sync.BatchItem
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.RecordKey
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.recurring.data.dto.OccurrenceDto
import com.fares.saveme.feature.recurring.data.dto.OccurrencePayload
import com.fares.saveme.feature.recurring.data.dto.OccurrenceWriteRequest
import com.fares.saveme.feature.recurring.data.dto.RecurringFields
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * An occurrence's way through the sync engine (research R-05): `PUT` only, never `DELETE` and never a
 * create (ADR-0018); the version is always at least 1. A confirmation carries the payload's
 * `transaction_id`, generated once. A clash with an occurrence confirmed elsewhere is accepted without
 * asking (R-06). The deletion of an occurrence is final ([canRestore] is false).
 *
 * @param queuedPayload the change waiting for an occurrence, read when a server copy arrives for it.
 * @param dropConfirmation drops the phone-only transaction row of an occurrence's confirmation, except the
 *   one with the given id (research R-07); implemented by transactions, passed as a lambda (P-10.6).
 */
class OccurrenceSyncHandler(
    private val repository: OccurrenceRepository,
    private val api: RecurringApi,
    private val queuedPayload: suspend (String) -> OccurrencePayload?,
    private val dropConfirmation: suspend (occurrenceId: String, exceptTransactionId: String?) -> Unit,
) : SyncHandler {

    override val recordType: String = RECORD_TYPE

    override val canRestore: Boolean = false

    override suspend fun batchItem(change: PendingChange): BatchItem =
        BatchItem(RECORD_TYPE, BatchOp.Upsert, change.recordId, change.scope.id, writeRequest(change).toJson())

    /** An edit to a category made on this phone needs that category on the server first. */
    override suspend fun prerequisites(change: PendingChange): List<RecordKey> =
        listOfNotNull(decode(change)?.categoryId?.let { RecordKey(CATEGORY_TYPE, it) })

    override suspend fun send(change: PendingChange): ApiResult<JsonElement> {
        val result: ApiResult<OccurrenceDto> = api.putOccurrence(change.scope.id, change.recordId, writeRequest(change))
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(AppJson.encodeToJsonElement(OccurrenceDto.serializer(), result.value))
            is ApiResult.Failure -> result
            ApiResult.Offline -> ApiResult.Offline
            ApiResult.Timeout -> ApiResult.Timeout
        }
    }

    private fun writeRequest(change: PendingChange): OccurrenceWriteRequest {
        val payload = checkNotNull(decode(change)) { "an occurrence change without content" }
        return OccurrenceWriteRequest(
            status = payload.status,
            date = payload.date,
            amountMinor = payload.amountMinor,
            categoryId = payload.categoryId,
            description = payload.description,
            memo = payload.memo,
            counterparty = payload.counterparty,
            transactionId = payload.transactionId,
            version = change.baseVersion,
        )
    }

    /** Whatever the phone's change was, a confirmed occurrence never changes: nothing is left to choose. */
    override suspend fun acceptsClash(change: PendingChange, current: JsonElement): Boolean {
        val obj = current as? JsonObject ?: return false
        val deleted = obj["deleted_at"]?.takeIf { it !is JsonNull }
        return deleted == null && obj["status"]?.jsonPrimitive?.contentOrNull == STATUS_CONFIRMED
    }

    /**
     * A copy that is not this phone's own answer. When it is confirmed under another transaction id than the
     * one waiting here, the person is told it was already confirmed and the phone-only transaction goes.
     */
    override suspend fun applyServerCopy(json: JsonElement) {
        val dto = AppJson.decodeFromJsonElement(OccurrenceDto.serializer(), json)
        val queued = queuedPayload(dto.id)
        val confirmedElsewhere = dto.status == STATUS_CONFIRMED && dto.deletedAt == null && queued != null &&
            queued.transactionId != dto.transactionId
        repository.storeCopy(dto, respectQueued = false, noticeAlreadyConfirmed = confirmedElsewhere)
        if (dto.status == STATUS_CONFIRMED && dto.transactionId != null) dropConfirmation(dto.id, dto.transactionId)
    }

    /** This phone's own answer: the occurrence copy only; the balance is the transactions side's (R-07). */
    override suspend fun applySentAnswer(json: JsonElement) {
        repository.storeCopy(AppJson.decodeFromJsonElement(OccurrenceDto.serializer(), json), respectQueued = false)
    }

    /** A dropped confirmation (discard, "Use the other device's", accepted clash, deletion) takes its phone-only transaction with it. */
    override suspend fun onDiscarded(recordId: String) {
        dropConfirmation(recordId, null)
    }

    override fun refusalFields(problem: ProblemDetails): Map<String, String> =
        problem.errors.orEmpty()
            .mapNotNull { e -> RecurringFields.occurrenceByPointer[e.pointer]?.let { it to e.code } }
            .toMap()

    private fun decode(change: PendingChange): OccurrencePayload? =
        runCatching { AppJson.decodeFromString(OccurrencePayload.serializer(), change.payloadJson) }.getOrNull()

    companion object {
        const val RECORD_TYPE = "occurrence"
        private const val CATEGORY_TYPE = "category"
        private const val STATUS_CONFIRMED = "confirmed"
    }
}
