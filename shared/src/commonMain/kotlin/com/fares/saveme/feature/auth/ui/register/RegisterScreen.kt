package com.fares.saveme.feature.auth.ui.register

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
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PasswordField
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.ui.AuthTopBar
import com.fares.saveme.feature.auth.ui.text
import com.fares.saveme.feature.auth.ui.tone
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

@Composable
fun RegisterScreen(state: RegisterState, onIntent: (RegisterIntent) -> Unit) {
    AppScreen(topBar = { AuthTopBar(stringResource(Res.string.action_create_account)) { onIntent(RegisterIntent.Back) } }) {
        state.banner?.let { InlineAlert(tone = it.tone(), text = it.text()) }
        if (state.alreadyRegistered) {
            InlineAlert(tone = AlertTone.Info, text = stringResource(Res.string.register_already_registered))
            SecondaryButton(
                stringResource(Res.string.action_sign_in),
                { onIntent(RegisterIntent.SignInClicked) },
                modifier = Modifier.testTag("register.alreadySignIn"),
            )
            AppTextButton(
                stringResource(Res.string.action_reset_password),
                { onIntent(RegisterIntent.ResetPasswordClicked) },
                modifier = Modifier.testTag("register.resetPassword"),
            )
        }
        AppTextField(
            value = state.email,
            onValueChange = { onIntent(RegisterIntent.EmailChanged(it)) },
            label = stringResource(Res.string.field_email),
            modifier = Modifier.testTag("register.email"),
            errorText = state.emailError.text(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        PasswordField(
            value = state.password,
            onValueChange = { onIntent(RegisterIntent.PasswordChanged(it)) },
            label = stringResource(Res.string.field_password),
            modifier = Modifier.testTag("register.password"),
            visible = state.passwordVisible,
            onToggleVisible = { onIntent(RegisterIntent.TogglePasswordVisibility) },
            supportingText = stringResource(Res.string.reset_hint),
            errorText = state.passwordError.text(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        )
        AppTextField(
            value = state.displayName,
            onValueChange = { onIntent(RegisterIntent.DisplayNameChanged(it)) },
            label = stringResource(Res.string.register_display_name_optional),
            modifier = Modifier.testTag("register.displayName"),
            errorText = state.displayNameError.text(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onIntent(RegisterIntent.Submit) }),
        )
        PrimaryButton(
            stringResource(Res.string.action_create_account),
            { onIntent(RegisterIntent.Submit) },
            modifier = Modifier.testTag("register.submit"),
            loading = state.submitting,
        )
        AppTextButton(
            stringResource(Res.string.register_have_account),
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
