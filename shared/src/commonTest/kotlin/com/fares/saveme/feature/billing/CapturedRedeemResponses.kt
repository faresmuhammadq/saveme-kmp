package com.fares.saveme.feature.billing

/**
 * Real answers of `POST /billing/redeem` captured on 2026-10-05 from the local backend (saveme-backend@7155d6d, spec
 * 020, `instapay.yaml` v1.12) with throwaway accounts (specs/015-redeem-code/qa-report.md). Every redeem code is
 * replaced by `XXXX-XXXX-XXXX-XXXX` (none appears in an answer anyway) and no credential is kept. Only the headers
 * that matter are kept: `Retry-After` and `Idempotency-Replayed`.
 */
data class Captured(val status: Int, val headers: List<Pair<String, String>>, val body: String)

object CapturedRedeemResponses {
    /** Live `r1`. */
    val successFirstRedeem = Captured(200, listOf(), """{"data":{"subscription":{"id":"01a10e30-8a07-73a1-9f45-245a9210e58c","plan":{"id":"01a0e69a-9639-7118-8ed9-3de1ed414a35","name":"Plus \u00b7 Monthly"},"provider":"instapay","status":"active","current_period_start":"2026-10-05T22:30:24.494422Z","current_period_end":"2026-11-05T22:30:24.494422Z"},"governing":{"plan":{"id":"01a0e69a-9639-7118-8ed9-3de1ed414a35","name":"Plus \u00b7 Monthly"},"provider":"instapay","status":"active","current_period_start":"2026-10-05T22:30:24.494422Z","current_period_end":"2026-11-05T22:30:24.494422Z","grace_until":null,"auto_renew":false},"entitlements":{"plan":{"id":"01a0e69a-9639-7118-8ed9-3de1ed414a35","name":"Plus \u00b7 Monthly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}}},"meta":{}}""")

    /** Live `r2`. */
    val successSameKeyReplay = Captured(200, listOf("Idempotency-Replayed" to "true"), """{"data": {"governing": {"plan": {"id": "01a0e69a-9639-7118-8ed9-3de1ed414a35", "name": "Plus · Monthly"}, "status": "active", "provider": "instapay", "auto_renew": false, "grace_until": null, "current_period_end": "2026-11-05T22:30:24.494422Z", "current_period_start": "2026-10-05T22:30:24.494422Z"}, "entitlements": {"plan": {"id": "01a0e69a-9639-7118-8ed9-3de1ed414a35", "name": "Plus · Monthly"}, "features": {"wallets.max": null, "export.enabled": true, "sharing.enabled": true, "reports.advanced": true, "sharing.max_members_per_wallet": 5}}, "subscription": {"id": "01a10e30-8a07-73a1-9f45-245a9210e58c", "plan": {"id": "01a0e69a-9639-7118-8ed9-3de1ed414a35", "name": "Plus · Monthly"}, "status": "active", "provider": "instapay", "current_period_end": "2026-11-05T22:30:24.494422Z", "current_period_start": "2026-10-05T22:30:24.494422Z"}}, "meta": {}}""")

    /** Live `a_repeat_newkey`. */
    val successRepeatNewKey = Captured(200, listOf(), """{"data":{"subscription":{"id":"01a10e30-8a07-73a1-9f45-245a9210e58c","plan":{"id":"01a0e69a-9639-7118-8ed9-3de1ed414a35","name":"Plus \u00b7 Monthly"},"provider":"instapay","status":"active","current_period_start":"2026-10-05T22:30:24.494422Z","current_period_end":"2026-11-05T22:30:24.494422Z"},"governing":{"plan":{"id":"01a0e69a-9639-7118-8ed9-3de1ed414a35","name":"Plus \u00b7 Monthly"},"provider":"instapay","status":"active","current_period_start":"2026-10-05T22:30:24.494422Z","current_period_end":"2026-11-05T22:30:24.494422Z","grace_until":null,"auto_renew":false},"entitlements":{"plan":{"id":"01a0e69a-9639-7118-8ed9-3de1ed414a35","name":"Plus \u00b7 Monthly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}}},"meta":{}}""")

    /** Live `a_second_yearly`. */
    val successEarlyRenewal = Captured(200, listOf(), """{"data":{"subscription":{"id":"01a10e32-2ca2-7374-bd2b-250e1eb8d961","plan":{"id":"01a0e69a-9641-710e-a99d-616946c5c919","name":"Plus \u00b7 Yearly"},"provider":"instapay","status":"active","current_period_start":"2026-11-05T22:30:24.494422Z","current_period_end":"2027-11-05T22:30:24.494422Z"},"governing":{"plan":{"id":"01a0e69a-9641-710e-a99d-616946c5c919","name":"Plus \u00b7 Yearly"},"provider":"instapay","status":"active","current_period_start":"2026-11-05T22:30:24.494422Z","current_period_end":"2027-11-05T22:30:24.494422Z","grace_until":null,"auto_renew":false},"entitlements":{"plan":{"id":"01a0e69a-9641-710e-a99d-616946c5c919","name":"Plus \u00b7 Yearly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}}},"meta":{}}""")

    /** Live `d_manual_governs`. */
    val successManualLifetimeGoverns = Captured(200, listOf(), """{"data":{"subscription":{"id":"01a10e32-8670-713c-add2-ded05911e601","plan":{"id":"01a0e69a-9639-7118-8ed9-3de1ed414a35","name":"Plus \u00b7 Monthly"},"provider":"instapay","status":"active","current_period_start":"2026-11-05T22:32:11.396307Z","current_period_end":"2026-12-05T22:32:11.396307Z"},"governing":{"plan":{"id":"01a0e69a-9641-710e-a99d-616946c5c919","name":"Plus \u00b7 Yearly"},"provider":"manual","status":"active","current_period_start":"2026-10-05T22:32:25.781433Z","current_period_end":null,"grace_until":null,"auto_renew":false},"entitlements":{"plan":{"id":"01a0e69a-9641-710e-a99d-616946c5c919","name":"Plus \u00b7 Yearly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}}},"meta":{}}""")

    /** Live `e_play_governs`. */
    val successPlayRenewsGoverns = Captured(200, listOf(), """{"data":{"subscription":{"id":"01a10e32-efa3-73ff-9047-68360ce0ee44","plan":{"id":"01a0e69a-9639-7118-8ed9-3de1ed414a35","name":"Plus \u00b7 Monthly"},"provider":"instapay","status":"active","current_period_start":"2026-10-05T22:33:01.574833Z","current_period_end":"2026-11-05T22:33:01.574833Z"},"governing":{"plan":{"id":"01a0e69a-9641-710e-a99d-616946c5c919","name":"Plus \u00b7 Yearly"},"provider":"google_play","status":"active","current_period_start":"2026-10-05T22:32:54.000000Z","current_period_end":"2027-10-05T22:32:54.000000Z","grace_until":null,"auto_renew":true},"entitlements":{"plan":{"id":"01a0e69a-9641-710e-a99d-616946c5c919","name":"Plus \u00b7 Yearly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}}},"meta":{}}""")

    /** Live `b_mismatch`. */
    val refusedOtherAddress = Captured(403, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/redeem-code-email-mismatch","title":"Redeem code for another address","status":403,"detail":"Sign in with the email address this code was sent to.","code":"REDEEM_CODE_EMAIL_MISMATCH","request_id":"01a10e30-f99c-73ef-b81c-403643149309"}""")

    /** Live `c_mismatch_unverified`. */
    val refusedOtherAddressWhileUnverified = Captured(403, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/redeem-code-email-mismatch","title":"Redeem code for another address","status":403,"detail":"Sign in with the email address this code was sent to.","code":"REDEEM_CODE_EMAIL_MISMATCH","request_id":"01a10e31-0eef-7369-8bdb-706e87535bf6"}""")

    /** Live `c_unverified`. */
    val refusedNotVerified = Captured(403, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/email-not-verified","title":"Address not verified","status":403,"detail":"Verify your email address first. We can send the link again.","code":"EMAIL_NOT_VERIFIED","request_id":"01a10e31-2322-703a-9f81-a190cc5d5dc2"}""")

    /** Live `a_404`. */
    val refusedNotFound = Captured(404, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10e30-d0a8-73d1-86aa-4d4b34956ac5"}""")

    /** Live `d_revoked`. */
    val refusedRevokedCode = Captured(404, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10e32-0cfe-70bb-9adc-e15662343063"}""")

    /** Live `b_mismatch_used`. */
    val refusedUsed = Captured(409, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/redeem-code-used","title":"Redeem code already used","status":409,"detail":"This code has already been used by another account.","code":"REDEEM_CODE_USED","request_id":"01a10e30-fa5d-71fe-9924-3c0d83002212"}""")

    /** Live `b_expired`. */
    val refusedExpired = Captured(410, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/redeem-code-expired","title":"Redeem code expired","status":410,"detail":"This code has expired. Contact support.","code":"REDEEM_CODE_EXPIRED","request_id":"01a10e32-0bd1-7381-83ee-044e60a8ab5d"}""")

    /** Live `a_422`. */
    val refusedMalformed = Captured(422, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10e30-d012-72d7-b509-5315e7de9126","errors":[{"pointer":"\/code","code":"INVALID_TYPE","detail":"This value is not in the expected form."}]}""")

    /** Live `a_422_missing`. */
    val refusedMissingCode = Captured(422, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10e30-d15f-72f7-8f8c-c3dc910d6ba2","errors":[{"pointer":"\/code","code":"REQUIRED","detail":"This field is required."}]}""")

    /** Live `b_rl_8`. */
    val refusedRateLimited = Captured(429, listOf("Retry-After" to "3466"), """{"type":"http:\/\/saveme-backend.test\/problems\/rate-limited","title":"Rate limited","status":429,"detail":"Too many attempts. Please wait a moment and try again.","code":"RATE_LIMITED","request_id":"01a10e33-0505-7258-ad9d-db2db7b73494","retry_after_seconds":3466}""")

    /** Live `a_nokey`. */
    val refusedKeyRequired = Captured(400, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/idempotency-key-required","title":"Idempotency key required","status":400,"detail":"This request needs an Idempotency-Key header.","code":"IDEMPOTENCY_KEY_REQUIRED","request_id":"01a10e30-ceee-737d-99f6-bf2557268450"}""")

    /** Live `a_keyreused`. */
    val refusedKeyReused = Captured(409, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/idempotency-key-reused","title":"Idempotency key reused","status":409,"detail":"This request was already made with different details.","code":"IDEMPOTENCY_KEY_REUSED","request_id":"01a10e30-cf75-71ab-912b-64aa8abe52de"}""")
}
