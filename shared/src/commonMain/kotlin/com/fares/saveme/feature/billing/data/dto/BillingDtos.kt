package com.fares.saveme.feature.billing.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Mirrors saveme-backend/docs/openapi/paths/billing.yaml. Responses only; unknown members are
// ignored (P-02.3). Money is integer minor units plus a currency (P-03).

@Serializable
data class PriceDto(
    val channel: String,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
)

@Serializable
data class PlanDto(
    val id: String,
    val name: String,
    @SerialName("period_type") val periodType: String = "",
    @SerialName("period_days") val periodDays: Int? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    val features: Map<String, JsonElement> = emptyMap(),
    val prices: List<PriceDto> = emptyList(),
)

/** Billing's own copy of `PlanRef`: it may not import `auth.data` (P-10.4). */
@Serializable
data class PlanRefDto(val id: String, val name: String)

/** Every member but [plan] is null on the free plan. */
@Serializable
data class MySubscriptionDto(
    val plan: PlanRefDto,
    val provider: String? = null,
    val status: String? = null,
    @SerialName("current_period_start") val currentPeriodStart: String? = null,
    @SerialName("current_period_end") val currentPeriodEnd: String? = null,
    @SerialName("grace_until") val graceUntil: String? = null,
    @SerialName("auto_renew") val autoRenew: Boolean? = null,
)

/** What is stored under `billing_catalogue`: the plans and the language they were requested in. */
@Serializable
data class CatalogueRecord(val language: String, val plans: List<PlanDto>)
