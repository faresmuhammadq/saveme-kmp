package com.fares.saveme.feature.billing.domain.model

import com.fares.saveme.feature.auth.domain.model.FeatureValue
import com.fares.saveme.feature.auth.domain.toFeatureValue
import com.fares.saveme.feature.billing.data.dto.PlanDto
import com.fares.saveme.feature.billing.domain.EntitlementSet
import kotlinx.serialization.json.JsonPrimitive

/** Integer minor units plus an ISO 4217 code (P-03). */
data class Money(val amountMinor: Long, val currency: String)

sealed interface BillingPeriod {
    data object Monthly : BillingPeriod
    data object Yearly : BillingPeriod
    data object Lifetime : BillingPeriod
    data class EveryDays(val days: Int) : BillingPeriod
    data object Unknown : BillingPeriod
}

data class CataloguePlan(
    val id: String,
    val name: String,
    val period: BillingPeriod,
    val isDefault: Boolean,
    val features: EntitlementSet,
    /** Google Play's price only; null when the plan has none (P-12.3, P-12.4). */
    val price: Money?,
)

internal const val GOOGLE_PLAY_CHANNEL = "google_play"

internal fun PlanDto.toDomain() = CataloguePlan(
    id = id,
    name = name,
    period = when (periodType) {
        "monthly" -> BillingPeriod.Monthly
        "yearly" -> BillingPeriod.Yearly
        "lifetime" -> BillingPeriod.Lifetime
        "custom" -> periodDays?.takeIf { it > 0 }?.let { BillingPeriod.EveryDays(it) } ?: BillingPeriod.Unknown
        else -> BillingPeriod.Unknown
    },
    isDefault = isDefault,
    features = EntitlementSet(
        features.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.toFeatureValue()?.let { key to it } }.toMap(),
    ),
    price = prices.firstOrNull { it.channel == GOOGLE_PLAY_CHANNEL }?.let { Money(it.amountMinor, it.currency) },
)
