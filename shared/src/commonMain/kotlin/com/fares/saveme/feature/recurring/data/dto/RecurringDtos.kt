package com.fares.saveme.feature.recurring.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * recurring.yaml `RecurringRule`. Enums stay strings so an unknown value decodes (P-02.3, research R-15);
 * members the client does not use are ignored.
 */
@Serializable
data class RecurringRuleDto(
    val id: String,
    @SerialName("wallet_id") val walletId: String,
    val version: Long,
    @SerialName("change_seq") val changeSeq: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    val preset: String,
    val status: String,
    val kind: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("amount_basis") val amountBasis: String = "per_occurrence",
    val currency: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("counter_wallet_id") val counterWalletId: String? = null,
    val description: String? = null,
    val memo: String? = null,
    val counterparty: String? = null,
    @SerialName("auto_confirm") val autoConfirm: Boolean = false,
    val frequency: String,
    val interval: Int = 1,
    @SerialName("anchor_day") val anchorDay: Int? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("occurrence_count") val occurrenceCount: Int? = null,
    @SerialName("payout_round") val payoutRound: Int? = null,
    @SerialName("payout_category_id") val payoutCategoryId: String? = null,
    val timezone: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("shared_budget_id") val sharedBudgetId: String? = null,
)

/** recurring.yaml `Occurrence`. */
@Serializable
data class OccurrenceDto(
    val id: String,
    @SerialName("wallet_id") val walletId: String,
    val version: Long,
    @SerialName("change_seq") val changeSeq: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("recurring_rule_id") val recurringRuleId: String,
    val ordinal: Int = 0,
    @SerialName("is_payout") val isPayout: Boolean = false,
    @SerialName("scheduled_date") val scheduledDate: String,
    val date: String,
    val status: String,
    val kind: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("counter_wallet_id") val counterWalletId: String? = null,
    val description: String? = null,
    val memo: String? = null,
    val counterparty: String? = null,
    val edited: Boolean = false,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("auto_confirm_refusal") val autoConfirmRefusal: String? = null,
)

private fun String?.orNull() = this?.let { JsonPrimitive(it) } ?: JsonNull
private fun Int?.orNull() = this?.let { JsonPrimitive(it) } ?: JsonNull

/**
 * recurring.yaml `RecurringRuleWrite`: exactly the 21 writable members, every nullable one as an explicit
 * `null` (the app's JSON drops nulls, so it is built by hand). `timezone`, `created_by` and `wallet_id` are
 * never sent.
 */
data class RecurringRuleWriteRequest(
    val preset: String,
    val status: String,
    val kind: String,
    val amountMinor: Long,
    val amountBasis: String,
    val currency: String,
    val categoryId: String?,
    val counterWalletId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val autoConfirm: Boolean,
    val frequency: String,
    val interval: Int,
    val anchorDay: Int?,
    val startDate: String,
    val endDate: String?,
    val occurrenceCount: Int?,
    val payoutRound: Int?,
    val payoutCategoryId: String?,
    val version: Long,
    /** Sent only when there is something to say: a budget, or clearing one that was set; an older server never sees the member. */
    val sharedBudgetId: String? = null,
    val sendSharedBudget: Boolean = false,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("preset", JsonPrimitive(preset))
        put("status", JsonPrimitive(status))
        put("kind", JsonPrimitive(kind))
        put("amount_minor", JsonPrimitive(amountMinor))
        put("amount_basis", JsonPrimitive(amountBasis))
        put("currency", JsonPrimitive(currency))
        put("category_id", categoryId.orNull())
        put("counter_wallet_id", counterWalletId.orNull())
        put("description", description.orNull())
        put("memo", memo.orNull())
        put("counterparty", counterparty.orNull())
        put("auto_confirm", JsonPrimitive(autoConfirm))
        put("frequency", JsonPrimitive(frequency))
        put("interval", JsonPrimitive(interval))
        put("anchor_day", anchorDay.orNull())
        put("start_date", JsonPrimitive(startDate))
        put("end_date", endDate.orNull())
        put("occurrence_count", occurrenceCount.orNull())
        put("payout_round", payoutRound.orNull())
        put("payout_category_id", payoutCategoryId.orNull())
        if (sendSharedBudget) put("shared_budget_id", sharedBudgetId.orNull())
        put("version", JsonPrimitive(version))
    }
}

/** The DELETE body of a rule: only the last-seen version. */
@Serializable
data class RuleVersionOnlyRequest(val version: Long)

/** recurring.yaml `OccurrenceWrite`: the nine writable members, explicit nulls. */
data class OccurrenceWriteRequest(
    val status: String,
    val date: String,
    val amountMinor: Long,
    val categoryId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val transactionId: String?,
    val version: Long,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("status", JsonPrimitive(status))
        put("date", JsonPrimitive(date))
        put("amount_minor", JsonPrimitive(amountMinor))
        put("category_id", categoryId.orNull())
        put("description", description.orNull())
        put("memo", memo.orNull())
        put("counterparty", counterparty.orNull())
        put("transaction_id", transactionId.orNull())
        put("version", JsonPrimitive(version))
    }
}
