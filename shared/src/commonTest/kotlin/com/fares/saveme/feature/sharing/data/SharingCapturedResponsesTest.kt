package com.fares.saveme.feature.sharing.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.HttpSyncApi
import com.fares.saveme.core.sync.FeedPuller
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.ScopeRead
import com.fares.saveme.core.sync.SyncLock
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.data.CapturedSharingResponses as C
import com.fares.saveme.feature.sharing.domain.model.ActivityAction
import com.fares.saveme.feature.sharing.domain.model.ActivityPage
import com.fares.saveme.feature.sharing.domain.model.Actor
import com.fares.saveme.feature.sharing.domain.model.DeclineResult
import com.fares.saveme.feature.sharing.domain.model.InvitationDraft
import com.fares.saveme.feature.sharing.domain.model.InvitationRefusalKind
import com.fares.saveme.feature.sharing.domain.model.InvitedRole
import com.fares.saveme.feature.sharing.domain.model.InviteResult
import com.fares.saveme.feature.sharing.domain.model.JoinResult
import com.fares.saveme.feature.sharing.domain.model.LeaveResult
import com.fares.saveme.feature.sharing.domain.model.MemberActionResult
import com.fares.saveme.feature.sharing.domain.model.PreviewResult
import com.fares.saveme.feature.sharing.domain.model.WithdrawResult
import com.fares.saveme.feature.wallets.domain.MemberRecord
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The live backend's sharing answers (spec 013 contract QA) through the real engine, [InvitationSyncHandler], the feed
 * puller and appliers, and [com.fares.saveme.feature.sharing.domain.DefaultSharingService]: every outcome the spec asks for is the
 * outcome the real answer produces.
 */
class SharingCapturedResponsesTest {

    private val draft = InvitationDraft(InvitedRole.Editor, "karim@example.com")

    private fun parse(json: String): JsonObject = AppJson.parseToJsonElement(json).jsonObject

    private fun owner(scope: kotlinx.coroutines.CoroutineScope, status: Int, body: String, verified: Boolean = true) =
        SharingTestEnv(scope, verified = verified, userId = C.USER_OWNER) { jsonResponse(status, body) }

    private suspend fun SharingTestEnv.ready(role: String = "owner", members: Int = 1): SharingTestEnv {
        signedIn()
        seedOwnerWallet(C.WALLET, members = members, role = role)
        return this
    }

    /** The seeded wallet's own row has a made-up id; the live rows carry the server's, so drop it before storing the live one. */
    private suspend fun SharingTestEnv.dropSeedOwnRow() =
        memberships.store(MemberRecord("m-$userId", C.WALLET, userId, "Sara", "owner", 1, "2026-09-01T00:00:00Z", deleted = true))

    private suspend fun SharingTestEnv.memberRow(id: String, user: String, role: String, version: Long) =
        memberships.store(MemberRecord(id, C.WALLET, user, "QA", role, version, "2026-10-05T09:11:13Z"))

    // --- engine and handler ---

    @Test
    fun anEmailInvitationQueuedOnThePhoneIsSentOnceAndTheLiveCopyReplacesThePlaceholder() = runTest {
        val env = owner(backgroundScope, 201, C.PUT_EMAIL_CREATED_201).ready()
        env.idSupplier = { C.INVITATION_EMAIL }
        val id = assertIs<InviteResult.Queued>(env.service.invite(C.WALLET, draft)).invitationId
        env.engine.drain()
        assertEquals(C.INVITATION_EMAIL, id)
        assertNull(env.engine.get("invitation", id), "nothing waits after the 201")
        val stored = assertNotNull(env.repository.get(id))
        assertEquals(1L, stored.version)
        assertEquals("pending", stored.status)
        assertEquals(1, env.requests.size, "one request per invitation, never batched")
        assertEquals("0", env.requests.single().jsonBody()["version"].toString())
    }

    @Test
    fun aLinkInvitationHandsTheLiveLinkToTheMemoryHolderAndShareTextHasNoWalletNameOrAmount() = runTest {
        val env = owner(backgroundScope, 201, C.PUT_LINK_CREATED_201).ready()
        env.idSupplier = { C.INVITATION_LINK }
        val id = assertIs<InviteResult.Queued>(env.service.invite(C.WALLET, InvitationDraft(InvitedRole.Viewer, null))).invitationId
        env.engine.drain()
        assertTrue(env.service.observeLinkHeld(id).first())
        val text = assertNotNull(env.service.shareTextFor(id))
        assertTrue(text.contains("token=${C.TOKEN_LINK}"))
        assertFalse(text.contains("Home"), "the wallet's name is never in the share text")
        assertNull(env.repository.get(id)?.inviteeEmailOrNull(), "a link invitation stores no address")
        assertFalse(env.repository.get(id).toString().contains(C.TOKEN_LINK))
    }

    private fun com.fares.saveme.feature.sharing.data.local.InvitationEntity?.inviteeEmailOrNull() = this?.inviteeEmail

    @Test
    fun everyLiveCreateRefusalBecomesItsOwnReasonOnTheInvitation() = runTest {
        val cases = listOf(
            Triple(403, C.PUT_FREE_OWNER_403, InvitationRefusalKind.SharingNotIncluded),
            Triple(403, C.PUT_OVER_CAP_403, InvitationRefusalKind.PlanFull),
            Triple(403, C.PUT_UNVERIFIED_OWNER_403, InvitationRefusalKind.NotVerified),
            Triple(403, C.PUT_EDITOR_FORBIDDEN_403, InvitationRefusalKind.NotOwner),
        )
        for ((status, body, kind) in cases) {
            val env = owner(backgroundScope, status, body).ready()
            val id = assertIs<InviteResult.Queued>(env.service.invite(C.WALLET, draft)).invitationId
            env.engine.drain()
            assertEquals(ChangeState.Refused, env.engine.get("invitation", id)?.state, "$kind")
            assertEquals(kind, env.service.observeInvitations(C.WALLET).first().single { it.id == id }.refusal?.kind)
        }
    }

    @Test
    fun aCreateRetriedAfterTheInvitationWasClosedTakesTheTombstoneWithoutAsking() = runTest {
        val env = owner(backgroundScope, 409, C.PUT_REPLAY_AFTER_CLOSE_409).ready()
        env.idSupplier = { "01a10b5a-5f61-7401-81d1-00686c68e955" }
        val id = assertIs<InviteResult.Queued>(env.service.invite(C.WALLET, draft)).invitationId
        env.engine.drain()
        assertNull(env.engine.get("invitation", id), "the change is dropped")
        assertNull(env.repository.get(id), "a closed invitation is not shown: the tombstone removes the row")
    }

    @Test
    fun aRateLimitedCreateStaysWaitingAndIsNeverShownAsNotSent() = runTest {
        val env = SharingTestEnv(backgroundScope, userId = C.USER_OWNER) { jsonResponse(429, C.PUT_RATE_LIMITED_429, "Retry-After" to "86394") }.ready()
        val id = assertIs<InviteResult.Queued>(env.service.invite(C.WALLET, draft)).invitationId
        env.engine.drain()
        val change = assertNotNull(env.engine.get("invitation", id))
        assertTrue(change.state != ChangeState.Refused, "a 429 is waited out, not refused")
        assertTrue(env.service.observeInvitations(C.WALLET).first().single { it.id == id }.refusal == null)
    }

    @Test
    fun aWithdrawalIsQueuedWithTheStoredVersionAndTheLiveTombstoneRemovesTheRow() = runTest {
        val env = owner(backgroundScope, 200, C.DELETE_WITHDRAW_200).ready()
        env.repository.storeServerCopy(AppJson.decodeFromString(com.fares.saveme.feature.sharing.data.dto.InvitationDto.serializer(), parse(C.PUT_OWN_ADDRESS_CREATED_201).getValue("data").toString()))
        assertEquals(WithdrawResult.Withdrawn, env.service.withdraw(C.INVITATION_OWN_ADDRESS))
        env.engine.drain()
        testScheduler.advanceUntilIdle()
        assertEquals(parse(C.DELETE_WITHDRAW_200_REQUEST), env.requests.single().jsonBody())
        assertTrue(env.service.observeInvitations(C.WALLET).first().isEmpty())
        assertNull(env.repository.get(C.INVITATION_OWN_ADDRESS))
        assertNull(env.engine.get("invitation", C.INVITATION_OWN_ADDRESS))
    }

    // --- the invitee ---

    @Test
    fun thePreviewAndTheJoinOutcomesOfTheRealAnswers() = runTest {
        val ready = assertIs<PreviewResult.Ready>(owner(backgroundScope, 200, C.PREVIEW_EMAIL_200).ready().service.preview(C.TOKEN_MAIL))
        assertEquals("Home", ready.preview.walletName)
        assertEquals("OWN QA", ready.preview.inviterName)
        assertFalse(ready.preview.alreadyMember)
        assertTrue(assertIs<PreviewResult.Ready>(owner(backgroundScope, 200, C.PREVIEW_ALREADY_MEMBER_200).ready().service.preview(C.TOKEN_LINK)).preview.alreadyMember)

        for (body in listOf(C.PREVIEW_UNKNOWN_TOKEN_404, C.PREVIEW_OTHER_ADDRESS_404, C.PREVIEW_EXPIRED_LINK_404, C.PREVIEW_AFTER_ACCEPT_404)) {
            val env = owner(backgroundScope, 404, body).ready()
            assertEquals(PreviewResult.Unavailable, env.service.preview(C.TOKEN_UNKNOWN))
            assertEquals(JoinResult.Unavailable, env.service.join(C.TOKEN_UNKNOWN))
            assertEquals(DeclineResult.Unavailable, env.service.decline(C.TOKEN_UNKNOWN))
        }
        assertEquals(PreviewResult.InvalidLink, owner(backgroundScope, 422, C.PREVIEW_MALFORMED_TOKEN_422).ready().service.preview("short"))
        assertEquals(JoinResult.OwnerPlanFull, owner(backgroundScope, 403, C.ACCEPT_OWNER_PLAN_FREE_403).ready().service.join(C.TOKEN_LINK_THREE))
        for (body in listOf(C.TOKEN_RATE_LIMITED_429, C.ACCEPT_RATE_LIMITED_429, C.DECLINE_RATE_LIMITED_429)) {
            val env = SharingTestEnv(backgroundScope, userId = C.USER_OWNER) { jsonResponse(429, body, "Retry-After" to "3597") }.ready()
            assertEquals(PreviewResult.TooMany(60), env.service.preview(C.TOKEN_UNKNOWN))
            assertEquals(JoinResult.TooMany(60), env.service.join(C.TOKEN_UNKNOWN))
            assertEquals(DeclineResult.TooMany(60), env.service.decline(C.TOKEN_UNKNOWN))
        }
    }

    @Test
    fun anUnverifiedPersonJoinsAndTheLiveMemberRowMakesTheWalletFollowed() = runTest {
        val env = SharingTestEnv(backgroundScope, verified = false, userId = C.USER_UNVERIFIED) { jsonResponse(200, C.ACCEPT_UNVERIFIED_200) }
        env.signedIn()
        assertEquals(JoinResult.Joined(C.WALLET), env.service.join(C.TOKEN_LINK))
        assertEquals(parse(C.ACCEPT_UNVERIFIED_200_REQUEST), env.requests.single().jsonBody())
        assertEquals("viewer", env.service.observeMembers(C.WALLET).first().single().roleWire)
        assertTrue(env.walletRepository.followedIds(C.USER_UNVERIFIED).contains(C.WALLET))
        val declined = SharingTestEnv(backgroundScope, verified = false, userId = C.USER_UNVERIFIED) { respond("", HttpStatusCode.NoContent) }
        declined.signedIn()
        assertEquals(DeclineResult.Declined, declined.service.decline(C.TOKEN_LINK_TWO))
    }

    // --- members and leaving ---

    @Test
    fun theOwnersRoleChangesAndRemovalsFollowTheLiveAnswers() = runTest {
        val env = owner(backgroundScope, 200, C.PATCH_ROLE_VIEWER_200).ready()
        env.memberRow(C.EMAIL_MEMBER_ROW, C.USER_EDITOR, "editor", 1)
        assertEquals(MemberActionResult.Done, env.service.changeRole(C.WALLET, C.USER_EDITOR, WalletRole.Viewer))
        assertEquals(parse(C.PATCH_ROLE_VIEWER_200_REQUEST), env.requests.single().jsonBody())
        val after = env.service.observeMembers(C.WALLET).first().single { it.userId == C.USER_EDITOR }
        assertEquals("viewer", after.roleWire)
        assertEquals(2L, after.version)

        val stale = owner(backgroundScope, 409, C.PATCH_ROLE_STALE_409).ready()
        stale.memberRow(C.EMAIL_MEMBER_ROW, C.USER_EDITOR, "viewer", 1)
        assertEquals(MemberActionResult.Changed, stale.service.changeRole(C.WALLET, C.USER_EDITOR, WalletRole.Editor))
        assertEquals(parse(C.PATCH_ROLE_STALE_409_REQUEST), stale.requests.first().jsonBody())
        assertEquals(2L, stale.service.observeMembers(C.WALLET).first().single { it.userId == C.USER_EDITOR }.version, "the server's copy is stored")

        // The owner's phone still thinks the plan allows it; the server is final.
        val free = owner(backgroundScope, 403, C.PATCH_FREE_OWNER_RAISE_403).ready()
        free.memberRow(C.EMAIL_MEMBER_ROW, C.USER_EDITOR, "viewer", 4)
        assertEquals(MemberActionResult.NotEntitled("sharing.enabled"), free.service.changeRole(C.WALLET, C.USER_EDITOR, WalletRole.Editor))

        val notOwner = owner(backgroundScope, 403, C.PATCH_BY_EDITOR_403).ready()
        notOwner.memberRow(C.EMAIL_MEMBER_ROW, C.USER_EDITOR, "editor", 1)
        assertEquals(MemberActionResult.OnlyOwner, notOwner.service.changeRole(C.WALLET, C.USER_EDITOR, WalletRole.Viewer))

        val remove = owner(backgroundScope, 200, C.DELETE_MEMBER_200).ready()
        remove.memberRow("01a10b54-e078-7098-becb-f1c55198e003", C.USER_UNVERIFIED, "viewer", 1)
        assertEquals(MemberActionResult.Done, remove.service.remove(C.WALLET, C.USER_UNVERIFIED))
        assertEquals(parse(C.DELETE_MEMBER_200_REQUEST), remove.requests.single().jsonBody())
        assertTrue(remove.service.observeMembers(C.WALLET).first().none { it.userId == C.USER_UNVERIFIED })

        val gone = owner(backgroundScope, 404, C.PATCH_REMOVED_MEMBER_404).ready()
        gone.memberRow("x", C.USER_UNVERIFIED, "viewer", 2)
        assertEquals(MemberActionResult.Gone, gone.service.changeRole(C.WALLET, C.USER_UNVERIFIED, WalletRole.Editor))
    }

    @Test
    fun leavingFollowsTheLiveAnswersAndRemovesTheWallet() = runTest {
        suspend fun editorEnv(status: Int, body: String, version: Long): SharingTestEnv {
            val env = SharingTestEnv(backgroundScope, userId = C.USER_EDITOR) { jsonResponse(status, body) }.ready(role = "viewer")
            env.dropSeedOwnRow()
            env.memberships.store(MemberRecord(C.EMAIL_MEMBER_ROW, C.WALLET, C.USER_EDITOR, "ED QA", "viewer", version, "2026-10-05T09:11:13Z"))
            return env
        }
        val left = editorEnv(200, C.LEAVE_200, 4)
        assertEquals(LeaveResult.Left("Home"), left.service.leave(C.WALLET))
        assertEquals(parse(C.LEAVE_200_REQUEST), left.requests.single().jsonBody())
        assertNull(left.wallets.observeWallet(C.WALLET).first())
        assertTrue(left.removedWallets.contains(C.WALLET))

        val repeat = editorEnv(404, C.LEAVE_REPEAT_404, 4)
        assertEquals(LeaveResult.Left("Home"), repeat.service.leave(C.WALLET))
        assertNull(repeat.wallets.observeWallet(C.WALLET).first())

        val stale = editorEnv(409, C.LEAVE_STALE_409, 1)
        assertEquals(LeaveResult.Changed, stale.service.leave(C.WALLET))
        assertEquals(4L, stale.memberships.ownMembershipVersion(C.WALLET))

        assertEquals(LeaveResult.OwnerCannotLeave, editorEnv(403, C.LEAVE_OWNER_403, 1).service.leave(C.WALLET))
    }

    // --- activity ---

    @Test
    fun theLiveActivityIsNamedFromTheServerThenTheFormerMemberWordAndNeverShowsAnAmount() = runTest {
        val env = owner(backgroundScope, 200, C.ACTIVITY_PAGE_200).ready()
        val page = assertIs<ActivityPage.Loaded>(env.service.activityPage(C.WALLET, null))
        assertEquals(ActivityAction.Created, page.items.first().action)
        assertEquals(Actor.Named("ED QA"), page.items.first().actor)
        assertTrue(page.items.any { it.actor == Actor.FormerMember }, "an actor the server no longer names and the phone does not know")
        assertNull(page.nextCursor)
        assertEquals("50", env.requests.single().url.parameters["limit"])
    }

    @Test
    fun aBadCursorRestartsFromTheFirstPageOnceAndAPlanDemotionOfThePersonIsRemembered() = runTest {
        var calls = 0
        val env = SharingTestEnv(backgroundScope, userId = C.USER_EDITOR) {
            calls++
            if (calls == 1) jsonResponse(400, C.ACTIVITY_INVALID_CURSOR_400) else jsonResponse(200, C.ACTIVITY_AFTER_DEMOTION_200)
        }.ready(role = "viewer")
        val page = assertIs<ActivityPage.Loaded>(env.service.activityPage(C.WALLET, "garbage"))
        assertTrue(page.restarted)
        assertEquals(Actor.Automatic, page.items.first().actor)
        assertEquals(ActivityAction.RoleLimitedByPlan, page.items.first().action)
        assertTrue(env.planNotes.observe(C.WALLET).first(), "the viewer note may now name the owner's plan")
        assertEquals(ActivityPage.Gone, owner(backgroundScope, 404, C.ACTIVITY_NON_MEMBER_404).ready().service.activityPage(C.WALLET, null))
    }

    // --- feeds ---

    private fun SharingTestEnv.puller(feeds: Map<String, Pair<Int, String>>): FeedPuller {
        return FeedPuller(
            HttpSyncApi(auth.client, auth.events, batchTimeoutMillis = null), scopeDao,
            {
                listOf(
                    InvitationFeedApplier(repository),
                    com.fares.saveme.feature.wallets.data.WalletMemberFeedApplier(walletRepository),
                    com.fares.saveme.feature.wallets.data.WalletFeedApplier(walletRepository),
                    com.fares.saveme.feature.wallets.data.OwnMembershipFeedApplier(walletRepository),
                )
            },
            { com.fares.saveme.feature.wallets.domain.DefaultWalletScopes(walletRepository, { userId }) }, SyncLock(), { userId }, clock,
        )
    }

    private fun feedEnv(scope: kotlinx.coroutines.CoroutineScope, userId: String, vararg answers: Pair<String, Pair<Int, String>>): SharingTestEnv {
        val map = answers.toMap()
        return SharingTestEnv(scope, userId = userId) { request ->
            val path = request.url.encodedPath
            val key = if (path.endsWith("/sync/user/changes")) "user" else "wallet"
            val (status, body) = map.getValue(key)
            jsonResponse(status, body)
        }
    }

    @Test
    fun theOwnersLiveWalletFeedStoresOnlyThePendingInvitationsAndTheLiveMembers() = runTest {
        val env = feedEnv(backgroundScope, C.USER_OWNER, "wallet" to (200 to C.FEED_WALLET_OWNER_ALL_STATUSES_200))
        env.signedIn()
        val rows = parse(C.FEED_WALLET_OWNER_ALL_STATUSES_200).getValue("data").jsonArray.map { it.jsonObject }
        val invitations = rows.filter { it.getValue("type").jsonPrimitive.content == "invitation" }.map { it.getValue("record").jsonObject }
        val statuses = invitations.map { it.getValue("status").jsonPrimitive.content }.toSet()
        assertEquals(setOf("pending", "accepted", "declined", "revoked", "expired"), statuses, "the feed carries every status")
        val read = env.puller(emptyMap()).read(SyncScope(ScopeKind.Wallet, C.WALLET))
        assertIs<ScopeRead.Done>(read)
        val pending = invitations.filter { it.getValue("deleted_at").toString() == "null" }.map { it.getValue("id").jsonPrimitive.content }.toSet()
        assertTrue(pending.isNotEmpty())
        assertEquals(pending, env.invitationDao.rows.value.keys, "only live pending invitations are stored; every closed one is a tombstone")
        assertTrue(env.invitationDao.rows.value.values.all { it.status == "pending" })
        val members = env.service.observeMembers(C.WALLET).first()
        assertEquals(listOf("owner", "viewer"), members.map { it.roleWire }.take(2), "owner first, the demoted editor (viewer v4) after")
    }

    @Test
    fun anInvitationTombstoneInAFeedRemovesAStoredRowAndAClosedWalletTombstonesItsInvitation() = runTest {
        val env = feedEnv(backgroundScope, C.USER_OWNER, "wallet" to (200 to C.FEED_WALLET_DELETED_200))
        env.signedIn()
        env.seed(com.fares.saveme.feature.sharing.invitationJson("01a10b58-0857-7c14-8b9f-cee74fe34dd5", walletId = "01a10b58-07c7-7759-aa35-ef64c004d441", email = null, version = 1))
        assertIs<ScopeRead.Done>(env.puller(emptyMap()).read(SyncScope(ScopeKind.Wallet, "01a10b58-07c7-7759-aa35-ef64c004d441")))
        assertTrue(env.invitationDao.rows.value.isEmpty(), "the revoked tombstone removed the row")
    }

    @Test
    fun aDemotionArrivesAsAnOrdinaryOwnMemberRowAndEveryScreenGatesOnTheNewRoleAtOnce() = runTest {
        val env = feedEnv(backgroundScope, C.USER_EDITOR, "user" to (200 to C.FEED_USER_AFTER_DEMOTION_200), "wallet" to (200 to C.FEED_WALLET_AFTER_DEMOTION_200))
        env.signedIn()
        env.seedOwnerWallet(C.WALLET, role = "editor")
        env.dropSeedOwnRow()
        env.memberRow(C.EMAIL_MEMBER_ROW, C.USER_EDITOR, "editor", 3)
        assertIs<ScopeRead.Done>(env.puller(emptyMap()).read(SyncScope(ScopeKind.User, C.USER_EDITOR)))
        assertEquals("viewer", env.walletDao.rows.value.getValue(C.WALLET).myRole)
        assertEquals(WalletRole.Viewer, env.memberships.ownRole(C.WALLET))
        val own = env.service.observeMembers(C.WALLET).first().single { it.userId == C.USER_EDITOR }
        assertEquals(4L, own.version)
    }

    @Test
    fun anOwnMembershipTombstoneOnTheUserFeedRemovesTheWalletForARemovedMemberAndAfterALeave() = runTest {
        for ((user, body) in listOf(C.USER_UNVERIFIED to C.FEED_USER_REMOVED_200, C.USER_EDITOR to C.FEED_USER_LEFT_200)) {
            val env = feedEnv(backgroundScope, user, "user" to (200 to body))
            env.signedIn()
            env.seedOwnerWallet(C.WALLET, role = "viewer")
            env.invitationDao.upsert(com.fares.saveme.feature.sharing.data.local.InvitationEntity("inv", user, C.WALLET, "viewer", "link", null, "u2", "pending", null, 1, 1, "2026-10-05T09:00:00Z", "2026-10-05T09:00:00Z", "2026-10-05T09:00:00Z"))
            assertIs<ScopeRead.Done>(env.puller(emptyMap()).read(SyncScope(ScopeKind.User, user)))
            assertTrue(env.removedWallets.contains(C.WALLET), "the wallet and everything of it leave the phone")
            assertNull(env.walletDao.rows.value[C.WALLET])
        }
    }

    @Test
    fun aMemberRemovedAndTakenBackWhileThePhoneWasAwayEndsWithTheWalletFollowedAndReadAgainFromTheStart() = runTest {
        val env = feedEnv(backgroundScope, C.USER_UNVERIFIED, "user" to (200 to C.FEED_USER_AFTER_REJOIN_200))
        env.signedIn()
        env.seedOwnerWallet(C.WALLET, role = "viewer")
        env.memberRow("01a10b54-e078-7098-becb-f1c55198e003", C.USER_UNVERIFIED, "viewer", 1)
        env.scopeDao.storeFeedPage("wallet:${C.WALLET}", "old-position", 40)
        assertIs<ScopeRead.Done>(env.puller(emptyMap()).read(SyncScope(ScopeKind.User, C.USER_UNVERIFIED)))
        val own = env.service.observeMembers(C.WALLET).first().filter { it.userId == C.USER_UNVERIFIED }
        assertEquals(1, own.size, "the old row is gone and the new one stored")
        assertTrue(env.walletRepository.followedIds(C.USER_UNVERIFIED).contains(C.WALLET))
        assertNull(env.scopeDao.rows.value["wallet:${C.WALLET}"]?.feedCursor, "the wallet is read again from the start, never from a stale position")
    }

    @Test
    fun aWalletFeedThatAnswers404MeansTheAccessIsLost() = runTest {
        for (body in listOf(C.FEED_WALLET_REMOVED_404, C.FEED_WALLET_LEFT_404)) {
            val env = feedEnv(backgroundScope, C.USER_UNVERIFIED, "wallet" to (404 to body))
            env.signedIn()
            val read = env.puller(emptyMap()).read(SyncScope(ScopeKind.Wallet, C.WALLET))
            assertTrue(assertIs<ScopeRead.Done>(read).lost)
        }
    }
}
