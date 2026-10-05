package com.fares.saveme.feature.billing

import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.billing.domain.AttemptKey
import com.fares.saveme.feature.sharing.domain.LogCapture
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The code (raw and grouped) and the attempt key never reach a log line, whatever the answer (SC-004, P-09.2). */
class RedeemLoggingTest {

    private val attempt = AttemptKey("0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70")

    @Test
    fun noLogLineCarriesTheCodeOrTheKeyForAnyAnswer() = runTest {
        val answers: List<suspend io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData> = listOf(
            { jsonResponse(200, redeemedGovernsBody()) },
            { jsonResponse(404, problemBody(404, "NOT_FOUND")) },
            { jsonResponse(409, problemBody(409, "REDEEM_CODE_USED")) },
            { jsonResponse(410, problemBody(410, "REDEEM_CODE_EXPIRED")) },
            { jsonResponse(403, problemBody(403, "REDEEM_CODE_EMAIL_MISMATCH")) },
            { jsonResponse(429, problemBody(429, "RATE_LIMITED", ""","retry_after_seconds":60""")) },
            { jsonResponse(422, problemBody(422, "VALIDATION_FAILED")) },
            { jsonResponse(500, "boom") },
            { throw IOException("no route") },
        )
        val capture = LogCapture()
        try {
            answers.forEach { answer ->
                val t = RedeemTestEnv(backgroundScope) { answer(this) }
                t.signIn()
                t.service.redeem(RAW_CODE, attempt)
                advanceUntilIdle()
            }
        } finally {
            capture.close()
        }
        assertTrue(capture.lines.isNotEmpty(), "the capture saw the redemption logs")
        capture.lines.forEach { line ->
            assertFalse(line.contains(RAW_CODE), line)
            assertFalse(line.contains(GROUPED_CODE.take(9)), line)
            assertFalse(line.contains(attempt.value), line)
        }
    }
}
