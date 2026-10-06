package com.fares.saveme.feature.notifications.ui

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionRequester(): NotificationPermissionRequester = object : NotificationPermissionRequester {
    override fun status(): PermissionStatus = PermissionStatus.Blocked
    override fun request(onResult: (Boolean) -> Unit) = onResult(false)
}

@Composable
actual fun rememberOpenAppNotificationSettings(): () -> Unit = {}
