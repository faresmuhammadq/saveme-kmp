package com.fares.saveme.feature.auth.ui.checkinbox

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.CalmOutlinedButton
import com.fares.saveme.designsystem.component.styledParts
import com.fares.saveme.feature.auth.ui.AuthStep
import com.fares.saveme.feature.auth.ui.AuthStepMarker
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.fares.saveme.designsystem.icon.FlowCheckCircle
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.FlowMailUnread
import com.fares.saveme.feature.auth.ui.AuthBadge
import com.fares.saveme.feature.auth.ui.AuthStatusScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

@Composable
fun CheckInboxScreen(state: CheckInboxState, onIntent: (CheckInboxIntent) -> Unit) {
    AuthStatusScreen(
        tint = AppTheme.colors.primaryContainer,
        header = {
            AuthBadge(AppIcons.FlowMailUnread, AppTheme.colors.primary, size = 72.dp, iconSize = 38.dp, radius = 24.dp)
            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(Res.string.check_inbox_title),
                style = AppTheme.type.headline.copy(fontSize = 36.sp, lineHeight = 40.sp),
                color = AppTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            if (state.kind == CheckInboxKind.Verification && state.email.isNotBlank()) {
                Text(
                    state.email,
                    style = AppTheme.type.label.copy(fontWeight = FontWeight.Bold),
                    color = AppTheme.colors.ink,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .background(AppTheme.colors.surface, AppTheme.shapes.pill)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        },
        panel = {
            val verification = state.kind == CheckInboxKind.Verification
            val phone = stringResource(Res.string.authb_inbox_this_phone)
            AuthStep(
                marker = { AuthStepMarker({ Icon(AppIcons.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }, AppTheme.money.positive) },
                text = AnnotatedString(
                    if (verification) stringResource(Res.string.authb_inbox_step1) else stringResource(Res.string.check_inbox_reset),
                ),
                first = true,
            )
            AuthStep(
                marker = { AuthStepMarker({ StepNumber("2", AppTheme.colors.onPrimary) }, AppTheme.colors.primary) },
                text = styledParts(
                    stringResource(if (verification) Res.string.authb_inbox_step2 else Res.string.authb_inbox_reset_step2, phone),
                    phone,
                ),
                first = false,
            )
            AuthStep(
                marker = { AuthStepMarker({ StepNumber("3", AppTheme.colors.ink2) }, AppTheme.colors.surface, border = AppTheme.colors.outlineVariant) },
                text = AnnotatedString(stringResource(if (verification) Res.string.authb_inbox_step3 else Res.string.authb_inbox_reset_step3)),
                first = false,
                muted = true,
            )
        },
        actions = {
            CalmOutlinedButton(
                stringResource(Res.string.action_go_to_sign_in),
                { onIntent(CheckInboxIntent.GoToSignInClicked) },
                modifier = Modifier.testTag("checkInbox.goToSignIn"),
            )
        },
    )
}

@Composable
private fun StepNumber(number: String, color: Color) {
    Text(number, style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = color)
}

@Preview
@Composable
private fun CheckInboxScreenPreview() {
    AppTheme { CheckInboxScreen(CheckInboxState("sara@example.com", CheckInboxKind.Verification)) {} }
}
