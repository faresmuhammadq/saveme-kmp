package com.fares.saveme.feature.auth.domain

import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.domain.model.AuthField
import com.fares.saveme.feature.auth.domain.model.RegisterOutcome
import com.fares.saveme.feature.auth.domain.usecase.RegisterUseCase
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class RegisterUseCaseTest {

    private suspend fun run(status: Int, body: String, vararg headers: Pair<String, String>): Pair<RegisterOutcome, AuthTestEnv> {
        val env = AuthTestEnv { jsonResponse(status, body, *headers) }
        return RegisterUseCase(env.clientInfo, env.repository)(" new@example.com ", "long-password", null) to env
    }

    @Test
    fun sendsTheDeviceTimezoneAndATrimmedEmail() = runTest {
        val (outcome, env) = run(202, """{"data":{"status":"accepted"},"meta":{}}""")
        assertEquals(RegisterOutcome.Accepted, outcome)
        val body = Json.parseToJsonElement((env.requests.single().body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
        assertEquals("Africa/Cairo", body["timezone"]!!.jsonPrimitive.content)
        assertEquals("new@example.com", body["email"]!!.jsonPrimitive.content)
    }

    @Test
    fun anExistingAddressIsAlreadyRegistered() = runTest {
        assertEquals(RegisterOutcome.AlreadyRegistered, run(409, problemBody(409, "EMAIL_ALREADY_REGISTERED")).first)
    }

    @Test
    fun fieldErrorsLandOnFields() = runTest {
        val body = problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/password","code":"TOO_SHORT"},{"pointer":"/display_name","code":"TOO_LONG"}]""")
        val outcome = run(422, body).first
        assertEquals(RegisterOutcome.Validation(mapOf(AuthField.Password to "TOO_SHORT", AuthField.DisplayName to "TOO_LONG")), outcome)
    }

    @Test
    fun rateLimitCarriesTheWait() = runTest {
        assertEquals(RegisterOutcome.RateLimited(600), run(429, problemBody(429, "RATE_LIMITED", ""","retry_after_seconds":600""")).first)
    }

    @Test
    fun serverFailuresAreNotRetried() = runTest {
        val (outcome, env) = run(503, problemBody(503, "SERVICE_UNAVAILABLE"))
        assertEquals(1, env.requests.size)
        assertEquals(RegisterOutcome.Error(com.fares.saveme.core.network.ApiErrorCode.SERVICE_UNAVAILABLE, "req-42"), outcome)
    }
}
