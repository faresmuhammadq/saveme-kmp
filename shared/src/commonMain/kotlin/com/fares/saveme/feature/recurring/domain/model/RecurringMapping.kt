package com.fares.saveme.feature.recurring.domain.model

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.feature.recurring.data.dto.OccurrencePayload
import com.fares.saveme.feature.recurring.data.dto.RulePayload
import com.fares.saveme.feature.recurring.data.local.OccurrenceEntity
import com.fares.saveme.feature.recurring.data.local.RecurringRuleEntity
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

private val epoch = Instant.fromEpochSeconds(0)

private fun parseInstant(value: String?): Instant = value?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: epoch

internal fun parseDate(value: String): LocalDate = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate(1970, 1, 1))

internal fun RecurringRuleEntity.toRule() = Rule(
    id = id, walletId = walletId, presetWire = preset, statusWire = status, kindWire = kind, amountMinor = amountMinor,
    amountBasisWire = amountBasis, currency = currency, categoryId = categoryId, counterWalletId = counterWalletId,
    description = description, memo = memo, counterparty = counterparty, autoConfirm = autoConfirm, frequencyWire = frequency,
    interval = interval, anchorDay = anchorDay, startDate = parseDate(startDate), endDate = endDate?.let(::parseDate),
    occurrenceCount = occurrenceCount, payoutRound = payoutRound, payoutCategoryId = payoutCategoryId, timezone = timezone,
    createdBy = createdBy, version = version, createdAt = parseInstant(createdAt), updatedAt = parseInstant(updatedAt),
    followsRuleId = followsRuleId,
)

internal fun OccurrenceEntity.toOccurrence() = Occurrence(
    id = id, walletId = walletId, ruleId = recurringRuleId, ordinal = ordinal, isPayout = isPayout,
    scheduledDate = parseDate(scheduledDate), date = parseDate(date), statusWire = status, kindWire = kind,
    amountMinor = amountMinor, currency = currency, categoryId = categoryId, counterWalletId = counterWalletId,
    description = description, memo = memo, counterparty = counterparty, edited = edited, transactionId = transactionId,
    autoConfirmRefusalWire = autoConfirmRefusal, version = version, updatedAt = parseInstant(updatedAt), localNotice = localNotice,
)

internal fun Rule.toDraft() = RuleDraft(
    preset = preset, kind = kind, amountMinor = amountMinor, amountBasis = amountBasis, categoryId = categoryId,
    counterWalletId = counterWalletId, description = description, memo = memo, counterparty = counterparty,
    autoConfirm = autoConfirm, frequency = frequency, interval = interval, anchorDay = anchorDay, startDate = startDate,
    endDate = endDate, occurrenceCount = occurrenceCount, payoutRound = payoutRound, payoutCategoryId = payoutCategoryId,
)

/** The queue content of a rule change; wire values that are unknown stay as the server sent them (via [rule]). */
internal fun RuleDraft.toPayload(status: String, currency: String, followsRuleId: String? = null, rule: Rule? = null) = RulePayload(
    preset = rule?.presetWire ?: preset.wire,
    status = status,
    kind = rule?.kindWire ?: kind.wire,
    amountMinor = amountMinor,
    amountBasis = amountBasis.wire,
    currency = currency,
    categoryId = categoryId,
    counterWalletId = counterWalletId,
    description = description,
    memo = memo,
    counterparty = counterparty,
    autoConfirm = autoConfirm,
    frequency = frequency.wire,
    interval = interval,
    anchorDay = anchorDay,
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    occurrenceCount = occurrenceCount,
    payoutRound = payoutRound,
    payoutCategoryId = payoutCategoryId,
    followsRuleId = followsRuleId,
)

internal fun RulePayload.toDraft() = RuleDraft(
    preset = Preset.fromWire(preset), kind = TransactionKind.fromWire(kind), amountMinor = amountMinor,
    amountBasis = AmountBasis.fromWire(amountBasis), categoryId = categoryId, counterWalletId = counterWalletId,
    description = description, memo = memo, counterparty = counterparty, autoConfirm = autoConfirm,
    frequency = Frequency.fromWire(frequency), interval = interval, anchorDay = anchorDay, startDate = parseDate(startDate),
    endDate = endDate?.let(::parseDate), occurrenceCount = occurrenceCount, payoutRound = payoutRound,
    payoutCategoryId = payoutCategoryId,
)

internal fun decodeRulePayload(json: String): RulePayload? =
    runCatching { AppJson.decodeFromString(RulePayload.serializer(), json) }.getOrNull()

internal fun decodeOccurrencePayload(json: String): OccurrencePayload? =
    runCatching { AppJson.decodeFromString(OccurrencePayload.serializer(), json) }.getOrNull()

internal fun OccurrencePayload.toPending() = OccurrencePending(
    status = OccurrenceStatus.fromWire(status), date = parseDate(date), amountMinor = amountMinor, categoryId = categoryId,
    description = description, memo = memo, counterparty = counterparty, transactionId = transactionId,
)

/** Maps the queue row (research R-04, R-05) to what the screens show; [heldOn] names the rule a split waits for. */
internal fun PendingChange?.toRecurringSyncState(heldOn: String? = null): RecurringSyncState = when (this?.state) {
    null -> RecurringSyncState.None
    ChangeState.Waiting, ChangeState.Sending -> if (heldOn != null) RecurringSyncState.Held(heldOn) else RecurringSyncState.Waiting
    ChangeState.Clash -> {
        val deleted = clashJson?.let { json ->
            val stamp = runCatching { (AppJson.parseToJsonElement(json) as? JsonObject)?.get("deleted_at") }.getOrNull()
            stamp != null && stamp !is JsonNull
        } ?: true
        RecurringSyncState.Clash(deletedElsewhere = deleted, mineIsDeletion = isDelete)
    }
    ChangeState.Refused -> RecurringSyncState.Refused(refusal?.code, refusal?.requestId, refusal?.fields.orEmpty())
}
