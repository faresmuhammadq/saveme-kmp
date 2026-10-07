package com.fares.saveme.feature.billing.domain.model

import com.fares.saveme.feature.billing.data.dto.MySubscriptionDto
import kotlin.time.Instant

enum class SubscriptionStatus { Active, Grace }

/** The governing subscription as the Plan screen shows it. An unknown status is shown as no status. */
data class Subscription(
    val planId: String,
    val planName: String,
    val status: SubscriptionStatus?,
    val periodEnd: Instant?,
    val graceUntil: Instant?,
    val autoRenew: Boolean?,
    /** `google_play`, `instapay`, `manual` or null; read only to decide Google Play links, never printed (spec 018 FR-030). */
    val provider: String? = null,
) {
    val isPaid: Boolean get() = status != null
}

internal fun MySubscriptionDto.toDomain() = Subscription(
    planId = plan.id,
    planName = plan.name,
    status = when (status) {
        "active" -> SubscriptionStatus.Active
        "grace" -> SubscriptionStatus.Grace
        else -> null
    },
    periodEnd = currentPeriodEnd.toInstantOrNull(),
    graceUntil = graceUntil.toInstantOrNull(),
    autoRenew = autoRenew,
    provider = provider,
)

/** A bad value becomes "no date", never a crash (data-model §2). */
private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
