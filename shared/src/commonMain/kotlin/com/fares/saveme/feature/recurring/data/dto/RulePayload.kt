package com.fares.saveme.feature.recurring.data.dto

import kotlinx.serialization.Serializable

/**
 * What a rule's waiting change holds in `pending_change.payload_json` (data-model §5): every
 * `RecurringRuleWrite` member except `version`, plus the phone-only [followsRuleId]. Not a wire type; the
 * domain maps its draft to and from it so `data` imports nothing from `domain`. The wallet is the queue
 * row's scope. A delete is `isDelete` and uses no payload.
 */
@Serializable
data class RulePayload(
    val preset: String,
    val status: String,
    val kind: String,
    val amountMinor: Long,
    val amountBasis: String,
    val currency: String,
    val categoryId: String? = null,
    val counterWalletId: String? = null,
    val description: String? = null,
    val memo: String? = null,
    val counterparty: String? = null,
    val autoConfirm: Boolean = false,
    val frequency: String,
    val interval: Int,
    val anchorDay: Int? = null,
    /** `YYYY-MM-DD`. */
    val startDate: String,
    val endDate: String? = null,
    val occurrenceCount: Int? = null,
    val payoutRound: Int? = null,
    val payoutCategoryId: String? = null,
    /** Phone-only: the rule this one continues after "Change from a date" (research R-09). */
    val followsRuleId: String? = null,
)

/**
 * What an occurrence's waiting change holds (data-model §5): the nine writable members without the
 * version. [transactionId] is the UUIDv7 of a confirmation, generated once and kept by every later edit of
 * the same waiting change (FR-024); null for skip, un-skip and edits.
 */
@Serializable
data class OccurrencePayload(
    val status: String,
    /** `YYYY-MM-DD`. */
    val date: String,
    val amountMinor: Long,
    val categoryId: String? = null,
    val description: String? = null,
    val memo: String? = null,
    val counterparty: String? = null,
    val transactionId: String? = null,
)

/** The plain field names the handlers' `refusalFields` return (research R-04, R-05). */
object RecurringFields {
    const val AMOUNT = "amount"
    const val AMOUNT_BASIS = "amountBasis"
    const val CATEGORY = "category"
    const val PAYOUT_CATEGORY = "payoutCategory"
    const val DESTINATION = "destination"
    const val CURRENCY = "currency"
    const val FREQUENCY = "frequency"
    const val INTERVAL = "interval"
    const val DAY = "day"
    const val START = "start"
    const val END = "end"
    const val COUNT = "count"
    const val PAYOUT_ROUND = "payoutRound"
    const val STATUS = "status"
    const val PRESET = "preset"
    const val KIND = "kind"
    const val DATE = "date"
    const val NOTE = "note"
    const val DETAILS = "details"
    const val PERSON = "person"

    val ruleByPointer: Map<String, String> = mapOf(
        "/amount_minor" to AMOUNT,
        "/amount_basis" to AMOUNT_BASIS,
        "/category_id" to CATEGORY,
        "/payout_category_id" to PAYOUT_CATEGORY,
        "/counter_wallet_id" to DESTINATION,
        "/currency" to CURRENCY,
        "/frequency" to FREQUENCY,
        "/interval" to INTERVAL,
        "/anchor_day" to DAY,
        "/start_date" to START,
        "/end_date" to END,
        "/occurrence_count" to COUNT,
        "/payout_round" to PAYOUT_ROUND,
        "/status" to STATUS,
        "/preset" to PRESET,
        "/kind" to KIND,
        "/description" to NOTE,
        "/memo" to DETAILS,
        "/counterparty" to PERSON,
    )

    val occurrenceByPointer: Map<String, String> = mapOf(
        "/status" to STATUS,
        "/date" to DATE,
        "/amount_minor" to AMOUNT,
        "/category_id" to CATEGORY,
        "/transaction_id" to STATUS,
        "/description" to NOTE,
        "/memo" to DETAILS,
        "/counterparty" to PERSON,
    )
}
