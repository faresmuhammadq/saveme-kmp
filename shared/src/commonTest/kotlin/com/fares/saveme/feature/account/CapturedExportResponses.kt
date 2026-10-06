package com.fares.saveme.feature.account

import com.fares.saveme.feature.billing.Captured

/**
 * Real answers of the export and deletion endpoints captured on 2026-10-06 from the local backend
 * (saveme-backend@776e5b2, spec 022, `exports.yaml` / `identity.yaml` v1.13) with throwaway accounts (specs/016-export-deletion/qa-report.md).
 * Every signed download link has its signature replaced by a fake of the same shape (64 hex); no bearer credential, password or
 * address of a real person is kept. The cursors are the server's opaque, signature-free values.
 */
object CapturedExportResponses {

    /** Live `x_202_account`. */
    val requestAccountQueued = Captured(202, listOf(), """{"data":{"id":"01a10e94-eb46-71dd-8e95-242559118764","scope":"account","wallet_id":null,"status":"queued","failure_code":null,"requested_at":"2026-10-06T00:20:03.014585Z","completed_at":null,"expires_at":null,"size_bytes":null,"download_url":null,"download_url_expires_at":null},"meta":{}}""")

    /** Live `x_202_wallet`. */
    val requestWalletQueued = Captured(202, listOf(), """{"data":{"id":"01a10e95-71aa-7082-b075-73d352f908d4","scope":"wallet","wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","status":"queued","failure_code":null,"requested_at":"2026-10-06T00:20:37.418407Z","completed_at":null,"expires_at":null,"size_bytes":null,"download_url":null,"download_url_expires_at":null},"meta":{}}""")

    /** Live `x_202_wallet_as_viewer`. */
    val requestWalletAsViewer = Captured(202, listOf(), """{"data":{"id":"01a10e96-01f6-7175-bd9f-0f231cf58685","scope":"wallet","wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","status":"queued","failure_code":null,"requested_at":"2026-10-06T00:21:14.358353Z","completed_at":null,"expires_at":null,"size_bytes":null,"download_url":null,"download_url_expires_at":null},"meta":{}}""")

    /** Live `x_409_account_again`. */
    val conflictAccountAgain = Captured(409, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/export-in-progress","title":"Export already in progress","status":409,"detail":"An export is already being prepared. Wait for it to finish.","code":"EXPORT_IN_PROGRESS","request_id":"01a10e94-eb9f-71e7-a049-469f7f115f21","current":{"id":"01a10e94-eb46-71dd-8e95-242559118764","scope":"account","wallet_id":null,"status":"queued","failure_code":null,"requested_at":"2026-10-06T00:20:03.014585Z","completed_at":null,"expires_at":null,"size_bytes":null,"download_url":null,"download_url_expires_at":null}}""")

    /** Live `x_409_wallet_while_active`. */
    val conflictWalletWhileActive = Captured(409, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/export-in-progress","title":"Export already in progress","status":409,"detail":"An export is already being prepared. Wait for it to finish.","code":"EXPORT_IN_PROGRESS","request_id":"01a10e94-ebf3-70cf-978e-0b39a13ada9c","current":{"id":"01a10e94-eb46-71dd-8e95-242559118764","scope":"account","wallet_id":null,"status":"queued","failure_code":null,"requested_at":"2026-10-06T00:20:03.014585Z","completed_at":null,"expires_at":null,"size_bytes":null,"download_url":null,"download_url_expires_at":null}}""")

    /** Live `x_429_sixth`. */
    val rateLimitedSixth = Captured(429, listOf("Retry-After" to "86386"), """{"type":"http:\/\/saveme-backend.test\/problems\/rate-limited","title":"Rate limited","status":429,"detail":"Too many attempts. Please wait a moment and try again.","code":"RATE_LIMITED","request_id":"01a10e99-1537-7389-85f3-e8b9ea236bf4","retry_after_seconds":86386}""")

    /** Live `x_404_unknown_wallet`. */
    val walletNotFoundUnknown = Captured(404, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10e94-ea3b-71fd-851e-b31346a55387"}""")

    /** Live `x_404_non_member`. */
    val walletNotFoundNonMember = Captured(404, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10e94-ea99-7377-8dd0-214b0be63903"}""")

    /** Live `x_422_scope`. */
    val invalidScope = Captured(422, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10e94-e8db-701e-baa0-0583f0e95f82","errors":[{"pointer":"\/scope","code":"INVALID_ENUM_VALUE","detail":"This value is not one of the allowed options."}]}""")

    /** Live `x_422_wallet_missing`. */
    val walletMissing = Captured(422, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10e94-e93d-733a-b683-3eecff4615da","errors":[{"pointer":"\/wallet_id","code":"REQUIRED","detail":"This field is required."}]}""")

    /** Live `x_422_empty`. */
    val emptyBody = Captured(422, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10e94-e991-718e-97ab-8bbe106cce6a","errors":[{"pointer":"\/scope","code":"REQUIRED","detail":"This field is required."}]}""")

    /** Live `x_422_bad_wallet_id`. */
    val badWalletId = Captured(422, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/validation-failed","title":"Validation failed","status":422,"detail":"Some of the information sent is not valid. Each problem is listed.","code":"VALIDATION_FAILED","request_id":"01a10e94-e9e6-719a-8e7e-6c68b174ff02","errors":[{"pointer":"\/wallet_id","code":"INVALID_ID","detail":"The identifier is not valid."}]}""")

    /** Live `x_401`. */
    val unauthenticated = Captured(401, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/unauthenticated","title":"Unauthenticated","status":401,"detail":"Please sign in again.","code":"UNAUTHENTICATED","request_id":"01a10e94-eaf0-72d9-b08c-a3f6ce95aeda"}""")

    /** Live `x_get_account_queued`. */
    val getAccountQueued = Captured(200, listOf(), """{"data":{"id":"01a10e94-eb46-71dd-8e95-242559118764","scope":"account","wallet_id":null,"status":"queued","failure_code":null,"requested_at":"2026-10-06T00:20:03.014585Z","completed_at":null,"expires_at":null,"size_bytes":null,"download_url":null,"download_url_expires_at":null},"meta":{}}""")

    /** Live `x_get_account_ready_c`. */
    val getAccountReady = Captured(200, listOf(), """{"data":{"id":"01a10e94-eb46-71dd-8e95-242559118764","scope":"account","wallet_id":null,"status":"ready","failure_code":null,"requested_at":"2026-10-06T00:20:03.014585Z","completed_at":"2026-10-06T00:20:06.097645Z","expires_at":"2026-10-07T00:20:06.097645Z","size_bytes":3066,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e94-eb46-71dd-8e95-242559118764\/file?expires=1791246907&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:35:07.884145Z"},"meta":{}}""")

    /** Live `y_get_wallet_queued`. */
    val getWalletQueued = Captured(200, listOf(), """{"data":{"id":"01a10ea2-69db-712c-8d51-913ade9f8bcd","scope":"wallet","wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","status":"queued","failure_code":null,"requested_at":"2026-10-06T00:34:47.387425Z","completed_at":null,"expires_at":null,"size_bytes":null,"download_url":null,"download_url_expires_at":null},"meta":{}}""")

    /** Live `y_get_wallet_running`. */
    val getWalletRunning = Captured(200, listOf(), """{"data":{"id":"01a10ea2-69db-712c-8d51-913ade9f8bcd","scope":"wallet","wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","status":"running","failure_code":null,"requested_at":"2026-10-06T00:34:47.387425Z","completed_at":null,"expires_at":null,"size_bytes":null,"download_url":null,"download_url_expires_at":null},"meta":{}}""")

    /** Live `y_get_wallet_ready`. */
    val getWalletReady = Captured(200, listOf(), """{"data":{"id":"01a10ea2-69db-712c-8d51-913ade9f8bcd","scope":"wallet","wallet_id":"01a10e97-a75f-7d07-a16c-84044a9b88ef","status":"ready","failure_code":null,"requested_at":"2026-10-06T00:34:47.387425Z","completed_at":"2026-10-06T00:34:50.483101Z","expires_at":"2026-10-07T00:34:50.483101Z","size_bytes":1948,"download_url":"http://saveme-backend.test/exports/01a10ea2-69db-712c-8d51-913ade9f8bcd/file?expires=1791247790&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:49:50.543770Z"},"meta":{}}""")

    /** Live `x_get_wallet_ready_before_expiry`. */
    val getWalletReadyBeforeExpiry = Captured(200, listOf(), """{"data":{"id":"01a10e95-71aa-7082-b075-73d352f908d4","scope":"wallet","wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","status":"ready","failure_code":null,"requested_at":"2026-10-06T00:20:37.418407Z","completed_at":"2026-10-06T00:20:39.325307Z","expires_at":"2026-10-07T00:20:39.325307Z","size_bytes":1309,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e95-71aa-7082-b075-73d352f908d4\/file?expires=1791246945&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:35:45.011154Z"},"meta":{}}""")

    /** Live `x_get_wallet_expired`. */
    val getWalletExpired = Captured(200, listOf(), """{"data":{"id":"01a10e95-71aa-7082-b075-73d352f908d4","scope":"wallet","wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","status":"expired","failure_code":null,"requested_at":"2026-10-06T00:20:37.418407Z","completed_at":"2026-10-06T00:20:39.325307Z","expires_at":"2026-10-06T00:19:45.665090Z","size_bytes":1309,"download_url":null,"download_url_expires_at":null},"meta":{}}""")

    /** Live `x_404_get_unknown`. */
    val getUnknown = Captured(404, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10e94-fe7e-732a-8c47-9ea6ff57c670"}""")

    /** Live `x_404_get_foreign`. */
    val getForeign = Captured(404, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10e94-fed4-709c-88a3-47854329c1ea"}""")

    /** Live `x_get_bad_id`. */
    val getBadId = Captured(422, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/invalid-id","title":"Invalid identifier","status":422,"detail":"The identifier is not valid.","code":"INVALID_ID","request_id":"01a10e94-ff2f-7021-aae5-5bf3be21ddca"}""")

    /** Live `x_list_20`. */
    val listTwo = Captured(200, listOf(), """{"data":[{"id":"01a10e95-71aa-7082-b075-73d352f908d4","scope":"wallet","wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","status":"ready","failure_code":null,"requested_at":"2026-10-06T00:20:37.418407Z","completed_at":"2026-10-06T00:20:39.325307Z","expires_at":"2026-10-07T00:20:39.325307Z","size_bytes":1309,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e95-71aa-7082-b075-73d352f908d4\/file?expires=1791246939&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:35:39.694650Z"},{"id":"01a10e94-eb46-71dd-8e95-242559118764","scope":"account","wallet_id":null,"status":"ready","failure_code":null,"requested_at":"2026-10-06T00:20:03.014585Z","completed_at":"2026-10-06T00:20:06.097645Z","expires_at":"2026-10-07T00:20:06.097645Z","size_bytes":3066,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e94-eb46-71dd-8e95-242559118764\/file?expires=1791246939&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:35:39.695273Z"}],"meta":{"next_cursor":null,"has_more":false,"limit":20}}""")

    /** Live `x_list_p1`. */
    val listPageOne = Captured(200, listOf(), """{"data":[{"id":"01a10e95-71aa-7082-b075-73d352f908d4","scope":"wallet","wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","status":"ready","failure_code":null,"requested_at":"2026-10-06T00:20:37.418407Z","completed_at":"2026-10-06T00:20:39.325307Z","expires_at":"2026-10-07T00:20:39.325307Z","size_bytes":1309,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e95-71aa-7082-b075-73d352f908d4\/file?expires=1791246939&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:35:39.784046Z"}],"meta":{"next_cursor":"eyJrIjoiOTBlZDA5ZDMyZDQ1N2NiMSIsInAiOnsidmFsdWUiOiIyMDI2LTEwLTA2IDAwOjIwOjM3LjQxODQwNyswMDowMCIsImlkIjoiMDFhMTBlOTUtNzFhYS03MDgyLWIwNzUtNzNkMzUyZjkwOGQ0In19","has_more":true,"limit":1}}""")

    /** Live `x_list_p2`. */
    val listPageTwo = Captured(200, listOf(), """{"data":[{"id":"01a10e94-eb46-71dd-8e95-242559118764","scope":"account","wallet_id":null,"status":"ready","failure_code":null,"requested_at":"2026-10-06T00:20:03.014585Z","completed_at":"2026-10-06T00:20:06.097645Z","expires_at":"2026-10-07T00:20:06.097645Z","size_bytes":3066,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e94-eb46-71dd-8e95-242559118764\/file?expires=1791246939&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:35:39.894282Z"}],"meta":{"next_cursor":null,"has_more":false,"limit":1}}""")

    /** Live `x_400_invalid_cursor`. */
    val listInvalidCursor = Captured(400, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/invalid-cursor","title":"Invalid cursor","status":400,"detail":"The page cursor is not valid. Start again from the first page.","code":"INVALID_CURSOR","request_id":"01a10e95-7b84-7356-bd10-2ec2a5f95c20"}""")

    /** Live `x_list_limit0`. */
    val listLimitZero = Captured(400, listOf(), """{"type":"http:\/\/saveme-backend.test\/problems\/invalid-query-parameter","title":"Invalid query parameter","status":400,"detail":"A query parameter cannot be used as sent.","code":"INVALID_QUERY_PARAMETER","request_id":"01a10e95-7bd4-724c-85a8-170824b39d09"}""")

    /** Live `x_list_limit500`. */
    val listLimitHuge = Captured(200, listOf(), """{"data":[{"id":"01a10e95-71aa-7082-b075-73d352f908d4","scope":"wallet","wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","status":"ready","failure_code":null,"requested_at":"2026-10-06T00:20:37.418407Z","completed_at":"2026-10-06T00:20:39.325307Z","expires_at":"2026-10-07T00:20:39.325307Z","size_bytes":1309,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e95-71aa-7082-b075-73d352f908d4\/file?expires=1791246940&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:35:40.141221Z"},{"id":"01a10e94-eb46-71dd-8e95-242559118764","scope":"account","wallet_id":null,"status":"ready","failure_code":null,"requested_at":"2026-10-06T00:20:03.014585Z","completed_at":"2026-10-06T00:20:06.097645Z","expires_at":"2026-10-07T00:20:06.097645Z","size_bytes":3066,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e94-eb46-71dd-8e95-242559118764\/file?expires=1791246940&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:35:40.141730Z"}],"meta":{"next_cursor":null,"has_more":false,"limit":200}}""")

    /** Live `x_list_ready_and_expired`. */
    val listReadyAndExpired = Captured(200, listOf(), """{"data":[{"id":"01a10e95-71aa-7082-b075-73d352f908d4","scope":"wallet","wallet_id":"01a10e94-7f9b-7b48-bc3e-6fb34d109977","status":"expired","failure_code":null,"requested_at":"2026-10-06T00:20:37.418407Z","completed_at":"2026-10-06T00:20:39.325307Z","expires_at":"2026-10-06T00:19:45.665090Z","size_bytes":1309,"download_url":null,"download_url_expires_at":null},{"id":"01a10e94-eb46-71dd-8e95-242559118764","scope":"account","wallet_id":null,"status":"ready","failure_code":null,"requested_at":"2026-10-06T00:20:03.014585Z","completed_at":"2026-10-06T00:20:06.097645Z","expires_at":"2026-10-07T00:20:06.097645Z","size_bytes":3066,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e94-eb46-71dd-8e95-242559118764\/file?expires=1791246945&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:35:45.875302Z"}],"meta":{"next_cursor":null,"has_more":false,"limit":20}}""")

    /** Live `x_list_five_ready`. */
    val listFiveReady = Captured(200, listOf(), """{"data":[{"id":"01a10e99-091b-7323-8a33-33ad55fbfefb","scope":"account","wallet_id":null,"status":"ready","failure_code":null,"requested_at":"2026-10-06T00:24:32.795243Z","completed_at":"2026-10-06T00:24:35.349294Z","expires_at":"2026-10-07T00:24:35.349294Z","size_bytes":2396,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e99-091b-7323-8a33-33ad55fbfefb\/file?expires=1791247176&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:39:36.114495Z"},{"id":"01a10e98-fca6-70be-bfa8-33a930cc398b","scope":"account","wallet_id":null,"status":"ready","failure_code":null,"requested_at":"2026-10-06T00:24:29.606786Z","completed_at":"2026-10-06T00:24:32.197237Z","expires_at":"2026-10-07T00:24:32.197237Z","size_bytes":2396,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e98-fca6-70be-bfa8-33a930cc398b\/file?expires=1791247176&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:39:36.115049Z"},{"id":"01a10e98-f060-7236-9ebd-231ed4579329","scope":"account","wallet_id":null,"status":"ready","failure_code":null,"requested_at":"2026-10-06T00:24:26.464769Z","completed_at":"2026-10-06T00:24:29.046082Z","expires_at":"2026-10-07T00:24:29.046082Z","size_bytes":2395,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e98-f060-7236-9ebd-231ed4579329\/file?expires=1791247176&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:39:36.115305Z"},{"id":"01a10e98-e3fa-7115-b4e2-10826376c179","scope":"account","wallet_id":null,"status":"ready","failure_code":null,"requested_at":"2026-10-06T00:24:23.290330Z","completed_at":"2026-10-06T00:24:25.893830Z","expires_at":"2026-10-07T00:24:25.893830Z","size_bytes":2395,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e98-e3fa-7115-b4e2-10826376c179\/file?expires=1791247176&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:39:36.115541Z"},{"id":"01a10e98-de6f-705d-8673-8df4ea439392","scope":"account","wallet_id":null,"status":"ready","failure_code":null,"requested_at":"2026-10-06T00:24:21.871681Z","completed_at":"2026-10-06T00:24:22.737275Z","expires_at":"2026-10-07T00:24:22.737275Z","size_bytes":2395,"download_url":"http:\/\/saveme-backend.test\/exports\/01a10e98-de6f-705d-8673-8df4ea439392\/file?expires=1791247176&signature=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","download_url_expires_at":"2026-10-06T00:39:36.115768Z"}],"meta":{"next_cursor":null,"has_more":false,"limit":20}}""")

    /** Live `x_202_account_while_pending`. */
    val requestWhilePending = Captured(202, listOf(), """{"data":{"id":"01a10e98-2cb7-71f2-a5e2-6ae395a7505b","scope":"account","wallet_id":null,"status":"queued","failure_code":null,"requested_at":"2026-10-06T00:23:36.375275Z","completed_at":null,"expires_at":null,"size_bytes":null,"download_url":null,"download_url_expires_at":null},"meta":{}}""")
}
