package com.fares.saveme.feature.sharing.ui.members

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppAlertDialogCard
import com.fares.saveme.designsystem.component.AppBottomSheet
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.ReportsExpandMore
import com.fares.saveme.designsystem.icon.SharingPersonAdd
import com.fares.saveme.designsystem.icon.SharingSchedule
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.sharing.domain.model.InvitationChannel
import com.fares.saveme.feature.sharing.domain.model.InvitationView
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.domain.model.MemberView
import com.fares.saveme.feature.sharing.ui.calmColorAt
import com.fares.saveme.feature.sharing.ui.shortDate
import com.fares.saveme.feature.sharing.ui.explanation
import com.fares.saveme.feature.sharing.ui.isPlanReason
import com.fares.saveme.feature.sharing.ui.roleLabelRes
import com.fares.saveme.feature.sharing.ui.text
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_discard
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.calm_shr_edit_places
import saveme.shared.generated.resources.calm_shr_invitation_actions
import saveme.shared.generated.resources.calm_shr_invited_as
import saveme.shared.generated.resources.calm_shr_joined
import saveme.shared.generated.resources.calm_shr_transfer_to_leave
import saveme.shared.generated.resources.calm_shr_places_cd
import saveme.shared.generated.resources.calm_shr_places_of
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
import saveme.shared.generated.resources.member_make_editor
import saveme.shared.generated.resources.member_make_viewer
import saveme.shared.generated.resources.member_remove
import saveme.shared.generated.resources.member_remove_confirm
import saveme.shared.generated.resources.member_unnamed
import saveme.shared.generated.resources.member_you
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
    CalmScreen(
        title = stringResource(Res.string.members_title),
        eyebrow = state.walletName.takeIf { it.isNotEmpty() },
        onBack = { onIntent(MembersIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        snackbarHostState = snackbar,
        scrollable = false,
        actions = { if (state.loaded && state.canInvite) InviteButton(state, onIntent) },
        modifier = Modifier.testTag("members.list"),
    ) {
        if (!state.loaded) {
            Skeleton(Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.xl))
            return@CalmScreen
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            state.planNote?.takeIf { state.isOwner }?.let { max -> PlanNote(state, max) }
            MembersSection(state, onIntent)
            InvitationsSection(state, onIntent)
            InviteReason(state, onIntent)
        }
        Footer(state, onIntent)
    }
    Sheets(state, onIntent)
    Confirmations(state, onIntent)
}

/** The header pill that opens the Invite screen; it stays visible but inert while the plan or verification blocks inviting. */
@Composable
private fun InviteButton(state: MembersState, onIntent: (MembersIntent) -> Unit) {
    val enabled = state.inviteCheck == InviteCheck.Allowed || state.inviteCheck == InviteCheck.NotVerified
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = AppTheme.sizes.touchTarget)
            .testTag("members.invite")
            .clickable(enabled = enabled, role = Role.Button) { onIntent(MembersIntent.InviteClicked) },
    ) {
        val container = if (enabled) AppTheme.colors.primary else AppTheme.colors.sunken2
        val content = if (enabled) AppTheme.colors.onPrimary else AppTheme.colors.outline
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(44.dp)
                .background(container, AppTheme.shapes.pill)
                .padding(start = 12.dp, end = 16.dp),
        ) {
            Icon(AppIcons.SharingPersonAdd, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Text(stringResource(Res.string.invite_action), style = AppTheme.type.label.copy(fontSize = 14.sp), color = content, maxLines = 1)
        }
    }
}

/** How many of the plan's edit places the wallet uses: the owner and the editors; viewers are free. */
@Composable
private fun PlanNote(state: MembersState, max: Int) {
    val used = state.members.count { it.role != WalletRole.Viewer }
    val places = max.coerceIn(1, MAX_PLACES_SHOWN)
    val filled = used.coerceAtMost(places)
    val description = stringResource(Res.string.calm_shr_places_cd, used, max)
    CalmCard(padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp), modifier = Modifier.testTag("members.planNote")) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(Res.string.calm_shr_edit_places), style = AppTheme.type.itemTitle, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
            Text(stringResource(Res.string.calm_shr_places_of, used, max), style = AppTheme.type.label, color = AppTheme.colors.ink2)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clearAndSetSemantics { contentDescription = description },
        ) {
            repeat(places) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(if (index < filled) AppTheme.colors.primary else AppTheme.colors.sunken2, RoundedCornerShape(9.dp)),
                )
            }
        }
        Text(
            stringResource(Res.string.plan_note, max),
            style = AppTheme.type.caption,
            color = AppTheme.colors.ink2,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private const val MAX_PLACES_SHOWN = 10

@Composable
private fun MemberAvatar(initial: String, color: Color) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).background(color, CircleShape)) {
        Text(initial.take(1).uppercase(), style = AppTheme.type.itemTitle.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
    }
}

@Composable
private fun MembersSection(state: MembersState, onIntent: (MembersIntent) -> Unit) {
    state.members.forEach { member ->
        val name = member.displayName ?: stringResource(Res.string.member_unnamed)
        val manageable = state.canManage(member)
        val actionsLabel = stringResource(Res.string.members_cd_actions, name)
        val role = stringResource(roleLabelRes(member.roleWire))
        val color = if (member.isYou) AppTheme.colors.primary else calmColorAt(state.members.filter { !it.isYou }.indexOfFirst { it.userId == member.userId })
        CalmCard(
            radius = 20.dp,
            padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            modifier = Modifier
                .testTag("members.row.${member.userId}")
                .then(
                    if (manageable) {
                        Modifier.clickable(role = Role.Button) { onIntent(MembersIntent.MemberClicked(member.userId)) }.semantics { contentDescription = actionsLabel }
                    } else {
                        Modifier.semantics(mergeDescendants = true) {}
                    },
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MemberAvatar(name, color)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (member.isYou) stringResource(Res.string.member_you) else name,
                        style = AppTheme.type.itemTitle,
                        color = AppTheme.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = if (member.isYou) {
                        member.email ?: member.displayName
                    } else {
                        member.joinedAt?.let { stringResource(Res.string.calm_shr_joined, shortDate(it, state.timeZoneId)) }
                    }
                    if (subtitle != null) {
                        Text(subtitle, style = AppTheme.type.caption, color = AppTheme.colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (manageable) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(34.dp).padding(start = 12.dp, end = 8.dp),
                    ) {
                        Text(role, style = AppTheme.type.itemTitle.copy(fontWeight = FontWeight.Medium), color = AppTheme.colors.ink, maxLines = 1)
                        Icon(AppIcons.ReportsExpandMore, contentDescription = null, tint = AppTheme.colors.ink, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Text(role, style = AppTheme.type.label, color = AppTheme.colors.ink2, textAlign = TextAlign.End, modifier = Modifier.padding(end = 6.dp))
                }
            }
        }
    }
    if (state.isOwner && !state.hasOtherMembers && state.invitations.isEmpty()) {
        Text(stringResource(Res.string.members_alone), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("members.alone"))
    }
}

@Composable
private fun InvitationsSection(state: MembersState, onIntent: (MembersIntent) -> Unit) {
    if (state.invitations.isEmpty()) return
    state.invitations.forEach { invitation -> InvitationCard(invitation, state, onIntent) }
}

@Composable
internal fun InvitationCard(invitation: InvitationView, state: MembersState, onIntent: (MembersIntent) -> Unit) {
    val headline = invitation.email ?: stringResource(Res.string.invitation_link)
    val role = stringResource(roleLabelRes(invitation.roleWire))
    val status = invitationStatus(invitation, state.timeZoneId, short = true)
    val supporting = stringResource(Res.string.calm_shr_invited_as, role) + " · " + status
    val description = stringResource(Res.string.members_cd_invitation, "$headline, $role, ${invitationStatus(invitation, state.timeZoneId)}")
    val line = AppTheme.colors.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("members.invitation.${invitation.id}")
            .drawBehind {
                drawRoundRect(
                    line,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = description },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).background(AppTheme.colors.sunken, CircleShape)) {
                    Icon(AppIcons.SharingSchedule, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(headline, style = AppTheme.type.itemTitle, color = AppTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(supporting, style = AppTheme.type.caption, color = AppTheme.colors.ink2)
                }
            }
            if (state.isOwner) InvitationMenu(invitation, onIntent)
        }
        if (invitation.refusal != null) {
            Text(
                invitation.refusal.text(),
                style = AppTheme.type.caption,
                color = AppTheme.colors.error,
                modifier = Modifier.padding(top = 6.dp).testTag("members.invitation.${invitation.id}.reason"),
            )
        }
        if (invitation.channel == InvitationChannel.Link && !invitation.linkHeld && !invitation.waiting && invitation.refusal == null && !invitation.expired && !invitation.withdrawalRefused) {
            Text(
                stringResource(Res.string.invitation_link_gone),
                style = AppTheme.type.caption,
                color = AppTheme.colors.caption,
                modifier = Modifier.padding(top = 6.dp).testTag("members.invitation.${invitation.id}.gone"),
            )
        }
    }
}

/** The three-dots button of a pending invitation: share, copy, replace, withdraw, retry or discard, whichever apply. */
@Composable
private fun InvitationMenu(invitation: InvitationView, onIntent: (MembersIntent) -> Unit) {
    val id = invitation.id
    val actionsLabel = stringResource(Res.string.calm_shr_invitation_actions)
    val retry = stringResource(Res.string.action_retry)
    val discard = stringResource(Res.string.action_discard)
    val items = buildList<Triple<String, String, () -> Unit>> {
        when {
            invitation.refusal != null -> {
                if (invitation.refusal.canRetryUnchanged) add(Triple(retry, "tryAgain") { onIntent(MembersIntent.TryAgainClicked(id)) })
                add(Triple(discard, "discard") { onIntent(MembersIntent.DiscardClicked(id)) })
            }
            invitation.withdrawalRefused -> {
                add(Triple(retry, "tryAgain") { onIntent(MembersIntent.TryAgainClicked(id)) })
                add(Triple(discard, "discard") { onIntent(MembersIntent.DiscardClicked(id)) })
            }
            else -> {
                if (invitation.channel == InvitationChannel.Link && !invitation.expired) {
                    if (invitation.linkHeld) {
                        add(Triple(stringResource(Res.string.invite_share), "share") { onIntent(MembersIntent.ShareClicked(id)) })
                        add(Triple(stringResource(Res.string.invite_copy), "copy") { onIntent(MembersIntent.CopyClicked(id)) })
                    } else if (!invitation.waiting) {
                        add(Triple(stringResource(Res.string.invitation_replace_link), "replace") { onIntent(MembersIntent.ReplaceLinkClicked(id)) })
                    }
                }
                add(Triple(stringResource(Res.string.invitation_withdraw), "withdraw") { onIntent(MembersIntent.WithdrawClicked(id)) })
            }
        }
    }
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .testTag("members.invitation.$id.menu")
                .semantics { contentDescription = actionsLabel }
                .clickable(role = Role.Button) { open = true },
        ) {
            Icon(AppIcons.More, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (label, key, action) ->
                DropdownMenuItem(
                    text = { Text(label, style = AppTheme.type.label) },
                    onClick = { open = false; action() },
                    modifier = Modifier.testTag("members.invitation.$id.$key"),
                )
            }
        }
    }
}

/** "Waiting to sync", "Not sent", "Expired" or "Pending · Expires <date>" in words (P-08.3); [short] drops "Pending" ("Expires <date>"). */
@Composable
private fun invitationStatus(invitation: InvitationView, timeZoneId: String, short: Boolean = false): String = when {
    invitation.refusal != null -> stringResource(Res.string.invitation_not_sent)
    invitation.withdrawalRefused -> stringResource(Res.string.invitation_withdrawal_failed)
    invitation.expired -> stringResource(Res.string.invitation_expired)
    invitation.waiting || invitation.expiresAt == null -> stringResource(Res.string.invitation_waiting)
    short -> stringResource(Res.string.invitation_expires, shortDate(invitation.expiresAt, timeZoneId))
    else -> stringResource(Res.string.invitation_pending) + " · " + stringResource(Res.string.invitation_expires, shortDate(invitation.expiresAt, timeZoneId))
}

@Composable
private fun InviteReason(state: MembersState, onIntent: (MembersIntent) -> Unit) {
    if (!state.canInvite) return
    val explanation = state.inviteCheck.explanation() ?: return
    InlineAlert(
        AlertTone.Info, explanation, Modifier.testTag("members.inviteReason"),
        actionLabel = if (state.inviteCheck.isPlanReason) stringResource(Res.string.gate_see_plan) else null,
        onAction = if (state.inviteCheck.isPlanReason) ({ onIntent(MembersIntent.SeePlanClicked) }) else null,
    )
}

@Composable
private fun Footer(state: MembersState, onIntent: (MembersIntent) -> Unit) {
    if (state.canLeave) {
        DestructiveButton(stringResource(Res.string.leave_action), { onIntent(MembersIntent.LeaveClicked) }, Modifier.testTag("members.leave"), enabled = !state.busy)
    } else if (state.isOwner && state.hasOtherMembers) {
        Text(
            stringResource(Res.string.calm_shr_transfer_to_leave),
            style = AppTheme.type.label.copy(fontSize = 14.sp),
            color = AppTheme.colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp).testTag("members.leaveNote"),
        )
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
