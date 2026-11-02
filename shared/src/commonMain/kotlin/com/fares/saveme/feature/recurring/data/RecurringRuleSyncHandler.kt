package com.fares.saveme.feature.recurring.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.sync.BatchItem
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.RecordKey
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.recurring.data.dto.RecurringFields
import com.fares.saveme.feature.recurring.data.dto.RecurringRuleDto
import com.fares.saveme.feature.recurring.data.dto.RecurringRuleWriteRequest
import com.fares.saveme.feature.recurring.data.dto.RuleVersionOnlyRequest
import com.fares.saveme.feature.recurring.data.dto.RulePayload
import com.fares.saveme.feature.recurring.data.local.RecurringRuleEntity
import kotlinx.serialization.json.JsonElement

/**
 * A rule's way through the sync engine (research R-04): `PUT` to create or save, `DELETE` with a JSON body.
 * The preset, kind, destination and (for a rule the server has) currency sent are always the stored ones;
 * a never-sent rule sends the wallet's present currency (FR-007). A rule's deletion is final
 * ([canRestore] is false). Nothing here logs amounts, notes or payloads (P-09.2).
 *
 * @param walletCurrency the currency of a wallet on this phone, null when it is gone.
 * @param discardRule drops the queued change of a rule, launched outside the engine lock by the caller
 *   (spec 008 lesson); used to cancel a split's new rule when the old rule's change is dropped (R-09).
 */
class RecurringRuleSyncHandler(
    private val repository: RecurringRepository,
    private val api: RecurringApi,
    private val walletCurrency: suspend (String) -> String?,
    private val discardRule: (String) -> Unit,
) : SyncHandler {

    override val recordType: String = RECORD_TYPE

    override val canRestore: Boolean = false

    override suspend fun batchItem(change: PendingChange): BatchItem {
        val wallet = change.scope.id
        return if (change.isDelete) {
            BatchItem(RECORD_TYPE, BatchOp.Delete, change.recordId, wallet, AppJson.encodeToJsonElement(RuleVersionOnlyRequest.serializer(), RuleVersionOnlyRequest(change.baseVersion)))
        } else {
            BatchItem(RECORD_TYPE, BatchOp.Upsert, change.recordId, wallet, writeRequest(change, repository.get(change.recordId), change.baseVersion).toJson())
        }
    }

    /** The wallet, the categories and a transfer's destination first; a split's new rule after the old rule's change is settled (R-09). */
    override suspend fun prerequisites(change: PendingChange): List<RecordKey> {
        if (change.isDelete) return emptyList()
        val payload = decode(change) ?: return emptyList()
        return listOfNotNull(
            RecordKey(WALLET_TYPE, change.scope.id),
            payload.counterWalletId?.let { RecordKey(WALLET_TYPE, it) },
            payload.categoryId?.let { RecordKey(CATEGORY_TYPE, it) },
            payload.payoutCategoryId?.let { RecordKey(CATEGORY_TYPE, it) },
            payload.followsRuleId?.let { RecordKey(RECORD_TYPE, it, untilSettled = true) },
        )
    }

    override suspend fun send(change: PendingChange): ApiResult<JsonElement> {
        val wallet = change.scope.id
        val result: ApiResult<RecurringRuleDto> = if (change.isDelete) {
            api.deleteRule(wallet, change.recordId, change.baseVersion)
        } else {
            api.putRule(wallet, change.recordId, writeRequest(change, repository.get(change.recordId), change.baseVersion))
        }
        return result.toJson()
    }

    private suspend fun writeRequest(change: PendingChange, stored: RecurringRuleEntity?, version: Long): RecurringRuleWriteRequest {
        val payload = checkNotNull(decode(change)) { "a rule change without content" }
        val existing = stored?.takeIf { it.version > 0 }
        return RecurringRuleWriteRequest(
            preset = existing?.preset ?: payload.preset,
            status = payload.status,
            kind = existing?.kind ?: payload.kind,
            amountMinor = payload.amountMinor,
            amountBasis = payload.amountBasis,
            currency = existing?.currency ?: walletCurrency(change.scope.id) ?: payload.currency,
            categoryId = payload.categoryId,
            counterWalletId = if (existing != null) existing.counterWalletId else payload.counterWalletId,
            description = payload.description,
            memo = payload.memo,
            counterparty = payload.counterparty,
            autoConfirm = payload.autoConfirm,
            frequency = payload.frequency,
            interval = payload.interval,
            anchorDay = payload.anchorDay,
            startDate = payload.startDate,
            endDate = payload.endDate,
            occurrenceCount = payload.occurrenceCount,
            payoutRound = payload.payoutRound,
            payoutCategoryId = payload.payoutCategoryId,
            version = version,
            sharedBudgetId = payload.sharedBudgetId,
            sendSharedBudget = payload.sharedBudgetId != null || existing?.sharedBudgetId != null,
        )
    }

    /** A copy that is not this phone's own answer (clash copy, "Use the other device's", "Accept the deletion"). */
    override suspend fun applyServerCopy(json: JsonElement) {
        repository.storeCopy(AppJson.decodeFromJsonElement(RecurringRuleDto.serializer(), json), respectQueued = false)
    }

    /**
     * A rule that never reached the server has no copy to fall back to: it disappears. Rules that continue it
     * after "Change from a date" are cancelled with it (R-09), outside the engine lock.
     */
    override suspend fun onDiscarded(recordId: String) {
        repository.removePlaceholder(recordId)
        repository.following(recordId).forEach { discardRule(it.id) }
    }

    override fun refusalFields(problem: ProblemDetails): Map<String, String> =
        problem.errors.orEmpty()
            .mapNotNull { e -> RecurringFields.ruleByPointer[e.pointer]?.let { it to e.code } }
            .toMap()

    private fun decode(change: PendingChange): RulePayload? =
        runCatching { AppJson.decodeFromString(RulePayload.serializer(), change.payloadJson) }.getOrNull()

    private fun ApiResult<RecurringRuleDto>.toJson(): ApiResult<JsonElement> = when (this) {
        is ApiResult.Success -> ApiResult.Success(AppJson.encodeToJsonElement(RecurringRuleDto.serializer(), value))
        is ApiResult.Failure -> this
        ApiResult.Offline -> ApiResult.Offline
        ApiResult.Timeout -> ApiResult.Timeout
    }

    companion object {
        const val RECORD_TYPE = "recurring_rule"

        /** The record types of the wallet and category handlers; `data` imports no other feature (P-10.6). */
        private const val WALLET_TYPE = "wallet"
        private const val CATEGORY_TYPE = "category"
    }
}
