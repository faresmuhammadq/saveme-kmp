package com.fares.saveme.feature.sharing.ui.invitation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppAlertDialogCard
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.RolePill
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.sharing.domain.model.InvitationPreview
import com.fares.saveme.feature.sharing.domain.model.InvitedRole
import com.fares.saveme.feature.sharing.ui.roleWireToMemberRole
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_create_account
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.action_sign_in
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.invite_role_editor_hint
import saveme.shared.generated.resources.invite_role_viewer_hint
import saveme.shared.generated.resources.join_action
import saveme.shared.generated.resources.join_already
import saveme.shared.generated.resources.join_decline
import saveme.shared.generated.resources.join_decline_confirm
import saveme.shared.generated.resources.join_failed
import saveme.shared.generated.resources.join_failed_plain
import saveme.shared.generated.resources.join_invalid_link
import saveme.shared.generated.resources.join_later
import saveme.shared.generated.resources.join_open_wallet
import saveme.shared.generated.resources.join_owner_plan_full
import saveme.shared.generated.resources.join_signed_out
import saveme.shared.generated.resources.join_someone
import saveme.shared.generated.resources.join_title
import saveme.shared.generated.resources.join_too_many
import saveme.shared.generated.resources.join_unavailable
import saveme.shared.generated.resources.join_until
import saveme.shared.generated.resources.needs_connection
import kotlin.time.Instant

@Composable
fun InvitationScreen(state: InvitationState, onIntent: (InvitationIntent) -> Unit) {
    AppScreen(
        topBar = {
            AppTopAppBar(
                title = "",
                navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), { onIntent(InvitationIntent.Back) }) },
            )
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.m),
            modifier = Modifier.fillMaxWidth().padding(top = AppTheme.spacing.l),
        ) {
            when (val mode = state.mode) {
                InvitationMode.Loading -> Skeleton(Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.xl))
                InvitationMode.SignedOut -> SignedOut(onIntent)
                is InvitationMode.Ready -> Ready(mode.preview, state, onIntent)
                is InvitationMode.AlreadyMember -> {
                    Message(stringResource(Res.string.join_already))
                    PrimaryButton(stringResource(Res.string.join_open_wallet), { onIntent(InvitationIntent.OpenWalletClicked) }, Modifier.testTag("invitation.openWallet"))
                }
                InvitationMode.Unavailable -> Message(stringResource(Res.string.join_unavailable))
                InvitationMode.InvalidLink -> Message(stringResource(Res.string.join_invalid_link))
                InvitationMode.Offline -> Retry(stringResource(Res.string.needs_connection), state, onIntent)
                is InvitationMode.TooMany -> Message(stringResource(Res.string.join_too_many, mode.minutes))
                InvitationMode.OwnerPlanFull -> {
                    Message(stringResource(Res.string.join_owner_plan_full))
                    AppTextButton(stringResource(Res.string.join_later), { onIntent(InvitationIntent.LaterClicked) }, Modifier.fillMaxWidth().testTag("invitation.later"))
                }
                is InvitationMode.Failed -> Retry(
                    if (mode.requestId != null) stringResource(Res.string.join_failed, mode.requestId) else stringResource(Res.string.join_failed_plain),
                    state, onIntent,
                )
            }
        }
    }
    if (state.confirmDecline) Dialog(onDismissRequest = { onIntent(InvitationIntent.DeclineDismissed) }) { DeclineCard(state, onIntent) }
}

@Composable
private fun Message(text: String) {
    InlineAlert(AlertTone.Info, text, Modifier.testTag("invitation.message"))
}

@Composable
private fun Retry(text: String, state: InvitationState, onIntent: (InvitationIntent) -> Unit) {
    Message(text)
    PrimaryButton(stringResource(Res.string.action_retry), { onIntent(InvitationIntent.RetryClicked) }, Modifier.testTag("invitation.retry"), loading = state.busy)
}

@Composable
private fun SignedOut(onIntent: (InvitationIntent) -> Unit) {
    Message(stringResource(Res.string.join_signed_out))
    PrimaryButton(stringResource(Res.string.action_sign_in), { onIntent(InvitationIntent.SignInClicked) }, Modifier.testTag("invitation.signIn"))
    SecondaryButton(stringResource(Res.string.action_create_account), { onIntent(InvitationIntent.RegisterClicked) }, Modifier.testTag("invitation.register"))
}

@Composable
private fun Ready(preview: InvitationPreview, state: InvitationState, onIntent: (InvitationIntent) -> Unit) {
    Text(
        stringResource(Res.string.join_title, preview.inviterName ?: stringResource(Res.string.join_someone), preview.walletName),
        style = AppTheme.type.title,
        color = AppTheme.colors.ink,
        modifier = Modifier.semantics { heading() }.testTag("invitation.title"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs), horizontalAlignment = Alignment.Start) {
        RolePill(roleWireToMemberRole(preview.roleWire))
        Text(
            stringResource(if (preview.role == InvitedRole.Editor) Res.string.invite_role_editor_hint else Res.string.invite_role_viewer_hint),
            style = AppTheme.type.body,
            color = AppTheme.colors.ink2,
        )
        preview.expiresAt?.let {
            Text(stringResource(Res.string.join_until, formatDate(it, state.timeZoneId)), style = AppTheme.type.caption, color = AppTheme.colors.caption, textAlign = TextAlign.Start)
        }
    }
    PrimaryButton(stringResource(Res.string.join_action), { onIntent(InvitationIntent.JoinClicked) }, Modifier.testTag("invitation.join"), loading = state.busy)
    SecondaryButton(stringResource(Res.string.join_decline), { onIntent(InvitationIntent.DeclineClicked) }, Modifier.testTag("invitation.decline"), enabled = !state.busy)
    AppTextButton(stringResource(Res.string.join_later), { onIntent(InvitationIntent.LaterClicked) }, Modifier.fillMaxWidth().testTag("invitation.later"), enabled = !state.busy)
}

/** The decline confirmation's surface (also shown by the screenshot tests, which cannot see a dialog window). */
@Composable
internal fun DeclineCard(state: InvitationState, onIntent: (InvitationIntent) -> Unit) {
    AppAlertDialogCard(
        title = stringResource(Res.string.join_decline),
        text = stringResource(Res.string.join_decline_confirm),
        confirmLabel = stringResource(Res.string.join_decline),
        onConfirm = { onIntent(InvitationIntent.DeclineConfirmed) },
        dismissLabel = stringResource(Res.string.action_cancel),
        onDismiss = { onIntent(InvitationIntent.DeclineDismissed) },
        destructive = true,
        confirmLoading = state.busy,
        confirmModifier = Modifier.testTag("invitation.confirm"),
        dismissModifier = Modifier.testTag("invitation.cancel"),
    )
}

@Preview
@Composable
private fun InvitationScreenPreview() {
    AppTheme {
        InvitationScreen(
            InvitationState(
                InvitationMode.Ready(InvitationPreview("i1", "w1", "Home", "editor", "Mona", Instant.parse("2026-10-09T10:00:00Z"), false)),
            ),
            {},
        )
    }
}
