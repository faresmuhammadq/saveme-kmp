package com.fares.saveme.feature.reports

/**
 * Real answers of the local backend (`saveme-backend@b63e917`, reports.yaml v1.11) recorded in the live contract QA of
 * spec 014 (T089, qa-report.md) with the app's headers: the reports of REF.
 * Accounts A (Plus), B (partner) and D (start day 25, pending) are throwaway QA accounts; no credential is kept.
 * Bodies are verbatim.
 */

internal object CapturedReportsRefusals {
    val all: Map<String, CapturedReportsResponses.Captured> = mapOf(
        "ref/other-wallet-404" to CapturedReportsResponses.Captured("wallets/01a10e5f-7aa6-7265-a399-142f524272b2/reports/summary", """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10e61-f2a4-7391-bf71-ab6654b08580"}""", 404),
        "ref/malformed-id" to CapturedReportsResponses.Captured("wallets/not-a-uuid/reports/summary", """{"type":"http:\/\/saveme-backend.test\/problems\/invalid-id","title":"Invalid identifier","status":422,"detail":"The identifier is not valid.","code":"INVALID_ID","request_id":"01a10e61-f31b-706d-9a68-81e186407c8f"}""", 422),
        "ref/unknown-wallet" to CapturedReportsResponses.Captured("wallets/01a10e61-f34e-7eba-92c4-568567b2ea7e/reports/summary", """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10e61-f3a2-71a5-98b2-3365e864eea3"}""", 404),
        "ref/deleted-wallet" to CapturedReportsResponses.Captured("wallets/01a10e5f-7a1a-75d8-8ff6-c74a5f7152c4/reports/summary", """{"type":"http:\/\/saveme-backend.test\/problems\/not-found","title":"Not found","status":404,"detail":"We could not find that.","code":"NOT_FOUND","request_id":"01a10e61-f50f-73c7-9f8f-aab97e127a31"}""", 404),
    )
}
