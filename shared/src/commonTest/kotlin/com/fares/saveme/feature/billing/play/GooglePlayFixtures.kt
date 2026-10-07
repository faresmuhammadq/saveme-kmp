package com.fares.saveme.feature.billing.play

import com.fares.saveme.feature.auth.problemBody

// The examples of saveme-backend/docs/openapi/paths/google-play.yaml v1.15, verbatim (spec 018 P-14.3).

private const val PLUS_MONTHLY_ID = "0192f7a0-0000-7000-8000-0000000000c2"
private const val PLUS_YEARLY_ID = "0192f7a0-0000-7000-8000-0000000000c3"
private const val FREE_PLAN_ID = "0192f7a0-0000-7000-8000-0000000000c1"

private const val PLUS_FEATURES =
    """{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}"""
private const val FREE_FEATURES =
    """{"sharing.enabled":false,"sharing.max_members_per_wallet":1,"export.enabled":true,"reports.advanced":true,"wallets.max":null}"""

object GooglePlayFixtures {
    const val TOKEN = "opaque-token-from-play-billing.AO-J1OxPLAyTokEnExAmPlE0123456789"
    const val RESTORE_TOKEN_ONE = "opaque-token-from-play-billing.AO-J1OxRESToReONE0123456789"
    const val RESTORE_TOKEN_TWO = "opaque-token-from-play-billing.AO-J1OxRESToReTWO0123456789"
    const val REFERENCE = "3f7a9c0e5b2d4186a0c9e7f1b3d5a7c9e1f3b5d7a9c1e3f5b7d9a1c3e5f7b9d1"

    private fun purchaseBody(
        product: String,
        state: String,
        acknowledged: Boolean,
        autoRenew: Boolean,
        end: String?,
        test: Boolean,
        planId: String,
        planName: String,
        features: String,
        provider: String?,
        status: String?,
        start: String?,
        graceUntil: String? = null,
        subscriptionAutoRenew: Boolean?,
    ) = """{"data":{"purchase":{"product_id":"$product","state":"$state","acknowledged":$acknowledged,"auto_renew":$autoRenew,"current_period_end":${end?.let { "\"$it\"" } ?: "null"},"test":$test,"future_member":1},"entitlements":{"plan":{"id":"$planId","name":"$planName"},"features":$features},"subscription":{"plan":{"id":"$planId","name":"$planName"},"provider":${provider?.let { "\"$it\"" } ?: "null"},"status":${status?.let { "\"$it\"" } ?: "null"},"current_period_start":${start?.let { "\"$it\"" } ?: "null"},"current_period_end":${end?.let { "\"$it\"" } ?: "null"},"grace_until":${graceUntil?.let { "\"$it\"" } ?: "null"},"auto_renew":${subscriptionAutoRenew ?: "null"}}},"meta":{}}"""

    /** `active`: a new monthly subscription, acknowledged (200). */
    fun active(state: String = "active", test: Boolean = false, acknowledged: Boolean = true, end: String? = "2026-11-06T09:12:44.000000Z") = purchaseBody(
        "plus_monthly", state, acknowledged, true, end, test, PLUS_MONTHLY_ID, "Plus · Monthly", PLUS_FEATURES, "google_play", "active",
        "2026-10-06T09:12:44.000000Z", subscriptionAutoRenew = true,
    )

    /** `ackPending`: granted, Google's acknowledgement still being retried by the server (200). */
    fun ackPending() = purchaseBody(
        "plus_yearly", "active", false, true, "2027-10-06T09:12:44.000000Z", false, PLUS_YEARLY_ID, "Plus · Yearly", PLUS_FEATURES, "google_play",
        "active", "2026-10-06T09:12:44.000000Z", subscriptionAutoRenew = true,
    )

    /** `pending`: Google reports the purchase pending; nothing granted (202). */
    fun pending() = purchaseBody(
        "plus_monthly", "pending", false, false, null, false, FREE_PLAN_ID, "Free", FREE_FEATURES, null, null, null, subscriptionAutoRenew = null,
    )

    /** An answer whose purchase state is Google's `on_hold`/`paused` for an old token (200, the plan is Free). */
    fun heldOrPaused(state: String) = purchaseBody(
        "plus_monthly", state, true, false, "2026-10-01T09:12:44.000000Z", false, FREE_PLAN_ID, "Free", FREE_FEATURES, null, null, null, subscriptionAutoRenew = null,
    )

    /** `restored`: one linked, one belonging to another account (200). */
    fun restored() =
        """{"data":{"results":[{"index":0,"product_id":"plus_monthly","outcome":"linked"},{"index":1,"product_id":"plus_yearly","outcome":"linked_elsewhere"}],"entitlements":{"plan":{"id":"$PLUS_MONTHLY_ID","name":"Plus · Monthly"},"features":$PLUS_FEATURES},"subscription":{"plan":{"id":"$PLUS_MONTHLY_ID","name":"Plus · Monthly"},"provider":"google_play","status":"active","current_period_start":"2026-09-20T18:02:10.000000Z","current_period_end":"2026-10-20T18:02:10.000000Z","grace_until":null,"auto_renew":true}},"meta":{}}"""

    /** A restore answer with the given per-item outcomes, in request order, applying the monthly Plus plan. */
    fun restoreOf(vararg outcomes: String, unknownMember: Boolean = true) =
        """{"data":{"results":[${outcomes.mapIndexed { i, o -> """{"index":$i,"product_id":"plus_monthly","outcome":"$o"}""" }.joinToString(",")}],"entitlements":{"plan":{"id":"$PLUS_MONTHLY_ID","name":"Plus · Monthly"},"features":$PLUS_FEATURES},"subscription":{"plan":{"id":"$PLUS_MONTHLY_ID","name":"Plus · Monthly"},"provider":"google_play","status":"active","current_period_start":"2026-09-20T18:02:10.000000Z","current_period_end":"2026-10-20T18:02:10.000000Z","grace_until":null,"auto_renew":true}${if (unknownMember) ""","future":1""" else ""}},"meta":{}}"""

    /** A grace subscription for a Google Play plan (the Plan screen warning). */
    fun reference(value: String = REFERENCE) = """{"data":{"obfuscated_account_id":"$value"},"meta":{}}"""

    fun alreadyLinked() = problemBody(409, "PURCHASE_ALREADY_LINKED")
    fun notVerified() = problemBody(422, "PURCHASE_NOT_VERIFIED")
    fun unknownProduct() = problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/product_id","code":"INVALID_ENUM_VALUE"}]""")
    fun inProgress() = problemBody(409, "IDEMPOTENCY_REQUEST_IN_PROGRESS")
    fun keyReused() = problemBody(409, "IDEMPOTENCY_KEY_REUSED")
    fun rateLimited() = problemBody(429, "RATE_LIMITED", ""","retry_after_seconds":120""")
    fun unavailable() = problemBody(503, "SERVICE_UNAVAILABLE", ""","retry_after_seconds":45""")
    fun unauthenticated() = problemBody(401, "UNAUTHENTICATED")
    fun upgradeRequired() = problemBody(426, "CLIENT_VERSION_UNSUPPORTED", ""","minimum_version":"9.9.9"""")
}
