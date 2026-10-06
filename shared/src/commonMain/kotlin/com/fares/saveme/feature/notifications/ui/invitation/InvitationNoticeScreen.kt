package com.fares.saveme.feature.notifications.ui.invitation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.invitation_notice_body
import saveme.shared.generated.resources.invitation_notice_ok
import saveme.shared.generated.resources.invitation_notice_title

@Composable
fun InvitationNoticeScreen(state: InvitationNoticeState, onIntent: (InvitationNoticeIntent) -> Unit) {
    AppScreen {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(Res.string.invitation_notice_title),
                style = AppTheme.type.title,
                color = AppTheme.colors.ink,
                modifier = Modifier.testTag("invitationNotice.title").semantics { heading() },
            )
            Text(
                stringResource(Res.string.invitation_notice_body, state.address),
                style = AppTheme.type.body,
                color = AppTheme.colors.ink2,
                modifier = Modifier.testTag("invitationNotice.body"),
            )
        }
        PrimaryButton(
            stringResource(Res.string.invitation_notice_ok),
            { onIntent(InvitationNoticeIntent.Ok) },
            modifier = Modifier.testTag("invitationNotice.ok"),
        )
    }
}

@Preview
@Composable
private fun InvitationNoticeScreenPreview() {
    AppTheme { InvitationNoticeScreen(InvitationNoticeState("sara@example.com")) {} }
}
