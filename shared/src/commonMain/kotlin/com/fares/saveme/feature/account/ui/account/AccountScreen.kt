package com.fares.saveme.feature.account.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import saveme.shared.generated.resources.account_change_password
import saveme.shared.generated.resources.account_delete
import saveme.shared.generated.resources.account_devices
import saveme.shared.generated.resources.account_email_unverified
import saveme.shared.generated.resources.account_email_verified
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
        Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
            AppListItem(
                stringResource(Res.string.account_profile),
                leadingIcon = AppIcons.Person,
                trailing = ListTrailing.Chevron,
                onClick = { onIntent(AccountIntent.Open(AccountDestination.Profile)) },
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
        )
    }
}

@Preview
@Composable
private fun AccountScreenPreview() {
    AppTheme { AccountScreen(AccountState(email = "sara@example.com", emailVerified = false)) {} }
}
