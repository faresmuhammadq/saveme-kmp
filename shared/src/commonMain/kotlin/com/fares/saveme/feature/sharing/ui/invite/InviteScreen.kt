package com.fares.saveme.feature.sharing.ui.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppAlertDialogCard
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.SegmentedControl
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.sharing.domain.model.EmailProblem
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.domain.model.InvitedRole
import com.fares.saveme.feature.sharing.ui.explanation
import com.fares.saveme.feature.sharing.ui.isPlanReason
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.gate_see_plan
import saveme.shared.generated.resources.invite_by_email
import saveme.shared.generated.resources.invite_by_link
import saveme.shared.generated.resources.invite_copy
import saveme.shared.generated.resources.invite_create_link
import saveme.shared.generated.resources.invite_done
import saveme.shared.generated.resources.invite_email_invalid
import saveme.shared.generated.resources.invite_email_label
import saveme.shared.generated.resources.invite_link_ready
import saveme.shared.generated.resources.invite_link_wait
import saveme.shared.generated.resources.invite_link_wait_offline
import saveme.shared.generated.resources.invite_not_owner
import saveme.shared.generated.resources.invite_own_address
import saveme.shared.generated.resources.invite_role_editor_hint
import saveme.shared.generated.resources.invite_role_label
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
    AppScreen(
        snackbarHostState = snackbar,
        topBar = {
            AppTopAppBar(
                title = stringResource(Res.string.invite_title),
                navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), { onIntent(InviteIntent.Back) }) },
            )
        },
    ) {
        SegmentedControl(
            options = InviteChannel.entries,
            label = { channel -> stringResource(if (channel == InviteChannel.Email) Res.string.invite_by_email else Res.string.invite_by_link) },
            selected = state.channel,
            onSelect = { onIntent(InviteIntent.ChannelChosen(it)) },
            optionModifier = { channel -> Modifier.testTag(if (channel == InviteChannel.Email) "invite.channel.email" else "invite.channel.link") },
        )
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

@Composable
private fun RoleChoice(state: InviteState, onIntent: (InviteIntent) -> Unit) {
    Text(stringResource(Res.string.invite_role_label), style = AppTheme.type.title, color = AppTheme.colors.ink, modifier = Modifier.semantics { heading() })
    SegmentedControl(
        options = listOf(InvitedRole.Viewer, InvitedRole.Editor),
        label = { role -> stringResource(if (role == InvitedRole.Editor) Res.string.role_editor else Res.string.role_viewer) },
        selected = state.role,
        onSelect = { if (!state.locked) onIntent(InviteIntent.RoleChosen(it)) },
        optionModifier = { role -> Modifier.testTag(if (role == InvitedRole.Editor) "invite.role.editor" else "invite.role.viewer") },
    )
    Text(
        stringResource(if (state.role == InvitedRole.Editor) Res.string.invite_role_editor_hint else Res.string.invite_role_viewer_hint),
        style = AppTheme.type.caption,
        color = AppTheme.colors.caption,
    )
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
    PrimaryButton(
        stringResource(Res.string.invite_send), { onIntent(InviteIntent.SendClicked) }, Modifier.testTag("invite.send"),
        enabled = state.check == InviteCheck.Allowed || state.check == InviteCheck.NotVerified, loading = state.sending,
    )
}

@Composable
private fun LinkForm(state: InviteState, onIntent: (InviteIntent) -> Unit) {
    GateExplanation(state, onIntent)
    when (val link = state.link) {
        LinkProgress.None -> PrimaryButton(
            stringResource(Res.string.invite_create_link), { onIntent(InviteIntent.SendClicked) }, Modifier.testTag("invite.send"),
            enabled = state.check == InviteCheck.Allowed || state.check == InviteCheck.NotVerified, loading = state.sending,
        )
        is LinkProgress.Waiting -> {
            Skeleton(Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.xs))
            Text(stringResource(Res.string.invite_link_wait), style = AppTheme.type.body, color = AppTheme.colors.ink2, modifier = Modifier.testTag("invite.waiting"))
        }
        is LinkProgress.WaitingOffline -> {
            InlineAlert(AlertTone.Info, stringResource(Res.string.invite_link_wait_offline), Modifier.testTag("invite.waitingOffline"))
            SecondaryButton(stringResource(Res.string.invite_done), { onIntent(InviteIntent.DoneClicked) }, Modifier.testTag("invite.done"))
        }
        is LinkProgress.Ready -> {
            InlineAlert(AlertTone.Info, stringResource(Res.string.invite_link_ready), Modifier.testTag("invite.ready"))
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs), modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(stringResource(Res.string.invite_share), { onIntent(InviteIntent.ShareClicked) }, Modifier.weight(1f).testTag("invite.share"), fullWidth = false)
                SecondaryButton(stringResource(Res.string.invite_copy), { onIntent(InviteIntent.CopyClicked) }, Modifier.weight(1f).testTag("invite.copy"), fullWidth = false)
            }
            SecondaryButton(stringResource(Res.string.invite_done), { onIntent(InviteIntent.DoneClicked) }, Modifier.testTag("invite.done"))
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
