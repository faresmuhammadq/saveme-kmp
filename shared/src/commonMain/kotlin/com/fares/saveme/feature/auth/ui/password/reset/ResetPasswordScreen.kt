package com.fares.saveme.feature.auth.ui.password.reset

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.CalmActionBar
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.FlowLockReset
import com.fares.saveme.feature.auth.ui.AuthBadge
import com.fares.saveme.feature.auth.ui.AuthSheetScreen
import com.fares.saveme.feature.auth.ui.CalmPasswordField
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.CalmAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.ui.text
import com.fares.saveme.feature.auth.ui.tone
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

@Composable
fun ResetPasswordScreen(state: ResetPasswordState, onIntent: (ResetPasswordIntent) -> Unit) {
    if (state.status == ResetStatus.Done) {
        Column(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
            ) {
                AuthBadge(
                    AppIcons.FlowLockReset,
                    AppTheme.money.positive,
                    size = 96.dp,
                    iconSize = 52.dp,
                    radius = 32.dp,
                    container = AppTheme.money.positiveContainer,
                )
                CalmTitle(stringResource(Res.string.authb_reset_done_title))
                Text(
                    stringResource(Res.string.authb_reset_done_body),
                    style = AppTheme.type.body.copy(fontSize = 15.5.sp, lineHeight = 23.sp),
                    color = AppTheme.colors.ink2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("resetPassword.doneMessage"),
                )
            }
            CalmActionBar {
                PrimaryButton(
                    stringResource(Res.string.action_continue),
                    { onIntent(ResetPasswordIntent.ContinueClicked) },
                    modifier = Modifier.testTag("resetPassword.continue"),
                )
            }
        }
        return
    }
    AuthSheetScreen(
        onBack = { onIntent(ResetPasswordIntent.Back) },
        header = {
            AuthBadge(AppIcons.Lock, AppTheme.colors.primary, size = 64.dp, iconSize = 32.dp, radius = 20.dp)
            CalmTitle(stringResource(Res.string.reset_title))
        },
    ) {
        when (state.status) {
            ResetStatus.LinkInvalid -> {
                CalmAlert(tone = AlertTone.Error, text = stringResource(Res.string.error_reset_link_invalid))
                PrimaryButton(
                    stringResource(Res.string.action_request_new_link),
                    { onIntent(ResetPasswordIntent.RequestNewLinkClicked) },
                    modifier = Modifier.testTag("resetPassword.requestNewLink"),
                )
            }
            else -> {
                state.banner?.let { CalmAlert(tone = it.tone(), text = it.text()) }
                CalmPasswordField(
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
