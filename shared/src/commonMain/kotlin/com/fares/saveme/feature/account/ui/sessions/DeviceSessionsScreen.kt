package com.fares.saveme.feature.account.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppAlertDialog
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.format.formatDateTime
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.domain.model.DeviceSession
import com.fares.saveme.feature.account.ui.BackBar
import com.fares.saveme.feature.account.ui.text
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_retry
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
import saveme.shared.generated.resources.sessions_title
import saveme.shared.generated.resources.sessions_updated_at
import kotlin.time.Instant

@Composable
fun DeviceSessionsScreen(
    state: DeviceSessionsState,
    onIntent: (DeviceSessionsIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    AppScreen(
        snackbarHostState = snackbar,
        topBar = { BackBar(stringResource(Res.string.sessions_title)) { onIntent(DeviceSessionsIntent.Back) } },
    ) {
        state.banner?.let { banner ->
            InlineAlert(
                tone = AlertTone.Error,
                text = banner.text(),
                actionLabel = stringResource(Res.string.action_retry),
                onAction = { onIntent(DeviceSessionsIntent.Refresh) },
            )
        }
        if (state.sessions.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
                state.sessions.forEach { session -> SessionRow(session, state.timeZoneId, onIntent) }
            }
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
        is ConfirmEnd.One -> AppAlertDialog(
            title = stringResource(Res.string.sessions_confirm_end_title, confirm.deviceName),
            text = stringResource(Res.string.sessions_confirm_end_body),
            confirmLabel = stringResource(Res.string.sessions_end),
            onConfirm = { onIntent(DeviceSessionsIntent.ConfirmEnd) },
            dismissLabel = stringResource(Res.string.action_cancel),
            onDismiss = { onIntent(DeviceSessionsIntent.DismissConfirm) },
            destructive = true,
            confirmLoading = state.ending,
        )
        ConfirmEnd.Others -> AppAlertDialog(
            title = stringResource(Res.string.sessions_confirm_end_others_title),
            text = stringResource(Res.string.sessions_confirm_end_others_body),
            confirmLabel = stringResource(Res.string.sessions_end_others),
            onConfirm = { onIntent(DeviceSessionsIntent.ConfirmEnd) },
            dismissLabel = stringResource(Res.string.action_cancel),
            onDismiss = { onIntent(DeviceSessionsIntent.DismissConfirm) },
            destructive = true,
            confirmLoading = state.ending,
        )
        null -> Unit
    }
}

@Composable
private fun SessionRow(session: DeviceSession, timeZoneId: String, onIntent: (DeviceSessionsIntent) -> Unit) {
    val signedIn = stringResource(Res.string.sessions_signed_in_at, formatDateTime(session.signedInAt, timeZoneId))
    val lastUsed = session.lastUsedAt?.let { stringResource(Res.string.sessions_last_used, formatDateTime(it, timeZoneId)) }
        ?: stringResource(Res.string.sessions_not_used_yet)
    AppListItem(
        headline = session.deviceName,
        supporting = "$signedIn\n$lastUsed",
        leadingIcon = AppIcons.Devices,
        trailing = if (session.isCurrent) {
            // The current device is signed out with Sign out, never from this list (FR-003).
            ListTrailing.Text(stringResource(Res.string.sessions_this_device))
        } else {
            ListTrailing.Custom {
                AppTextButton(stringResource(Res.string.sessions_end), { onIntent(DeviceSessionsIntent.EndClicked(session.id)) })
            }
        },
    )
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
