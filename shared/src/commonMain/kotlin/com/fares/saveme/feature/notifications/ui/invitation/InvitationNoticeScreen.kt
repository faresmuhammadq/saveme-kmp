package com.fares.saveme.feature.notifications.ui.invitation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.CalmIconTile
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.component.styledParts
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.FlowMailUnread
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.invitation_notice_body
import saveme.shared.generated.resources.invitation_notice_ok
import saveme.shared.generated.resources.invitation_notice_title

@Composable
fun InvitationNoticeScreen(state: InvitationNoticeState, onIntent: (InvitationNoticeIntent) -> Unit) {
    Column(Modifier.fillMaxSize().background(AppTheme.colors.primaryContainer).windowInsetsPadding(WindowInsets.navigationBars)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 120.dp),
        ) {
            CalmIconTile(
                AppIcons.FlowMailUnread,
                AppTheme.colors.primary,
                AppTheme.colors.surface,
                size = 84.dp,
                iconSize = 44.dp,
                radius = 28.dp,
            )
            Text(
                stringResource(Res.string.invitation_notice_title),
                style = AppTheme.type.headline.copy(fontSize = 34.sp, lineHeight = 38.sp),
                color = AppTheme.colors.ink,
                modifier = Modifier.testTag("invitationNotice.title").semantics { heading() },
            )
            Text(
                styledParts(stringResource(Res.string.invitation_notice_body, state.address), state.address, color = AppTheme.colors.ink),
                style = AppTheme.type.body.copy(fontSize = 16.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium),
                color = AppTheme.colors.ink2,
                modifier = Modifier.testTag("invitationNotice.body"),
            )
        }
        PrimaryButton(
            stringResource(Res.string.invitation_notice_ok),
            { onIntent(InvitationNoticeIntent.Ok) },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 26.dp).testTag("invitationNotice.ok"),
        )
    }
}

@Preview
@Composable
private fun InvitationNoticeScreenPreview() {
    AppTheme { InvitationNoticeScreen(InvitationNoticeState("sara@example.com")) {} }
}
