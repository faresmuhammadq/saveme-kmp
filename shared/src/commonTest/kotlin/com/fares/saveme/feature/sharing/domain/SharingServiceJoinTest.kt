package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.sharing.FAKE_TOKEN
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.dataBody
import com.fares.saveme.feature.sharing.domain.model.DeclineResult
import com.fares.saveme.feature.sharing.domain.model.JoinResult
import com.fares.saveme.feature.sharing.domain.model.PreviewResult
import com.fares.saveme.feature.sharing.previewJson
import com.fares.saveme.feature.wallets.memberJson
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The invitee's side: preview, join, decline (contracts A3-A5, spec 013 FR-012-FR-015). */
class SharingServiceJoinTest {

    @Test
    fun aPreviewReadsTheWalletTheInviterAndTheRole() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, dataBody(previewJson(inviter = null))) }
        env.signedIn()
        val ready = assertIs<PreviewResult.Ready>(env.service.preview(FAKE_TOKEN))
        assertEquals("Home", ready.preview.walletName)
        assertNull(ready.preview.inviterName)
        assertEquals("editor", ready.preview.roleWire)
        assertFalse(ready.preview.alreadyMember)
    }

    @Test
    fun everyNotFoundIsOneUnavailableAnswer() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
        env.signedIn()
        assertEquals(PreviewResult.Unavailable, env.service.preview(FAKE_TOKEN))
        assertEquals(JoinResult.Unavailable, env.service.join(FAKE_TOKEN))
        assertEquals(DeclineResult.Unavailable, env.service.decline(FAKE_TOKEN))
    }

    @Test
    fun theOwnersPlanBeingFullIsOneAnswerForBothFeatures() = runTest {
        for (feature in listOf("sharing.max_members_per_wallet", "sharing.enabled")) {
            val env = SharingTestEnv(backgroundScope) { jsonResponse(403, problemBody(403, "FEATURE_NOT_ENTITLED", ""","feature":"$feature"""")) }
            env.signedIn()
            assertEquals(JoinResult.OwnerPlanFull, env.service.join(FAKE_TOKEN), feature)
        }
    }

    @Test
    fun aBadTokenShapeAndTooManyAttemptsAreTheirOwnAnswers() = runTest {
        val bad = SharingTestEnv(backgroundScope) { jsonResponse(422, problemBody(422, "VALIDATION_FAILED")) }
        bad.signedIn()
        assertEquals(PreviewResult.InvalidLink, bad.service.preview(FAKE_TOKEN))
        assertEquals(JoinResult.InvalidLink, bad.service.join(FAKE_TOKEN))

        val many = SharingTestEnv(backgroundScope) { jsonResponse(429, problemBody(429, "RATE_LIMITED", ""","retry_after_seconds":61""")) }
        many.signedIn()
        assertEquals(PreviewResult.TooMany(2), many.service.preview(FAKE_TOKEN))
        assertEquals(JoinResult.TooMany(2), many.service.join(FAKE_TOKEN))
        assertEquals(DeclineResult.TooMany(2), many.service.decline(FAKE_TOKEN))
    }

    @Test
    fun offlineAndOtherFailuresAreNamedAndCarryTheRequestId() = runTest {
        val offline = SharingTestEnv(backgroundScope)
        offline.signedIn()
        assertEquals(PreviewResult.Offline, offline.service.preview(FAKE_TOKEN))
        assertEquals(JoinResult.Offline, offline.service.join(FAKE_TOKEN))
        assertEquals(DeclineResult.Offline, offline.service.decline(FAKE_TOKEN))

        val broken = SharingTestEnv(backgroundScope) { jsonResponse(500, problemBody(500, "INTERNAL_ERROR")) }
        broken.signedIn()
        assertEquals(JoinResult.Failed("req-42"), broken.service.join(FAKE_TOKEN))
    }

    @Test
    fun joiningStoresTheMemberRowRequestsAForcedReadAndClearsTheHeldInvitation() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(200, dataBody(memberJson("m-u1", "u1", "Sara", "editor"))) }
        env.signedIn()
        env.held.hold(InvitationLink.Valid(FAKE_TOKEN))
        assertEquals(JoinResult.Joined("w1"), env.service.join(FAKE_TOKEN))
        assertEquals(1, env.memberships.observe("w1").first().size)
        assertTrue(env.wallets.observeJoining("w1").first(), "the wallet row has not arrived yet")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.Wallet("w1") to true), env.runner.refreshes)
        assertNull(env.held.held.value)
    }

    @Test
    fun decliningClearsTheHeldInvitation() = runTest {
        val env = SharingTestEnv(backgroundScope) { respond("", HttpStatusCode.NoContent) }
        env.signedIn()
        env.held.hold(InvitationLink.Valid(FAKE_TOKEN))
        assertEquals(DeclineResult.Declined, env.service.decline(FAKE_TOKEN))
        assertNull(env.held.held.value)
    }

    @Test
    fun noTokenCallIsRetriedAutomatically() = runTest {
        for (answer in listOf(500, 503, 429)) {
            val env = SharingTestEnv(backgroundScope) { jsonResponse(answer, problemBody(answer, "INTERNAL_ERROR")) }
            env.signedIn()
            env.service.preview(FAKE_TOKEN)
            assertEquals(1, env.requests.size, "preview $answer")
            env.service.join(FAKE_TOKEN)
            assertEquals(2, env.requests.size, "join $answer")
            env.service.decline(FAKE_TOKEN)
            assertEquals(3, env.requests.size, "decline $answer")
        }
    }
}
