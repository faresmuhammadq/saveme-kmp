package com.fares.saveme.feature.notifications

import com.fares.saveme.feature.notifications.domain.ChannelLabels
import com.fares.saveme.feature.notifications.domain.NotificationDisplay
import com.fares.saveme.feature.notifications.domain.NotificationPermissionState
import com.fares.saveme.feature.notifications.domain.PostedNotification
import com.fares.saveme.feature.notifications.domain.PushRegistrationScheduler
import com.fares.saveme.feature.notifications.domain.ReconcileTrigger

/** iOS compiles with no push behaviour (spec 017 FR-042, P-15). */
class NoopNotificationDisplay : NotificationDisplay {
    override suspend fun ensureChannels(labels: ChannelLabels) = Unit
    override fun post(notification: PostedNotification): Boolean = false
    override fun cancelAll() = Unit
}

class IosNotificationPermissionState : NotificationPermissionState {
    override fun notificationsAllowed(): Boolean = false
    override fun canAskAtRuntime(): Boolean = false
}

class NoopPushRegistrationScheduler : PushRegistrationScheduler {
    override fun request(trigger: ReconcileTrigger) = Unit
    override fun cancel() = Unit
}
