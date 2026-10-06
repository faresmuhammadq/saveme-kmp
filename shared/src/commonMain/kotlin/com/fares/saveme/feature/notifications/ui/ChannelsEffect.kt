package com.fares.saveme.feature.notifications.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.feature.notifications.domain.NotificationDisplay
import com.fares.saveme.feature.notifications.domain.PushTokenProvider
import org.koin.compose.koinInject

/**
 * Creates the six channels at app start and renames them whenever the app language changes (FR-022). Placed inside the
 * locale provider, so the resources already resolve in the app language. Builds without a push provider create none.
 */
@Composable
fun ChannelsEffect(
    language: AppLanguage,
    provider: PushTokenProvider = koinInject(),
    display: NotificationDisplay = koinInject(),
) {
    LaunchedEffect(language, provider) {
        if (provider.isAvailable) display.ensureChannels(resolveChannelLabels())
    }
}
