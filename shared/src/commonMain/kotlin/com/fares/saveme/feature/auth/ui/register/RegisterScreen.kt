package com.fares.saveme.feature.auth.ui.register

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.feature.auth.ui.AuthLogo
import com.fares.saveme.feature.auth.ui.AuthSheetScreen
import com.fares.saveme.feature.auth.ui.CalmPasswordField
import com.fares.saveme.feature.auth.ui.CalmTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.CalmAlert
import com.fares.saveme.designsystem.component.linkSentence
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.FlowCheckCircle
import com.fares.saveme.feature.auth.ui.AuthLink
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.ui.text
import com.fares.saveme.feature.auth.ui.tone
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

@Composable
fun RegisterScreen(state: RegisterState, onIntent: (RegisterIntent) -> Unit) {
    AuthSheetScreen(
        onBack = { onIntent(RegisterIntent.Back) },
        header = {
            AuthLogo(52.dp)
            CalmTitle(stringResource(Res.string.authb_register_title))
            Text(stringResource(Res.string.authb_register_subtitle), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        },
    ) {
        state.banner?.let { CalmAlert(tone = it.tone(), text = it.text()) }
        if (state.alreadyRegistered) {
            CalmAlert(tone = AlertTone.Info, text = stringResource(Res.string.register_already_registered))
            SecondaryButton(
                stringResource(Res.string.action_sign_in),
                { onIntent(RegisterIntent.SignInClicked) },
                modifier = Modifier.testTag("register.alreadySignIn"),
            )
            AppTextButton(
                stringResource(Res.string.action_reset_password),
                { onIntent(RegisterIntent.ResetPasswordClicked) },
                modifier = Modifier.fillMaxWidth().testTag("register.resetPassword"),
            )
        }
        CalmTextField(
            value = state.displayName,
            onValueChange = { onIntent(RegisterIntent.DisplayNameChanged(it)) },
            label = stringResource(Res.string.register_display_name_optional),
            modifier = Modifier.testTag("register.displayName"),
            errorText = state.displayNameError.text(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        CalmTextField(
            value = state.email,
            onValueChange = { onIntent(RegisterIntent.EmailChanged(it)) },
            label = stringResource(Res.string.field_email),
            modifier = Modifier.testTag("register.email"),
            errorText = state.emailError.text(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        CalmPasswordField(
            value = state.password,
            onValueChange = { onIntent(RegisterIntent.PasswordChanged(it)) },
            label = stringResource(Res.string.field_password),
            modifier = Modifier.testTag("register.password"),
            visible = state.passwordVisible,
            onToggleVisible = { onIntent(RegisterIntent.TogglePasswordVisibility) },
            errorText = state.passwordError.text(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onIntent(RegisterIntent.Submit) }),
        )
        if (state.passwordError == null) {
            val valid = state.password.length in 8..128
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Icon(
                    AppIcons.FlowCheckCircle,
                    contentDescription = null,
                    tint = if (valid) AppTheme.money.positive else AppTheme.colors.caption,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(Res.string.reset_hint).trimEnd('.'),
                    style = AppTheme.type.caption.copy(fontSize = 13.sp),
                    color = if (valid) AppTheme.money.positive else AppTheme.colors.ink2,
                )
            }
        }
        PrimaryButton(
            stringResource(Res.string.action_create_account),
            { onIntent(RegisterIntent.Submit) },
            modifier = Modifier.testTag("register.submit"),
            loading = state.submitting,
        )
        AuthLink(
            linkSentence(stringResource(Res.string.authb_have_account), stringResource(Res.string.action_sign_in)),
            { onIntent(RegisterIntent.SignInClicked) },
            modifier = Modifier.testTag("register.signIn"),
        )
    }
}

@Preview
@Composable
private fun RegisterScreenPreview() {
    AppTheme { RegisterScreen(RegisterState(email = "sara@example.com", alreadyRegistered = true)) {} }
}
