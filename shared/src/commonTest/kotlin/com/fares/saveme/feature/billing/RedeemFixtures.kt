package com.fares.saveme.feature.billing

import com.fares.saveme.feature.billing.data.dto.MySubscriptionDto
import com.fares.saveme.feature.billing.data.dto.PlanRefDto
import com.fares.saveme.feature.billing.data.dto.RedeemEntitlementsDto
import com.fares.saveme.feature.billing.data.dto.RedeemResultDto
import com.fares.saveme.feature.billing.data.dto.RedeemedSubscriptionDto
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

const val YEARLY_ID = "0192f7a0-0000-7000-8000-0000000000c3"
const val MONTHLY_ID = "0192f7a0-0000-7000-8000-0000000000c2"
const val RAW_CODE = "7KQ2MX4D9TRBH3WE"
const val GROUPED_CODE = "7KQ2-MX4D-9TRB-H3WE"

private const val PLUS_FEATURES_JSON =
    """{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}"""

private fun redeemBody(subscription: String, governing: String, extra: String = "") =
    """{"data":{"subscription":$subscription,"governing":$governing,"entitlements":{"plan":{"id":"$YEARLY_ID","name":"Plus · Yearly"},"features":$PLUS_FEATURES_JSON}$extra},"meta":{}}"""

/** The `redeemedGoverns` example of instapay.yaml v1.12 (with an unknown member to prove it is ignored). */
fun redeemedGovernsBody() = redeemBody(
    """{"id":"0192f7d0-1111-7222-8333-444455556666","plan":{"id":"$YEARLY_ID","name":"Plus · Yearly"},"provider":"instapay","status":"active","current_period_start":"2026-10-05T12:00:00.000000Z","current_period_end":"2027-10-05T12:00:00.000000Z","future_member":1}""",
    """{"plan":{"id":"$YEARLY_ID","name":"Plus · Yearly"},"provider":"instapay","status":"active","current_period_start":"2026-10-05T12:00:00.000000Z","current_period_end":"2027-10-05T12:00:00.000000Z","grace_until":null,"auto_renew":false}""",
)

/** The `playStillGoverns` example: a Play yearly subscription ending later keeps governing. */
fun playStillGovernsBody() = redeemBody(
    """{"id":"0192f7d0-1111-7222-8333-444455557777","plan":{"id":"$MONTHLY_ID","name":"Plus · Monthly"},"provider":"instapay","status":"active","current_period_start":"2026-10-05T12:00:00.000000Z","current_period_end":"2026-11-05T12:00:00.000000Z"}""",
    """{"plan":{"id":"$YEARLY_ID","name":"Plus · Yearly"},"provider":"google_play","status":"active","current_period_start":"2026-01-10T08:00:00.000000Z","current_period_end":"2027-01-10T08:00:00.000000Z","grace_until":null,"auto_renew":true}""",
)

/** An operator-granted lifetime plan (no end) governs over a redeemed monthly one. */
fun lifetimeStillGovernsBody() = redeemBody(
    """{"id":"0192f7d0-1111-7222-8333-444455558888","plan":{"id":"$MONTHLY_ID","name":"Plus · Monthly"},"provider":"instapay","status":"active","current_period_start":"2026-10-05T12:00:00.000000Z","current_period_end":"2026-11-05T12:00:00.000000Z"}""",
    """{"plan":{"id":"$YEARLY_ID","name":"Plus · Yearly"},"provider":"manual","status":"active","current_period_start":"2026-09-01T00:00:00.000000Z","current_period_end":null,"grace_until":null,"auto_renew":false}""",
)

fun redeemResult(
    governingProvider: String = "instapay",
    governingPlanId: String = YEARLY_ID,
    governingEnd: String? = "2027-10-05T12:00:00Z",
    governingStatus: String = "active",
    graceUntil: String? = null,
    autoRenew: Boolean = false,
    redeemedPlanId: String = YEARLY_ID,
    redeemedStart: String = "2026-10-05T12:00:00Z",
    redeemedEnd: String? = "2027-10-05T12:00:00Z",
) = RedeemResultDto(
    subscription = RedeemedSubscriptionDto("sub-1", PlanRefDto(redeemedPlanId, "Redeemed plan"), "instapay", "active", redeemedStart, redeemedEnd),
    governing = MySubscriptionDto(
        plan = PlanRefDto(governingPlanId, "Governing plan"),
        provider = governingProvider,
        status = governingStatus,
        currentPeriodStart = "2026-01-01T00:00:00Z",
        currentPeriodEnd = governingEnd,
        graceUntil = graceUntil,
        autoRenew = autoRenew,
    ),
    entitlements = RedeemEntitlementsDto(PlanRefDto(YEARLY_ID, "Plus · Yearly"), mapOf("sharing.enabled" to JsonPrimitive(true), "wallets.max" to JsonNull)),
)
