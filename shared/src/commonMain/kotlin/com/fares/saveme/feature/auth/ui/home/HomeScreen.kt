package com.fares.saveme.feature.auth.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.ui.signout.SignOutConfirmSheet
import com.fares.saveme.feature.auth.ui.verify.text
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_resend_email
import saveme.shared.generated.resources.home_verify_prompt
import saveme.shared.generated.resources.cd_account
import saveme.shared.generated.resources.home_cancel_deletion
import saveme.shared.generated.resources.home_deletion_cancelled
import saveme.shared.generated.resources.home_deletion_pending
import saveme.shared.generated.resources.home_signed_in_as
import saveme.shared.generated.resources.home_title
import saveme.shared.generated.resources.home_add_transaction
import saveme.shared.generated.resources.home_categories
import saveme.shared.generated.resources.home_wallets
import saveme.shared.generated.resources.sign_out_action
import kotlin.time.Instant

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    AppScreen(
        snackbarHostState = snackbar,
        topBar = {
            AppTopAppBar(
                stringResource(Res.string.home_title),
                actions = {
                    AppIconButton(AppIcons.Person, stringResource(Res.string.cd_account), { onIntent(HomeIntent.AccountClicked) })
                },
            )
        },
    ) {
        state.deletionPendingOn?.let { date ->
            // Always visible while pending, with its way out (spec 002 FR-019).
            InlineAlert(
                tone = AlertTone.Warning,
                text = stringResource(Res.string.home_deletion_pending, formatDate(date, TimeZone.currentSystemDefault().id)),
                actionLabel = stringResource(Res.string.home_cancel_deletion),
                onAction = { onIntent(HomeIntent.CancelDeletion) }.takeUnless { state.cancellingDeletion },
            )
        }
        if (!state.emailVerified) {
            // Persistent until the address is verified (FR-012); resend is throttled (FR-013).
            InlineAlert(
                tone = AlertTone.Warning,
                text = stringResource(Res.string.home_verify_prompt),
                modifier = Modifier.testTag("home.verifyPrompt"),
                actionLabel = stringResource(Res.string.action_resend_email),
                onAction = { onIntent(HomeIntent.ResendClicked) }.takeIf { state.resend.canResend },
            )
            state.resend.message?.let { Text(it.text(), style = AppTheme.type.caption, color = AppTheme.colors.ink2) }
        }
        if (state.deletionCancelledNotice) {
            InlineAlert(
                tone = AlertTone.Info,
                text = stringResource(Res.string.home_deletion_cancelled),
            )
        }
        PrimaryButton(
            stringResource(Res.string.home_add_transaction),
            { onIntent(HomeIntent.AddTransactionClicked) },
            modifier = Modifier.testTag("home.addTransaction"),
        )
        AppListItem(
            headline = stringResource(Res.string.home_wallets),
            modifier = Modifier.testTag("home.wallets"),
            trailing = ListTrailing.Chevron,
            onClick = { onIntent(HomeIntent.WalletsClicked) },
        )
        AppListItem(
            headline = stringResource(Res.string.home_categories),
            modifier = Modifier.testTag("home.categories"),
            trailing = ListTrailing.Chevron,
            onClick = { onIntent(HomeIntent.CategoriesClicked) },
        )
        Text(stringResource(Res.string.home_signed_in_as, state.email), style = AppTheme.type.body, color = AppTheme.colors.ink)
        DestructiveButton(stringResource(Res.string.sign_out_action), { onIntent(HomeIntent.SignOutClicked) })
    }
    if (state.confirmSignOutVisible) {
        SignOutConfirmSheet(
            signingOut = state.signingOut,
            onConfirm = { onIntent(HomeIntent.SignOutConfirmed) },
            onDismiss = { onIntent(HomeIntent.SignOutDismissed) },
            pendingChanges = state.sync.pending,
            onSyncNow = { onIntent(HomeIntent.SyncNowClicked) },
        )
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    AppTheme {
        HomeScreen(
            HomeState(email = "sara@example.com", deletionPendingOn = Instant.parse("2026-10-28T10:00:00Z")),
            {},
        )
    }
}
