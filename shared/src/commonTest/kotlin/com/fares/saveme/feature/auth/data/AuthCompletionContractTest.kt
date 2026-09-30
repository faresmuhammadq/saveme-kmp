package com.fares.saveme.feature.auth.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** contracts/identity-api-client.md: one test per operation and handled code (P-14.3). */
class AuthCompletionContractTest {

    private val accepted = """{"data":{"status":"accepted"},"meta":{}}"""

    private fun bodyOf(env: AuthTestEnv): JsonObject {
        val content = env.requests.single().body as OutgoingContent.ByteArrayContent
        return Json.parseToJsonElement(content.bytes().decodeToString()).jsonObject
    }

    private fun AuthTestEnv.assertPost(pathSuffix: String, public: Boolean) {
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.toString().endsWith("/api/v1/$pathSuffix"), request.url.toString())
        if (public) assertNull(request.headers[HttpHeaders.Authorization]) else assertNotNull(request.headers[HttpHeaders.Authorization])
    }

    private fun validation(pointer: String, code: String = "INVALID") =
        problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"$pointer","code":"$code"}]""")

    private fun assertFailure(result: ApiResult<*>, code: ApiErrorCode): ApiResult.Failure {
        assertIs<ApiResult.Failure>(result)
        assertEquals(code, result.code)
        return result
    }

    // --- register ---------------------------------------------------------------------------

    @Test
    fun registerSendsExactlyTheSchemaAndOmitsABlankDisplayName() = runTest {
        val env = AuthTestEnv { jsonResponse(202, accepted) }
        val result = env.repository.register("new@example.com", "long-password", displayName = "  ", timezone = "Africa/Cairo")
        assertIs<ApiResult.Success<*>>(result)
        env.assertPost("auth/register", public = true)
        assertEquals(setOf("email", "password", "timezone"), bodyOf(env).keys)
    }

    @Test
    fun registerSendsTheDisplayNameWhenGiven() = runTest {
        val env = AuthTestEnv { jsonResponse(202, accepted) }
        env.repository.register("new@example.com", "long-password", displayName = "Sara", timezone = "Africa/Cairo")
        assertEquals(setOf("email", "password", "display_name", "timezone"), bodyOf(env).keys)
    }

    @Test
    fun registerMapsAlreadyRegisteredValidationAndRateLimit() = runTest {
        val existing = AuthTestEnv { jsonResponse(409, problemBody(409, "EMAIL_ALREADY_REGISTERED")) }
        assertFailure(existing.repository.register("a@example.com", "long-password", null, "UTC"), ApiErrorCode.EMAIL_ALREADY_REGISTERED)

        listOf("/email", "/password", "/display_name").forEach { pointer ->
            val env = AuthTestEnv { jsonResponse(422, validation(pointer)) }
            val failure = assertFailure(env.repository.register("a@example.com", "long-password", "S", "UTC"), ApiErrorCode.VALIDATION_FAILED)
            assertEquals(pointer, failure.problem.errors!!.single().pointer)
        }

        val limited = AuthTestEnv { jsonResponse(429, problemBody(429, "RATE_LIMITED", ""","retry_after_seconds":600""")) }
        assertEquals(600L, assertFailure(limited.repository.register("a@example.com", "long-password", null, "UTC"), ApiErrorCode.RATE_LIMITED).retryAfterSeconds)
    }

    // --- verify-email -------------------------------------------------------------------------

    @Test
    fun verifyEmailIsPublicAndSendsOnlyTheToken() = runTest {
        val env = AuthTestEnv { jsonResponse(200, """{"data":{"email_verified":true},"meta":{}}""") }
        val result = env.repository.verifyEmail("secret-1")
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(true, (result as ApiResult.Success).value.emailVerified)
        env.assertPost("auth/verify-email", public = true)
        assertEquals(setOf("token"), bodyOf(env).keys)
    }

    @Test
    fun verifyEmailMapsAnInvalidLink() = runTest {
        val env = AuthTestEnv { jsonResponse(422, problemBody(422, "VERIFICATION_TOKEN_INVALID")) }
        assertFailure(env.repository.verifyEmail("old"), ApiErrorCode.VERIFICATION_TOKEN_INVALID)
    }

    // --- resend-verification ------------------------------------------------------------------

    @Test
    fun resendIsCredentialedAndHasNoBody() = runTest {
        val env = AuthTestEnv { jsonResponse(202, accepted) }
        env.secureStore.write(StoredSession(CREDENTIAL, "s1", Instant.parse("2026-09-26T10:00:00Z")))
        assertIs<ApiResult.Success<*>>(env.repository.resendVerification())
        env.assertPost("auth/resend-verification", public = false)
    }

    @Test
    fun resendMapsTheRateLimit() = runTest {
        val env = AuthTestEnv { jsonResponse(429, problemBody(429, "RATE_LIMITED", ""","retry_after_seconds":1800""")) }
        env.secureStore.write(StoredSession(CREDENTIAL, "s1", Instant.parse("2026-09-26T10:00:00Z")))
        assertEquals(1800L, assertFailure(env.repository.resendVerification(), ApiErrorCode.RATE_LIMITED).retryAfterSeconds)
    }

    // --- forgot-password ----------------------------------------------------------------------

    @Test
    fun forgotPasswordIsPublicAndSendsOnlyTheEmail() = runTest {
        val env = AuthTestEnv { jsonResponse(202, accepted) }
        assertIs<ApiResult.Success<*>>(env.repository.forgotPassword("anyone@example.com"))
        env.assertPost("auth/forgot-password", public = true)
        assertEquals(setOf("email"), bodyOf(env).keys)
    }

    @Test
    fun forgotPasswordMapsValidationAndRateLimit() = runTest {
        val invalid = AuthTestEnv { jsonResponse(422, validation("/email")) }
        assertFailure(invalid.repository.forgotPassword("x"), ApiErrorCode.VALIDATION_FAILED)
        val limited = AuthTestEnv { jsonResponse(429, problemBody(429, "RATE_LIMITED")) }
        assertFailure(limited.repository.forgotPassword("a@example.com"), ApiErrorCode.RATE_LIMITED)
    }

    // --- reset-password -----------------------------------------------------------------------

    @Test
    fun resetPasswordIsPublicSendsTokenAndPasswordAndAnswers204() = runTest {
        val env = AuthTestEnv { respond("", HttpStatusCode.NoContent) }
        assertIs<ApiResult.Success<*>>(env.repository.resetPassword("secret-2", "new-password"))
        env.assertPost("auth/reset-password", public = true)
        assertEquals(setOf("token", "password"), bodyOf(env).keys)
    }

    @Test
    fun resetPasswordMapsInvalidLinkFieldErrorsAndRateLimit() = runTest {
        val invalid = AuthTestEnv { jsonResponse(422, problemBody(422, "RESET_TOKEN_INVALID")) }
        assertFailure(invalid.repository.resetPassword("old", "new-password"), ApiErrorCode.RESET_TOKEN_INVALID)

        listOf("/password", "/token").forEach { pointer ->
            val env = AuthTestEnv { jsonResponse(422, validation(pointer)) }
            val failure = assertFailure(env.repository.resetPassword("t", "short"), ApiErrorCode.VALIDATION_FAILED)
            assertEquals(pointer, failure.problem.errors!!.single().pointer)
        }

        val limited = AuthTestEnv { jsonResponse(429, problemBody(429, "RATE_LIMITED")) }
        assertFailure(limited.repository.resetPassword("t", "new-password"), ApiErrorCode.RATE_LIMITED)
    }

    @Test
    fun nothingIsRetriedAutomatically() = runTest {
        val env = AuthTestEnv { jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE")) }
        env.repository.register("a@example.com", "long-password", null, "UTC")
        env.repository.forgotPassword("a@example.com")
        env.repository.resetPassword("t", "new-password")
        env.repository.verifyEmail("t")
        assertEquals(4, env.requests.size)
    }
}
