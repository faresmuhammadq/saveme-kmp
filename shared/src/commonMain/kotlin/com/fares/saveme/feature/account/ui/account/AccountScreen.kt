package com.fares.saveme.feature.account.ui.account

import androidx.compose.ui.platform.testTag
import com.fares.saveme.core.sync.SyncStatus
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.format.formatDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.ui.BackBar
import com.fares.saveme.feature.auth.ui.signout.SignOutConfirmSheet
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_sync_now
import saveme.shared.generated.resources.sync_all_synced
import saveme.shared.generated.resources.sync_last_synced
import saveme.shared.generated.resources.sync_needs_decision_count
import saveme.shared.generated.resources.sync_never
import saveme.shared.generated.resources.sync_waiting_count
import saveme.shared.generated.resources.account_change_password
import saveme.shared.generated.resources.account_delete
import saveme.shared.generated.resources.account_devices
import saveme.shared.generated.resources.account_email_unverified
import saveme.shared.generated.resources.account_email_verified
import saveme.shared.generated.resources.account_plan
import saveme.shared.generated.resources.account_profile
import saveme.shared.generated.resources.account_title
import saveme.shared.generated.resources.sign_out_action

@Composable
fun AccountScreen(state: AccountState, onIntent: (AccountIntent) -> Unit) {
    AppScreen(topBar = { BackBar(stringResource(Res.string.account_title)) { onIntent(AccountIntent.Back) } }) {
        Column {
            Text(state.email, style = AppTheme.type.title, color = AppTheme.colors.ink)
            Text(
                stringResource(if (state.emailVerified) Res.string.account_email_verified else Res.string.account_email_unverified),
                style = AppTheme.type.caption,
                color = AppTheme.colors.caption,
            )
        }
        SyncStatusCard(state.sync, onSyncNow = { onIntent(AccountIntent.SyncNowClicked) })
        Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
            AppListItem(
                stringResource(Res.string.account_profile),
                leadingIcon = AppIcons.Person,
                trailing = ListTrailing.Chevron,
                onClick = { onIntent(AccountIntent.Open(AccountDestination.Profile)) },
            )
            AppListItem(
                stringResource(Res.string.account_plan),
                leadingIcon = AppIcons.Card,
                trailing = ListTrailing.Chevron,
                modifier = Modifier.testTag("account.plan"),
                onClick = { onIntent(AccountIntent.Open(AccountDestination.Plan)) },
            )
            AppListItem(
                stringResource(Res.string.account_devices),
                leadingIcon = AppIcons.Devices,
                trailing = ListTrailing.Chevron,
                onClick = { onIntent(AccountIntent.Open(AccountDestination.Devices)) },
            )
            AppListItem(
                stringResource(Res.string.account_change_password),
                leadingIcon = AppIcons.Lock,
                trailing = ListTrailing.Chevron,
                onClick = { onIntent(AccountIntent.Open(AccountDestination.ChangePassword)) },
            )
        }
        Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
            AppListItem(
                stringResource(Res.string.sign_out_action),
                leadingIcon = AppIcons.Logout,
                onClick = { onIntent(AccountIntent.SignOutClicked) },
            )
            AppListItem(
                stringResource(Res.string.account_delete),
                leadingIcon = AppIcons.Delete,
                headlineColor = AppTheme.colors.error,
                trailing = ListTrailing.Chevron,
                onClick = { onIntent(AccountIntent.Open(AccountDestination.DeleteAccount)) },
            )
        }
    }
    if (state.confirmSignOutVisible) {
        SignOutConfirmSheet(
            signingOut = state.signingOut,
            onConfirm = { onIntent(AccountIntent.SignOutConfirmed) },
            onDismiss = { onIntent(AccountIntent.SignOutDismissed) },
            pendingChanges = state.sync.pending,
            onSyncNow = { onIntent(AccountIntent.SyncNowClicked) },
        )
    }
}

/** What is still only on this phone, and when the app last synced (spec 004 FR-016, FR-010). */
@Composable
private fun SyncStatusCard(sync: SyncStatus, onSyncNow: () -> Unit) {
    val headline = when {
        sync.waiting > 0 -> pluralStringResource(Res.plurals.sync_waiting_count, sync.waiting, sync.waiting)
        sync.needsDecision > 0 -> pluralStringResource(Res.plurals.sync_needs_decision_count, sync.needsDecision, sync.needsDecision)
        else -> stringResource(Res.string.sync_all_synced)
    }
    val lines = listOfNotNull(
        pluralStringResource(Res.plurals.sync_needs_decision_count, sync.needsDecision, sync.needsDecision)
            .takeIf { sync.waiting > 0 && sync.needsDecision > 0 },
        sync.lastSyncedAt?.let { stringResource(Res.string.sync_last_synced, formatDateTime(it, TimeZone.currentSystemDefault().id)) }
            ?: stringResource(Res.string.sync_never).takeIf { sync.pending > 0 },
    )
    Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
        AppListItem(
            headline,
            modifier = Modifier.testTag("account.syncStatus"),
            supporting = lines.joinToString("\n").ifEmpty { null },
        )
        if (sync.waiting > 0) {
            SecondaryButton(stringResource(Res.string.action_sync_now), onSyncNow, modifier = Modifier.testTag("account.syncNow"))
        }
    }
}

@Preview
@Composable
private fun AccountScreenPreview() {
    AppTheme { AccountScreen(AccountState(email = "sara@example.com", emailVerified = false)) {} }
}
