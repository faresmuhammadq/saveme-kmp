package com.fares.saveme.feature.transactions.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** transactions.yaml `Transaction`; members the client does not use are ignored (P-02.3). */
@Serializable
data class TransactionDto(
    val id: String,
    @SerialName("wallet_id") val walletId: String,
    val kind: String,
    val date: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("counter_wallet_id") val counterWalletId: String? = null,
    val description: String? = null,
    val memo: String? = null,
    val counterparty: String? = null,
    val cleared: Boolean = false,
    @SerialName("created_by") val createdBy: String = "",
    @SerialName("recurring_rule_id") val recurringRuleId: String? = null,
    @SerialName("occurrence_id") val occurrenceId: String? = null,
    /** The Shared Budget it counts toward (backend ADR-0027); absent from a server older than v1.16. */
    @SerialName("shared_budget_id") val sharedBudgetId: String? = null,
    val version: Long,
    @SerialName("change_seq") val changeSeq: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** transactions.yaml `TransactionWrite`: exactly the writable members plus the last-seen version (0 = create). */
data class TransactionWriteRequest(
    val kind: String,
    val date: String,
    val amountMinor: Long,
    val currency: String,
    val categoryId: String?,
    val counterWalletId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val cleared: Boolean,
    val version: Long,
    /** The budget to count toward; null clears it. Sent only when [sendSharedBudget], so a server older than v1.16 never sees the member. */
    val sharedBudgetId: String? = null,
    val sendSharedBudget: Boolean = false,
) {
    /**
     * Built by hand because every nullable member is required-nullable: it is sent as an explicit
     * `null`, and the app's JSON settings drop nulls (`explicitNulls = false`).
     */
    fun toJson(): JsonObject = buildJsonObject {
        put("kind", JsonPrimitive(kind))
        put("date", JsonPrimitive(date))
        put("amount_minor", JsonPrimitive(amountMinor))
        put("currency", JsonPrimitive(currency))
        put("category_id", categoryId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("counter_wallet_id", counterWalletId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("description", description?.let { JsonPrimitive(it) } ?: JsonNull)
        put("memo", memo?.let { JsonPrimitive(it) } ?: JsonNull)
        put("counterparty", counterparty?.let { JsonPrimitive(it) } ?: JsonNull)
        put("cleared", JsonPrimitive(cleared))
        put("version", JsonPrimitive(version))
        if (sendSharedBudget) put("shared_budget_id", sharedBudgetId?.let { JsonPrimitive(it) } ?: JsonNull)
    }
}

/** transactions.yaml `TransactionRestore`: `{"deleted_at": null, "version": n}` with the null present. */
data class TransactionRestoreRequest(val version: Long) {
    fun toJson(): JsonObject = buildJsonObject {
        put("deleted_at", JsonNull)
        put("version", JsonPrimitive(version))
    }
}

/** The DELETE body: only the last-seen version. */
@Serializable
data class VersionOnlyRequest(val version: Long)
