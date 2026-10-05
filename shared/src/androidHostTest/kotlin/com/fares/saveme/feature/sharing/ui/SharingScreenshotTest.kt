package com.fares.saveme.feature.sharing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.ui.home.HomeScreen
import com.fares.saveme.feature.auth.ui.home.HomeState
import com.fares.saveme.feature.sharing.domain.model.ActivityAction
import com.fares.saveme.feature.sharing.domain.model.ActivityItem
import com.fares.saveme.feature.sharing.domain.model.ActivitySubject
import com.fares.saveme.feature.sharing.domain.model.Actor
import com.fares.saveme.feature.sharing.domain.model.InvitationRefusal
import com.fares.saveme.feature.sharing.domain.model.InvitationRefusalKind
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.domain.model.SubjectKind
import com.fares.saveme.feature.sharing.ui.activity.ActivityError
import com.fares.saveme.feature.sharing.ui.activity.ActivityScreen
import com.fares.saveme.feature.sharing.ui.activity.ActivityState
import com.fares.saveme.feature.sharing.ui.invitation.InvitationMode
import com.fares.saveme.feature.sharing.ui.invitation.InvitationScreen
import com.fares.saveme.feature.sharing.ui.invitation.InvitationState
import com.fares.saveme.feature.sharing.ui.invite.InviteChannel
import com.fares.saveme.feature.sharing.ui.invite.InviteScreen
import com.fares.saveme.feature.sharing.ui.invite.InviteState
import com.fares.saveme.feature.sharing.ui.invite.LinkProgress
import com.fares.saveme.feature.sharing.ui.invite.SupersedeCard
import com.fares.saveme.feature.sharing.ui.members.ConfirmCard
import com.fares.saveme.feature.sharing.ui.members.MemberActions
import com.fares.saveme.feature.sharing.ui.members.MembersConfirm
import com.fares.saveme.feature.sharing.ui.members.MembersScreen
import com.fares.saveme.feature.sharing.ui.members.MembersState
import com.fares.saveme.feature.transactions.ui.list.TransactionsScreen
import com.fares.saveme.feature.transactions.ui.list.TransactionsState
import com.fares.saveme.feature.wallets.domain.model.Member
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.ui.detail.WalletDetailScreen
import com.fares.saveme.feature.wallets.ui.detail.WalletDetailState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/**
 * The sharing screens' states in English LTR, Arabic RTL and English at font scale 2.0 (P-14.4, spec 013 FR-035). Every date is
 * part of a state, so no golden depends on the real date.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1900dp")
class SharingScreenshotTest(
    private val name: String,
    private val screen: @Composable () -> Unit,
) {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(config: String, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AppTheme { screen() }
            }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/screen_sharing_${name}_$config.png")
    }

    @Test
    fun englishLtr() = capture("en")

    @Test
    @Config(qualifiers = "+ar-rEG")
    fun arabicRtl() = capture("ar", rtl = true)

    @Test
    fun englishFontScale2() {
        RuntimeEnvironment.setFontScale(2f)
        capture("en_fs2")
    }

    companion object {
        private val owner = uiMember("u1", "Sara", "owner", isYou = true)
        private val editor = uiMember("u2", "Karim", "editor", version = 2)
        private val viewer = uiMember("u3", "Mona", "viewer", version = 3)

        private fun membersState(
            role: WalletRole = WalletRole.Owner,
            members: List<com.fares.saveme.feature.sharing.domain.model.MemberView> = listOf(owner, editor, viewer),
            invitations: List<com.fares.saveme.feature.sharing.domain.model.InvitationView> = emptyList(),
            check: InviteCheck = InviteCheck.Allowed,
            planNote: Int? = null,
        ) = MembersState(
            walletId = "w1", walletName = "Home", loaded = true, myRole = role,
            members = if (role == WalletRole.Owner) members else members.map { it.copy(isYou = it.userId == "u2") },
            invitations = invitations, inviteCheck = check, planNote = planNote,
        )

        private val emailInvitation = uiInvitation("i1", "omar@example.com", "viewer")

        private fun dialog(state: MembersState): @Composable () -> Unit = {
            Column(Modifier.background(AppTheme.colors.background).padding(16.dp)) { ConfirmCard(state, {}) }
        }

        private fun item(id: String, actor: Actor, action: ActivityAction, kind: SubjectKind, name: String?, role: String? = null, at: String = "2026-10-02T09:30:00Z", onPhone: Boolean = true) =
            ActivityItem(id, Instant.parse(at), actor, action, ActivitySubject(kind, "s-$id", name, onPhone), role)

        private val activityItems = listOf(
            item("a1", Actor.Named("Karim"), ActivityAction.Created, SubjectKind.Transaction, "Lunch", at = "2026-10-02T09:30:00Z"),
            item("a2", Actor.FormerMember, ActivityAction.Edited, SubjectKind.Transaction, null, at = "2026-10-02T08:10:00Z", onPhone = false),
            item("a3", Actor.Automatic, ActivityAction.Confirmed, SubjectKind.Occurrence, "Rent", at = "2026-10-01T06:00:00Z"),
            item("a4", Actor.Automatic, ActivityAction.RoleLimitedByPlan, SubjectKind.Member, "Mona", role = "viewer", at = "2026-10-01T05:00:00Z"),
            item("a5", Actor.Named("Sara"), ActivityAction.RoleChanged, SubjectKind.Member, "Karim", role = "editor", at = "2026-09-30T12:00:00Z"),
            item("a6", Actor.Named("Karim"), ActivityAction.Joined, SubjectKind.Member, "Karim", role = "editor", at = "2026-09-30T11:00:00Z"),
            item("a7", Actor.Named("Sara"), ActivityAction.Invited, SubjectKind.Invitation, null, role = "editor", at = "2026-09-30T10:00:00Z", onPhone = false),
        )

        private fun detail(role: WalletRole = WalletRole.Owner, members: List<Member> = emptyList(), pending: Int = 0, planLimited: Boolean = false) =
            WalletDetailState(
                walletId = "w1", wallet = uiWalletView(role), members = members, pendingInvitations = pending, planLimited = planLimited,
            )

        private val detailMembers = listOf(Member("u1", "Sara", WalletRole.Owner, true), Member("u2", "Karim", WalletRole.Editor, false))

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Array<Any>> = listOf(
            arrayOf("members_owner_pending", @Composable {
                MembersScreen(membersState(invitations = listOf(emailInvitation, uiInvitation("i2", "lina@example.com", "editor", waiting = true))), {})
            }),
            arrayOf("members_owner_alone", @Composable { MembersScreen(membersState(members = listOf(owner)), {}) }),
            arrayOf("members_editor", @Composable { MembersScreen(membersState(WalletRole.Editor), {}) }),
            arrayOf("members_invite_disabled_plan", @Composable {
                MembersScreen(membersState(check = InviteCheck.SharingNotIncluded), {})
            }),
            arrayOf("members_invite_full", @Composable {
                MembersScreen(membersState(check = InviteCheck.Full(3), planNote = 2), {})
            }),
            arrayOf("members_invitation_states", @Composable {
                MembersScreen(
                    membersState(
                        invitations = listOf(
                            uiInvitation("i1", "omar@example.com", expired = true),
                            uiInvitation("i2", "lina@example.com", refusal = InvitationRefusal(InvitationRefusalKind.PlanFull, null)),
                            uiInvitation("i3", null, "viewer", linkHeld = true),
                            uiInvitation("i4", null, "editor", linkHeld = false),
                        ),
                    ),
                    {},
                )
            }),
            arrayOf("members_sheet", @Composable {
                Column(Modifier.background(AppTheme.colors.surface).padding(16.dp)) { MemberActions(editor, {}) }
            }),
            arrayOf("members_confirm_remove", dialog(membersState().copy(confirm = MembersConfirm.Remove("u2", "Karim")))),
            arrayOf("members_confirm_withdraw", dialog(membersState(invitations = listOf(emailInvitation)).copy(confirm = MembersConfirm.Withdraw("i1")))),
            arrayOf("members_leave_editor", @Composable { MembersScreen(membersState(WalletRole.Editor), {}) }),
            arrayOf("members_confirm_leave", dialog(membersState(WalletRole.Editor).copy(confirm = MembersConfirm.Leave(2)))),
            arrayOf("invite_email", @Composable { InviteScreen(InviteState(walletId = "w1", email = "karim@example.com"), {}) }),
            arrayOf("invite_email_problem", @Composable {
                InviteScreen(
                    InviteState(walletId = "w1", email = "karim", emailError = com.fares.saveme.feature.sharing.domain.model.EmailProblem.Invalid),
                    {},
                )
            }),
            arrayOf("invite_not_included", @Composable { InviteScreen(InviteState(walletId = "w1", check = InviteCheck.SharingNotIncluded), {}) }),
            arrayOf("invite_supersede", @Composable {
                Column(Modifier.background(AppTheme.colors.background).padding(16.dp)) {
                    SupersedeCard(InviteState(walletId = "w1", email = "karim@example.com", confirmSupersede = true), {})
                }
            }),
            arrayOf("invite_link_waiting", @Composable {
                InviteScreen(InviteState(walletId = "w1", channel = InviteChannel.Link, link = LinkProgress.Waiting("i1")), {})
            }),
            arrayOf("invite_link_waiting_offline", @Composable {
                InviteScreen(InviteState(walletId = "w1", channel = InviteChannel.Link, link = LinkProgress.WaitingOffline("i1")), {})
            }),
            arrayOf("invite_link_ready", @Composable {
                InviteScreen(InviteState(walletId = "w1", channel = InviteChannel.Link, link = LinkProgress.Ready("i1")), {})
            }),
            arrayOf("invitation_ready_editor", @Composable {
                InvitationScreen(InvitationState(mode = InvitationMode.Ready(uiPreview())), {})
            }),
            arrayOf("invitation_ready_unnamed", @Composable {
                InvitationScreen(InvitationState(mode = InvitationMode.Ready(uiPreview(inviter = null, role = "viewer"))), {})
            }),
            arrayOf("invitation_signed_out", @Composable { InvitationScreen(InvitationState(mode = InvitationMode.SignedOut), {}) }),
            arrayOf("invitation_already_member", @Composable {
                InvitationScreen(InvitationState(mode = InvitationMode.AlreadyMember("w1", "Home")), {})
            }),
            arrayOf("invitation_unavailable", @Composable { InvitationScreen(InvitationState(mode = InvitationMode.Unavailable), {}) }),
            arrayOf("invitation_offline", @Composable { InvitationScreen(InvitationState(mode = InvitationMode.Offline), {}) }),
            arrayOf("invitation_owner_plan_full", @Composable { InvitationScreen(InvitationState(mode = InvitationMode.OwnerPlanFull), {}) }),
            arrayOf("activity_mixed", @Composable { ActivityScreen(ActivityState(walletId = "w1", items = activityItems, loading = false), {}) }),
            arrayOf("activity_offline", @Composable {
                ActivityScreen(ActivityState(walletId = "w1", loading = false, error = ActivityError.Offline), {})
            }),
            arrayOf("activity_empty", @Composable { ActivityScreen(ActivityState(walletId = "w1", loading = false), {}) }),
            arrayOf("wallet_detail_owner_members", @Composable { WalletDetailScreen(detail(members = detailMembers, pending = 2), {}) }),
            arrayOf("wallet_detail_joining", @Composable {
                WalletDetailScreen(WalletDetailState(walletId = "w1", joining = true), {})
            }),
            arrayOf("wallet_detail_joining_failed", @Composable {
                WalletDetailScreen(WalletDetailState(walletId = "w1", joining = true, joiningFailed = true), {})
            }),
            arrayOf("wallet_detail_viewer_note", @Composable { WalletDetailScreen(detail(WalletRole.Viewer, detailMembers), {}) }),
            arrayOf("wallet_detail_viewer_note_plan", @Composable { WalletDetailScreen(detail(WalletRole.Viewer, detailMembers, planLimited = true), {}) }),
            arrayOf("transactions_viewer_note", @Composable {
                TransactionsScreen(
                    TransactionsState(
                        walletId = "w1", walletName = "Home", currency = "EGP", balanceMinor = 250_000, loading = false, canAdd = false,
                        updatedAt = Instant.parse("2026-10-02T09:00:00Z"), myRole = WalletRole.Viewer,
                    ),
                    {},
                )
            }),
            arrayOf("home_invitation_card", @Composable { HomeScreen(HomeState(email = "sara@example.com", invitationHeld = true), {}) }),
        )
    }
}
