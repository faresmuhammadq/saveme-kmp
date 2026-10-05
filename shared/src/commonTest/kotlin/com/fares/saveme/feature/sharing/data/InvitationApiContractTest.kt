package com.fares.saveme.feature.sharing.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.sharing.FAKE_LINK
import com.fares.saveme.feature.sharing.FAKE_TOKEN
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.data.dto.InvitationDto
import com.fares.saveme.feature.sharing.data.dto.InvitationPreviewDto
import com.fares.saveme.feature.sharing.data.dto.InvitationWriteRequest
import com.fares.saveme.feature.sharing.data.dto.MemberDto
import com.fares.saveme.feature.sharing.dataBody
import com.fares.saveme.feature.sharing.invitationBody
import com.fares.saveme.feature.sharing.invitationJson
import com.fares.saveme.feature.sharing.previewJson
import com.fares.saveme.feature.wallets.memberJson
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every call and handled answer of contracts/sharing-api-client.md A1-A5, with the examples of sharing.yaml v1.9 (P-14.3). */
class InvitationApiContractTest {

    private fun env(scope: kotlinx.coroutines.CoroutineScope, handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        SharingTestEnv(scope, handler = handler)

    @Test
    fun anEmailInvitationIsAPutWithTheDeclaredMembersOnly() = runTest {
        val env = env(backgroundScope) { jsonResponse(201, invitationBody()) }
        env.signedIn()
        assertIs<ApiResult.Success<*>>(env.invitationApi.put("w1", "i1", InvitationWriteRequest("editor", "karim@example.com", 0)))
        val request = env.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/invitations/i1"))
        val body = request.jsonBody()
        assertEquals(setOf("role", "invitee_email", "version"), body.keys)
        assertEquals("\"editor\"", body["role"].toString())
        assertEquals("\"karim@example.com\"", body["invitee_email"].toString())
        assertEquals("0", body["version"].toString())
    }

    @Test
    fun aLinkInvitationSendsAnExplicitNullAddress() = runTest {
        val env = env(backgroundScope) { jsonResponse(201, invitationBody(invitationJson(email = null))) }
        env.signedIn()
        env.invitationApi.put("w1", "i1", InvitationWriteRequest("viewer", null, 0))
        val body = env.requests.single().jsonBody()
        assertEquals("null", body["invitee_email"].toString(), "the address is serialised as an explicit null")
        assertEquals(setOf("role", "invitee_email", "version"), body.keys)
    }

    @Test
    fun aCreatedLinkInvitationAnswersTheOneTimeLinkInMeta() = runTest {
        val env = env(backgroundScope) { jsonResponse(201, invitationBody(invitationJson(email = null), meta = """{"share_url":"$FAKE_LINK"}""")) }
        env.signedIn()
        val put = assertIs<ApiResult.Success<InvitationPut>>(env.invitationApi.put("w1", "i1", InvitationWriteRequest("viewer", null, 0))).value
        assertEquals(FAKE_LINK, put.shareUrl)
        assertEquals("link", put.invitation.channel)
        assertFalse(put.toString().contains(FAKE_TOKEN), "the link never prints")
    }

    @Test
    fun aReplayAnswers200WithNoLinkAndAnEmailCreateHasNone() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, invitationBody(invitationJson(email = null))) }
        env.signedIn()
        val put = assertIs<ApiResult.Success<InvitationPut>>(env.invitationApi.put("w1", "i1", InvitationWriteRequest("viewer", null, 0))).value
        assertNull(put.shareUrl)
    }

    @Test
    fun withdrawIsADeleteWithAJsonBodyOfTheVersionOnly() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, invitationBody(invitationJson(status = "revoked", version = 2, deletedAt = "2026-10-02T11:00:00Z", email = null, channel = "email"))) }
        env.signedIn()
        val answer = assertIs<ApiResult.Success<InvitationDto>>(env.invitationApi.delete("w1", "i1", 1)).value
        assertEquals("revoked", answer.status)
        assertNotNull(answer.deletedAt)
        val request = env.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/invitations/i1"))
        assertEquals(setOf("version"), request.jsonBody().keys)
        assertEquals("1", request.jsonBody()["version"].toString())
        assertTrue(request.body.contentType.toString().startsWith("application/json"))
    }

    @Test
    fun theTokenTravelsInTheBodyAndNeverInTheUrl() = runTest {
        val env = env(backgroundScope) { request ->
            when {
                request.url.encodedPath.endsWith("/preview") -> jsonResponse(200, dataBody(previewJson()))
                request.url.encodedPath.endsWith("/accept") -> jsonResponse(200, dataBody(memberJson("m1", "u1", "Sara", "editor")))
                else -> respond("", HttpStatusCode.NoContent)
            }
        }
        env.signedIn()
        env.invitationApi.preview(FAKE_TOKEN)
        env.invitationApi.accept(FAKE_TOKEN)
        env.invitationApi.decline(FAKE_TOKEN)
        assertEquals(3, env.requests.size)
        for (request in env.requests) {
            assertEquals(HttpMethod.Post, request.method)
            assertFalse(request.url.toString().contains(FAKE_TOKEN), "the secret is never in a path or query")
            assertEquals(emptySet(), request.url.parameters.names())
            assertEquals(setOf("token"), request.jsonBody().keys)
            assertEquals("\"$FAKE_TOKEN\"", request.jsonBody()["token"].toString())
        }
        assertEquals(listOf("preview", "accept", "decline"), env.requests.map { it.url.encodedPath.substringAfterLast('/') })
    }

    @Test
    fun aPreviewDecodesANullInviterNameAndAlreadyMember() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, dataBody(previewJson(inviter = null, already = true))) }
        env.signedIn()
        val preview = assertIs<ApiResult.Success<InvitationPreviewDto>>(env.invitationApi.preview(FAKE_TOKEN)).value
        assertNull(preview.invitedByDisplayName)
        assertTrue(preview.alreadyMember)
        assertEquals("Home", preview.walletName)
        assertEquals("w1", preview.walletId)
    }

    @Test
    fun acceptAnswersTheMemberAndDeclineAnswers204() = runTest {
        val env = env(backgroundScope) { request ->
            if (request.url.encodedPath.endsWith("/accept")) jsonResponse(200, dataBody(memberJson("m1", "u1", null, "editor"))) else respond("", HttpStatusCode.NoContent)
        }
        env.signedIn()
        val member = assertIs<ApiResult.Success<MemberDto>>(env.invitationApi.accept(FAKE_TOKEN)).value
        assertEquals("editor", member.role)
        assertNull(member.displayName)
        assertIs<ApiResult.Success<Unit>>(env.invitationApi.decline(FAKE_TOKEN))
    }

    @Test
    fun everyHandledCodeIsReadByCodeAndTheFeatureMember() = runTest {
        val answers = mapOf(
            "PUT 409" to (409 to problemBody(409, "VERSION_CONFLICT", ""","current":${invitationJson(status = "revoked", deletedAt = "2026-10-02T11:00:00Z", version = 2)}""")),
            "PUT 403 verified" to (403 to problemBody(403, "EMAIL_NOT_VERIFIED")),
            "PUT 403 plan" to (403 to problemBody(403, "FEATURE_NOT_ENTITLED", ""","feature":"sharing.enabled"""")),
            "PUT 403 quota" to (403 to problemBody(403, "FEATURE_NOT_ENTITLED", ""","feature":"sharing.max_members_per_wallet"""")),
            "PUT 403 forbidden" to (403 to problemBody(403, "FORBIDDEN")),
            "PUT 404" to (404 to problemBody(404, "NOT_FOUND")),
            "PUT 422" to (422 to problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/role","code":"IMMUTABLE_FIELD"}]""")),
            "PUT 429" to (429 to problemBody(429, "RATE_LIMITED", ""","retry_after_seconds":120""")),
        )
        for ((name, answer) in answers) {
            val env = env(backgroundScope) { jsonResponse(answer.first, answer.second) }
            env.signedIn()
            val failure = assertIs<ApiResult.Failure>(env.invitationApi.put("w1", "i1", InvitationWriteRequest("editor", "a@b.co", 0)), name)
            when (name) {
                "PUT 409" -> {
                    assertEquals(ApiErrorCode.VERSION_CONFLICT, failure.code)
                    assertNotNull(failure.problem.current)
                }
                "PUT 403 verified" -> assertEquals(ApiErrorCode.EMAIL_NOT_VERIFIED, failure.code)
                "PUT 403 plan" -> {
                    assertEquals(ApiErrorCode.FEATURE_NOT_ENTITLED, failure.code)
                    assertEquals("sharing.enabled", failure.problem.feature)
                }
                "PUT 403 quota" -> assertEquals("sharing.max_members_per_wallet", failure.problem.feature)
                "PUT 403 forbidden" -> assertEquals(ApiErrorCode.FORBIDDEN, failure.code)
                "PUT 404" -> assertEquals(ApiErrorCode.NOT_FOUND, failure.code)
                "PUT 422" -> {
                    assertEquals(ApiErrorCode.VALIDATION_FAILED, failure.code)
                    assertEquals("/role", failure.problem.errors!!.single().pointer)
                }
                "PUT 429" -> {
                    assertEquals(ApiErrorCode.RATE_LIMITED, failure.code)
                    assertEquals(120L, failure.retryAfterSeconds)
                }
            }
        }
    }

    @Test
    fun theTokenCallsReadTheirAnswersByCode() = runTest {
        for ((status, code) in listOf(404 to "NOT_FOUND", 422 to "VALIDATION_FAILED", 429 to "RATE_LIMITED", 403 to "FEATURE_NOT_ENTITLED")) {
            val env = env(backgroundScope) { jsonResponse(status, problemBody(status, code, if (code == "RATE_LIMITED") ""","retry_after_seconds":90""" else if (code == "FEATURE_NOT_ENTITLED") ""","feature":"sharing.max_members_per_wallet"""" else "")) }
            env.signedIn()
            for (result in listOf(env.invitationApi.preview(FAKE_TOKEN), env.invitationApi.accept(FAKE_TOKEN), env.invitationApi.decline(FAKE_TOKEN))) {
                val failure = assertIs<ApiResult.Failure>(result)
                assertEquals(ApiErrorCode.from(code), failure.code)
                if (code == "RATE_LIMITED") assertEquals(90L, failure.retryAfterSeconds)
            }
        }
    }

    @Test
    fun offlineAndTimeoutAreNotFailures() = runTest {
        val env = env(backgroundScope) { throw kotlinx.io.IOException("offline") }
        env.signedIn()
        assertEquals(ApiResult.Offline, env.invitationApi.preview(FAKE_TOKEN))
        assertEquals(ApiResult.Offline, env.invitationApi.put("w1", "i1", InvitationWriteRequest("editor", null, 0)))
    }

    @Test
    fun theRequestsCarryTheStandardHeaders() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, dataBody(previewJson())) }
        env.signedIn()
        env.invitationApi.preview(FAKE_TOKEN)
        val headers = env.requests.single().headers
        assertNotNull(headers["X-Client-Platform"])
        assertNotNull(headers["X-Client-Version"])
        assertNotNull(headers["X-Correlation-Id"])
        assertNotNull(headers["Authorization"])
        assertNotNull(headers["Accept-Language"])
    }

    @Test
    fun unknownMembersOfAnInvitationAreIgnoredAndTheAddressNeverPrints() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, invitationBody()) }
        env.signedIn()
        val dto = assertIs<ApiResult.Success<InvitationPut>>(env.invitationApi.put("w1", "i1", InvitationWriteRequest("editor", "karim@example.com", 0))).value.invitation
        assertEquals("karim@example.com", dto.inviteeEmail)
        assertFalse(dto.toString().contains("karim"))
    }
}
