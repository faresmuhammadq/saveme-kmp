package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.sharing.data.InvitationPayload
import com.fares.saveme.feature.sharing.FAKE_LINK
import com.fares.saveme.feature.sharing.SharingTestEnv
import com.fares.saveme.feature.sharing.domain.model.InvitationDraft
import com.fares.saveme.feature.sharing.domain.model.InviteResult
import com.fares.saveme.feature.sharing.domain.model.InvitedRole
import com.fares.saveme.feature.sharing.invitationBody
import com.fares.saveme.feature.sharing.invitationJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Link invitations: the one-time link, the share text, replacing a lost link (spec 013 FR-005, FR-006). */
class SharingServiceLinkTest {

    private val link = InvitationDraft(InvitedRole.Viewer, null)

    private fun linkCreated(url: String = FAKE_LINK) = """{"data":${invitationJson(id = "i-new-1", email = null, role = "viewer")},"meta":{"share_url":"$url"}}"""

    @Test
    fun aLinkInvitationQueuesWithANullAddress() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet()
        val id = assertIs<InviteResult.Queued>(env.service.invite("w1", link)).invitationId
        assertNull(AppJson.decodeFromString(InvitationPayload.serializer(), env.engine.get("invitation", id)!!.payloadJson).email)
        assertNull(env.repository.get(id)!!.inviteeEmail)
        assertEquals("link", env.repository.get(id)!!.channel)
    }

    @Test
    fun theServersLinkMakesTheShareTextAvailableAndAReplayLeavesItNull() = runTest {
        val env = SharingTestEnv(backgroundScope) { jsonResponse(201, linkCreated()) }
        env.signedIn()
        env.seedOwnerWallet()
        val id = assertIs<InviteResult.Queued>(env.service.invite("w1", link)).invitationId
        env.engine.drain()
        val text = assertNotNull(env.service.shareTextFor(id))
        assertTrue(text.contains(FAKE_LINK))
        assertTrue(text.contains("Save Me"))
        assertTrue(env.service.observeLinkHeld(id).first())

        val replay = SharingTestEnv(backgroundScope) { jsonResponse(200, invitationBody(invitationJson(id = "i-new-1", email = null, role = "viewer"))) }
        replay.signedIn()
        replay.seedOwnerWallet()
        val replayId = assertIs<InviteResult.Queued>(replay.service.invite("w1", link)).invitationId
        replay.engine.drain()
        assertNull(replay.service.shareTextFor(replayId))
        assertFalse(replay.service.observeLinkHeld(replayId).first())
    }

    @Test
    fun replacingWithdrawsTheOldLinkAndQueuesANewOneWithTheSameRole() = runTest {
        val env = SharingTestEnv(backgroundScope)
        env.seedOwnerWallet()
        env.seed(invitationJson(id = "old", email = null, role = "editor"))
        env.shareLinks.offer("old", FAKE_LINK)
        val result = assertIs<InviteResult.Queued>(env.service.replaceLink("old"))
        val withdrawal = assertNotNull(env.engine.get("invitation", "old"))
        assertTrue(withdrawal.isDelete)
        assertNull(env.shareLinks.get("old"), "the old link is forgotten")
        val created = assertNotNull(env.engine.get("invitation", result.invitationId))
        val payload = AppJson.decodeFromString(InvitationPayload.serializer(), created.payloadJson)
        assertEquals("editor", payload.role)
        assertNull(payload.email)
    }

    @Test
    fun nothingTheServiceLogsContainsTheLink() = runTest {
        val logs = LogCapture()
        try {
            val env = SharingTestEnv(backgroundScope) { jsonResponse(201, linkCreated()) }
            env.signedIn()
            env.seedOwnerWallet()
            val id = assertIs<InviteResult.Queued>(env.service.invite("w1", link)).invitationId
            env.engine.drain()
            env.service.shareTextFor(id)
        } finally {
            logs.close()
        }
        assertTrue(logs.lines.isNotEmpty())
        assertTrue(logs.lines.none { it.contains(FAKE_LINK) || it.contains("token=") })
    }
}
