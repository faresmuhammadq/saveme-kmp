package com.fares.saveme.feature.budgets.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * budgets.yaml `Budget`; members the client does not use (`user_id`) and members a later server adds are ignored
 * (P-02.3). The amounts are the person's own and never printed (P-09.2).
 */
@Serializable
data class BudgetDto(
    val id: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("period_type") val periodType: String = "monthly",
    @SerialName("amount_minor") val amountMinor: Long = 0,
    val currency: String = "",
    @SerialName("archived_at") val archivedAt: String? = null,
    val version: Long,
    @SerialName("change_seq") val changeSeq: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    override fun toString() = "BudgetDto($id, version=$version)"
}

/**
 * budgets.yaml `BudgetWrite`: exactly the writable members and the last-seen version (0 = create). Built by hand
 * because `category_id` is required-nullable: all expenses must travel as an explicit null, and the app's JSON
 * settings drop nulls (`explicitNulls = false`). Read-only members are never sent (FR-008).
 */
data class BudgetWriteRequest(
    val categoryId: String?,
    val periodType: String,
    val amountMinor: Long,
    val currency: String,
    val version: Long,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("category_id", categoryId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("period_type", JsonPrimitive(periodType))
        put("amount_minor", JsonPrimitive(amountMinor))
        put("currency", JsonPrimitive(currency))
        put("version", JsonPrimitive(version))
    }

    override fun toString() = "BudgetWriteRequest(version=$version)"
}

/** budgets.yaml `BudgetVersionOnly`: the delete and archive bodies. */
@Serializable
data class BudgetVersionRequest(val version: Long)

/** components `Money`. */
@Serializable
data class MoneyDto(
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
) {
    override fun toString() = "MoneyDto($currency)"
}

@Serializable
data class BudgetPeriodDto(
    val start: String,
    val end: String,
    @SerialName("days_in_period") val daysInPeriod: Int,
    @SerialName("days_elapsed") val daysElapsed: Int? = null,
)

/**
 * budgets.yaml `BudgetProgress`. There is no percentage member: the share is derived on the phone (FR-013). Only the
 * budget, the period, the limit, the spent amount and the other currencies are stored; the rest is re-derived.
 */
@Serializable
data class BudgetProgressDto(
    @SerialName("budget_id") val budgetId: String,
    val period: BudgetPeriodDto,
    @SerialName("is_current") val isCurrent: Boolean = true,
    val limit: MoneyDto,
    val spent: MoneyDto,
    val remaining: MoneyDto? = null,
    @SerialName("projected_end") val projectedEnd: MoneyDto? = null,
    @SerialName("over_limit") val overLimit: Boolean = false,
    @SerialName("other_currencies") val otherCurrencies: List<String> = emptyList(),
) {
    override fun toString() = "BudgetProgressDto($budgetId)"
}
