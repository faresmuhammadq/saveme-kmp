package com.fares.saveme.feature.sharing.ui.invitation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppAlertDialogCard
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.CalmActionBar
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmDivider
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.component.Skeleton
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.SharingBlock
import com.fares.saveme.designsystem.icon.SharingLuggage
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.sharing.domain.model.InvitationPreview
import com.fares.saveme.feature.sharing.domain.model.InvitedRole
import com.fares.saveme.feature.sharing.ui.calmColorAt
import com.fares.saveme.feature.sharing.ui.calmColorFor
import com.fares.saveme.feature.sharing.ui.shortDate
import com.fares.saveme.feature.sharing.ui.roleLabelRes
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_create_account
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.action_sign_in
import saveme.shared.generated.resources.calm_shr_invited_you
import saveme.shared.generated.resources.calm_shr_join_as
import saveme.shared.generated.resources.calm_shr_join_wallet
import saveme.shared.generated.resources.calm_shr_perm_edit
import saveme.shared.generated.resources.calm_shr_perm_manage
import saveme.shared.generated.resources.calm_shr_perm_view
import saveme.shared.generated.resources.calm_shr_shared_wallet
import saveme.shared.generated.resources.cd_back
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
import saveme.shared.generated.resources.join_too_many
import saveme.shared.generated.resources.join_unavailable
import saveme.shared.generated.resources.join_until
import saveme.shared.generated.resources.needs_connection
import kotlin.time.Instant

@Composable
fun InvitationScreen(state: InvitationState, onIntent: (InvitationIntent) -> Unit) {
    val mode = state.mode
    CalmScreen(
        title = null,
        onBack = { onIntent(InvitationIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        spacing = 16.dp,
        bottomBar = { if (mode is InvitationMode.Ready) ReadyActions(state, mode.preview, onIntent) },
    ) {
        when (mode) {
            InvitationMode.Loading -> Skeleton(Modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.xl))
            InvitationMode.SignedOut -> SignedOut(onIntent)
            is InvitationMode.Ready -> Ready(mode.preview, state)
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
private fun Ready(preview: InvitationPreview, state: InvitationState) {
    val inviter = preview.inviterName ?: stringResource(Res.string.join_someone)
    val role = stringResource(roleLabelRes(preview.roleWire))
    val subtitle = stringResource(Res.string.calm_shr_join_as, role) +
        (preview.expiresAt?.let { " · " + stringResource(Res.string.join_until, shortDate(it, state.timeZoneId)) } ?: "")
    val invitedYou = stringResource(Res.string.calm_shr_invited_you, inviter)
    val nameAt = invitedYou.indexOf(inviter).coerceAtLeast(0)
    val ink = AppTheme.colors.ink
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).background(calmColorAt(0), CircleShape)) {
            Text(inviter.take(1).uppercase(), style = AppTheme.type.itemTitle.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
        }
        Text(
            buildAnnotatedString {
                append(invitedYou)
                addStyle(SpanStyle(color = ink, fontWeight = FontWeight.Bold), nameAt, nameAt + inviter.length)
            },
            style = AppTheme.type.itemTitle.copy(fontWeight = FontWeight.SemiBold),
            color = AppTheme.colors.ink2,
            modifier = Modifier.semantics { heading() }.testTag("invitation.title"),
        )
    }
    val hero = calmColorFor(preview.walletName)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(hero, RoundedCornerShape(26.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.SharingLuggage, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Box(Modifier.weight(1f))
            Text(
                stringResource(Res.string.calm_shr_shared_wallet),
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), AppTheme.shapes.pill).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Box(Modifier.weight(1f))
        Text(preview.walletName, style = AppTheme.type.headline.copy(fontSize = 30.sp), color = Color.White, maxLines = 2)
        Text(subtitle, style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Color.White.copy(alpha = 0.85f))
    }
    val canEdit = preview.role == InvitedRole.Editor
    CalmCard(radius = 22.dp, padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
        Permission(stringResource(Res.string.calm_shr_perm_view), allowed = true)
        CalmDivider()
        Permission(stringResource(Res.string.calm_shr_perm_edit), allowed = canEdit)
        CalmDivider()
        Permission(stringResource(Res.string.calm_shr_perm_manage), allowed = false)
    }
}

@Composable
private fun Permission(text: String, allowed: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics(mergeDescendants = true) {},
    ) {
        if (allowed) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(22.dp).background(AppTheme.money.positive, CircleShape)) {
                Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        } else {
            Icon(AppIcons.SharingBlock, contentDescription = null, tint = AppTheme.colors.ink2, modifier = Modifier.size(22.dp))
        }
        Text(
            text,
            style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (allowed) AppTheme.colors.ink else AppTheme.colors.ink2,
        )
    }
}

@Composable
private fun ReadyActions(state: InvitationState, preview: InvitationPreview, onIntent: (InvitationIntent) -> Unit) {
    CalmActionBar {
        PrimaryButton(
            stringResource(Res.string.calm_shr_join_wallet, preview.walletName), { onIntent(InvitationIntent.JoinClicked) },
            Modifier.testTag("invitation.join"), loading = state.busy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            TextAction(stringResource(Res.string.join_later), AppTheme.colors.ink2, !state.busy, Modifier.testTag("invitation.later")) { onIntent(InvitationIntent.LaterClicked) }
            TextAction(stringResource(Res.string.join_decline), AppTheme.money.over, !state.busy, Modifier.testTag("invitation.decline")) { onIntent(InvitationIntent.DeclineClicked) }
        }
    }
}

@Composable
private fun TextAction(text: String, color: Color, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = AppTheme.sizes.touchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
    ) {
        Text(text, style = AppTheme.type.label.copy(fontSize = 14.5.sp), color = if (enabled) color else AppTheme.colors.outline)
    }
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
