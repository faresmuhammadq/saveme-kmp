package com.fares.saveme.feature.billing.domain

import com.fares.saveme.feature.billing.data.dto.RedeemResultDto
import kotlin.time.Instant

/** What the confirmation says; contains no code (spec 015 data-model §2). Dates show in the profile time zone (P-05). */
data class RedeemConfirmation(
    /** The plan that applies now: the governing subscription's. */
    val governingPlanName: String,
    /** Its end: the grace end when it is in grace; null is "no end date". */
    val governingEnd: Instant?,
    /** The redeemed subscription is the one that governs. */
    val governingIsRedeemed: Boolean,
    /** A renewing Google Play subscription governs and keeps billing. */
    val playRenews: Boolean,
    val redeemedPlanName: String,
    val redeemedEnd: Instant?,
    /** Only when the redeemed billing period begins after now. */
    val redeemedStartsAt: Instant?,
) {
    companion object {
        private const val REDEEMED_PROVIDER = "instapay"
        private const val PLAY_PROVIDER = "google_play"

        fun from(result: RedeemResultDto, now: Instant): RedeemConfirmation {
            val governing = result.governing
            val redeemed = result.subscription
            val redeemedEnd = redeemed.currentPeriodEnd.toInstantOrNull()
            val governingPeriodEnd = governing.currentPeriodEnd.toInstantOrNull()
            // `MySubscription` carries no id (backend gap), so the redeemed record is recognised by what it states.
            val governingIsRedeemed = governing.provider == REDEEMED_PROVIDER &&
                governing.plan.id == redeemed.plan.id &&
                governingPeriodEnd == redeemedEnd
            val start = redeemed.currentPeriodStart.toInstantOrNull()
            return RedeemConfirmation(
                governingPlanName = governing.plan.name,
                governingEnd = if (governing.status == "grace") governing.graceUntil.toInstantOrNull() ?: governingPeriodEnd else governingPeriodEnd,
                governingIsRedeemed = governingIsRedeemed,
                playRenews = !governingIsRedeemed && governing.provider == PLAY_PROVIDER && governing.autoRenew == true,
                redeemedPlanName = redeemed.plan.name,
                redeemedEnd = redeemedEnd,
                redeemedStartsAt = start?.takeIf { it > now },
            )
        }
    }
}

private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
