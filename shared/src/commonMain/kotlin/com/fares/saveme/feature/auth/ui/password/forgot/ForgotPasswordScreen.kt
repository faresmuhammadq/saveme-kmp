package com.fares.saveme.feature.auth.ui.password.forgot

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextField
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.ui.AuthTopBar
import com.fares.saveme.feature.auth.ui.text
import com.fares.saveme.feature.auth.ui.tone
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

@Composable
fun ForgotPasswordScreen(state: ForgotPasswordState, onIntent: (ForgotPasswordIntent) -> Unit) {
    AppScreen(topBar = { AuthTopBar(stringResource(Res.string.forgot_title)) { onIntent(ForgotPasswordIntent.Back) } }) {
        state.banner?.let { InlineAlert(tone = it.tone(), text = it.text()) }
        Text(stringResource(Res.string.forgot_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
        AppTextField(
            value = state.email,
            onValueChange = { onIntent(ForgotPasswordIntent.EmailChanged(it)) },
            label = stringResource(Res.string.field_email),
            modifier = Modifier.testTag("forgotPassword.email"),
            errorText = state.emailError.text(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onIntent(ForgotPasswordIntent.Submit) }),
        )
        PrimaryButton(
            stringResource(Res.string.action_send_link),
            { onIntent(ForgotPasswordIntent.Submit) },
            modifier = Modifier.testTag("forgotPassword.submit"),
            loading = state.submitting,
        )
    }
}

@Preview
@Composable
private fun ForgotPasswordScreenPreview() {
    AppTheme { ForgotPasswordScreen(ForgotPasswordState(email = "sara@example.com")) {} }
}
