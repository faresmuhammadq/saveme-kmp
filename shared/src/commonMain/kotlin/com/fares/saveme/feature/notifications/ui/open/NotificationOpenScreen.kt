package com.fares.saveme.feature.notifications.ui.open

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.push_opening

@Composable
fun NotificationOpenScreen(onIntent: (NotificationOpenIntent) -> Unit) {
    AppScreen(scrollable = false) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.m, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(AppTheme.spacing.xl),
        ) {
            CircularProgressIndicator(color = AppTheme.colors.primary)
            Text(
                stringResource(Res.string.push_opening),
                style = AppTheme.type.title,
                color = AppTheme.colors.ink,
                modifier = Modifier.testTag("open.progress").semantics { liveRegion = LiveRegionMode.Polite },
            )
            SecondaryButton(
                stringResource(Res.string.action_cancel),
                { onIntent(NotificationOpenIntent.Cancel) },
                modifier = Modifier.testTag("open.cancel"),
            )
        }
    }
}

@Preview
@Composable
private fun NotificationOpenScreenPreview() {
    AppTheme { NotificationOpenScreen {} }
}
