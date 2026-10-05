package com.fares.saveme.feature.billing.domain

import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.billing.RAW_CODE
import com.fares.saveme.feature.billing.RedeemTestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every refusal of research R-08 maps to its own outcome and nothing is retried (FR-017 to FR-026). */
class RedeemServiceRefusalTest {

    private val attempt = AttemptKey("0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70")

    private suspend fun outcome(
        scope: kotlinx.coroutines.CoroutineScope,
        status: Int,
        code: String,
        extra: String = "",
        headers: Array<Pair<String, String>> = emptyArray(),
    ): Pair<RedeemOutcome, Int> {
        val t = RedeemTestEnv(scope) { jsonResponse(status, problemBody(status, code, extra), *headers) }
        t.signIn()
        val result = t.service.redeem(RAW_CODE, attempt)
        return result to t.redeemRequests.size
    }

    @Test
    fun eachCatalogueCodeHasItsOwnOutcome() = runTest {
        val table = listOf(
            Triple(404, "NOT_FOUND", RedeemOutcome.NotValid),
            Triple(409, "REDEEM_CODE_USED", RedeemOutcome.Used),
            Triple(410, "REDEEM_CODE_EXPIRED", RedeemOutcome.Expired),
            Triple(403, "REDEEM_CODE_EMAIL_MISMATCH", RedeemOutcome.OtherAddress),
            Triple(403, "EMAIL_NOT_VERIFIED", RedeemOutcome.NotVerified),
            Triple(409, "IDEMPOTENCY_REQUEST_IN_PROGRESS", RedeemOutcome.InProgress),
            Triple(422, "VALIDATION_FAILED", RedeemOutcome.InvalidFormat),
        )
        table.forEach { (status, code, expected) ->
            val (result, requests) = outcome(backgroundScope, status, code)
            assertEquals(expected, result, code)
            assertEquals(1, requests, "$code is never retried")
        }
    }

    @Test
    fun aReusedKeyCarriesTheRequestId() = runTest {
        assertEquals(RedeemOutcome.KeyReused("req-42"), outcome(backgroundScope, 409, "IDEMPOTENCY_KEY_REUSED").first)
    }

    @Test
    fun rateLimitTakesTheBodyWaitThenTheHeaderThenNothing() = runTest {
        assertEquals(RedeemOutcome.RateLimited(120), outcome(backgroundScope, 429, "RATE_LIMITED", ""","retry_after_seconds":120""").first)
        assertEquals(RedeemOutcome.RateLimited(90), outcome(backgroundScope, 429, "RATE_LIMITED", headers = arrayOf("Retry-After" to "90")).first)
        assertEquals(RedeemOutcome.RateLimited(null), outcome(backgroundScope, 429, "RATE_LIMITED").first)
    }

    @Test
    fun sessionAndUpdateRefusalsAreHandledAppWide() = runTest {
        assertEquals(RedeemOutcome.Failed(null), outcome(backgroundScope, 401, "UNAUTHENTICATED").first)
        assertEquals(RedeemOutcome.Failed(null), outcome(backgroundScope, 426, "CLIENT_VERSION_UNSUPPORTED").first)
    }

    @Test
    fun anythingElseIsAnUnexpectedFailureWithTheRequestIdWhenThereIsOne() = runTest {
        assertEquals(RedeemOutcome.Failed("req-42"), outcome(backgroundScope, 500, "INTERNAL_ERROR").first)
        val t = RedeemTestEnv(backgroundScope) { jsonResponse(500, "not json at all") }
        t.signIn()
        assertEquals(RedeemOutcome.Failed(null), t.service.redeem(RAW_CODE, attempt))
    }
}
