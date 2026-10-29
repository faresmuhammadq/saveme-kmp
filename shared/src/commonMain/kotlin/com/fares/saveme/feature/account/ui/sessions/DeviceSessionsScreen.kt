package com.fares.saveme.feature.account.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmIconTile
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.icon.AccPhone
import com.fares.saveme.designsystem.icon.AccTablet
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.model.DeviceSession
import com.fares.saveme.feature.account.ui.text
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.acct_devices
import saveme.shared.generated.resources.acct_other_devices
import saveme.shared.generated.resources.acct_sign_out_device
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_retry
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.sessions_confirm_end_body
import saveme.shared.generated.resources.sessions_confirm_end_others_body
import saveme.shared.generated.resources.sessions_confirm_end_others_title
import saveme.shared.generated.resources.sessions_confirm_end_title
import saveme.shared.generated.resources.sessions_end
import saveme.shared.generated.resources.sessions_end_others
import saveme.shared.generated.resources.sessions_last_used
import saveme.shared.generated.resources.sessions_none_other
import saveme.shared.generated.resources.sessions_not_used_yet
import saveme.shared.generated.resources.sessions_signed_in_at
import saveme.shared.generated.resources.sessions_this_device
import saveme.shared.generated.resources.sessions_updated_at
import kotlin.time.Instant

@Composable
fun DeviceSessionsScreen(
    state: DeviceSessionsState,
    onIntent: (DeviceSessionsIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    CalmScreen(
        title = stringResource(Res.string.acct_devices),
        onBack = { onIntent(DeviceSessionsIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        snackbarHostState = snackbar,
        spacing = 12.dp,
    ) {
        state.banner?.let { banner ->
            InlineAlert(
                tone = AlertTone.Error,
                text = banner.text(),
                actionLabel = stringResource(Res.string.action_retry),
                onAction = { onIntent(DeviceSessionsIntent.Refresh) },
            )
        }
        val others = state.sessions.filter { !it.isCurrent }
        state.sessions.filter { it.isCurrent }.forEach { CurrentDeviceCard(it, state.timeZoneId) }
        if (others.isNotEmpty()) {
            Text(
                stringResource(Res.string.acct_other_devices, others.size),
                style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
                color = AppTheme.colors.ink,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
            others.forEach { session -> SessionRow(session, state.timeZoneId, onIntent) }
        }
        state.refreshedAt?.let { at ->
            Text(
                stringResource(Res.string.sessions_updated_at, formatDateTime(at, state.timeZoneId)),
                style = AppTheme.type.caption,
                color = AppTheme.colors.caption,
            )
        }
        if (state.canEndOthers) {
            SecondaryButton(stringResource(Res.string.sessions_end_others), { onIntent(DeviceSessionsIntent.EndOthersClicked) })
        } else if (state.sessions.isNotEmpty()) {
            Text(stringResource(Res.string.sessions_none_other), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        }
    }
    when (val confirm = state.confirm) {
        is ConfirmEnd.One -> CalmConfirmDialog(
            title = stringResource(Res.string.sessions_confirm_end_title, confirm.deviceName),
            text = stringResource(Res.string.sessions_confirm_end_body),
            confirmLabel = stringResource(Res.string.sessions_end),
            onConfirm = { onIntent(DeviceSessionsIntent.ConfirmEnd) },
            dismissLabel = stringResource(Res.string.action_cancel),
            onDismiss = { onIntent(DeviceSessionsIntent.DismissConfirm) },
            loading = state.ending,
        )
        ConfirmEnd.Others -> CalmConfirmDialog(
            title = stringResource(Res.string.sessions_confirm_end_others_title),
            text = stringResource(Res.string.sessions_confirm_end_others_body),
            confirmLabel = stringResource(Res.string.sessions_end_others),
            onConfirm = { onIntent(DeviceSessionsIntent.ConfirmEnd) },
            dismissLabel = stringResource(Res.string.action_cancel),
            onDismiss = { onIntent(DeviceSessionsIntent.DismissConfirm) },
            loading = state.ending,
        )
        null -> Unit
    }
}

@Composable
private fun CurrentDeviceCard(session: DeviceSession, timeZoneId: String) {
    CalmCard(
        container = AppTheme.colors.primary,
        bordered = false,
        padding = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(AppIcons.AccPhone, contentDescription = null, tint = AppTheme.colors.onPrimary, modifier = Modifier.size(34.dp))
            Column(Modifier.weight(1f)) {
                val thisDevice = stringResource(Res.string.sessions_this_device)
                Text(
                    if (AppTheme.type.overlineUppercase) thisDevice.uppercase() else thisDevice,
                    style = AppTheme.type.overline.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = AppTheme.colors.onPrimary.copy(alpha = 0.85f),
                )
                Text(session.deviceName, style = AppTheme.type.title.copy(fontWeight = FontWeight.ExtraBold), color = AppTheme.colors.onPrimary)
                Text(
                    stringResource(Res.string.sessions_signed_in_at, formatDate(session.signedInAt, timeZoneId)),
                    style = AppTheme.type.caption,
                    color = AppTheme.colors.onPrimary.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: DeviceSession, timeZoneId: String, onIntent: (DeviceSessionsIntent) -> Unit) {
    val lastUsed = session.lastUsedAt?.let { stringResource(Res.string.sessions_last_used, formatDate(it, timeZoneId)) }
        ?: stringResource(Res.string.sessions_not_used_yet)
    val endLabel = stringResource(Res.string.acct_sign_out_device, session.deviceName)
    CalmCard(radius = 20.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CalmIconTile(session.icon(), AppTheme.colors.ink2, AppTheme.colors.sunken, size = 44.dp, radius = 14.dp)
            Column(Modifier.weight(1f)) {
                Text(session.deviceName, style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold), color = AppTheme.colors.ink)
                Text(lastUsed, style = AppTheme.type.caption, color = AppTheme.colors.ink2, modifier = Modifier.padding(top = 2.dp))
            }
            // The current device is signed out with Sign out, never from this list (FR-003).
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = endLabel }
                    .clickable(role = Role.Button, onClick = { onIntent(DeviceSessionsIntent.EndClicked(session.id)) }),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).background(AppTheme.money.overContainer, CircleShape),
                ) {
                    Icon(AppIcons.Logout, contentDescription = null, tint = AppTheme.money.over, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private val TabletName = Regex("tab|pad|tablet", RegexOption.IGNORE_CASE)

/** A tablet's icon when its name says so (Galaxy Tab, iPad), a phone's otherwise. */
private fun DeviceSession.icon() = if (TabletName.containsMatchIn(deviceName)) AppIcons.AccTablet else AppIcons.AccPhone

@Composable
private fun CalmConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    loading: Boolean,
) {
    Dialog(onDismissRequest = { if (!loading) onDismiss() }) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .background(AppTheme.colors.surface, RoundedCornerShape(26.dp))
                .padding(22.dp)
                .semantics { paneTitle = title },
        ) {
            CalmIconTile(AppIcons.Logout, AppTheme.money.over, AppTheme.money.overContainer, size = 48.dp, iconSize = 22.dp, radius = 15.dp)
            Text(
                title,
                style = AppTheme.type.title.copy(fontWeight = FontWeight.ExtraBold),
                color = AppTheme.colors.ink,
                modifier = Modifier.padding(top = 14.dp).semantics { heading() },
            )
            Text(
                text,
                style = AppTheme.type.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = AppTheme.colors.ink2,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 20.dp)) {
                DialogButton(dismissLabel, AppTheme.colors.sunken, AppTheme.colors.ink, !loading, onDismiss, Modifier.weight(1f))
                DialogButton(confirmLabel, AppTheme.money.over, Color.White, !loading, onConfirm, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, container: Color, content: Color, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(50.dp)
            .background(container, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(label, style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold), color = content, maxLines = 2)
    }
}

internal val previewSessions = listOf(
    DeviceSession("a", "Pixel 8", "android", "1.0.0", Instant.parse("2026-09-28T09:00:00Z"), Instant.parse("2026-09-28T10:00:00Z"), isCurrent = true),
    DeviceSession("b", "Galaxy S24", "android", "1.0.0", Instant.parse("2026-09-20T08:30:00Z"), null, isCurrent = false),
    DeviceSession("c", "Pixel 6a", "android", "0.9.0", Instant.parse("2026-08-02T18:15:00Z"), Instant.parse("2026-09-01T07:45:00Z"), isCurrent = false),
)

@Preview
@Composable
private fun DeviceSessionsScreenPreview() {
    AppTheme {
        DeviceSessionsScreen(DeviceSessionsState(previewSessions, Instant.parse("2026-09-28T10:05:00Z"), "Africa/Cairo"), {})
    }
}
