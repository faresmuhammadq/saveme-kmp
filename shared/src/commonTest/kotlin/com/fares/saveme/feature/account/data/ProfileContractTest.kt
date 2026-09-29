package com.fares.saveme.feature.account.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.data.dto.ProfileDto
import com.fares.saveme.feature.account.data.dto.ProfileWriteRequest
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.account.meBody
import com.fares.saveme.feature.account.profileJson
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileContractTest {
    private val write = ProfileWriteRequest(null, "ar", "Africa/Cairo", "EGP", 5, version = 3)

    @Test
    fun getMeDecodesAccountAndProfile() = runTest {
        val env = AccountTestEnv { jsonResponse(200, meBody()) }
        env.signedIn()
        val me = assertIs<ApiResult.Success<*>>(env.api.getMe()).value as com.fares.saveme.feature.account.data.dto.MeDto
        assertEquals("sara@example.com", me.account.email)
        assertEquals(3L, me.profile.version)
        assertEquals("Africa/Cairo", me.profile.timezone)
        val request = env.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/me"))
    }

    @Test
    fun saveSendsTheWholeProfileWithAClearedNameAsNull() = runTest {
        val env = AccountTestEnv { jsonResponse(200, """{"data":${profileJson(version = 4, displayName = null, locale = "ar")},"meta":{}}""") }
        env.signedIn()
        val saved = assertIs<ApiResult.Success<ProfileDto>>(env.api.saveProfile(write)).value
        assertEquals(4L, saved.version)
        val request = env.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/me/profile"))
        val body = request.jsonBody()
        assertEquals(setOf("display_name", "locale", "timezone", "default_currency", "budget_period_start_day", "version"), body.keys)
        assertEquals(JsonNull, body["display_name"])
        assertEquals("3", body["version"].toString())
    }

    @Test
    fun conflictCarriesTheStoredProfile() = runTest {
        val env = AccountTestEnv { jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${profileJson(version = 6, displayName = "Web")}""")) }
        env.signedIn()
        val failure = assertIs<ApiResult.Failure>(env.api.saveProfile(write))
        assertEquals(ApiErrorCode.VERSION_CONFLICT, failure.code)
        val current = AppJson.decodeFromJsonElement(ProfileDto.serializer(), assertNotNull(failure.problem.current))
        assertEquals(6L, current.version)
        assertEquals("Web", current.displayName)
    }

    @Test
    fun lockedCurrencyIsAFieldError() = runTest {
        val env = AccountTestEnv {
            jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/default_currency","code":"IMMUTABLE_FIELD"}]"""))
        }
        env.signedIn()
        val failure = assertIs<ApiResult.Failure>(env.api.saveProfile(write))
        assertEquals("/default_currency", failure.problem.errors!!.single().pointer)
        assertEquals("IMMUTABLE_FIELD", failure.problem.errors!!.single().code)
    }
}
