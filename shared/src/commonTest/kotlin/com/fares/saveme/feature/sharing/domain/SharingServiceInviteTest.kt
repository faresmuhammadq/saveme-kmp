package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.Refusal
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.sharing.FAKE_LINK
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.domain.model.Delivery
import com.fares.saveme.feature.sharing.domain.model.InvitationDraft
import com.fares.saveme.feature.sharing.domain.model.InvitationRefusalKind
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.domain.model.InviteResult
import com.fares.saveme.feature.sharing.domain.model.InvitedRole
import com.fares.saveme.feature.sharing.invitationBody
import com.fares.saveme.feature.sharing.invitationJson
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Inviting by email: gate order, validation, the queued change, offline, refusals (spec 013 FR-001-FR-004, FR-008). */
class SharingServiceInviteTest {

    private val email = InvitationDraft(InvitedRole.Editor, "karim@example.com")

    @Test
    fun anInvitationQueuesOneChangeInTheWalletScopeWithVersionZeroAndAPlaceholder() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet()
        val result = env.service.invite("w1", email)
        val id = assertIs<InviteResult.Queued>(result).invitationId
        assertEquals("i-new-1", id)
        val change = assertNotNull(env.engine.get("invitation", id))
        assertEquals(0, change.baseVersion)
        assertEquals("wallet:w1", change.scope.key)
        val placeholder = assertNotNull(env.repository.get(id))
        assertEquals(0, placeholder.version)
        assertEquals("editor", placeholder.role)
        assertEquals(1, env.engine.pendingIn(change.scope).size)
    }

    @Test
    fun theGateAnswersInOrderVerifiedThenIncludedThenCount() = runTest {
        val unverified = SharingTestEnv(backgroundScope, verified = false)
        unverified.seedOwnerWallet()
        assertEquals(InviteResult.NotVerified, unverified.service.invite("w1", email))
        assertNull(unverified.engine.get("invitation", "i-new-1"))

        val free = SharingTestEnv(backgroundScope)
        free.seedOwnerWallet()
        free.entitlements.set(sharing = false, max = 5)
        assertEquals(InviteResult.SharingNotIncluded, free.service.invite("w1", email))

        val full = SharingTestEnv(backgroundScope)
        full.seedOwnerWallet()
        full.entitlements.set(sharing = true, max = 1)
        assertEquals(InviteResult.Full(1), full.service.invite("w1", email))
        assertTrue(full.engine.pendingIn(com.fares.saveme.core.sync.SyncScope(com.fares.saveme.core.sync.ScopeKind.Wallet, "w1")).isEmpty())
    }

    @Test
    fun theFormatAndTheOwnAddressAreRefusedBeforeAnythingIsQueued() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet()
        assertEquals(InviteResult.Invalid, env.service.invite("w1", InvitationDraft(InvitedRole.Viewer, "not an address")))
        assertEquals(InviteResult.OwnAddress, env.service.invite("w1", InvitationDraft(InvitedRole.Viewer, " SARA@example.com ")))
        assertNull(env.engine.get("invitation", "i-new-1"))
    }

    @Test
    fun aSecondInvitationToTheSameAddressNeedsConfirmationAndThenSupersedes() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet()
        env.seed(invitationJson(email = "karim@example.com"))
        assertEquals(InviteResult.SupersedeNeeded, env.service.invite("w1", InvitationDraft(InvitedRole.Viewer, "KARIM@example.com")))
        assertNull(env.engine.get("invitation", "i-new-1"))
        assertIs<InviteResult.Queued>(env.service.inviteConfirmed("w1", InvitationDraft(InvitedRole.Viewer, "KARIM@example.com")))
        assertNotNull(env.engine.get("invitation", "i-new-1"))
    }

    @Test
    fun aFullWalletFreesTheEarlierInvitationsPlaceWhenSuperseding() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet()
        env.entitlements.set(sharing = true, max = 2)
        env.seed(invitationJson(email = "karim@example.com"))
        assertEquals(InviteResult.SupersedeNeeded, env.service.invite("w1", InvitationDraft(InvitedRole.Viewer, "karim@example.com")))
    }

    @Test
    fun anArchivedWalletAndANonOwnerAreNotOffered() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet(role = "editor")
        assertEquals(InviteResult.NotOwner, env.service.invite("w1", email))
        val archived = SharingTestEnv(backgroundScope)
        archived.seedOwnerWallet()
        archived.walletRepository.storeServerCopy(
            com.fares.saveme.core.network.AppJson.decodeFromString(
                com.fares.saveme.feature.wallets.data.dto.WalletDto.serializer(),
                com.fares.saveme.feature.wallets.walletJson("w1", name = "Home", archivedAt = "2026-10-01T00:00:00Z", version = 4),
            ),
        )
        assertEquals(InviteResult.WalletArchived, archived.service.invite("w1", email))
    }

    @Test
    fun offlineDoesNotBlockAndTheInvitationShowsAsWaiting() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet()
        val id = assertIs<InviteResult.Queued>(env.service.invite("w1", email)).invitationId
        testScheduler.advanceUntilIdle()
        val view = env.service.observeInvitations("w1").first().single { it.id == id }
        assertTrue(view.waiting)
        assertEquals(Delivery.Waiting, env.service.awaitDelivery(id))
    }

    @Test
    fun anOnlineInvitationIsSentAndReportedAsDelivered() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(201, invitationBody(invitationJson(id = "i-new-1"))) }
        env.signedIn()
        env.seedOwnerWallet()
        val id = assertIs<InviteResult.Queued>(env.service.invite("w1", email)).invitationId
        env.engine.drain()
        assertEquals(Delivery.Sent, env.service.awaitDelivery(id))
        assertEquals(1, env.repository.get(id)!!.version)
    }

    @Test
    fun aRefusalByFeatureMapsToTheRightReason() = runTest {
        suspend fun refusalFor(feature: String?, code: String = "FEATURE_NOT_ENTITLED", status: Int = 403): InvitationRefusalKind? {
            val extra = feature?.let { ""","feature":"$it"""" }.orEmpty()
            val env = SharingTestEnv(backgroundScope) { jsonResponse(status, problemBody(status, code, extra)) }
            env.signedIn()
            env.seedOwnerWallet()
            val id = assertIs<InviteResult.Queued>(env.service.invite("w1", email)).invitationId
            env.engine.drain()
            assertEquals(Delivery.Refused, env.service.awaitDelivery(id))
            val change = env.engine.get("invitation", id)
            assertEquals(ChangeState.Refused, change?.state)
            return env.service.observeInvitations("w1").first().single { it.id == id }.refusal?.kind
        }
        assertEquals(InvitationRefusalKind.SharingNotIncluded, refusalFor("sharing.enabled"))
        assertEquals(InvitationRefusalKind.PlanFull, refusalFor("sharing.max_members_per_wallet"))
        assertEquals(InvitationRefusalKind.NotVerified, refusalFor(null, "EMAIL_NOT_VERIFIED"))
    }

    @Test
    fun discardRemovesThePlaceholderAndTheChangeAndTryAgainRequeues() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(403, problemBody(403, "EMAIL_NOT_VERIFIED")) }
        env.signedIn()
        env.seedOwnerWallet()
        val id = assertIs<InviteResult.Queued>(env.service.invite("w1", email)).invitationId
        env.engine.drain()
        assertEquals(ChangeState.Refused, env.engine.get("invitation", id)?.state)
        env.service.tryAgain(id)
        assertNotNull(env.engine.get("invitation", id))
        testScheduler.advanceUntilIdle()
        env.service.discard(id)
        assertNull(env.engine.get("invitation", id))
        assertNull(env.repository.get(id))
    }

    @Test
    fun theOutcomeMessageDoesNotDependOnWhetherTheAddressHasAnAccount() = runTest {
        // Two addresses, one with an account (201) and one without (201 as well): the service answers the same, no hint (P-09.4).
        val known = SharingTestEnv(backgroundScope) { jsonResponse(201, invitationBody(invitationJson(id = "i-new-1", email = "known@example.com"))) }
        known.signedIn(); known.seedOwnerWallet()
        val unknown = SharingTestEnv(backgroundScope) { jsonResponse(201, invitationBody(invitationJson(id = "i-new-1", email = "stranger@example.com"))) }
        unknown.signedIn(); unknown.seedOwnerWallet()
        val a = known.service.invite("w1", InvitationDraft(InvitedRole.Viewer, "known@example.com"))
        val b = unknown.service.invite("w1", InvitationDraft(InvitedRole.Viewer, "stranger@example.com"))
        assertEquals(a, b)
        known.engine.drain()
        unknown.engine.drain()
        assertEquals(known.service.awaitDelivery("i-new-1"), unknown.service.awaitDelivery("i-new-1"))
    }

    @Test
    fun theCheckFollowsEntitlementsAndWaitingWithdrawalsAreNotCounted() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet()
        env.entitlements.set(sharing = true, max = 2)
        assertEquals(InviteCheck.Allowed, env.service.observeInviteCheck("w1").first())
        env.seed(invitationJson(email = "karim@example.com"))
        assertEquals(InviteCheck.Full(2), env.service.observeInviteCheck("w1").first())
        env.service.withdraw("i1")
        assertEquals(InviteCheck.Allowed, env.service.observeInviteCheck("w1").first())
        env.authState.value = AuthState.SignedIn(env.account.copy(emailVerified = false))
        assertEquals(InviteCheck.NotVerified, env.service.observeInviteCheck("w1").first())
    }

    @Test
    fun theRefusalRecordKeepsTheFeatureAndRequestId() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(403, problemBody(403, "FEATURE_NOT_ENTITLED", ""","feature":"sharing.enabled"""")) }
        env.signedIn()
        env.seedOwnerWallet()
        val id = assertIs<InviteResult.Queued>(env.service.invite("w1", email)).invitationId
        env.engine.drain()
        val refusal: Refusal = assertNotNull(env.engine.get("invitation", id)?.refusal)
        assertEquals("sharing.enabled", refusal.feature)
        assertEquals(FAKE_LINK.isNotEmpty(), true)
    }
}
