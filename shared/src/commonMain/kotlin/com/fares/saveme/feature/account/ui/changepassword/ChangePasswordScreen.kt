package com.fares.saveme.feature.account.ui.changepassword

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PasswordField
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.account.ui.BackBar
import com.fares.saveme.feature.account.ui.text
import com.fares.saveme.feature.auth.ui.text
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_done
import saveme.shared.generated.resources.change_password_current
import saveme.shared.generated.resources.change_password_done
import saveme.shared.generated.resources.change_password_new
import saveme.shared.generated.resources.change_password_rules
import saveme.shared.generated.resources.change_password_submit
import saveme.shared.generated.resources.change_password_title
import saveme.shared.generated.resources.error_account_locked
import saveme.shared.generated.resources.error_current_password_incorrect
import saveme.shared.generated.resources.error_rate_limited
import saveme.shared.generated.resources.error_rate_limited_soon
import saveme.shared.generated.resources.error_validation

@Composable
fun ChangePasswordScreen(state: ChangePasswordState, onIntent: (ChangePasswordIntent) -> Unit) {
    AppScreen(topBar = { BackBar(stringResource(Res.string.change_password_title)) { onIntent(ChangePasswordIntent.Back) } }) {
        if (state.done) {
            InlineAlert(tone = AlertTone.Info, text = stringResource(Res.string.change_password_done))
            PrimaryButton(stringResource(Res.string.action_done), { onIntent(ChangePasswordIntent.Back) })
            return@AppScreen
        }
        state.waitReason?.let { reason ->
            val minutes = state.waitMinutes
            InlineAlert(
                tone = AlertTone.Warning,
                text = when {
                    minutes == null -> stringResource(Res.string.error_rate_limited_soon)
                    reason == WaitReason.Locked -> pluralStringResource(Res.plurals.error_account_locked, minutes, minutes)
                    else -> pluralStringResource(Res.plurals.error_rate_limited, minutes, minutes)
                },
            )
        }
        state.banner?.let { banner ->
            InlineAlert(
                tone = AlertTone.Error,
                text = when (banner) {
                    ChangePasswordBanner.Offline -> AccountBanner.Offline.text()
                    ChangePasswordBanner.Validation -> stringResource(Res.string.error_validation)
                    is ChangePasswordBanner.Error -> AccountBanner.Error(banner.code, banner.requestId).text()
                },
            )
        }
        PasswordField(
            value = state.current,
            onValueChange = { onIntent(ChangePasswordIntent.CurrentChanged(it)) },
            label = stringResource(Res.string.change_password_current),
            modifier = Modifier.testTag("changePassword.current"),
            visible = state.currentVisible,
            onToggleVisible = { onIntent(ChangePasswordIntent.ToggleCurrentVisible) },
            errorText = when (val error = state.currentError) {
                CurrentPasswordProblem.Incorrect -> stringResource(Res.string.error_current_password_incorrect)
                is CurrentPasswordProblem.Field -> error.problem.text()
                null -> null
            },
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        PasswordField(
            value = state.new,
            onValueChange = { onIntent(ChangePasswordIntent.NewChanged(it)) },
            label = stringResource(Res.string.change_password_new),
            modifier = Modifier.testTag("changePassword.new"),
            visible = state.newVisible,
            onToggleVisible = { onIntent(ChangePasswordIntent.ToggleNewVisible) },
            supportingText = stringResource(Res.string.change_password_rules),
            errorText = state.newError.text(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onIntent(ChangePasswordIntent.Submit) }),
        )
        PrimaryButton(
            stringResource(Res.string.change_password_submit),
            { onIntent(ChangePasswordIntent.Submit) },
            modifier = Modifier.testTag("changePassword.submit"),
            enabled = state.waitMinutes == null,
            loading = state.submitting,
        )
    }
}

@Preview
@Composable
private fun ChangePasswordScreenPreview() {
    AppTheme { ChangePasswordScreen(ChangePasswordState(current = "secret", currentError = CurrentPasswordProblem.Incorrect)) {} }
}
