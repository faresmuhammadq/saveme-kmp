package com.fares.saveme.feature.auth.ui.signin

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.fares.saveme.designsystem.component.CalmAlert
import com.fares.saveme.designsystem.component.linkSentence
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.FlowLockClock
import androidx.compose.ui.text.AnnotatedString
import com.fares.saveme.feature.auth.ui.AuthLink
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.ui.message
import com.fares.saveme.feature.auth.ui.text
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

@Composable
fun SignInScreen(state: SignInState, onIntent: (SignInIntent) -> Unit) {
    AuthSheetScreen(
        onBack = { onIntent(SignInIntent.Back) },
        headerAlignment = Alignment.CenterHorizontally,
        header = {
            AuthLogo(64.dp)
            CalmTitle(stringResource(Res.string.authb_sign_in_welcome))
        },
    ) {
        state.banner?.let { banner ->
            CalmAlert(
                tone = when (banner) {
                    SignInBanner.SessionEnded, SignInBanner.PasswordReset -> AlertTone.Info
                    SignInBanner.Offline, is SignInBanner.RateLimited -> AlertTone.Warning
                    else -> AlertTone.Error
                },
                text = bannerText(banner),
                icon = if (banner is SignInBanner.Locked) AppIcons.FlowLockClock else null,
                actionLabel = if (banner is SignInBanner.Locked) stringResource(Res.string.sign_in_forgot_password) else null,
                onAction = if (banner is SignInBanner.Locked) ({ onIntent(SignInIntent.ForgotPasswordClicked) }) else null,
            )
        }
        CalmTextField(
            value = state.email,
            onValueChange = { onIntent(SignInIntent.EmailChanged(it)) },
            label = stringResource(Res.string.field_email),
            modifier = Modifier.testTag("signIn.email"),
            errorText = state.emailError.text(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        CalmPasswordField(
            value = state.password,
            onValueChange = { onIntent(SignInIntent.PasswordChanged(it)) },
            label = stringResource(Res.string.field_password),
            modifier = Modifier.testTag("signIn.password"),
            visible = state.passwordVisible,
            onToggleVisible = { onIntent(SignInIntent.TogglePasswordVisibility) },
            errorText = state.passwordError.text(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onIntent(SignInIntent.Submit) }),
        )
        PrimaryButton(
            stringResource(Res.string.sign_in_title),
            { onIntent(SignInIntent.Submit) },
            modifier = Modifier.testTag("signIn.submit"),
            loading = state.submitting,
        )
        if (state.banner !is SignInBanner.Locked) {
            AuthLink(
                AnnotatedString(stringResource(Res.string.sign_in_forgot_password)),
                { onIntent(SignInIntent.ForgotPasswordClicked) },
                modifier = Modifier.testTag("signIn.forgot"),
            )
        }
        AuthLink(
            linkSentence(stringResource(Res.string.authb_new_here), stringResource(Res.string.action_create_account)),
            { onIntent(SignInIntent.CreateAccountClicked) },
            modifier = Modifier.testTag("signIn.createAccount"),
        )
    }
}

@Composable
private fun bannerText(banner: SignInBanner): String = when (banner) {
    SignInBanner.InvalidCredentials -> stringResource(Res.string.error_invalid_credentials)
    is SignInBanner.Locked -> banner.minutes?.let { pluralStringResource(Res.plurals.error_account_locked, it, it) }
        ?: stringResource(Res.string.error_rate_limited_soon)
    is SignInBanner.RateLimited -> banner.minutes?.let { pluralStringResource(Res.plurals.error_rate_limited, it, it) }
        ?: stringResource(Res.string.error_rate_limited_soon)
    SignInBanner.Validation -> stringResource(Res.string.error_validation)
    SignInBanner.Offline -> stringResource(Res.string.error_offline)
    SignInBanner.SessionEnded -> stringResource(Res.string.error_session_ended)
    SignInBanner.PasswordReset -> stringResource(Res.string.error_password_reset_done)
    SignInBanner.PlatformUnsupported -> stringResource(Res.string.error_platform_unsupported)
    is SignInBanner.Error -> errorText(banner.code.message(), banner.requestId)
}

@Composable
internal fun errorText(resource: org.jetbrains.compose.resources.StringResource, requestId: String?): String =
    if (resource == Res.string.error_generic_reference) {
        requestId?.let { stringResource(Res.string.error_generic_reference, it) } ?: stringResource(Res.string.error_generic)
    } else {
        stringResource(resource)
    }

@Preview
@Composable
private fun SignInScreenPreview() {
    AppTheme { SignInScreen(SignInState(email = "sara@example.com", banner = SignInBanner.InvalidCredentials)) {} }
}
