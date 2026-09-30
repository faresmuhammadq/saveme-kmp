package com.fares.saveme.feature.auth.ui.verify

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.ui.signin.errorText
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

@Composable
fun VerifyEmailScreen(state: VerifyEmailState, onIntent: (VerifyEmailIntent) -> Unit) {
    AppScreen {
        Text(
            stringResource(Res.string.verify_title),
            style = AppTheme.type.headline,
            color = AppTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        when (state.status) {
            VerifyStatus.Verifying -> Text(stringResource(Res.string.verify_verifying), style = AppTheme.type.body, color = AppTheme.colors.ink2)
            VerifyStatus.Verified -> {
                InlineAlert(tone = AlertTone.Info, text = stringResource(Res.string.verify_done))
                PrimaryButton(
                    stringResource(Res.string.action_continue),
                    { onIntent(VerifyEmailIntent.ContinueClicked) },
                    modifier = Modifier.testTag("verifyEmail.continue"),
                )
            }
            VerifyStatus.LinkInvalid -> {
                InlineAlert(tone = AlertTone.Error, text = stringResource(Res.string.error_verification_link_invalid))
                if (state.signedIn) {
                    PrimaryButton(
                        stringResource(Res.string.action_resend_email),
                        { onIntent(VerifyEmailIntent.ResendClicked) },
                        modifier = Modifier.testTag("verifyEmail.resend"),
                        enabled = state.resend.canResend,
                        loading = state.resend.sending,
                    )
                    state.resend.message?.let { Text(it.text(), style = AppTheme.type.caption, color = AppTheme.colors.ink2) }
                    SecondaryButton(
                        stringResource(Res.string.action_continue),
                        { onIntent(VerifyEmailIntent.ContinueClicked) },
                        modifier = Modifier.testTag("verifyEmail.continue"),
                    )
                } else {
                    PrimaryButton(
                        stringResource(Res.string.action_sign_in),
                        { onIntent(VerifyEmailIntent.SignInClicked) },
                        modifier = Modifier.testTag("verifyEmail.signIn"),
                    )
                }
            }
            VerifyStatus.Offline, VerifyStatus.Error -> {
                InlineAlert(
                    tone = if (state.status == VerifyStatus.Offline) AlertTone.Warning else AlertTone.Error,
                    text = if (state.status == VerifyStatus.Offline) {
                        stringResource(Res.string.error_offline)
                    } else {
                        errorText(Res.string.error_generic_reference, state.requestId)
                    },
                )
                PrimaryButton(
                    stringResource(Res.string.action_retry),
                    { onIntent(VerifyEmailIntent.Retry) },
                    modifier = Modifier.testTag("verifyEmail.retry"),
                )
            }
        }
    }
}

@Preview
@Composable
private fun VerifyEmailScreenPreview() {
    AppTheme { VerifyEmailScreen(VerifyEmailState(status = VerifyStatus.LinkInvalid, signedIn = true)) {} }
}
