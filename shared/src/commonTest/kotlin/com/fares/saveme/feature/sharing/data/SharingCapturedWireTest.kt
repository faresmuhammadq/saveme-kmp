package com.fares.saveme.feature.sharing.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.data.CapturedSharingResponses as C
import com.fares.saveme.feature.sharing.data.dto.ActivityEntryDto
import com.fares.saveme.feature.sharing.data.dto.InvitationDto
import com.fares.saveme.feature.sharing.data.dto.InvitationPreviewDto
import com.fares.saveme.feature.sharing.data.dto.InvitationWriteRequest
import com.fares.saveme.feature.sharing.data.dto.MemberDto
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The live backend's sharing answers (spec 013 contract QA, [CapturedSharingResponses]) through the real wire clients
 * [InvitationApi], [MemberApi] and [ActivityApi]: every request the client builds equals the body that was sent live, and every
 * real answer decodes to the handling the spec asks for. The invitation secrets in the fixtures are fake values of the real shape.
 */
class SharingCapturedWireTest {

    private fun parse(json: String): JsonObject = AppJson.parseToJsonElement(json).jsonObject

    private fun write(request: String): InvitationWriteRequest {
        val body = parse(request)
        val email = body.getValue("invitee_email").let { if (it is JsonNull) null else it.jsonPrimitive.content }
        return InvitationWriteRequest(body.getValue("role").jsonPrimitive.content, email, body.getValue("version").jsonPrimitive.long)
    }

    private suspend fun env(scope: CoroutineScope, status: Int, body: String, vararg headers: Pair<String, String>): SharingTestEnv =
        SharingTestEnv(scope, userId = C.USER_OWNER) { jsonResponse(status, body, *headers) }.also { it.signedIn() }

    private fun failure(result: ApiResult<*>): ApiResult.Failure = assertIs<ApiResult.Failure>(result)

    private fun errors(f: ApiResult.Failure) = f.problem.errors.orEmpty().map { it.pointer to it.code }

    private fun invitationOf(failure: ApiResult.Failure): InvitationDto = AppJson.decodeFromJsonElement(InvitationDto.serializer(), failure.problem.current!!)

    // --- A1: create ---

    @Test
    fun anEmailCreateSendsTheLiveBodyAndTheLiveCopyDecodesWithNoLink() = runTest {
        val env = env(backgroundScope, 201, C.PUT_EMAIL_CREATED_201)
        val put = assertIs<ApiResult.Success<InvitationPut>>(env.invitationApi.put(C.WALLET, C.INVITATION_EMAIL, write(C.PUT_EMAIL_CREATED_201_REQUEST))).value
        val request = env.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/${C.WALLET}/invitations/${C.INVITATION_EMAIL}"))
        assertEquals(parse(C.PUT_EMAIL_CREATED_201_REQUEST), request.jsonBody())
        assertEquals(C.INVITATION_EMAIL, put.invitation.id)
        assertEquals("pending", put.invitation.status)
        assertEquals("email", put.invitation.channel)
        assertEquals("editor", put.invitation.role)
        assertEquals(1L, put.invitation.version)
        assertNull(put.invitation.deletedAt)
        assertNotNull(put.invitation.expiresAt)
        assertNull(put.shareUrl, "an email invitation answers meta {} and no link")
    }

    @Test
    fun aLinkCreateSendsAnExplicitNullAddressAndTheLinkIsInTheCreateAnswerOnly() = runTest {
        val env = env(backgroundScope, 201, C.PUT_LINK_CREATED_201)
        val put = assertIs<ApiResult.Success<InvitationPut>>(env.invitationApi.put(C.WALLET, C.INVITATION_LINK, write(C.PUT_LINK_CREATED_201_REQUEST))).value
        val sent = env.requests.single().jsonBody()
        assertEquals(parse(C.PUT_LINK_CREATED_201_REQUEST), sent)
        assertEquals("null", sent["invitee_email"].toString(), "an explicit null on the wire")
        assertEquals("link", put.invitation.channel)
        assertNull(put.invitation.inviteeEmail)
        assertTrue(put.shareUrl!!.endsWith("/invite?token=${C.TOKEN_LINK}"))
        assertEquals(43, C.TOKEN_LINK.length)
        assertFalse(put.toString().contains(C.TOKEN_LINK), "the link never prints")

        val replay = env(backgroundScope, 200, C.PUT_LINK_REPLAY_200)
        val again = assertIs<ApiResult.Success<InvitationPut>>(replay.invitationApi.put(C.WALLET, C.INVITATION_LINK, write(C.PUT_LINK_CREATED_201_REQUEST))).value
        assertNull(again.shareUrl, "a replay returns no link")
        assertEquals(put.invitation.expiresAt, again.invitation.expiresAt)
    }

    @Test
    fun theReplaysAndTheServerAcceptingOwnAndMemberAddressesAreSuccesses() = runTest {
        val replay = env(backgroundScope, 200, C.PUT_EMAIL_REPLAY_200)
        assertIs<ApiResult.Success<InvitationPut>>(replay.invitationApi.put(C.WALLET, C.INVITATION_EMAIL, write(C.PUT_EMAIL_CREATED_201_REQUEST)))
        val same = env(backgroundScope, 200, C.PUT_SAME_BODY_VERSION_ONE_200)
        assertIs<ApiResult.Success<InvitationPut>>(same.invitationApi.put(C.WALLET, C.INVITATION_PENDING, write(C.PUT_SAME_BODY_VERSION_ONE_200_REQUEST)))
        assertEquals(parse(C.PUT_SAME_BODY_VERSION_ONE_200_REQUEST), same.requests.single().jsonBody())
        // The server does not refuse the owner's own address or a member's address: that rule is the client's (FR-003).
        val own = env(backgroundScope, 201, C.PUT_OWN_ADDRESS_CREATED_201)
        assertIs<ApiResult.Success<InvitationPut>>(own.invitationApi.put(C.WALLET, C.INVITATION_OWN_ADDRESS, write(C.PUT_OWN_ADDRESS_CREATED_201_REQUEST)))
        val member = env(backgroundScope, 201, C.PUT_MEMBER_ADDRESS_CREATED_201)
        assertIs<ApiResult.Success<InvitationPut>>(member.invitationApi.put(C.WALLET, "x", write(C.PUT_MEMBER_ADDRESS_CREATED_201_REQUEST)))
    }

    private data class Refusal(val status: Int, val body: String, val request: String, val code: String, val feature: String? = null, val errors: List<Pair<String, String>> = emptyList())

    @Test
    fun everyRealCreateRefusalKeepsItsCodeItsFeatureAndItsPointer() = runTest {
        val cases = listOf(
            Refusal(403, C.PUT_FREE_OWNER_403, C.PUT_FREE_OWNER_403_REQUEST, "FEATURE_NOT_ENTITLED", "sharing.enabled"),
            Refusal(403, C.PUT_OVER_CAP_403, C.PUT_OVER_CAP_403_REQUEST, "FEATURE_NOT_ENTITLED", "sharing.max_members_per_wallet"),
            Refusal(403, C.PUT_OVER_CAP_FIRST_403, C.PUT_OVER_CAP_FIRST_403_REQUEST, "FEATURE_NOT_ENTITLED", "sharing.max_members_per_wallet"),
            Refusal(403, C.PUT_OVER_CAP_LINK_403, C.PUT_OVER_CAP_LINK_403_REQUEST, "FEATURE_NOT_ENTITLED", "sharing.max_members_per_wallet"),
            Refusal(403, C.PUT_EDITOR_FORBIDDEN_403, C.PUT_EDITOR_FORBIDDEN_403_REQUEST, "FORBIDDEN"),
            Refusal(403, C.PUT_UNVERIFIED_OWNER_403, C.PUT_UNVERIFIED_OWNER_403_REQUEST, "EMAIL_NOT_VERIFIED"),
            Refusal(404, C.PUT_NON_MEMBER_404, C.PUT_NON_MEMBER_404_REQUEST, "NOT_FOUND"),
            Refusal(422, C.PUT_INVALID_ROLE_422, C.PUT_INVALID_ROLE_422_REQUEST, "VALIDATION_FAILED", errors = listOf("/role" to "INVALID_ENUM_VALUE")),
            Refusal(422, C.PUT_INVALID_EMAIL_422, C.PUT_INVALID_EMAIL_422_REQUEST, "VALIDATION_FAILED", errors = listOf("/invitee_email" to "INVALID_TYPE")),
            Refusal(422, C.PUT_EMAIL_TOO_LONG_422, C.PUT_EMAIL_TOO_LONG_422_REQUEST, "VALIDATION_FAILED", errors = listOf("/invitee_email" to "OUT_OF_RANGE")),
            Refusal(422, C.PUT_IMMUTABLE_ROLE_422, C.PUT_IMMUTABLE_ROLE_422_REQUEST, "VALIDATION_FAILED", errors = listOf("/role" to "IMMUTABLE_FIELD")),
            Refusal(422, C.PUT_IMMUTABLE_ADDRESS_422, C.PUT_IMMUTABLE_ADDRESS_422_REQUEST, "VALIDATION_FAILED", errors = listOf("/invitee_email" to "IMMUTABLE_FIELD")),
            Refusal(422, C.PUT_MALFORMED_ID_422, C.PUT_MALFORMED_ID_422_REQUEST, "INVALID_ID"),
        )
        for (case in cases) {
            val env = env(backgroundScope, case.status, case.body)
            val f = failure(env.invitationApi.put(C.WALLET, C.INVITATION_EMAIL, write(case.request)))
            assertEquals(parse(case.request), env.requests.single().jsonBody())
            assertEquals(case.code, f.problem.code, case.body.take(80))
            assertEquals(case.feature, f.problem.feature, "Problem.feature")
            assertEquals(case.errors, errors(f))
            assertTrue(!f.problem.requestId.isNullOrEmpty(), "the request id is kept")
        }
    }

    @Test
    fun aRateLimitedCreateCarriesTheServersWaitFromTheBodyAndTheHeader() = runTest {
        val env = env(backgroundScope, 429, C.PUT_RATE_LIMITED_429, "Retry-After" to "86394")
        val f = failure(env.invitationApi.put(C.WALLET, C.INVITATION_EMAIL, write(C.PUT_RATE_LIMITED_429_REQUEST)))
        assertEquals("RATE_LIMITED", f.problem.code)
        assertEquals(86_394L, f.retryAfterSeconds)
        val bodyOnly = env(backgroundScope, 429, C.PUT_RATE_LIMITED_429)
        assertEquals(86_394L, failure(bodyOnly.invitationApi.put(C.WALLET, C.INVITATION_EMAIL, write(C.PUT_RATE_LIMITED_429_REQUEST))).retryAfterSeconds)
    }

    @Test
    fun aVersionConflictOnACreateCarriesTheLiveCopyOrTheClosedTombstone() = runTest {
        val live = env(backgroundScope, 409, C.PUT_REPLAY_OTHER_ROLE_VERSION_ZERO_409)
        val liveFailure = failure(live.invitationApi.put(C.WALLET, C.INVITATION_EMAIL, write(C.PUT_REPLAY_OTHER_ROLE_VERSION_ZERO_409_REQUEST)))
        assertEquals("VERSION_CONFLICT", liveFailure.problem.code)
        val liveCopy = invitationOf(liveFailure)
        assertEquals("pending", liveCopy.status)
        assertNull(liveCopy.deletedAt)

        val closed = env(backgroundScope, 409, C.PUT_REPLAY_AFTER_CLOSE_409)
        val closedFailure = failure(closed.invitationApi.put(C.WALLET, C.INVITATION_EMAIL, write(C.PUT_REPLAY_AFTER_CLOSE_409_REQUEST)))
        val tombstone = invitationOf(closedFailure)
        assertEquals("revoked", tombstone.status)
        assertNotNull(tombstone.deletedAt)
        assertNull(tombstone.inviteeEmail, "the address is erased on a closed invitation")
        assertEquals(2L, tombstone.version)
    }

    // --- A2: withdraw ---

    @Test
    fun aWithdrawIsADeleteWithTheVersionBodyAndAnswersATombstoneEvenWhenRepeatedOrAlreadyExpired() = runTest {
        for ((body, status) in listOf(C.DELETE_WITHDRAW_200 to "revoked", C.DELETE_WITHDRAW_AGAIN_200 to "revoked", C.DELETE_ALREADY_EXPIRED_200 to "expired")) {
            val env = env(backgroundScope, 200, body)
            val tombstone = assertIs<ApiResult.Success<InvitationDto>>(env.invitationApi.delete(C.WALLET, C.INVITATION_OWN_ADDRESS, 1)).value
            val request = env.requests.single()
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals(parse(C.DELETE_WITHDRAW_200_REQUEST), request.jsonBody())
            assertEquals(status, tombstone.status)
            assertNotNull(tombstone.deletedAt)
            assertEquals(2L, tombstone.version)
        }
    }

    @Test
    fun aStaleWithdrawAndAnEditorsWithdrawAreReadByCode() = runTest {
        val stale = env(backgroundScope, 409, C.DELETE_STALE_VERSION_409)
        val f = failure(stale.invitationApi.delete(C.WALLET, C.INVITATION_PENDING, 5))
        assertEquals(parse(C.DELETE_STALE_VERSION_409_REQUEST), stale.requests.single().jsonBody())
        assertEquals("VERSION_CONFLICT", f.problem.code)
        assertNull(invitationOf(f).deletedAt, "a live copy: the invitation is still pending")
        val editor = env(backgroundScope, 403, C.DELETE_BY_EDITOR_403)
        assertEquals("FORBIDDEN", failure(editor.invitationApi.delete(C.WALLET, C.INVITATION_PENDING, 1)).problem.code)
    }

    @Test
    fun theReadsTheClientNeverUsesStillDecodeTheLiveShapes() {
        for (body in listOf(C.GET_ONE_200, C.GET_AGED_STILL_PENDING_200, C.GET_EXPIRED_TOMBSTONE_200, C.GET_REPLACED_INVITATION_200)) {
            val dto = AppJson.decodeFromJsonElement(InvitationDto.serializer(), parse(body).getValue("data"))
            assertTrue(dto.status in setOf("pending", "expired", "revoked", "accepted", "declined"), dto.status)
        }
        val aged = AppJson.decodeFromJsonElement(InvitationDto.serializer(), parse(C.GET_AGED_STILL_PENDING_200).getValue("data"))
        assertEquals("pending", aged.status, "past its end date but still pending until the sweep: the phone derives Expired")
        val expired = AppJson.decodeFromJsonElement(InvitationDto.serializer(), parse(C.GET_EXPIRED_TOMBSTONE_200).getValue("data"))
        assertEquals("expired", expired.status)
        assertNotNull(expired.deletedAt)
    }

    // --- A3-A5: the token calls ---

    private fun tokenOf(request: String) = parse(request).getValue("token").jsonPrimitive.content

    @Test
    fun aPreviewSendsOnlyTheTokenInTheBodyAndReadsWhatTheLinkOffers() = runTest {
        val env = env(backgroundScope, 200, C.PREVIEW_EMAIL_200)
        val preview = assertIs<ApiResult.Success<InvitationPreviewDto>>(env.invitationApi.preview(tokenOf(C.PREVIEW_EMAIL_200_REQUEST))).value
        val request = env.requests.single()
        assertEquals(parse(C.PREVIEW_EMAIL_200_REQUEST), request.jsonBody())
        assertEquals(setOf("token"), request.jsonBody().keys)
        assertFalse(request.url.toString().contains(C.TOKEN_MAIL), "the secret is never in the URL")
        assertEquals("Home", preview.walletName)
        assertEquals("OWN QA", preview.invitedByDisplayName)
        assertEquals("editor", preview.role)
        assertFalse(preview.alreadyMember)

        val already = env(backgroundScope, 200, C.PREVIEW_ALREADY_MEMBER_200)
        assertTrue(assertIs<ApiResult.Success<InvitationPreviewDto>>(already.invitationApi.preview(C.TOKEN_LINK)).value.alreadyMember)
        val unverified = env(backgroundScope, 200, C.PREVIEW_UNVERIFIED_200)
        assertIs<ApiResult.Success<InvitationPreviewDto>>(unverified.invitationApi.preview(C.TOKEN_LINK))
        val ownerFree = env(backgroundScope, 200, C.PREVIEW_OWNER_PLAN_FREE_200)
        assertIs<ApiResult.Success<InvitationPreviewDto>>(ownerFree.invitationApi.preview(C.TOKEN_LINK_THREE))
    }

    @Test
    fun everyNotFoundCauseIsTheSameAnswerSoNoCauseCanBeTold() = runTest {
        val causes = mapOf(
            "unknown token" to C.PREVIEW_UNKNOWN_TOKEN_404,
            "another address" to C.PREVIEW_OTHER_ADDRESS_404,
            "another address (accept)" to C.ACCEPT_OTHER_ADDRESS_404,
            "used" to C.PREVIEW_AFTER_ACCEPT_404,
            "used link (accept)" to C.ACCEPT_USED_LINK_404,
            "declined" to C.ACCEPT_AFTER_DECLINE_404,
            "expired" to C.PREVIEW_EXPIRED_LINK_404,
            "deleted wallet" to C.ACCEPT_DELETED_WALLET_404,
            "decline unknown" to C.DECLINE_UNKNOWN_404,
        )
        val answers = causes.mapValues { (_, body) -> parse(body).filterKeys { it != "request_id" } }
        assertEquals(1, answers.values.toSet().size, "every cause answers byte-for-byte the same apart from the request id: $answers")
        for ((cause, body) in causes) {
            val env = env(backgroundScope, 404, body)
            assertEquals("NOT_FOUND", failure(env.invitationApi.preview(C.TOKEN_UNKNOWN)).problem.code, cause)
        }
    }

    @Test
    fun aMalformedOrMissingTokenIs422WithItsPointerAndTooManyAttemptsIs429WithTheWait() = runTest {
        val malformed = env(backgroundScope, 422, C.PREVIEW_MALFORMED_TOKEN_422)
        assertEquals(listOf("/token" to "INVALID_TYPE"), errors(failure(malformed.invitationApi.preview("short"))))
        assertEquals(parse(C.PREVIEW_MALFORMED_TOKEN_422_REQUEST), malformed.requests.single().jsonBody())
        val empty = env(backgroundScope, 422, C.PREVIEW_EMPTY_BODY_422)
        assertEquals(listOf("/token" to "REQUIRED"), errors(failure(empty.invitationApi.preview(""))))
        val decline = env(backgroundScope, 422, C.DECLINE_MALFORMED_422)
        assertEquals(listOf("/token" to "INVALID_TYPE"), errors(failure(decline.invitationApi.decline("x"))))

        for ((body, call) in listOf<Pair<String, suspend (SharingTestEnv) -> ApiResult<*>>>(
            C.TOKEN_RATE_LIMITED_429 to { it.invitationApi.preview(C.TOKEN_UNKNOWN) },
            C.ACCEPT_RATE_LIMITED_429 to { it.invitationApi.accept(C.TOKEN_UNKNOWN) },
            C.DECLINE_RATE_LIMITED_429 to { it.invitationApi.decline(C.TOKEN_UNKNOWN) },
        )) {
            val env = env(backgroundScope, 429, body, "Retry-After" to "3597")
            val f = failure(call(env))
            assertEquals("RATE_LIMITED", f.problem.code)
            assertEquals(3_597L, f.retryAfterSeconds)
        }
    }

    @Test
    fun anAcceptAnswersTheMemberAndARepeatTheSameMemberAndAnUnverifiedPersonMayAccept() = runTest {
        val env = env(backgroundScope, 200, C.ACCEPT_EMAIL_200)
        val member = assertIs<ApiResult.Success<MemberDto>>(env.invitationApi.accept(tokenOf(C.ACCEPT_EMAIL_200_REQUEST))).value
        assertEquals(parse(C.ACCEPT_EMAIL_200_REQUEST), env.requests.single().jsonBody())
        assertEquals(C.USER_EDITOR, member.memberUserId)
        assertEquals("editor", member.role)
        assertEquals(C.WALLET, member.walletId)
        assertEquals(C.EMAIL_MEMBER_ROW, member.id)

        val repeat = env(backgroundScope, 200, C.ACCEPT_REPEAT_200)
        assertEquals(member, assertIs<ApiResult.Success<MemberDto>>(repeat.invitationApi.accept(tokenOf(C.ACCEPT_EMAIL_200_REQUEST))).value)

        val unverified = env(backgroundScope, 200, C.ACCEPT_UNVERIFIED_200)
        assertEquals(C.USER_UNVERIFIED, assertIs<ApiResult.Success<MemberDto>>(unverified.invitationApi.accept(C.TOKEN_LINK)).value.memberUserId)

        val rejoin = env(backgroundScope, 200, C.ACCEPT_REJOIN_200)
        val rejoined = assertIs<ApiResult.Success<MemberDto>>(rejoin.invitationApi.accept(C.TOKEN_LINK_FOUR)).value
        assertEquals(C.USER_UNVERIFIED, rejoined.memberUserId)
        assertTrue(rejoined.id != assertIs<ApiResult.Success<MemberDto>>(unverified.invitationApi.accept(C.TOKEN_LINK)).value.id, "a removed member who rejoins gets a NEW member row id")

        val planFull = env(backgroundScope, 403, C.ACCEPT_OWNER_PLAN_FREE_403)
        val refused = failure(planFull.invitationApi.accept(C.TOKEN_LINK_THREE))
        assertEquals("FEATURE_NOT_ENTITLED", refused.problem.code)
        assertEquals("sharing.enabled", refused.problem.feature)
    }

    @Test
    fun aDeclineAnswers204EvenWhenRepeated() = runTest {
        for (case in listOf(C.DECLINE_204_REQUEST, C.DECLINE_REPEAT_204_REQUEST)) {
            val env = SharingTestEnv(backgroundScope, userId = C.USER_OWNER) { respond("", HttpStatusCode.NoContent) }.also { it.signedIn() }
            assertEquals(ApiResult.Success(Unit), env.invitationApi.decline(tokenOf(case)))
            assertEquals(parse(case), env.requests.single().jsonBody())
        }
    }

    // --- A6-A8: members ---

    @Test
    fun aRoleChangeSendsRoleAndVersionOnlyAndEveryLiveAnswerDecodes() = runTest {
        val lowered = env(backgroundScope, 200, C.PATCH_ROLE_VIEWER_200)
        val member = assertIs<ApiResult.Success<MemberDto>>(lowered.memberApi.changeRole(C.WALLET, C.USER_EDITOR, "viewer", 1)).value
        val request = lowered.requests.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/${C.WALLET}/members/${C.USER_EDITOR}"))
        assertEquals(parse(C.PATCH_ROLE_VIEWER_200_REQUEST), request.jsonBody())
        assertEquals("viewer", member.role)
        assertEquals(2L, member.version)

        val raised = env(backgroundScope, 200, C.PATCH_ROLE_EDITOR_200)
        assertEquals(3L, assertIs<ApiResult.Success<MemberDto>>(raised.memberApi.changeRole(C.WALLET, C.USER_EDITOR, "editor", 2)).value.version)
        assertEquals(parse(C.PATCH_ROLE_EDITOR_200_REQUEST), raised.requests.single().jsonBody())

        val lowerOnFree = env(backgroundScope, 200, C.PATCH_FREE_OWNER_LOWER_200)
        assertEquals("viewer", assertIs<ApiResult.Success<MemberDto>>(lowerOnFree.memberApi.changeRole(C.WALLET, C.USER_EDITOR, "viewer", 4)).value.role, "lowering is never plan-checked")
    }

    @Test
    fun everyRealMemberRefusalIsReadByCodeFeatureAndPointer() = runTest {
        val stale = env(backgroundScope, 409, C.PATCH_ROLE_STALE_409)
        val conflict = failure(stale.memberApi.changeRole(C.WALLET, C.USER_EDITOR, "editor", 1))
        assertEquals(parse(C.PATCH_ROLE_STALE_409_REQUEST), stale.requests.single().jsonBody())
        assertEquals(2L, AppJson.decodeFromJsonElement(MemberDto.serializer(), conflict.problem.current!!).version)

        val cases = listOf(
            Triple(403 to C.PATCH_BY_EDITOR_403, "FORBIDDEN", null as String?),
            Triple(403 to C.PATCH_FREE_OWNER_RAISE_403, "FEATURE_NOT_ENTITLED", "sharing.enabled"),
            Triple(404 to C.PATCH_UNKNOWN_MEMBER_404, "NOT_FOUND", null),
            Triple(404 to C.PATCH_REMOVED_MEMBER_404, "NOT_FOUND", null),
            Triple(422 to C.PATCH_INVALID_ENUM_422, "VALIDATION_FAILED", null),
            Triple(422 to C.PATCH_OWNER_ROW_422, "VALIDATION_FAILED", null),
            Triple(422 to C.PATCH_TO_OWNER_422, "VALIDATION_FAILED", null),
        )
        for ((answer, code, feature) in cases) {
            val f = failure(env(backgroundScope, answer.first, answer.second).memberApi.changeRole(C.WALLET, C.USER_EDITOR, "viewer", 3))
            assertEquals(code, f.problem.code)
            assertEquals(feature, f.problem.feature)
        }
        assertEquals(listOf("/role" to "IMMUTABLE_FIELD"), errors(failure(env(backgroundScope, 422, C.PATCH_OWNER_ROW_422).memberApi.changeRole(C.WALLET, C.USER_OWNER, "viewer", 1))))
        assertEquals(listOf("/role" to "IMMUTABLE_FIELD"), errors(failure(env(backgroundScope, 422, C.PATCH_TO_OWNER_422).memberApi.changeRole(C.WALLET, C.USER_EDITOR, "owner", 3))))
        assertEquals(listOf("/role" to "INVALID_ENUM_VALUE"), errors(failure(env(backgroundScope, 422, C.PATCH_INVALID_ENUM_422).memberApi.changeRole(C.WALLET, C.USER_EDITOR, "admin", 3))))
    }

    @Test
    fun aRemovalSendsTheVersionInADeleteBodyAndAnswersATombstoneOnceAndAgain() = runTest {
        for (body in listOf(C.DELETE_MEMBER_200, C.DELETE_MEMBER_AGAIN_200)) {
            val env = env(backgroundScope, 200, body)
            val removed = assertIs<ApiResult.Success<MemberDto>>(env.memberApi.remove(C.WALLET, C.USER_UNVERIFIED, 1)).value
            assertEquals(HttpMethod.Delete, env.requests.single().method)
            assertEquals(parse(C.DELETE_MEMBER_200_REQUEST), env.requests.single().jsonBody())
            assertNotNull(removed.deletedAt)
            assertEquals(2L, removed.version)
        }
        assertEquals("VERSION_CONFLICT", failure(env(backgroundScope, 409, C.DELETE_MEMBER_STALE_409).memberApi.remove(C.WALLET, C.USER_UNVERIFIED, 9)).problem.code)
        assertEquals("FORBIDDEN", failure(env(backgroundScope, 403, C.DELETE_MEMBER_BY_EDITOR_403).memberApi.remove(C.WALLET, C.USER_UNVERIFIED, 1)).problem.code)
        assertEquals("FORBIDDEN", failure(env(backgroundScope, 403, C.DELETE_OWNER_ROW_403).memberApi.remove(C.WALLET, C.USER_OWNER, 1)).problem.code)
    }

    @Test
    fun leavingSendsTheOwnVersionAndTheLiveAnswersAre200Tombstone409404And403() = runTest {
        val left = env(backgroundScope, 200, C.LEAVE_200)
        val tombstone = assertIs<ApiResult.Success<MemberDto>>(left.memberApi.leave(C.WALLET, 4)).value
        assertEquals(HttpMethod.Post, left.requests.single().method)
        assertTrue(left.requests.single().url.encodedPath.endsWith("/wallets/${C.WALLET}/leave"))
        assertEquals(parse(C.LEAVE_200_REQUEST), left.requests.single().jsonBody())
        assertNotNull(tombstone.deletedAt)
        assertEquals(5L, tombstone.version)
        assertNotNull(assertIs<ApiResult.Success<MemberDto>>(env(backgroundScope, 200, C.LEAVE_VIEWER_200).memberApi.leave(C.WALLET, 1)).value.deletedAt)
        assertEquals(4L, AppJson.decodeFromJsonElement(MemberDto.serializer(), failure(env(backgroundScope, 409, C.LEAVE_STALE_409).memberApi.leave(C.WALLET, 1)).problem.current!!).version)
        assertEquals("NOT_FOUND", failure(env(backgroundScope, 404, C.LEAVE_REPEAT_404).memberApi.leave(C.WALLET, 4)).problem.code)
        assertEquals("FORBIDDEN", failure(env(backgroundScope, 403, C.LEAVE_OWNER_403).memberApi.leave(C.WALLET, 1)).problem.code)
    }

    // --- A9: activity ---

    @Test
    fun theActivityAsksForFiftyAndDecodesTheWholeLiveHistoryIncludingAnEntryWithNoName() = runTest {
        val env = env(backgroundScope, 200, C.ACTIVITY_PAGE_200)
        val page = assertIs<ApiResult.Success<com.fares.saveme.core.network.Page<ActivityEntryDto>>>(env.activityApi.page(C.WALLET, null)).value
        val request = env.requests.single()
        assertEquals("50", request.url.parameters["limit"])
        assertNull(request.url.parameters["cursor"])
        assertTrue(page.items.size >= 15)
        assertFalse(page.hasMore)
        assertEquals(setOf("created", "removed", "role_changed", "invited", "invitation_revoked", "joined"), page.items.map { it.action }.toSet())
        assertTrue(page.items.all { it.subjectType in setOf("transaction", "member", "invitation") })
        // Newest first.
        assertEquals(page.items.map { it.occurredAt }.sortedDescending(), page.items.map { it.occurredAt })
        // A person who has since left: the server sends their id and no name.
        assertTrue(page.items.any { it.actorUserId != null && it.actorDisplayName == null })
        // A closed invitation entry carries a role; a transaction entry carries none.
        assertNull(page.items.first { it.action == "created" }.role)
        assertEquals("editor", page.items.first { it.action == "joined" && it.actorDisplayName == "ED QA" }.role)
        // The activity carries no amount: the keys are the contract's, none is monetary.
        val keys = parse(C.ACTIVITY_PAGE_200).getValue("data").toString()
        assertFalse(keys.contains("amount"), "no amount anywhere in the activity")
    }

    @Test
    fun aPageFollowsTheCursorAndABadCursorIs400InvalidCursor() = runTest {
        val first = env(backgroundScope, 200, C.ACTIVITY_LIMIT_TWO_200)
        val firstPage = assertIs<ApiResult.Success<com.fares.saveme.core.network.Page<ActivityEntryDto>>>(first.activityApi.page(C.WALLET, null)).value
        assertEquals(2, firstPage.items.size)
        assertTrue(firstPage.hasMore)
        val cursor = assertNotNull(firstPage.nextCursor)
        val second = env(backgroundScope, 200, C.ACTIVITY_PAGE_TWO_200)
        val secondPage = assertIs<ApiResult.Success<com.fares.saveme.core.network.Page<ActivityEntryDto>>>(second.activityApi.page(C.WALLET, cursor)).value
        assertEquals(cursor, second.requests.single().url.parameters["cursor"])
        assertTrue(secondPage.items.none { entry -> firstPage.items.any { it.id == entry.id } }, "the second page continues, never repeats")

        val bad = env(backgroundScope, 400, C.ACTIVITY_INVALID_CURSOR_400)
        assertEquals("INVALID_CURSOR", failure(bad.activityApi.page(C.WALLET, "garbage")).problem.code)
        assertEquals("NOT_FOUND", failure(env(backgroundScope, 404, C.ACTIVITY_NON_MEMBER_404).activityApi.page(C.WALLET, null)).problem.code)
        assertEquals("NOT_FOUND", failure(env(backgroundScope, 404, C.ACTIVITY_LEFT_404).activityApi.page(C.WALLET, null)).problem.code)
    }

    @Test
    fun aPlanDemotionIsAnEntryWithNoActorAboutTheMemberWhoBecameAViewer() = runTest {
        val env = env(backgroundScope, 200, C.ACTIVITY_AFTER_DEMOTION_200)
        val page = assertIs<ApiResult.Success<com.fares.saveme.core.network.Page<ActivityEntryDto>>>(env.activityApi.page(C.WALLET, null)).value
        val limited = page.items.first()
        assertEquals("role_limited_by_plan", limited.action)
        assertNull(limited.actorUserId)
        assertNull(limited.actorDisplayName)
        assertEquals(C.USER_EDITOR, limited.subjectId)
        assertEquals("viewer", limited.role)
    }

    // --- the fixtures themselves ---

    @Test
    fun noFixtureHoldsARealSecretOnlyFakesOfTheRealShape() {
        val tokens = listOf(C.TOKEN_MAIL, C.TOKEN_LINK, C.TOKEN_LINK_TWO, C.TOKEN_LINK_THREE, C.TOKEN_LINK_FOUR, C.TOKEN_UNKNOWN)
        assertEquals(6, tokens.toSet().size)
        for (token in tokens) {
            assertEquals(43, token.length)
            assertTrue(token.all { it.isLetterOrDigit() || it == '_' || it == '-' })
            assertTrue(token.startsWith("Fake"), "a placeholder, never a live secret")
        }
        assertFalse(C.PUT_LINK_CREATED_201.contains(Regex("token=(?!Fake)")), "the share link carries only the placeholder")
    }
}
