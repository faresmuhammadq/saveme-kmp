package com.fares.saveme.feature.auth.ui.password.reset

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PasswordField
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.ui.AuthTopBar
import com.fares.saveme.feature.auth.ui.text
import com.fares.saveme.feature.auth.ui.tone
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

@Composable
fun ResetPasswordScreen(state: ResetPasswordState, onIntent: (ResetPasswordIntent) -> Unit) {
    AppScreen(topBar = { AuthTopBar(stringResource(Res.string.reset_title)) { onIntent(ResetPasswordIntent.Back) } }) {
        when (state.status) {
            ResetStatus.Done -> {
                InlineAlert(tone = AlertTone.Info, text = stringResource(Res.string.error_password_reset_done))
                PrimaryButton(
                    stringResource(Res.string.action_continue),
                    { onIntent(ResetPasswordIntent.ContinueClicked) },
                    modifier = Modifier.testTag("resetPassword.continue"),
                )
            }
            ResetStatus.LinkInvalid -> {
                InlineAlert(tone = AlertTone.Error, text = stringResource(Res.string.error_reset_link_invalid))
                PrimaryButton(
                    stringResource(Res.string.action_request_new_link),
                    { onIntent(ResetPasswordIntent.RequestNewLinkClicked) },
                    modifier = Modifier.testTag("resetPassword.requestNewLink"),
                )
            }
            ResetStatus.Editing -> {
                state.banner?.let { InlineAlert(tone = it.tone(), text = it.text()) }
                PasswordField(
                    value = state.password,
                    onValueChange = { onIntent(ResetPasswordIntent.PasswordChanged(it)) },
                    label = stringResource(Res.string.change_password_new),
                    modifier = Modifier.testTag("resetPassword.password"),
                    visible = state.passwordVisible,
                    onToggleVisible = { onIntent(ResetPasswordIntent.TogglePasswordVisibility) },
                    supportingText = stringResource(Res.string.reset_hint),
                    errorText = state.passwordError.text(),
                    enabled = !state.submitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onIntent(ResetPasswordIntent.Submit) }),
                )
                PrimaryButton(
                    stringResource(Res.string.action_set_password),
                    { onIntent(ResetPasswordIntent.Submit) },
                    modifier = Modifier.testTag("resetPassword.submit"),
                    loading = state.submitting,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ResetPasswordScreenPreview() {
    AppTheme { ResetPasswordScreen(ResetPasswordState()) {} }
}
