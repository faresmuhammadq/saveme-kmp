package com.fares.saveme.feature.sharing.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.network.ProblemError
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.RecordKey
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.sharing.FAKE_LINK
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.invitationBody
import com.fares.saveme.feature.sharing.invitationJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** An invitation's per-record sends, the one-time link, clashes and prerequisites (research R-03, R-04). */
class InvitationSyncHandlerTest {

    private fun change(base: Long, payload: InvitationPayload = InvitationPayload("editor", "karim@example.com"), delete: Boolean = false, id: String = "i1") = PendingChange(
        recordType = "invitation", recordId = id, accountId = "u1", scope = SyncScope(ScopeKind.Wallet, "w1"),
        baseVersion = base, payloadJson = AppJson.encodeToString(InvitationPayload.serializer(), payload), isDelete = delete,
        restore = false, revision = 0, createdOrder = 1, state = ChangeState.Waiting, attempts = 0, nextAttemptAt = null,
        clashJson = null, refusal = null, updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
    )

    @Test
    fun theRecordTypeIsInvitationAndItIsNeverABatchItem() = runTest {
        val env = SharingTestEnv(backgroundScope)
        assertEquals("invitation", env.syncHandler.recordType)
        assertNull(env.syncHandler.batchItem(change(0)))
        assertNull(env.syncHandler.batchItems(change(0)))
        assertNull(env.syncHandler.batchItems(change(3, delete = true)))
        assertFalse(env.syncHandler.canRestore)
    }

    @Test
    fun aCreateIsAPutWithVersionZeroAndThePayloadsMembers() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(201, invitationBody()) }
        env.signedIn()
        val result = env.syncHandler.send(change(0))
        assertIs<ApiResult.Success<*>>(result)
        val request = env.requests.single()
        assertEquals("PUT", request.method.value)
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/invitations/i1"))
        assertEquals("0", request.jsonBody()["version"].toString())
        assertEquals("\"editor\"", request.jsonBody()["role"].toString())
        assertEquals("\"karim@example.com\"", request.jsonBody()["invitee_email"].toString())
    }

    @Test
    fun aLinkCreateSendsANullAddress() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(201, invitationBody(invitationJson(email = null))) }
        env.signedIn()
        env.syncHandler.send(change(0, InvitationPayload("viewer", null)))
        assertEquals("null", env.requests.single().jsonBody()["invitee_email"].toString())
    }

    @Test
    fun aWithdrawalIsADeleteWithTheStoredVersion() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, invitationBody(invitationJson(status = "revoked", version = 2, deletedAt = "2026-10-02T11:00:00Z"))) }
        env.signedIn()
        env.syncHandler.send(change(1, delete = true))
        val request = env.requests.single()
        assertEquals("DELETE", request.method.value)
        assertEquals(setOf("version"), request.jsonBody().keys)
        assertEquals("1", request.jsonBody()["version"].toString())
    }

    @Test
    fun aCreatedLinkIsOfferedBeforeTheRecordIsReturnedAndAReplayOffersNone() = runTest {
        var answer = invitationBody(invitationJson(email = null), meta = """{"share_url":"$FAKE_LINK"}""")
        val env = SharingTestEnv(backgroundScope) { jsonResponse(201, answer) }
        env.signedIn()
        val link = change(0, InvitationPayload("viewer", null))
        env.syncHandler.send(link)
        assertEquals(FAKE_LINK, env.shareLinks.get("i1"))
        env.shareLinks.forget("i1")
        answer = invitationBody(invitationJson(email = null))
        env.syncHandler.send(link)
        assertNull(env.shareLinks.get("i1"), "a 200 replay carries no link")
    }

    @Test
    fun anEmailCreateOffersNoLink() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(201, invitationBody()) }
        env.signedIn()
        env.syncHandler.send(change(0))
        assertNull(env.shareLinks.get("i1"))
    }

    @Test
    fun theLinkIsOfferedEvenWhenTheAnswerIsAppliedLaterAndNeverEntersTheReturnedRecord() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(201, invitationBody(invitationJson(email = null), meta = """{"share_url":"$FAKE_LINK"}""")) }
        env.signedIn()
        val record = assertIs<ApiResult.Success<*>>(env.syncHandler.send(change(0, InvitationPayload("viewer", null)))).value.toString()
        assertFalse(record.contains("token"), "the record handed to the engine carries no link")
    }

    @Test
    fun aClashIsAcceptedAndTheTombstoneRemovesTheRow() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.signedIn()
        env.seed(invitationJson(version = 1))
        val tombstone = AppJson.parseToJsonElement(invitationJson(status = "revoked", version = 2, deletedAt = "2026-10-02T11:00:00Z"))
        assertTrue(env.syncHandler.acceptsClash(change(1), tombstone))
        env.syncHandler.applyServerCopy(tombstone)
        assertNull(env.invitationDao.rows.value["i1"])
    }

    @Test
    fun aWalletMadeOnThisPhoneAndNotYetAcceptedHoldsTheCreateBack() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.walletVersion = 0
        assertEquals(listOf(RecordKey("wallet", "w1")), env.syncHandler.prerequisites(change(0)))
        env.walletVersion = 2
        assertEquals(emptyList(), env.syncHandler.prerequisites(change(0)))
        env.walletVersion = null
        assertEquals(emptyList(), env.syncHandler.prerequisites(change(0)))
        env.walletVersion = 0
        assertEquals(emptyList(), env.syncHandler.prerequisites(change(1, delete = true)), "a withdrawal needs nothing first")
    }

    @Test
    fun refusalFieldsMapTheRoleAndTheAddress() = runTest {
        val env = SharingTestEnv(backgroundScope)
        val problem = ProblemDetails(
            status = 422, code = "VALIDATION_FAILED",
            errors = listOf(ProblemError("/role", "IMMUTABLE_FIELD"), ProblemError("/invitee_email", "INVALID_TYPE"), ProblemError("/other", "X")),
        )
        assertEquals(mapOf("role" to "IMMUTABLE_FIELD", "email" to "INVALID_TYPE"), env.syncHandler.refusalFields(problem))
    }

    @Test
    fun discardingRemovesAPlaceholderOnlyNeverASyncedRow() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.signedIn()
        env.repository.insertPlaceholder("p1", "w1", "u1", "viewer", null)
        env.seed(invitationJson("s1", version = 2))
        env.syncHandler.onDiscarded("p1")
        env.syncHandler.onDiscarded("s1")
        assertNull(env.invitationDao.rows.value["p1"])
        assertTrue("s1" in env.invitationDao.rows.value)
    }

    @Test
    fun offlineAndFailuresPassThroughForTheEngine() = runTest {
        val env = SharingTestEnv(backgroundScope) { throw kotlinx.io.IOException("offline") }
        env.signedIn()
        assertEquals(ApiResult.Offline, env.syncHandler.send(change(0)))
        assertEquals(ApiResult.Offline, env.syncHandler.send(change(1, delete = true)))
    }

    @Test
    fun theSentJsonIsAnObjectTheEngineCanReadTheVersionFrom() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(201, invitationBody(invitationJson(version = 1))) }
        env.signedIn()
        val record = assertIs<ApiResult.Success<*>>(env.syncHandler.send(change(0))).value
        assertIs<JsonObject>(record)
        assertEquals("1", record["version"].toString())
    }
}
