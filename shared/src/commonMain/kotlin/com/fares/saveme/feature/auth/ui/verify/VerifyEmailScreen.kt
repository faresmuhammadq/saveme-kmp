package com.fares.saveme.feature.auth.ui.verify

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.FlowCheckCircle
import com.fares.saveme.designsystem.icon.FlowLinkOff
import com.fares.saveme.designsystem.icon.FlowMail
import com.fares.saveme.feature.auth.ui.AuthBadge
import com.fares.saveme.feature.auth.ui.AuthStatusScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.CalmIconPrimaryButton
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.styledParts
import com.fares.saveme.designsystem.icon.FlowSend
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.auth.ui.signin.errorText
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

@Composable
fun VerifyEmailScreen(state: VerifyEmailState, onIntent: (VerifyEmailIntent) -> Unit) {
    val money = AppTheme.money
    val colors = AppTheme.colors
    val (tint, accent, icon) = when (state.status) {
        VerifyStatus.Verifying -> Triple(colors.primaryContainer, colors.primary, AppIcons.FlowMail)
        VerifyStatus.Verified -> Triple(money.positiveContainer, money.positive, AppIcons.FlowCheckCircle)
        VerifyStatus.LinkInvalid -> Triple(money.pendingContainer, money.pending, AppIcons.FlowLinkOff)
        VerifyStatus.Offline -> Triple(money.pendingContainer, money.pending, AppIcons.CloudOff)
        VerifyStatus.Error -> Triple(money.overContainer, money.over, AppIcons.Error)
    }
    val message = when (state.status) {
        VerifyStatus.Verifying -> stringResource(Res.string.verify_verifying)
        VerifyStatus.Verified -> stringResource(Res.string.verify_done)
        VerifyStatus.LinkInvalid -> stringResource(Res.string.error_verification_link_invalid)
        VerifyStatus.Offline -> stringResource(Res.string.error_offline)
        VerifyStatus.Error -> errorText(Res.string.error_generic_reference, state.requestId)
    }
    AuthStatusScreen(
        tint = tint,
        header = {
            AuthBadge(icon, accent, size = 72.dp, iconSize = 38.dp, radius = 24.dp)
            Text(
                stringResource(Res.string.verify_title),
                style = AppTheme.type.label.copy(fontWeight = FontWeight.Bold),
                color = accent,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                message,
                style = AppTheme.type.headline.copy(fontSize = 34.sp, lineHeight = 37.sp),
                color = colors.ink,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
        },
        panel = {
            if (state.status == VerifyStatus.LinkInvalid && state.signedIn) {
                state.email?.let { email ->
                    Text(
                        styledParts(stringResource(Res.string.authb_verify_resend_body, email), email, color = colors.ink),
                        style = AppTheme.type.body.copy(fontSize = 15.5.sp, lineHeight = 24.sp),
                        color = colors.ink2,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                state.resend.message?.let {
                    Text(it.text(), style = AppTheme.type.body, color = colors.ink2, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        actions = {
            when (state.status) {
                VerifyStatus.Verifying -> Unit
                VerifyStatus.Verified -> PrimaryButton(
                    stringResource(Res.string.action_continue),
                    { onIntent(VerifyEmailIntent.ContinueClicked) },
                    modifier = Modifier.testTag("verifyEmail.continue"),
                )
                VerifyStatus.LinkInvalid -> if (state.signedIn) {
                    CalmIconPrimaryButton(
                        stringResource(Res.string.action_resend_email),
                        AppIcons.FlowSend,
                        { onIntent(VerifyEmailIntent.ResendClicked) },
                        modifier = Modifier.testTag("verifyEmail.resend"),
                        enabled = state.resend.canResend,
                        loading = state.resend.sending,
                    )
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
                VerifyStatus.Offline, VerifyStatus.Error -> PrimaryButton(
                    stringResource(Res.string.action_retry),
                    { onIntent(VerifyEmailIntent.Retry) },
                    modifier = Modifier.testTag("verifyEmail.retry"),
                )
            }
        },
    )
}

@Preview
@Composable
private fun VerifyEmailScreenPreview() {
    AppTheme { VerifyEmailScreen(VerifyEmailState(status = VerifyStatus.LinkInvalid, signedIn = true)) {} }
}
