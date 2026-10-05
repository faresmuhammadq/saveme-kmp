package com.fares.saveme.feature.billing.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

// Mirrors saveme-backend/docs/openapi/paths/instapay.yaml (v1.12) `POST /billing/redeem`. Unknown members are
// ignored (P-02.3). The request carries only the declared member, in the grouped form.

/** The code is a mailed secret: it never prints (P-09.2). */
@Serializable
data class RedeemRequestDto(val code: String) {
    override fun toString() = "RedeemRequestDto(code=<redacted>)"
}

/** The subscription this redemption created, or the one an earlier redemption of the same code created. */
@Serializable
data class RedeemedSubscriptionDto(
    val id: String,
    val plan: PlanRefDto,
    val provider: String,
    val status: String,
    @SerialName("current_period_start") val currentPeriodStart: String,
    @SerialName("current_period_end") val currentPeriodEnd: String? = null,
)

/** Billing's own copy of `EntitlementSummary`: it may not import `auth.data` (P-10.4). */
@Serializable
data class RedeemEntitlementsDto(
    val plan: PlanRefDto,
    val features: Map<String, JsonPrimitive> = emptyMap(),
)

@Serializable
data class RedeemResultDto(
    val subscription: RedeemedSubscriptionDto,
    val governing: MySubscriptionDto,
    val entitlements: RedeemEntitlementsDto,
)
