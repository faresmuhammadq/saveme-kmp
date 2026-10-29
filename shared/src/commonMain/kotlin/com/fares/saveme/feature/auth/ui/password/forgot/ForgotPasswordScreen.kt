package com.fares.saveme.feature.auth.ui.password.forgot

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.CalmTitle
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.FlowKey
import com.fares.saveme.feature.auth.ui.AuthBadge
import com.fares.saveme.feature.auth.ui.AuthSheetScreen
import com.fares.saveme.feature.auth.ui.CalmTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.CalmAlert
import com.fares.saveme.designsystem.component.primaryLinkText
import com.fares.saveme.feature.auth.ui.AuthLink
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.ui.text
import com.fares.saveme.feature.auth.ui.tone
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

@Composable
fun ForgotPasswordScreen(state: ForgotPasswordState, onIntent: (ForgotPasswordIntent) -> Unit) {
    AuthSheetScreen(
        onBack = { onIntent(ForgotPasswordIntent.Back) },
        header = {
            AuthBadge(AppIcons.FlowKey, AppTheme.colors.primary, size = 64.dp, iconSize = 32.dp, radius = 20.dp)
            CalmTitle(stringResource(Res.string.authb_forgot_heading))
            Text(stringResource(Res.string.forgot_body), style = AppTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = AppTheme.colors.ink2)
        },
    ) {
        state.banner?.let { CalmAlert(tone = it.tone(), text = it.text()) }
        CalmTextField(
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
        AuthLink(primaryLinkText(stringResource(Res.string.authb_back_to_sign_in)), { onIntent(ForgotPasswordIntent.Back) })
    }
}

@Preview
@Composable
private fun ForgotPasswordScreenPreview() {
    AppTheme { ForgotPasswordScreen(ForgotPasswordState(email = "sara@example.com")) {} }
}
