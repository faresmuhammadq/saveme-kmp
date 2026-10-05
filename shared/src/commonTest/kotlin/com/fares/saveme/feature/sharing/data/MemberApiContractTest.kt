package com.fares.saveme.feature.sharing.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.dataBody
import com.fares.saveme.feature.sharing.data.dto.MemberDto
import com.fares.saveme.feature.wallets.memberJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A6-A8 of contracts/sharing-api-client.md with the examples of sharing.yaml v1.9 (P-14.3). */
class MemberApiContractTest {

    private fun memberBody(role: String = "viewer", version: Int = 3, deleted: Boolean = false) =
        dataBody(memberJson("m2", "u2", "Karim", role).replace("\"version\":1", "\"version\":$version").let {
            if (deleted) it.replace("\"deleted_at\":null", "\"deleted_at\":\"2026-10-02T11:00:00Z\"") else it
        })

    @Test
    fun changeRoleIsAPatchOfRoleAndVersionOnly() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, memberBody()) }
        env.signedIn()
        val member = assertIs<ApiResult.Success<MemberDto>>(env.memberApi.changeRole("w1", "u2", "viewer", 2)).value
        assertEquals("viewer", member.role)
        assertEquals(3L, member.version)
        val request = env.requests.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/members/u2"))
        assertEquals(setOf("role", "version"), request.jsonBody().keys)
        assertEquals("\"viewer\"", request.jsonBody()["role"].toString())
        assertEquals("2", request.jsonBody()["version"].toString())
    }

    @Test
    fun removeIsADeleteWithTheVersionInAJsonBodyAndAnswersATombstone() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, memberBody(deleted = true)) }
        env.signedIn()
        val member = assertIs<ApiResult.Success<MemberDto>>(env.memberApi.remove("w1", "u2", 2)).value
        assertNotNull(member.deletedAt)
        val request = env.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/members/u2"))
        assertEquals(setOf("version"), request.jsonBody().keys)
        assertTrue(request.body.contentType.toString().startsWith("application/json"))
    }

    @Test
    fun leaveIsAPostOfTheOwnMembershipVersion() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, memberBody(deleted = true)) }
        env.signedIn()
        env.memberApi.leave("w1", 4)
        val request = env.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/leave"))
        assertEquals(setOf("version"), request.jsonBody().keys)
        assertEquals("4", request.jsonBody()["version"].toString())
    }

    @Test
    fun aMemberWithNoNameDecodesAndUnknownMembersAreIgnored() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, dataBody(memberJson("m2", "u2", null, "editor"))) }
        env.signedIn()
        val member = assertIs<ApiResult.Success<MemberDto>>(env.memberApi.changeRole("w1", "u2", "editor", 1)).value
        assertNull(member.displayName)
    }

    @Test
    fun everyHandledAnswerIsReadByCode() = runTest {
        val cases = listOf(
            Triple(409, problemBody(409, "VERSION_CONFLICT", ""","current":${memberJson("m2", "u2", "Karim", "editor")}"""), ApiErrorCode.VERSION_CONFLICT),
            Triple(403, problemBody(403, "FORBIDDEN"), ApiErrorCode.FORBIDDEN),
            Triple(403, problemBody(403, "FEATURE_NOT_ENTITLED", ""","feature":"sharing.enabled""""), ApiErrorCode.FEATURE_NOT_ENTITLED),
            Triple(403, problemBody(403, "EMAIL_NOT_VERIFIED"), ApiErrorCode.EMAIL_NOT_VERIFIED),
            Triple(404, problemBody(404, "NOT_FOUND"), ApiErrorCode.NOT_FOUND),
            Triple(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/role","code":"IMMUTABLE_FIELD"}]"""), ApiErrorCode.VALIDATION_FAILED),
        )
        for ((status, body, code) in cases) {
            val env = SharingTestEnv(backgroundScope) { jsonResponse(status, body) }
            env.signedIn()
            val failure = assertIs<ApiResult.Failure>(env.memberApi.changeRole("w1", "u2", "viewer", 1))
            assertEquals(code, failure.code)
            if (code == ApiErrorCode.VERSION_CONFLICT) assertNotNull(failure.problem.current)
            if (code == ApiErrorCode.FEATURE_NOT_ENTITLED) assertEquals("sharing.enabled", failure.problem.feature)
            if (status == 422) {
                assertEquals("/role", failure.problem.errors!!.single().pointer)
                assertEquals("IMMUTABLE_FIELD", failure.problem.errors!!.single().code)
            }
            assertIs<ApiResult.Failure>(env.memberApi.remove("w1", "u2", 1))
            assertIs<ApiResult.Failure>(env.memberApi.leave("w1", 1))
        }
    }

    @Test
    fun offlineIsNotAFailure() = runTest {
        val env = SharingTestEnv(backgroundScope) { throw kotlinx.io.IOException("offline") }
        env.signedIn()
        assertEquals(ApiResult.Offline, env.memberApi.leave("w1", 1))
    }
}
