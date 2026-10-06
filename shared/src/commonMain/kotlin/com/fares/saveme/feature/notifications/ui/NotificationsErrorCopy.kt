package com.fares.saveme.feature.notifications.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.error_generic
import saveme.shared.generated.resources.error_generic_reference
import saveme.shared.generated.resources.notifications_not_loaded
import saveme.shared.generated.resources.notifications_offline
import saveme.shared.generated.resources.notifications_rate_limited_later
import saveme.shared.generated.resources.notifications_rate_limited_minute
import saveme.shared.generated.resources.notifications_rate_limited_minutes
import saveme.shared.generated.resources.notifications_conflict
import com.fares.saveme.feature.notifications.ui.settings.LoadMessage
import com.fares.saveme.feature.notifications.ui.settings.SettingsMessage

/** Copy by outcome only, never server text (P-02.4, P-06.5). */
@Composable
fun SettingsMessage.text(): String = when (this) {
    SettingsMessage.Conflict -> stringResource(Res.string.notifications_conflict)
    SettingsMessage.Offline -> stringResource(Res.string.notifications_offline)
    is SettingsMessage.RateLimited -> when (val seconds = seconds) {
        null -> stringResource(Res.string.notifications_rate_limited_later)
        else -> if (seconds < 60) {
            stringResource(Res.string.notifications_rate_limited_minute)
        } else {
            stringResource(Res.string.notifications_rate_limited_minutes, minutesFor(seconds))
        }
    }
    is SettingsMessage.Failed -> requestId?.let { stringResource(Res.string.error_generic_reference, it) } ?: stringResource(Res.string.error_generic)
}

@Composable
fun LoadMessage.text(): String = when (this) {
    LoadMessage.Offline -> stringResource(Res.string.notifications_not_loaded)
    is LoadMessage.Failed -> requestId?.let { stringResource(Res.string.error_generic_reference, it) } ?: stringResource(Res.string.error_generic)
}

/** Whole minutes, rounded up (FR-018). */
fun minutesFor(seconds: Long): Int = ((seconds + 59) / 60).toInt()
