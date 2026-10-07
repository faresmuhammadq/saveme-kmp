package com.fares.saveme.feature.billing.play

import com.fares.saveme.feature.billing.Captured

/**
 * Real answers of the Google Play billing endpoints captured on 2026-10-07 from the local backend (saveme-backend@2062c0c, spec
 * 024, `google-play.yaml` v1.15, scripted `fake` gateway) with throwaway accounts (specs/018-google-play/qa-report.md). Every
 * purchase token is a made-up token of Google's shape (`<24 chars>.AO-J1O...`), every account reference belongs to a throwaway
 * account, and no credential is kept. Only the headers that matter are kept: `Retry-After` and `Idempotency-Replayed`. Bodies
 * are the server's JSON, re-serialised (slashes unescaped) with every value unchanged.
 */
object CapturedPlayResponses {
    /** Live `ref-a`. */
    val accountReference = Captured(200, listOf(), """{"data":{"obfuscated_account_id":"bb21b29b607c1635c3f2b077c9b756009169177276487aaf28327c5c75405bb6"},"meta":{}}""")

    /** Live `ref-a-again`. */
    val accountReferenceAgain = Captured(200, listOf(), """{"data":{"obfuscated_account_id":"bb21b29b607c1635c3f2b077c9b756009169177276487aaf28327c5c75405bb6"},"meta":{}}""")

    /** Live `ref-b`. */
    val accountReferenceOtherAccount = Captured(200, listOf(), """{"data":{"obfuscated_account_id":"d19ae4ca8187f529ffc6a206f4c3c94172bbfc32fabe2a9bcff7539577f0db07"},"meta":{}}""")

    /** Live `ref-no-bearer`. */
    val accountReferenceNoBearer = Captured(401, listOf(), """{"type":"http://saveme-backend.test/problems/unauthenticated","title":"Unauthenticated","status":401,"detail":"Please sign in again.","code":"UNAUTHENTICATED","request_id":"01a11401-62c1-733c-9c6a-42ca273233c2"}""")

    /** Live `A-verify-200`. */
    val verifyActive = Captured(200, listOf(), """{"data":{"purchase":{"product_id":"plus_monthly","state":"active","acknowledged":true,"auto_renew":true,"current_period_end":"2026-11-06T01:36:37.888549Z","test":true},"entitlements":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"provider":"google_play","status":"active","current_period_start":"2026-10-07T01:36:37.888549Z","current_period_end":"2026-11-06T01:36:37.888549Z","grace_until":null,"auto_renew":true}},"meta":{}}""")

    /** Live `A-verify-replay`. */
    val verifyReplay = Captured(200, listOf("Idempotency-Replayed" to "true"), """{"data":{"purchase":{"test":true,"state":"active","auto_renew":true,"product_id":"plus_monthly","acknowledged":true,"current_period_end":"2026-11-06T01:36:37.888549Z"},"entitlements":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"wallets.max":null,"export.enabled":true,"sharing.enabled":true,"reports.advanced":true,"sharing.max_members_per_wallet":5}},"subscription":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"status":"active","provider":"google_play","auto_renew":true,"grace_until":null,"current_period_end":"2026-11-06T01:36:37.888549Z","current_period_start":"2026-10-07T01:36:37.888549Z"}},"meta":{}}""")

    /** Live `A-verify-same-token-new-key`. */
    val verifySameTokenNewKey = Captured(200, listOf(), """{"data":{"purchase":{"product_id":"plus_monthly","state":"active","acknowledged":true,"auto_renew":true,"current_period_end":"2026-11-06T01:36:38.255434Z","test":true},"entitlements":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"provider":"google_play","status":"active","current_period_start":"2026-11-06T01:36:37.888549Z","current_period_end":"2026-11-06T01:36:38.255434Z","grace_until":null,"auto_renew":true}},"meta":{}}""")

    /** Live `C-verify-pending-202`. */
    val verifyPending = Captured(202, listOf(), """{"data":{"purchase":{"product_id":"plus_monthly","state":"pending","acknowledged":false,"auto_renew":true,"current_period_end":"2026-11-06T01:36:51.198942Z","test":true},"entitlements":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"features":{"sharing.enabled":false,"sharing.max_members_per_wallet":1,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"provider":null,"status":null,"current_period_start":null,"current_period_end":null,"grace_until":null,"auto_renew":null}},"meta":{}}""")

    /** Live `C-verify-pending-replay`. */
    val verifyPendingReplay = Captured(202, listOf("Idempotency-Replayed" to "true"), """{"data":{"purchase":{"test":true,"state":"pending","auto_renew":true,"product_id":"plus_monthly","acknowledged":false,"current_period_end":"2026-11-06T01:36:51.198942Z"},"entitlements":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"features":{"wallets.max":null,"export.enabled":true,"sharing.enabled":false,"reports.advanced":true,"sharing.max_members_per_wallet":1}},"subscription":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"status":null,"provider":null,"auto_renew":null,"grace_until":null,"current_period_end":null,"current_period_start":null}},"meta":{}}""")

    /** Live `C-verify-purchased-new-key-200`. */
    val verifyPurchasedAfterPending = Captured(200, listOf(), """{"data":{"purchase":{"product_id":"plus_monthly","state":"active","acknowledged":true,"auto_renew":true,"current_period_end":"2026-11-06T01:36:51.821418Z","test":true},"entitlements":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"provider":"google_play","status":"active","current_period_start":"2026-10-07T01:36:51.821418Z","current_period_end":"2026-11-06T01:36:51.821418Z","grace_until":null,"auto_renew":true}},"meta":{}}""")

    /** Live `C-verify-recheck-hour-key`. */
    val verifyRecheckHourKey = Captured(200, listOf(), """{"data":{"purchase":{"product_id":"plus_monthly","state":"active","acknowledged":true,"auto_renew":true,"current_period_end":"2026-11-06T01:36:52.308870Z","test":true},"entitlements":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"provider":"google_play","status":"active","current_period_start":"2026-11-06T01:36:51.821418Z","current_period_end":"2026-11-06T01:36:52.308870Z","grace_until":null,"auto_renew":true}},"meta":{}}""")

    /** Live `C-replay-after-google-state-changed`. */
    val verifyReplayAfterGoogleChanged = Captured(200, listOf("Idempotency-Replayed" to "true"), """{"data":{"purchase":{"test":true,"state":"active","auto_renew":true,"product_id":"plus_monthly","acknowledged":true,"current_period_end":"2026-11-06T01:36:51.821418Z"},"entitlements":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"wallets.max":null,"export.enabled":true,"sharing.enabled":true,"reports.advanced":true,"sharing.max_members_per_wallet":5}},"subscription":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"status":"active","provider":"google_play","auto_renew":true,"grace_until":null,"current_period_end":"2026-11-06T01:36:51.821418Z","current_period_start":"2026-10-07T01:36:51.821418Z"}},"meta":{}}""")

    /** Live `E-verify-grace`. */
    val verifyGrace = Captured(200, listOf(), """{"data":{"purchase":{"product_id":"plus_monthly","state":"grace","acknowledged":true,"auto_renew":true,"current_period_end":"2026-10-10T01:36:52.523762Z","test":true},"entitlements":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"provider":"google_play","status":"grace","current_period_start":"2026-10-07T01:36:52.523762Z","current_period_end":"2026-10-10T01:36:52.523762Z","grace_until":"2026-10-10T01:36:52.523762Z","auto_renew":true}},"meta":{}}""")

    /** Live `E-verify-canceled`. */
    val verifyCanceled = Captured(200, listOf(), """{"data":{"purchase":{"product_id":"plus_monthly","state":"canceled","acknowledged":true,"auto_renew":false,"current_period_end":"2026-10-10T01:36:53.542522Z","test":true},"entitlements":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"provider":"google_play","status":"active","current_period_start":"2026-10-10T01:36:53.029548Z","current_period_end":"2026-10-10T01:36:53.542522Z","grace_until":null,"auto_renew":false}},"meta":{}}""")

    /** Live `E-verify-on-hold`. */
    val verifyOnHold = Captured(200, listOf(), """{"data":{"purchase":{"product_id":"plus_monthly","state":"on_hold","acknowledged":true,"auto_renew":false,"current_period_end":"2026-10-10T01:36:53.542522Z","test":true},"entitlements":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"features":{"sharing.enabled":false,"sharing.max_members_per_wallet":1,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"provider":null,"status":null,"current_period_start":null,"current_period_end":null,"grace_until":null,"auto_renew":null}},"meta":{}}""")

    /** Live `E-verify-paused`. */
    val verifyPaused = Captured(200, listOf(), """{"data":{"purchase":{"product_id":"plus_monthly","state":"paused","acknowledged":true,"auto_renew":false,"current_period_end":"2026-10-10T01:36:53.542522Z","test":true},"entitlements":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"features":{"sharing.enabled":false,"sharing.max_members_per_wallet":1,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"provider":null,"status":null,"current_period_start":null,"current_period_end":null,"grace_until":null,"auto_renew":null}},"meta":{}}""")

    /** Live `E-verify-expired`. */
    val verifyExpired = Captured(200, listOf(), """{"data":{"purchase":{"product_id":"plus_monthly","state":"expired","acknowledged":true,"auto_renew":false,"current_period_end":"2026-10-10T01:36:53.542522Z","test":true},"entitlements":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"features":{"sharing.enabled":false,"sharing.max_members_per_wallet":1,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"provider":null,"status":null,"current_period_start":null,"current_period_end":null,"grace_until":null,"auto_renew":null}},"meta":{}}""")

    /** Live `E-verify-revoked`. */
    val verifyRevoked = Captured(200, listOf(), """{"data":{"purchase":{"product_id":"plus_monthly","state":"revoked","acknowledged":true,"auto_renew":false,"current_period_end":"2026-10-10T01:36:53.542522Z","test":true},"entitlements":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"features":{"sharing.enabled":false,"sharing.max_members_per_wallet":1,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"provider":null,"status":null,"current_period_start":null,"current_period_end":null,"grace_until":null,"auto_renew":null}},"meta":{}}""")

    /** Live `F-verify-play-under-manual`. */
    val verifyUnderManual = Captured(200, listOf(), """{"data":{"purchase":{"product_id":"plus_monthly","state":"active","acknowledged":true,"auto_renew":true,"current_period_end":"2026-11-06T01:37:14.861726Z","test":true},"entitlements":{"plan":{"id":"01a113ec-b122-7384-8a25-20a76b5f5c49","name":"Plus · Yearly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b122-7384-8a25-20a76b5f5c49","name":"Plus · Yearly"},"provider":"manual","status":"active","current_period_start":"2026-10-07T01:37:02.488993Z","current_period_end":"2026-12-06T01:37:02.488993Z","grace_until":null,"auto_renew":false}},"meta":{}}""")

    /** Live `B-verify-linked-elsewhere-by-first-link`. */
    val refusedAlreadyLinkedFirstLink = Captured(409, listOf(), """{"type":"http://saveme-backend.test/problems/purchase-already-linked","title":"Purchase linked to another account","status":409,"detail":"This purchase belongs to another account. Sign in with the account that bought it.","code":"PURCHASE_ALREADY_LINKED","request_id":"01a11401-6bd6-7368-9566-d156b8eb30bb"}""")

    /** Live `B-verify-linked-elsewhere-by-reference`. */
    val refusedAlreadyLinkedByReference = Captured(409, listOf(), """{"type":"http://saveme-backend.test/problems/purchase-already-linked","title":"Purchase linked to another account","status":409,"detail":"This purchase belongs to another account. Sign in with the account that bought it.","code":"PURCHASE_ALREADY_LINKED","request_id":"01a11401-6cbb-7027-8ef9-eb6c5fab3a93"}""")

    /** Live `A-verify-not-verified`. */
    val refusedNotVerified = Captured(422, listOf(), """{"type":"http://saveme-backend.test/problems/purchase-not-verified","title":"Purchase not verified","status":422,"detail":"Google Play does not confirm this purchase. Nothing was granted.","code":"PURCHASE_NOT_VERIFIED","request_id":"01a11401-6952-73b5-9f53-bfcb5a494916"}""")

    /** Live `A-verify-unknown-product`. */
    val refusedUnknownProduct = Captured(422, listOf(), """{"type":"http://saveme-backend.test/problems/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a11401-6a0f-71de-ae65-fbdbece5bf8d","errors":[{"pointer":"/product_id","code":"INVALID_ENUM_VALUE","detail":"This value is not one of the allowed options."}]}""")

    /** Live `A-verify-empty-token`. */
    val refusedEmptyToken = Captured(422, listOf(), """{"type":"http://saveme-backend.test/problems/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a11401-6aa5-718e-9c6a-3ebea0efdc83","errors":[{"pointer":"/purchase_token","code":"REQUIRED","detail":"This field is required."}]}""")

    /** Live `A-verify-extra-member`. */
    val refusedExtraMember = Captured(422, listOf(), """{"type":"http://saveme-backend.test/problems/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a11401-6b3e-706a-a780-9a6bb76efa04","errors":[{"pointer":"/price","code":"UNKNOWN_FIELD","detail":"This field is not accepted here."}]}""")

    /** Live `A-verify-key-reused`. */
    val refusedKeyReused = Captured(409, listOf(), """{"type":"http://saveme-backend.test/problems/idempotency-key-reused","title":"Idempotency key reused","status":409,"detail":"This request was already made with different details.","code":"IDEMPOTENCY_KEY_REUSED","request_id":"01a11401-673b-71a6-bc23-9539c10d2301"}""")

    /** Live `A-verify-no-key`. */
    val refusedKeyRequired = Captured(400, listOf(), """{"type":"http://saveme-backend.test/problems/idempotency-key-required","title":"Idempotency key required","status":400,"detail":"This request needs an Idempotency-Key header.","code":"IDEMPOTENCY_KEY_REQUIRED","request_id":"01a11401-67c4-7312-9ac1-f09eef43f50c"}""")

    /** Live `A-verify-short-key`. */
    val refusedKeyInvalid = Captured(400, listOf(), """{"type":"http://saveme-backend.test/problems/idempotency-key-invalid","title":"Idempotency key invalid","status":400,"detail":"The Idempotency-Key header is not valid.","code":"IDEMPOTENCY_KEY_INVALID","request_id":"01a11401-6849-70d4-9658-d1293066b5c2"}""")

    /** Live `A-verify-no-bearer`. */
    val refusedUnauthenticated = Captured(401, listOf(), """{"type":"http://saveme-backend.test/problems/unauthenticated","title":"Unauthenticated","status":401,"detail":"Please sign in again.","code":"UNAUTHENTICATED","request_id":"01a11401-68cd-7353-b57d-149ca46f46db"}""")

    /** Live `C-verify-throttle-18`. */
    val refusedRateLimited = Captured(429, listOf("Retry-After" to "3563"), """{"type":"http://saveme-backend.test/problems/rate-limited","title":"Rate limited","status":429,"detail":"Too many attempts. Please wait a moment and try again.","code":"RATE_LIMITED","request_id":"01a11402-27d5-71be-bd2f-9e11406e99db","retry_after_seconds":3563}""")

    /** Live `E-verify-gateway-unavailable-503`. */
    val refusedUnavailable = Captured(503, listOf("Retry-After" to "3600"), """{"type":"http://saveme-backend.test/problems/service-unavailable","title":"Service unavailable","status":503,"detail":"The service is temporarily unavailable. Please try again soon.","code":"SERVICE_UNAVAILABLE","request_id":"01a11402-574b-70bc-b229-61b1857bc22c","retry_after_seconds":3600}""")

    /** Live `D-restore-empty`. */
    val restoreEmpty = Captured(200, listOf(), """{"data":{"results":[],"entitlements":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"features":{"sharing.enabled":false,"sharing.max_members_per_wallet":1,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"provider":null,"status":null,"current_period_start":null,"current_period_end":null,"grace_until":null,"auto_renew":null}},"meta":{}}""")

    /** Live `D-restore-mixed`. */
    val restoreMixed = Captured(200, listOf(), """{"data":{"results":[{"index":0,"product_id":"plus_monthly","outcome":"linked"},{"index":1,"product_id":"plus_monthly","outcome":"linked_elsewhere"},{"index":2,"product_id":"plus_monthly","outcome":"not_verified"},{"index":3,"product_id":"plus_monthly","outcome":"pending"}],"entitlements":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"provider":"google_play","status":"active","current_period_start":"2026-10-07T01:37:15.570505Z","current_period_end":"2026-11-06T01:37:15.570505Z","grace_until":null,"auto_renew":true}},"meta":{}}""")

    /** Live `D-restore-mixed-replay`. */
    val restoreMixedReplay = Captured(200, listOf("Idempotency-Replayed" to "true"), """{"data":{"results":[{"index":0,"outcome":"linked","product_id":"plus_monthly"},{"index":1,"outcome":"linked_elsewhere","product_id":"plus_monthly"},{"index":2,"outcome":"not_verified","product_id":"plus_monthly"},{"index":3,"outcome":"pending","product_id":"plus_monthly"}],"entitlements":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"wallets.max":null,"export.enabled":true,"sharing.enabled":true,"reports.advanced":true,"sharing.max_members_per_wallet":5}},"subscription":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"status":"active","provider":"google_play","auto_renew":true,"grace_until":null,"current_period_end":"2026-11-06T01:37:15.570505Z","current_period_start":"2026-10-07T01:37:15.570505Z"}},"meta":{}}""")

    /** Live `D-restore-again-new-key`. */
    val restoreAlreadyYours = Captured(200, listOf(), """{"data":{"results":[{"index":0,"product_id":"plus_monthly","outcome":"already_yours"}],"entitlements":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"provider":"google_play","status":"active","current_period_start":"2026-11-06T01:37:15.570505Z","current_period_end":"2026-11-06T01:37:16.271170Z","grace_until":null,"auto_renew":true}},"meta":{}}""")

    /** Live `D-restore-25-unknown`. */
    val restore25Unknown = Captured(200, listOf(), """{"data":{"results":[{"index":0,"product_id":"plus_monthly","outcome":"not_verified"},{"index":1,"product_id":"plus_monthly","outcome":"not_verified"},{"index":2,"product_id":"plus_monthly","outcome":"not_verified"},{"index":3,"product_id":"plus_monthly","outcome":"not_verified"},{"index":4,"product_id":"plus_monthly","outcome":"not_verified"},{"index":5,"product_id":"plus_monthly","outcome":"not_verified"},{"index":6,"product_id":"plus_monthly","outcome":"not_verified"},{"index":7,"product_id":"plus_monthly","outcome":"not_verified"},{"index":8,"product_id":"plus_monthly","outcome":"not_verified"},{"index":9,"product_id":"plus_monthly","outcome":"not_verified"},{"index":10,"product_id":"plus_monthly","outcome":"not_verified"},{"index":11,"product_id":"plus_monthly","outcome":"not_verified"},{"index":12,"product_id":"plus_monthly","outcome":"not_verified"},{"index":13,"product_id":"plus_monthly","outcome":"not_verified"},{"index":14,"product_id":"plus_monthly","outcome":"not_verified"},{"index":15,"product_id":"plus_monthly","outcome":"not_verified"},{"index":16,"product_id":"plus_monthly","outcome":"not_verified"},{"index":17,"product_id":"plus_monthly","outcome":"not_verified"},{"index":18,"product_id":"plus_monthly","outcome":"not_verified"},{"index":19,"product_id":"plus_monthly","outcome":"not_verified"},{"index":20,"product_id":"plus_monthly","outcome":"not_verified"},{"index":21,"product_id":"plus_monthly","outcome":"not_verified"},{"index":22,"product_id":"plus_monthly","outcome":"not_verified"},{"index":23,"product_id":"plus_monthly","outcome":"not_verified"},{"index":24,"product_id":"plus_monthly","outcome":"not_verified"}],"entitlements":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"subscription":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"provider":"google_play","status":"active","current_period_start":"2026-11-06T01:37:16.271170Z","current_period_end":"2026-11-06T01:37:17.348742Z","grace_until":null,"auto_renew":true}},"meta":{}}""")

    /** Live `D-restore-dup-tokens`. */
    val restoreRefusedDuplicateTokens = Captured(422, listOf(), """{"type":"http://saveme-backend.test/problems/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a11401-faa0-7249-9411-61811065a35a","errors":[{"pointer":"/purchases/1/purchase_token","code":"INVALID_TYPE","detail":"This value is not in the expected form."}]}""")

    /** Live `D-restore-26`. */
    val restoreRefusedTwentySix = Captured(422, listOf(), """{"type":"http://saveme-backend.test/problems/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a11401-fb3c-7066-9908-0845ad2af4b7","errors":[{"pointer":"/purchases","code":"OUT_OF_RANGE","detail":"This value is too short, too long, or outside the allowed range."}]}""")

    /** Live `D-restore-no-key`. */
    val restoreRefusedKeyRequired = Captured(400, listOf(), """{"type":"http://saveme-backend.test/problems/idempotency-key-required","title":"Idempotency key required","status":400,"detail":"This request needs an Idempotency-Key header.","code":"IDEMPOTENCY_KEY_REQUIRED","request_id":"01a11401-ff0f-7250-bf17-88f16d25b09f"}""")

    /** Live `D-restore-key-reused`. */
    val restoreRefusedKeyReused = Captured(409, listOf(), """{"type":"http://saveme-backend.test/problems/idempotency-key-reused","title":"Idempotency key reused","status":409,"detail":"This request was already made with different details.","code":"IDEMPOTENCY_KEY_REUSED","request_id":"01a11401-ff98-72eb-b2ea-01a325069e16"}""")

    /** Live `D-restore-bad-product`. */
    val restoreRefusedUnknownProduct = Captured(422, listOf(), """{"type":"http://saveme-backend.test/problems/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a11402-008e-7372-85e7-b876f05bc99b","errors":[{"pointer":"/purchases/0/product_id","code":"INVALID_ENUM_VALUE","detail":"This value is not one of the allowed options."}]}""")

    /** Live `D-restore-throttle-7`. */
    val restoreRefusedRateLimited = Captured(429, listOf("Retry-After" to "3586"), """{"type":"http://saveme-backend.test/problems/rate-limited","title":"Rate limited","status":429,"detail":"Too many attempts. Please wait a moment and try again.","code":"RATE_LIMITED","request_id":"01a11402-2e6b-70d7-8313-5c600da712af","retry_after_seconds":3586}""")

    /** Live `E-restore-gateway-unavailable`. */
    val restoreRefusedUnavailable = Captured(503, listOf("Retry-After" to "3600"), """{"type":"http://saveme-backend.test/problems/service-unavailable","title":"Service unavailable","status":503,"detail":"The service is temporarily unavailable. Please try again soon.","code":"SERVICE_UNAVAILABLE","request_id":"01a11402-5861-7324-a980-e5863f042531","retry_after_seconds":3600}""")

    /** Live `E-restore-empty-gateway-unavailable`. */
    val restoreEmptyRefusedUnavailable = Captured(503, listOf("Retry-After" to "60"), """{"type":"http://saveme-backend.test/problems/service-unavailable","title":"Service unavailable","status":503,"detail":"The service is temporarily unavailable. Please try again soon.","code":"SERVICE_UNAVAILABLE","request_id":"01a11402-5966-73a8-8b86-787558d66d44","retry_after_seconds":60}""")

    /** Live `A-sub-before`. */
    val subscriptionFree = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"provider":null,"status":null,"current_period_start":null,"current_period_end":null,"grace_until":null,"auto_renew":null},"meta":{}}""")

    /** Live `A-after-verify`. */
    val subscriptionAfterPurchase = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"provider":"google_play","status":"active","current_period_start":"2026-11-06T01:36:37.888549Z","current_period_end":"2026-11-06T01:36:38.255434Z","grace_until":null,"auto_renew":true},"meta":{}}""")

    /** Live `A-after-verify-entitlements`. */
    val entitlementsAfterPurchase = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"meta":{}}""")

    /** Live `C-after-pending`. */
    val subscriptionAfterPending = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"provider":null,"status":null,"current_period_start":null,"current_period_end":null,"grace_until":null,"auto_renew":null},"meta":{}}""")

    /** Live `C-after-purchased`. */
    val subscriptionAfterPurchasedLater = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"provider":"google_play","status":"active","current_period_start":"2026-10-07T01:36:51.821418Z","current_period_end":"2026-11-06T01:36:51.821418Z","grace_until":null,"auto_renew":true},"meta":{}}""")

    /** Live `E-after-grace`. */
    val subscriptionGrace = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"provider":"google_play","status":"grace","current_period_start":"2026-10-07T01:36:52.523762Z","current_period_end":"2026-10-10T01:36:52.523762Z","grace_until":"2026-10-10T01:36:52.523762Z","auto_renew":true},"meta":{}}""")

    /** Live `E-after-grace-entitlements`. */
    val entitlementsGrace = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"meta":{}}""")

    /** Live `E-after-canceled`. */
    val subscriptionCanceled = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b118-715d-8416-8ab0492d9816","name":"Plus · Monthly"},"provider":"google_play","status":"active","current_period_start":"2026-10-10T01:36:53.029548Z","current_period_end":"2026-10-10T01:36:53.542522Z","grace_until":null,"auto_renew":false},"meta":{}}""")

    /** Live `E-after-on-hold`. */
    val subscriptionOnHold = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"provider":null,"status":null,"current_period_start":null,"current_period_end":null,"grace_until":null,"auto_renew":null},"meta":{}}""")

    /** Live `E-after-paused`. */
    val subscriptionPaused = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"provider":null,"status":null,"current_period_start":null,"current_period_end":null,"grace_until":null,"auto_renew":null},"meta":{}}""")

    /** Live `E-after-expired`. */
    val subscriptionExpired = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"provider":null,"status":null,"current_period_start":null,"current_period_end":null,"grace_until":null,"auto_renew":null},"meta":{}}""")

    /** Live `E-after-revoked`. */
    val subscriptionRevoked = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"provider":null,"status":null,"current_period_start":null,"current_period_end":null,"grace_until":null,"auto_renew":null},"meta":{}}""")

    /** Live `E-after-revoked-entitlements`. */
    val entitlementsRevoked = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b10e-7248-aa4b-0d1580b50e8e","name":"Free"},"features":{"sharing.enabled":false,"sharing.max_members_per_wallet":1,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"meta":{}}""")

    /** Live `F-after-play-under-manual`. */
    val subscriptionManualGoverns = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b122-7384-8a25-20a76b5f5c49","name":"Plus · Yearly"},"provider":"manual","status":"active","current_period_start":"2026-10-07T01:37:02.488993Z","current_period_end":"2026-12-06T01:37:02.488993Z","grace_until":null,"auto_renew":false},"meta":{}}""")

    /** Live `F-after-play-under-manual-entitlements`. */
    val entitlementsManualGoverns = Captured(200, listOf(), """{"data":{"plan":{"id":"01a113ec-b122-7384-8a25-20a76b5f5c49","name":"Plus · Yearly"},"features":{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}},"meta":{}}""")

}

/** The inputs of the live sends, so the app's `gp1:` keys can be checked against the keys the server accepted and replayed. */
object CapturedPlayInputs {
    const val USER_A = "01a113ff-b65f-71c4-b711-7da4e9bfaab6"
    const val USER_C = "01a11400-11d3-7174-b785-ccad155b509e"
    const val REFERENCE_A = "bb21b29b607c1635c3f2b077c9b756009169177276487aaf28327c5c75405bb6"
    const val TOKEN_ACTIVE = "qa018activexxxxxxxxxx.AO-J1ON4r4VNvp3ZuJ6EG68zGPmRIY72-LuKvzwBp9n3VdnCQEYizJTjNddqolizG2vaInmpvGUXP4rXk7TmdK"
    const val TOKEN_PENDING_THEN_PURCHASED = "qa018pendxxxxxxxxxx.AO-J1OMZ9niaEl_rRn0DM9j_QBHbPYGTNRmg3S1-PdlSmFQNrKCKrlmF2RJqgJrNnatcEZjYInTJ7sFnbSEgnr"
    const val KEY_ACTIVE = "gp1:7b858be8122614f085bdafd3943e65ab1cedb503af94eb802e2a8dbbca68cc27"
    const val KEY_SAME_TOKEN_RECHECK_HOUR = "gp1:d9492a946aa5b9cb8366174c7174eb4089d8e8f818aee13097c483730ce068ba"
    const val RECHECK_HOUR_OF_A = 491000L
    const val KEY_PENDING = "gp1:350070e128e346a6b936b53e447fbb1a4e6a899c8b47514ac7eafdd8911bc445"
    const val KEY_PURCHASED_AFTER_PENDING = "gp1:10ab8f1b20e9370e88c3ad45a40102d0e8d604b34da834b7d0647df07d422cc1"
    const val KEY_RECHECK_HOUR_OF_C = "gp1:38228442a6d2a701c43373e2880037b2b2999dc9ba451faeea66d1ff4fa40873"
    const val RECHECK_HOUR_OF_C = 497593L
}
