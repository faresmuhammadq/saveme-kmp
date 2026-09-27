package com.fares.saveme.feature.auth.data

import app.cash.turbine.test
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.data.dto.DeviceDescriptionDto
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.auth.signInSuccessBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignInContractTest {

    private val device = DeviceDescriptionDto(
        sessionId = "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70",
        deviceId = "4f7a1c3d-0000-4000-8000-000000000001",
        name = "Pixel 8",
        platform = "android",
        appVersion = "0.1.0",
    )

    private fun bodyOf(env: AuthTestEnv): JsonObject {
        val content = env.requests.single().body as OutgoingContent.ByteArrayContent
        return Json.parseToJsonElement(content.bytes().decodeToString()).jsonObject
    }

    @Test
    fun requestMatchesTheSchemaExactly() = runTest {
        val env = AuthTestEnv { jsonResponse(200, signInSuccessBody()) }
        env.api.signIn(com.fares.saveme.feature.auth.data.dto.SignInRequest("sara@example.com", "pw", device))

        val request = env.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.toString().endsWith("/api/v1/auth/login"))
        assertNull(request.headers[HttpHeaders.Authorization], "sign-in is public")
        val body = bodyOf(env)
        assertEquals(setOf("email", "password", "device"), body.keys)
        assertEquals(
            setOf("session_id", "device_id", "name", "platform", "app_version"),
            body["device"]!!.jsonObject.keys,
        )
    }

    @Test
    fun successDecodesAndIgnoresUnknownMembers() = runTest {
        val env = AuthTestEnv { jsonResponse(200, signInSuccessBody(deletionCancelled = true)) }
        val result = env.repository.signIn("sara@example.com", "pw", device)
        assertIs<ApiResult.Success<*>>(result)
        val value = (result as ApiResult.Success).value
        assertEquals(CREDENTIAL, value.credential)
        assertEquals("sara@example.com", value.account.email)
        assertEquals(true, value.deletionCancelled)
    }

    @Test
    fun failuresMapToTheirCodes() = runTest {
        val cases = listOf(
            Triple(401, "INVALID_CREDENTIALS", ApiErrorCode.INVALID_CREDENTIALS),
            Triple(423, "ACCOUNT_LOCKED", ApiErrorCode.ACCOUNT_LOCKED),
            Triple(422, "VALIDATION_FAILED", ApiErrorCode.VALIDATION_FAILED),
            Triple(429, "RATE_LIMITED", ApiErrorCode.RATE_LIMITED),
            Triple(426, "CLIENT_VERSION_UNSUPPORTED", ApiErrorCode.CLIENT_VERSION_UNSUPPORTED),
        )
        cases.forEach { (status, code, expected) ->
            val env = AuthTestEnv { jsonResponse(status, problemBody(status, code), "Retry-After" to "900") }
            val result = env.repository.signIn("sara@example.com", "pw", device)
            assertIs<ApiResult.Failure>(result)
            assertEquals(expected, result.code)
            assertEquals(900L, result.retryAfterSeconds)
        }
    }

    @Test
    fun wrongPasswordOnPublicSignInDoesNotEndASession() = runTest {
        val env = AuthTestEnv { jsonResponse(401, problemBody(401, "INVALID_CREDENTIALS")) }
        env.events.events.test {
            env.repository.signIn("sara@example.com", "pw", device)
            expectNoEvents()
        }
    }
}
