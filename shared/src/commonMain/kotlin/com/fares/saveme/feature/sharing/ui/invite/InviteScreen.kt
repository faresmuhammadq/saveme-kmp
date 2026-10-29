package com.fares.saveme.feature.sharing.ui.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppAlertDialogCard
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.CalmActionBar
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmChip
import com.fares.saveme.designsystem.component.CalmCircleButton
import com.fares.saveme.designsystem.component.CalmIconTile
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.CalmSectionTitle
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.SharingEditNote
import com.fares.saveme.designsystem.icon.SharingCopy
import com.fares.saveme.designsystem.icon.SharingLink
import com.fares.saveme.designsystem.icon.SharingShare
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.sharing.domain.model.EmailProblem
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.domain.model.InvitedRole
import com.fares.saveme.feature.sharing.ui.CalmIconButton
import com.fares.saveme.feature.sharing.ui.CalmOutlineButton
import com.fares.saveme.feature.sharing.ui.explanation
import com.fares.saveme.feature.sharing.ui.isPlanReason
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.calm_shr_invite_to
import saveme.shared.generated.resources.calm_shr_link_hint_editor
import saveme.shared.generated.resources.calm_shr_link_hint_viewer
import saveme.shared.generated.resources.calm_shr_link_pill
import saveme.shared.generated.resources.calm_shr_link_ready_title
import saveme.shared.generated.resources.calm_shr_what_can
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.gate_see_plan
import saveme.shared.generated.resources.invite_by_email
import saveme.shared.generated.resources.invite_by_link
import saveme.shared.generated.resources.invite_copy
import saveme.shared.generated.resources.invite_create_link
import saveme.shared.generated.resources.invite_done
import saveme.shared.generated.resources.invite_email_invalid
import saveme.shared.generated.resources.invite_email_label
import saveme.shared.generated.resources.invite_link_wait
import saveme.shared.generated.resources.invite_link_wait_offline
import saveme.shared.generated.resources.invite_not_owner
import saveme.shared.generated.resources.invite_own_address
import saveme.shared.generated.resources.invite_role_editor_hint
import saveme.shared.generated.resources.invite_role_viewer_hint
import saveme.shared.generated.resources.invite_send
import saveme.shared.generated.resources.invite_share
import saveme.shared.generated.resources.invite_supersede
import saveme.shared.generated.resources.invite_supersede_confirm
import saveme.shared.generated.resources.invite_title
import saveme.shared.generated.resources.invite_wallet_archived
import saveme.shared.generated.resources.role_editor
import saveme.shared.generated.resources.role_viewer

@Composable
fun InviteScreen(
    state: InviteState,
    onIntent: (InviteIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val title = if (state.walletName.isNotEmpty()) stringResource(Res.string.calm_shr_invite_to, state.walletName) else stringResource(Res.string.invite_title)
    CalmScreen(
        title = null,
        snackbarHostState = snackbar,
        spacing = 14.dp,
        bottomBar = { ActionBar(state, onIntent) },
        titleSlot = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                CalmCircleButton(AppIcons.Close, stringResource(Res.string.cd_back), { onIntent(InviteIntent.Back) })
                Text(
                    title,
                    style = AppTheme.type.headline.copy(fontSize = 22.sp),
                    color = AppTheme.colors.ink,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalmChip(
                stringResource(Res.string.invite_by_email),
                selected = state.channel == InviteChannel.Email,
                onClick = { onIntent(InviteIntent.ChannelChosen(InviteChannel.Email)) },
                modifier = Modifier.testTag("invite.channel.email"),
            )
            CalmChip(
                stringResource(Res.string.invite_by_link),
                selected = state.channel == InviteChannel.Link,
                onClick = { onIntent(InviteIntent.ChannelChosen(InviteChannel.Link)) },
                modifier = Modifier.testTag("invite.channel.link"),
            )
        }
        RoleChoice(state, onIntent)
        when (state.channel) {
            InviteChannel.Email -> EmailForm(state, onIntent)
            InviteChannel.Link -> LinkForm(state, onIntent)
        }
        state.error?.let { error ->
            InlineAlert(
                AlertTone.Error,
                stringResource(if (error == InviteError.NotOwner) Res.string.invite_not_owner else Res.string.invite_wallet_archived),
                Modifier.testTag("invite.error"),
            )
        }
    }
    if (state.confirmSupersede) Dialog(onDismissRequest = { onIntent(InviteIntent.SupersedeDismissed) }) { SupersedeCard(state, onIntent) }
}

/** The pinned main action: send or create, share and done once a link exists. */
@Composable
private fun ActionBar(state: InviteState, onIntent: (InviteIntent) -> Unit) {
    val allowed = state.check == InviteCheck.Allowed || state.check == InviteCheck.NotVerified
    when (state.channel) {
        InviteChannel.Email -> CalmActionBar(Modifier.imePadding()) {
            PrimaryButton(
                stringResource(Res.string.invite_send), { onIntent(InviteIntent.SendClicked) }, Modifier.testTag("invite.send"),
                enabled = allowed, loading = state.sending,
            )
        }
        InviteChannel.Link -> when (state.link) {
            LinkProgress.None -> CalmActionBar {
                PrimaryButton(
                    stringResource(Res.string.invite_create_link), { onIntent(InviteIntent.SendClicked) }, Modifier.testTag("invite.send"),
                    enabled = allowed, loading = state.sending,
                )
            }
            is LinkProgress.Waiting -> Unit
            is LinkProgress.WaitingOffline -> CalmActionBar {
                SecondaryButton(stringResource(Res.string.invite_done), { onIntent(InviteIntent.DoneClicked) }, Modifier.testTag("invite.done"))
            }
            is LinkProgress.Ready -> CalmActionBar {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    CalmOutlineButton(stringResource(Res.string.invite_done), { onIntent(InviteIntent.DoneClicked) }, Modifier.weight(1f).testTag("invite.done"))
                    CalmIconButton(stringResource(Res.string.invite_share), AppIcons.SharingShare, { onIntent(InviteIntent.ShareClicked) }, Modifier.weight(2f).testTag("invite.share"))
                }
            }
        }
    }
}

@Composable
private fun RoleChoice(state: InviteState, onIntent: (InviteIntent) -> Unit) {
    CalmSectionTitle(stringResource(Res.string.calm_shr_what_can), Modifier.padding(top = 2.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
        RoleCard(
            selected = state.role == InvitedRole.Editor,
            icon = AppIcons.SharingEditNote,
            title = stringResource(Res.string.role_editor),
            hint = stringResource(Res.string.invite_role_editor_hint),
            onClick = { if (!state.locked) onIntent(InviteIntent.RoleChosen(InvitedRole.Editor)) },
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("invite.role.editor"),
        )
        RoleCard(
            selected = state.role == InvitedRole.Viewer,
            icon = AppIcons.Visibility,
            title = stringResource(Res.string.role_viewer),
            hint = stringResource(Res.string.invite_role_viewer_hint),
            onClick = { if (!state.locked) onIntent(InviteIntent.RoleChosen(InvitedRole.Viewer)) },
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("invite.role.viewer"),
        )
    }
}

@Composable
private fun RoleCard(selected: Boolean, icon: ImageVector, title: String, hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val colors = AppTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surface, shape)
            .border(1.5.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) colors.primary else colors.ink2, modifier = Modifier.size(22.dp))
        Text(title, style = AppTheme.type.itemTitle, color = colors.ink)
        Text(hint, style = AppTheme.type.caption, color = colors.ink2)
    }
}

@Composable
private fun EmailForm(state: InviteState, onIntent: (InviteIntent) -> Unit) {
    AppTextField(
        value = state.email,
        onValueChange = { onIntent(InviteIntent.EmailChanged(it)) },
        label = stringResource(Res.string.invite_email_label),
        errorText = state.emailError?.let { stringResource(if (it == EmailProblem.OwnAddress) Res.string.invite_own_address else Res.string.invite_email_invalid) },
        enabled = !state.sending,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onIntent(InviteIntent.SendClicked) }),
        modifier = Modifier.testTag("invite.email").onFocusChanged { if (!it.isFocused) onIntent(InviteIntent.EmailLeft) },
    )
    GateExplanation(state, onIntent)
}

@Composable
private fun LinkForm(state: InviteState, onIntent: (InviteIntent) -> Unit) {
    GateExplanation(state, onIntent)
    when (state.link) {
        LinkProgress.None -> Unit
        is LinkProgress.Waiting -> {
            Skeleton(Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.xs))
            Text(stringResource(Res.string.invite_link_wait), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("invite.waiting"))
        }
        is LinkProgress.WaitingOffline -> InlineAlert(AlertTone.Info, stringResource(Res.string.invite_link_wait_offline), Modifier.testTag("invite.waitingOffline"))
        is LinkProgress.Ready -> CalmCard(padding = PaddingValues(20.dp), modifier = Modifier.padding(top = 6.dp).testTag("invite.ready")) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                CalmIconTile(
                    AppIcons.SharingLink, AppTheme.money.positive, AppTheme.money.positiveContainer,
                    size = 56.dp, iconSize = 28.dp, radius = 18.dp,
                )
                Text(
                    stringResource(Res.string.calm_shr_link_ready_title),
                    style = AppTheme.type.title.copy(fontWeight = FontWeight.ExtraBold),
                    color = AppTheme.colors.ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(if (state.role == InvitedRole.Editor) Res.string.calm_shr_link_hint_editor else Res.string.calm_shr_link_hint_viewer),
                    style = AppTheme.type.body,
                    color = AppTheme.colors.ink2,
                    textAlign = TextAlign.Center,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(AppTheme.colors.sunken, RoundedCornerShape(14.dp))
                        .padding(start = 14.dp, end = 6.dp),
                ) {
                    Text(
                        stringResource(Res.string.calm_shr_link_pill),
                        style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = AppTheme.colors.ink2,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppTheme.colors.surface)
                            .testTag("invite.copy")
                            .clickable(role = Role.Button) { onIntent(InviteIntent.CopyClicked) }
                            .padding(horizontal = 12.dp),
                    ) {
                        Icon(AppIcons.SharingCopy, contentDescription = null, tint = AppTheme.colors.ink, modifier = Modifier.size(16.dp))
                        Text(stringResource(Res.string.invite_copy), style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = AppTheme.colors.ink)
                    }
                }
            }
        }
    }
}

/** Why inviting is not open right now, with the way to the Plan screen for plan reasons (FR-002, FR-036). */
@Composable
private fun GateExplanation(state: InviteState, onIntent: (InviteIntent) -> Unit) {
    val text = state.check.explanation() ?: return
    if (state.check == InviteCheck.NotVerified) return
    InlineAlert(
        AlertTone.Info, text, Modifier.testTag("invite.reason"),
        actionLabel = if (state.check.isPlanReason) stringResource(Res.string.gate_see_plan) else null,
        onAction = if (state.check.isPlanReason) ({ onIntent(InviteIntent.SeePlanClicked) }) else null,
    )
}

/** The supersede confirmation's surface (also shown by the screenshot tests, which cannot see a dialog window). */
@Composable
internal fun SupersedeCard(state: InviteState, onIntent: (InviteIntent) -> Unit) {
    AppAlertDialogCard(
        title = stringResource(Res.string.invite_supersede_confirm),
        text = stringResource(Res.string.invite_supersede, state.email.trim()),
        confirmLabel = stringResource(Res.string.invite_supersede_confirm),
        onConfirm = { onIntent(InviteIntent.SupersedeConfirmed) },
        dismissLabel = stringResource(Res.string.action_cancel),
        onDismiss = { onIntent(InviteIntent.SupersedeDismissed) },
        confirmModifier = Modifier.testTag("invite.supersede.confirm"),
        dismissModifier = Modifier.testTag("invite.supersede.cancel"),
    )
}

@Preview
@Composable
private fun InviteScreenPreview() {
    AppTheme { InviteScreen(InviteState(walletId = "w1", email = "karim@example.com"), {}) }
}
