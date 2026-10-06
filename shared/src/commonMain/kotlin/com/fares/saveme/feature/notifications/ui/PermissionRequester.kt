package com.fares.saveme.feature.notifications.ui

import androidx.compose.runtime.Composable

/** What the phone says about notifications for the app (research R-14). */
enum class PermissionStatus {
    Allowed,

    /** The system can still show its dialog. */
    Askable,

    /** Refused twice, blocked in settings, or Android 12 and earlier with notifications off: only the phone's settings help. */
    Blocked,
}

/** The UI seam of the permission, because the rationale flag and the dialog need an activity (FR-010, FR-012). */
interface NotificationPermissionRequester {
    fun status(): PermissionStatus

    /** Shows the system dialog; [onResult] gets whether notifications are now allowed. Only ever called from a person's tap (FR-010). */
    fun request(onResult: (Boolean) -> Unit)
}

/** Android: the runtime permission on 33+, re-read when the app resumes. iOS: always [PermissionStatus.Blocked] (no push, FR-042). */
@Composable
expect fun rememberNotificationPermissionRequester(): NotificationPermissionRequester

/** Opens the system notification settings of the app (FR-021); a no-op where there is no push. */
@Composable
expect fun rememberOpenAppNotificationSettings(): () -> Unit
