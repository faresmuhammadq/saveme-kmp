package com.fares.saveme.feature.sharing.ui.members

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppAlertDialogCard
import com.fares.saveme.designsystem.component.AppBottomSheet
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.MemberRow
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.sharing.domain.model.InvitationChannel
import com.fares.saveme.feature.sharing.domain.model.InvitationView
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.domain.model.MemberView
import com.fares.saveme.feature.sharing.ui.explanation
import com.fares.saveme.feature.sharing.ui.isPlanReason
import com.fares.saveme.feature.sharing.ui.roleLabelRes
import com.fares.saveme.feature.sharing.ui.roleWireToMemberRole
import com.fares.saveme.feature.sharing.ui.text
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_discard
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.gate_see_plan
import saveme.shared.generated.resources.invitation_expired
import saveme.shared.generated.resources.invitation_expires
import saveme.shared.generated.resources.invitation_link
import saveme.shared.generated.resources.invitation_link_gone
import saveme.shared.generated.resources.invitation_not_sent
import saveme.shared.generated.resources.invitation_pending
import saveme.shared.generated.resources.invitation_replace_link
import saveme.shared.generated.resources.invitation_waiting
import saveme.shared.generated.resources.invitation_withdraw
import saveme.shared.generated.resources.invitation_withdraw_confirm
import saveme.shared.generated.resources.invitation_withdrawal_failed
import saveme.shared.generated.resources.invite_action
import saveme.shared.generated.resources.invite_copy
import saveme.shared.generated.resources.invite_share
import saveme.shared.generated.resources.leave_action
import saveme.shared.generated.resources.leave_confirm
import saveme.shared.generated.resources.leave_confirm_waiting
import saveme.shared.generated.resources.leave_owner_note
import saveme.shared.generated.resources.member_make_editor
import saveme.shared.generated.resources.member_make_viewer
import saveme.shared.generated.resources.member_remove
import saveme.shared.generated.resources.member_remove_confirm
import saveme.shared.generated.resources.member_unnamed
import saveme.shared.generated.resources.members_alone
import saveme.shared.generated.resources.members_cd_actions
import saveme.shared.generated.resources.members_cd_invitation
import saveme.shared.generated.resources.members_section_invitations
import saveme.shared.generated.resources.members_title
import saveme.shared.generated.resources.plan_note
import kotlin.time.Instant

@Composable
fun MembersScreen(
    state: MembersState,
    onIntent: (MembersIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    AppScreen(
        snackbarHostState = snackbar,
        topBar = {
            AppTopAppBar(
                title = stringResource(Res.string.members_title),
                navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), { onIntent(MembersIntent.Back) }) },
            )
        },
        modifier = Modifier.testTag("members.list"),
    ) {
        if (!state.loaded) {
            Skeleton(Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.xl))
            return@AppScreen
        }
        state.planNote?.takeIf { state.isOwner }?.let { max ->
            InlineAlert(AlertTone.Info, stringResource(Res.string.plan_note, max), Modifier.testTag("members.planNote"))
        }
        MembersSection(state, onIntent)
        InvitationsSection(state, onIntent)
        Footer(state, onIntent)
    }
    Sheets(state, onIntent)
    Confirmations(state, onIntent)
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppTheme.type.title, color = AppTheme.colors.ink, modifier = modifier.semantics { heading() })
}

@Composable
private fun MembersSection(state: MembersState, onIntent: (MembersIntent) -> Unit) {
    SectionTitle(stringResource(Res.string.members_title))
    Column(modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
        state.members.forEach { member ->
            val name = member.displayName ?: stringResource(Res.string.member_unnamed)
            val manageable = state.canManage(member)
            val actionsLabel = stringResource(Res.string.members_cd_actions, name)
            MemberRow(
                name = name,
                initial = name,
                role = roleWireToMemberRole(member.roleWire),
                isYou = member.isYou,
                modifier = Modifier
                    .testTag("members.row.${member.userId}")
                    .then(
                        if (manageable) {
                            Modifier
                                .clickable(role = Role.Button) { onIntent(MembersIntent.MemberClicked(member.userId)) }
                                .semantics { contentDescription = actionsLabel }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
    if (state.isOwner && !state.hasOtherMembers && state.invitations.isEmpty()) {
        Text(stringResource(Res.string.members_alone), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("members.alone"))
    }
}

@Composable
private fun InvitationsSection(state: MembersState, onIntent: (MembersIntent) -> Unit) {
    if (state.invitations.isEmpty()) return
    SectionTitle(stringResource(Res.string.members_section_invitations))
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
        modifier = Modifier.fillMaxWidth(),
    ) {
        state.invitations.forEach { invitation -> InvitationCard(invitation, state, onIntent) }
    }
}

@Composable
internal fun InvitationCard(invitation: InvitationView, state: MembersState, onIntent: (MembersIntent) -> Unit) {
    val headline = invitation.email ?: stringResource(Res.string.invitation_link)
    val role = stringResource(roleLabelRes(invitation.roleWire))
    val status = invitationStatus(invitation, state.timeZoneId)
    val description = stringResource(Res.string.members_cd_invitation, "$headline, $role, $status")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .testTag("members.invitation.${invitation.id}"),
    ) {
        AppListItem(
            headline = headline,
            supporting = "$role · $status",
            modifier = Modifier.semantics { contentDescription = description },
        )
        if (invitation.refusal != null) {
            Text(
                invitation.refusal.text(),
                style = AppTheme.type.caption,
                color = AppTheme.colors.error,
                modifier = Modifier.padding(horizontal = AppTheme.spacing.m).testTag("members.invitation.${invitation.id}.reason"),
            )
        }
        if (state.isOwner) InvitationActions(invitation, onIntent)
    }
}

@Composable
private fun InvitationActions(invitation: InvitationView, onIntent: (MembersIntent) -> Unit) {
    val id = invitation.id
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.xs),
    ) {
        when {
            invitation.refusal != null -> {
                if (invitation.refusal.canRetryUnchanged) {
                    AppTextButton(stringResource(Res.string.action_retry), { onIntent(MembersIntent.TryAgainClicked(id)) }, Modifier.testTag("members.invitation.$id.tryAgain"))
                }
                AppTextButton(stringResource(Res.string.action_discard), { onIntent(MembersIntent.DiscardClicked(id)) }, Modifier.testTag("members.invitation.$id.discard"))
            }
            invitation.withdrawalRefused -> {
                AppTextButton(stringResource(Res.string.action_retry), { onIntent(MembersIntent.TryAgainClicked(id)) }, Modifier.testTag("members.invitation.$id.tryAgain"))
                AppTextButton(stringResource(Res.string.action_discard), { onIntent(MembersIntent.DiscardClicked(id)) }, Modifier.testTag("members.invitation.$id.discard"))
            }
            else -> {
                if (invitation.channel == InvitationChannel.Link && !invitation.expired) {
                    if (invitation.linkHeld) {
                        AppTextButton(stringResource(Res.string.invite_share), { onIntent(MembersIntent.ShareClicked(id)) }, Modifier.testTag("members.invitation.$id.share"))
                        AppTextButton(stringResource(Res.string.invite_copy), { onIntent(MembersIntent.CopyClicked(id)) }, Modifier.testTag("members.invitation.$id.copy"))
                    } else if (!invitation.waiting) {
                        AppTextButton(stringResource(Res.string.invitation_replace_link), { onIntent(MembersIntent.ReplaceLinkClicked(id)) }, Modifier.testTag("members.invitation.$id.replace"))
                    }
                }
                AppTextButton(stringResource(Res.string.invitation_withdraw), { onIntent(MembersIntent.WithdrawClicked(id)) }, Modifier.testTag("members.invitation.$id.withdraw"))
            }
        }
    }
    if (invitation.channel == InvitationChannel.Link && !invitation.linkHeld && !invitation.waiting && invitation.refusal == null && !invitation.expired && !invitation.withdrawalRefused) {
        Text(
            stringResource(Res.string.invitation_link_gone),
            style = AppTheme.type.caption,
            color = AppTheme.colors.caption,
            modifier = Modifier.padding(horizontal = AppTheme.spacing.m, vertical = AppTheme.spacing.xxs).testTag("members.invitation.${invitation.id}.gone"),
        )
    }
}

/** "Waiting to sync", "Not sent", "Expired" or "Pending · Expires <date>" in words (P-08.3). */
@Composable
private fun invitationStatus(invitation: InvitationView, timeZoneId: String): String = when {
    invitation.refusal != null -> stringResource(Res.string.invitation_not_sent)
    invitation.withdrawalRefused -> stringResource(Res.string.invitation_withdrawal_failed)
    invitation.expired -> stringResource(Res.string.invitation_expired)
    invitation.waiting || invitation.expiresAt == null -> stringResource(Res.string.invitation_waiting)
    else -> stringResource(Res.string.invitation_pending) + " · " + stringResource(Res.string.invitation_expires, formatDate(invitation.expiresAt, timeZoneId))
}

@Composable
private fun Footer(state: MembersState, onIntent: (MembersIntent) -> Unit) {
    if (state.canInvite) {
        val explanation = state.inviteCheck.explanation()
        PrimaryButton(
            stringResource(Res.string.invite_action),
            { onIntent(MembersIntent.InviteClicked) },
            Modifier.testTag("members.invite"),
            enabled = state.inviteCheck == InviteCheck.Allowed || state.inviteCheck == InviteCheck.NotVerified,
        )
        if (explanation != null) {
            InlineAlert(
                AlertTone.Info, explanation, Modifier.testTag("members.inviteReason"),
                actionLabel = if (state.inviteCheck.isPlanReason) stringResource(Res.string.gate_see_plan) else null,
                onAction = if (state.inviteCheck.isPlanReason) ({ onIntent(MembersIntent.SeePlanClicked) }) else null,
            )
        }
    }
    if (state.canLeave) {
        DestructiveButton(stringResource(Res.string.leave_action), { onIntent(MembersIntent.LeaveClicked) }, Modifier.testTag("members.leave"), enabled = !state.busy)
    } else if (state.isOwner && state.hasOtherMembers) {
        Text(stringResource(Res.string.leave_owner_note), style = AppTheme.type.caption, color = AppTheme.colors.caption, modifier = Modifier.testTag("members.leaveNote"))
    }
}

@Composable
private fun Sheets(state: MembersState, onIntent: (MembersIntent) -> Unit) {
    val member = state.sheetFor?.let { id -> state.members.firstOrNull { it.userId == id } } ?: return
    AppBottomSheet(
        onDismiss = { onIntent(MembersIntent.SheetDismissed) },
        title = member.displayName ?: stringResource(Res.string.member_unnamed),
    ) { MemberActions(member, onIntent) }
}

/** What the owner may do to a member (the sheet's surface, also shown by the screenshot tests). */
@Composable
internal fun MemberActions(member: MemberView, onIntent: (MembersIntent) -> Unit) {
    if (member.role != WalletRole.Editor) {
        AppListItem(
            stringResource(Res.string.member_make_editor), Modifier.testTag("members.sheet.editor"),
            onClick = { onIntent(MembersIntent.ChangeRoleClicked(member.userId, WalletRole.Editor)) },
        )
    }
    if (member.role != WalletRole.Viewer) {
        AppListItem(
            stringResource(Res.string.member_make_viewer), Modifier.testTag("members.sheet.viewer"),
            onClick = { onIntent(MembersIntent.ChangeRoleClicked(member.userId, WalletRole.Viewer)) },
        )
    }
    AppListItem(
        stringResource(Res.string.member_remove), Modifier.testTag("members.sheet.remove"),
        headlineColor = AppTheme.colors.error,
        onClick = { onIntent(MembersIntent.RemoveClicked(member.userId)) },
    )
}

@Composable
private fun Confirmations(state: MembersState, onIntent: (MembersIntent) -> Unit) {
    if (state.confirm == null) return
    Dialog(onDismissRequest = { if (!state.busy) onIntent(MembersIntent.ConfirmDismissed) }) { ConfirmCard(state, onIntent) }
}

/** The confirmation dialog's surface (also shown by the screenshot tests, which cannot see a dialog window). */
@Composable
internal fun ConfirmCard(state: MembersState, onIntent: (MembersIntent) -> Unit) {
    val confirm = state.confirm ?: return
    val cancel = stringResource(Res.string.action_cancel)
    val confirmTag = Modifier.testTag("members.confirm")
    val cancelTag = Modifier.testTag("members.cancel")
    val dismiss = { onIntent(MembersIntent.ConfirmDismissed) }
    when (confirm) {
        is MembersConfirm.Remove -> AppAlertDialogCard(
            title = stringResource(Res.string.member_remove),
            text = stringResource(Res.string.member_remove_confirm, confirm.name ?: stringResource(Res.string.member_unnamed)),
            confirmLabel = stringResource(Res.string.member_remove),
            onConfirm = { onIntent(MembersIntent.ConfirmClicked) },
            dismissLabel = cancel, onDismiss = dismiss, destructive = true,
            confirmLoading = state.busy, confirmModifier = confirmTag, dismissModifier = cancelTag,
        )
        is MembersConfirm.Withdraw -> AppAlertDialogCard(
            title = stringResource(Res.string.invitation_withdraw),
            text = stringResource(Res.string.invitation_withdraw_confirm),
            confirmLabel = stringResource(Res.string.invitation_withdraw),
            onConfirm = { onIntent(MembersIntent.ConfirmClicked) },
            dismissLabel = cancel, onDismiss = dismiss, destructive = true,
            confirmModifier = confirmTag, dismissModifier = cancelTag,
        )
        is MembersConfirm.Leave -> AppAlertDialogCard(
            title = stringResource(Res.string.leave_action),
            text = stringResource(Res.string.leave_confirm, state.walletName) +
                if (confirm.waitingCount > 0) " " + stringResource(Res.string.leave_confirm_waiting, confirm.waitingCount) else "",
            confirmLabel = stringResource(Res.string.leave_action),
            onConfirm = { onIntent(MembersIntent.ConfirmClicked) },
            dismissLabel = cancel, onDismiss = dismiss, destructive = true,
            confirmLoading = state.busy, confirmModifier = confirmTag, dismissModifier = cancelTag,
        )
    }
}

@Preview
@Composable
private fun MembersScreenPreview() {
    AppTheme {
        MembersScreen(
            MembersState(
                walletId = "w1", walletName = "Home", loaded = true, myRole = WalletRole.Owner,
                members = listOf(MemberView("u1", "Sara", "owner", 1, true), MemberView("u2", "Karim", "editor", 2, false)),
                invitations = listOf(
                    InvitationView(
                        "i1", "w1", "viewer", InvitationChannel.Email, "omar@example.com", Instant.parse("2026-10-09T10:00:00Z"),
                        waiting = false, refusal = null, withdrawalRefused = false, expired = false, linkHeld = false,
                    ),
                ),
            ),
            {},
        )
    }
}
