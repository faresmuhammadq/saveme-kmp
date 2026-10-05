package com.fares.saveme.feature.sharing

import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.sharing.data.InvitationPayload
import com.fares.saveme.feature.sharing.data.dto.InvitationCreateMetaDto
import com.fares.saveme.feature.sharing.data.dto.TokenRequest
import com.fares.saveme.feature.sharing.domain.HeldInvitation
import com.fares.saveme.feature.sharing.domain.InvitationLink
import com.fares.saveme.feature.sharing.domain.LogCapture
import com.fares.saveme.feature.sharing.domain.PlanDemotionNotes
import com.fares.saveme.feature.sharing.domain.ShareLink
import com.fares.saveme.feature.sharing.domain.ShareLinks
import com.fares.saveme.feature.sharing.domain.model.InvitationDraft
import com.fares.saveme.feature.sharing.domain.model.InviteResult
import com.fares.saveme.feature.sharing.domain.model.InvitedRole
import com.fares.saveme.feature.sharing.ui.invite.InviteEffect
import com.fares.saveme.feature.sharing.ui.members.MembersEffect
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.memberJson
import com.fares.saveme.navigation.InvitationKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** FR-010, FR-034, SC-004: no token, link or address reaches a log, and every secret-carrying type prints redacted. */
class SharingLoggingTest {

    private val address = "karim.private@example.com"

    private fun noSecret(logs: LogCapture) {
        assertTrue(logs.lines.isNotEmpty(), "something was logged")
        logs.lines.forEach { line ->
            assertFalse(line.contains(FAKE_TOKEN), line)
            assertFalse(line.contains(FAKE_LINK), line)
            assertFalse(line.contains("token="), line)
            assertFalse(line.contains(address), line)
            assertFalse(line.contains("private@"), line)
        }
    }

    @Test
    fun anEmailInvitationALinkInvitationAndAShareLogNoSecret() = runTest {
        val logs = LogCapture()
        try {
            val env = SharingTestEnv(backgroundScope) { request ->
                if (request.url.encodedPath.endsWith("/invitations/i-new-2")) {
                    jsonResponse(201, """{"data":${invitationJson(id = "i-new-2", email = null)},"meta":{"share_url":"$FAKE_LINK"}}""")
                } else {
                    jsonResponse(201, invitationBody(invitationJson(id = "i-new-1", email = address)))
                }
            }
            env.signedIn()
            env.seedOwnerWallet()
            assertIs<InviteResult.Queued>(env.service.invite("w1", InvitationDraft(InvitedRole.Viewer, address)))
            assertIs<InviteResult.Queued>(env.service.invite("w1", InvitationDraft(InvitedRole.Viewer, null)))
            env.engine.drain()
            env.service.shareTextFor("i-new-2")
            env.service.withdraw("i-new-1")
        } finally {
            logs.close()
        }
        noSecret(logs)
    }

    @Test
    fun aJoinADeclineAndAPreviewLogNoSecret() = runTest {
        val logs = LogCapture()
        try {
            val joined = SharingTestEnv(backgroundScope) { jsonResponse(200, dataBody(memberJson("m-u1", "u1", "Sara", "editor"))) }
            joined.signedIn()
            joined.service.join(FAKE_TOKEN)
            val refused = SharingTestEnv(backgroundScope) { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
            refused.signedIn()
            refused.service.preview(FAKE_TOKEN)
            refused.service.join(FAKE_TOKEN)
            refused.service.decline(FAKE_TOKEN)
        } finally {
            logs.close()
        }
        noSecret(logs)
    }

    @Test
    fun aRefusalAMemberActionAndALeaveLogNoSecret() = runTest {
        val logs = LogCapture()
        try {
            val env = SharingTestEnv(backgroundScope) { jsonResponse(403, problemBody(403, "FEATURE_NOT_ENTITLED", ""","feature":"sharing.enabled"""")) }
            env.signedIn()
            env.seedOwnerWallet(members = 2)
            val id = assertIs<InviteResult.Queued>(env.service.invite("w1", InvitationDraft(InvitedRole.Editor, address))).invitationId
            env.engine.drain()
            env.service.changeRole("w1", "u2", WalletRole.Viewer)
            env.service.remove("w1", "u2")
            env.service.discard(id)
            val editor = SharingTestEnv(backgroundScope) { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
            editor.signedIn()
            editor.seedOwnerWallet(role = "editor")
            editor.service.leave("w1")
            editor.service.activityPage("w1", null)
        } finally {
            logs.close()
        }
        noSecret(logs)
    }

    @Test
    fun everyTypeThatCarriesASecretPrintsItRedacted() {
        val printed = listOf(
            InvitationLink.Valid(FAKE_TOKEN).toString(),
            InvitationKey(FAKE_TOKEN).toString(),
            TokenRequest(FAKE_TOKEN).toString(),
            InvitationCreateMetaDto(FAKE_LINK).toString(),
            ShareLink(FAKE_LINK).toString(),
            ShareLinks().also { it.offer("i1", FAKE_LINK) }.toString(),
            HeldInvitation().also { it.hold(InvitationLink.Valid(FAKE_TOKEN)) }.toString(),
            InviteEffect.Share(FAKE_LINK).toString(),
            InviteEffect.Copy(FAKE_LINK).toString(),
            MembersEffect.Share(FAKE_LINK).toString(),
            MembersEffect.Copy(FAKE_LINK).toString(),
            InvitationPayload("viewer", address).toString(),
            InvitationDraft(InvitedRole.Viewer, address).toString(),
            invitationDto(invitationJson(email = address)).toString(),
            PlanDemotionNotes().toString(),
        )
        printed.forEach { text ->
            assertFalse(text.contains(FAKE_TOKEN), text)
            assertFalse(text.contains("token=" + FAKE_TOKEN), text)
            assertFalse(text.contains(address), text)
            assertFalse(text.contains("example.com/invite"), text)
        }
    }
}
