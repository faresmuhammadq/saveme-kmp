package com.fares.saveme.feature.auth.domain

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.domain.model.ResendOutcome
import com.fares.saveme.feature.auth.domain.model.VerifyOutcome
import com.fares.saveme.feature.auth.domain.usecase.ResendVerificationUseCase
import com.fares.saveme.feature.auth.domain.usecase.VerifyEmailUseCase
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class VerifyAndResendUseCaseTest {

    private suspend fun verify(status: Int, body: String) =
        AuthTestEnv { jsonResponse(status, body) }.let { VerifyEmailUseCase(it.repository)("t") to it }

    @Test
    fun verifiedLeavesTheLocalAccountAlone() = runTest {
        val (outcome, env) = verify(200, """{"data":{"email_verified":true},"meta":{}}""")
        assertEquals(VerifyOutcome.Verified, outcome)
        // FR-011: the answer doesn't say whose address it was, so nothing is written locally.
        assertEquals(null, env.snapshots.read())
    }

    @Test
    fun invalidLinksMapToLinkInvalid() = runTest {
        assertEquals(VerifyOutcome.LinkInvalid, verify(422, problemBody(422, "VERIFICATION_TOKEN_INVALID")).first)
        val tokenField = problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/token","code":"INVALID"}]""")
        assertEquals(VerifyOutcome.LinkInvalid, verify(422, tokenField).first)
    }

    @Test
    fun otherFailuresCarryTheCode() = runTest {
        assertEquals(VerifyOutcome.Error(ApiErrorCode.RATE_LIMITED, "req-42"), verify(429, problemBody(429, "RATE_LIMITED")).first)
    }

    @Test
    fun resendMapsSentAndRateLimitWithoutRetrying() = runTest {
        val ok = AuthTestEnv { jsonResponse(202, """{"data":{"status":"accepted"},"meta":{}}""") }
        ok.secureStore.write(StoredSession(CREDENTIAL, "s1", Instant.parse("2026-09-26T10:00:00Z")))
        assertEquals(ResendOutcome.Sent, ResendVerificationUseCase(ok.repository)())

        val limited = AuthTestEnv { jsonResponse(429, problemBody(429, "RATE_LIMITED", ""","retry_after_seconds":1200""")) }
        limited.secureStore.write(StoredSession(CREDENTIAL, "s1", Instant.parse("2026-09-26T10:00:00Z")))
        assertEquals(ResendOutcome.RateLimited(1200), ResendVerificationUseCase(limited.repository)())
        assertEquals(1, limited.requests.size)
    }
}
