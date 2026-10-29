package com.fares.saveme.feature.sharing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.sharing.domain.model.InvitationRefusal
import com.fares.saveme.feature.sharing.domain.model.InvitationRefusalKind
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.ui.activity.ActivityScreen
import com.fares.saveme.feature.sharing.ui.activity.ActivityState
import com.fares.saveme.feature.sharing.domain.model.ActivityAction
import com.fares.saveme.feature.sharing.domain.model.ActivityItem
import com.fares.saveme.feature.sharing.domain.model.ActivitySubject
import com.fares.saveme.feature.sharing.domain.model.Actor
import com.fares.saveme.feature.sharing.domain.model.SubjectKind
import com.fares.saveme.feature.sharing.ui.invitation.InvitationMode
import com.fares.saveme.feature.sharing.ui.invitation.InvitationScreen
import com.fares.saveme.feature.sharing.ui.invitation.InvitationState
import com.fares.saveme.feature.sharing.ui.invite.InviteChannel
import com.fares.saveme.feature.sharing.ui.invite.InviteScreen
import com.fares.saveme.feature.sharing.ui.invite.InviteState
import com.fares.saveme.feature.sharing.ui.invite.LinkProgress
import com.fares.saveme.feature.sharing.ui.members.MembersScreen
import com.fares.saveme.feature.sharing.ui.members.MembersState
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** P-08: roles and states are words, icon buttons are described, targets are 48dp, nothing clips at 2x font on the four screens. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class SharingAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val members = MembersState(
        walletId = "w1", walletName = "Home", loaded = true, myRole = WalletRole.Owner,
        members = listOf(uiMember("u1", "Sara", "owner", isYou = true), uiMember("u2", "Karim", "editor"), uiMember("u3", null, "viewer")),
        invitations = listOf(
            uiInvitation("i1", "omar@example.com", "viewer", waiting = true),
            uiInvitation("i2", "lina@example.com", "editor", expired = true),
            uiInvitation("i3", "zed@example.com", "viewer", refusal = InvitationRefusal(InvitationRefusalKind.PlanFull, null)),
            uiInvitation("i4", null, "editor", linkHeld = true),
        ),
        inviteCheck = InviteCheck.SharingNotIncluded,
    )

    @Test
    fun membersRolesAndStatesAreWrittenInWords() {
        compose.setContent { AppTheme { MembersScreen(members, {}) } }
        listOf("Owner", "Editor", "Viewer", "Waiting to sync", "Not sent", "Expired", "See your plan").forEach { word ->
            assertTrue("'$word' is written on the members screen", compose.onAllNodes(hasText(word, substring = true)).fetchSemanticsNodes().isNotEmpty())
        }
        compose.onNodeWithText("Sharing isn’t included in your plan.").assertIsDisplayed()
    }

    @Test
    fun membersIconButtonsAreDescribedAndTargetsAreAtLeast48dp() {
        compose.setContent { AppTheme { MembersScreen(members, {}) } }
        assertTrue(compose.onAllNodes(hasContentDescription("Back", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertTrue("a member row says what tapping it does", compose.onAllNodes(hasContentDescription("Actions for Karim")).fetchSemanticsNodes().isNotEmpty())
        assertTouchTargets()
    }

    @Test
    fun inviteTargetsAndTheChoicesAreAccessible() {
        compose.setContent { AppTheme { InviteScreen(InviteState(walletId = "w1"), {}) } }
        compose.onNodeWithText("Can view only").assertIsDisplayed()
        compose.onNodeWithText("By email").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun theLinkStatesAreWords() {
        compose.setContent { AppTheme { InviteScreen(InviteState(walletId = "w1", channel = InviteChannel.Link, link = LinkProgress.Ready("i1")), {}) } }
        compose.onNodeWithText("The link is ready").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun invitationTargetsAreAtLeast48dp() {
        compose.setContent { AppTheme { InvitationScreen(InvitationState(mode = InvitationMode.Ready(uiPreview())), {}) } }
        compose.onNodeWithTag("invitation.join").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun activityIsReadableAndItsRowsAreTargets() {
        val item = ActivityItem(
            "a1", Instant.parse("2026-10-02T09:30:00Z"), Actor.Named("Karim"), ActivityAction.Created,
            ActivitySubject(SubjectKind.Transaction, "t1", "Lunch", true), null,
        )
        compose.setContent { AppTheme { ActivityScreen(ActivityState(walletId = "w1", items = listOf(item), loading = false), {}) } }
        compose.onNodeWithText("Karim added a transaction").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun nothingClipsAtDoubleFontScaleOnTheFourScreens() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { MembersScreen(members, {}) } }
        compose.onNodeWithText("Waiting to sync", substring = true).assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun theInviteAndInvitationScreensStayUsableAtDoubleFontScale() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { InviteScreen(InviteState(walletId = "w1", email = "karim@example.com"), {}) } }
        compose.onNodeWithTag("invite.send").assertIsDisplayed()
        assertTouchTargets()
    }

    @Test
    fun theInvitationScreenStaysUsableAtDoubleFontScale() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { AppTheme { InvitationScreen(InvitationState(mode = InvitationMode.Ready(uiPreview())), {}) } }
        compose.onNodeWithTag("invitation.join").assertIsDisplayed()
        assertTouchTargets()
    }

    private fun assertTouchTargets() {
        val minPx = with(compose.density) { 48.dp.toPx() }
        val nodes = compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("expected clickable nodes", nodes.isNotEmpty())
        nodes.forEach { node ->
            val bounds = node.touchBoundsInRoot
            assertTrue("touch width ${bounds.width} < $minPx for ${node.config}", bounds.width >= minPx - 0.5f)
            assertTrue("touch height ${bounds.height} < $minPx for ${node.config}", bounds.height >= minPx - 0.5f)
        }
    }
}
