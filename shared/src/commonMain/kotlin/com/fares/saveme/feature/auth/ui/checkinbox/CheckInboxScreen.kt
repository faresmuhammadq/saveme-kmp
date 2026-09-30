package com.fares.saveme.feature.auth.ui.checkinbox

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

@Composable
fun CheckInboxScreen(state: CheckInboxState, onIntent: (CheckInboxIntent) -> Unit) {
    AppScreen {
        Text(
            stringResource(Res.string.check_inbox_title),
            style = AppTheme.type.headline,
            color = AppTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            when (state.kind) {
                CheckInboxKind.Verification -> stringResource(Res.string.check_inbox_verification, state.email)
                CheckInboxKind.Reset -> stringResource(Res.string.check_inbox_reset)
            },
            style = AppTheme.type.body,
            color = AppTheme.colors.ink2,
        )
        PrimaryButton(
            stringResource(Res.string.action_go_to_sign_in),
            { onIntent(CheckInboxIntent.GoToSignInClicked) },
            modifier = Modifier.testTag("checkInbox.goToSignIn"),
        )
    }
}

@Preview
@Composable
private fun CheckInboxScreenPreview() {
    AppTheme { CheckInboxScreen(CheckInboxState("sara@example.com", CheckInboxKind.Verification)) {} }
}
