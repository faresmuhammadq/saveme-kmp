package com.fares.saveme.feature.auth.domain

import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.domain.model.AuthField
import com.fares.saveme.feature.auth.domain.model.ForgotOutcome
import com.fares.saveme.feature.auth.domain.model.ResetOutcome
import com.fares.saveme.feature.auth.domain.usecase.ForgotPasswordUseCase
import com.fares.saveme.feature.auth.domain.usecase.ResetPasswordUseCase
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

class PasswordRecoveryUseCaseTest {

    @Test
    fun forgotAnswersTheSameForAnyAddress() = runTest {
        listOf("known@example.com", "nobody@example.com").forEach { email ->
            val env = AuthTestEnv { jsonResponse(202, """{"data":{"status":"accepted"},"meta":{}}""") }
            assertEquals(ForgotOutcome.Sent, ForgotPasswordUseCase(env.repository)(email))
        }
    }

    @Test
    fun forgotMapsRateLimit() = runTest {
        val env = AuthTestEnv { jsonResponse(429, problemBody(429, "RATE_LIMITED", ""","retry_after_seconds":3600""")) }
        assertEquals(ForgotOutcome.RateLimited(3600), ForgotPasswordUseCase(env.repository)("a@example.com"))
    }

    @Test
    fun resetNeverTouchesTheLocalSession() = runTest {
        val env = AuthTestEnv { respond("", HttpStatusCode.NoContent) }
        val session = StoredSession(CREDENTIAL, "s1", Instant.parse("2026-09-26T10:00:00Z"))
        env.secureStore.write(session)
        assertEquals(ResetOutcome.Done, ResetPasswordUseCase(env.repository)("t", "new-password"))
        // FR-019: the server ends the sessions; the next 401 signs the phone out.
        assertNotNull(env.secureStore.read())
    }

    @Test
    fun resetMapsInvalidLinksAndFieldErrors() = runTest {
        val invalid = AuthTestEnv { jsonResponse(422, problemBody(422, "RESET_TOKEN_INVALID")) }
        assertEquals(ResetOutcome.LinkInvalid, ResetPasswordUseCase(invalid.repository)("t", "new-password"))

        val tokenField = AuthTestEnv { jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/token","code":"INVALID"}]""")) }
        assertEquals(ResetOutcome.LinkInvalid, ResetPasswordUseCase(tokenField.repository)("t", "new-password"))

        val passwordField = AuthTestEnv { jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/password","code":"TOO_WEAK"}]""")) }
        assertEquals(
            ResetOutcome.Validation(mapOf(AuthField.Password to "TOO_WEAK")),
            ResetPasswordUseCase(passwordField.repository)("t", "new-password"),
        )
    }
}
