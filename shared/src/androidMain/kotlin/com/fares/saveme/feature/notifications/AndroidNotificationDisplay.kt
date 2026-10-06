package com.fares.saveme.feature.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fares.saveme.feature.notifications.domain.ChannelLabels
import com.fares.saveme.feature.notifications.domain.NotificationDisplay
import com.fares.saveme.feature.notifications.domain.NotificationPermissionState
import com.fares.saveme.feature.notifications.domain.PostedNotification

/**
 * Channels and posting through the system notification service (research R-08, R-09). [smallIcon] is the host's
 * monochrome icon. Text is exactly what was received; the lock screen shows the public version instead (FR-023).
 */
class AndroidNotificationDisplay(
    private val context: Context,
    private val smallIcon: Int,
    private val permission: NotificationPermissionState,
) : NotificationDisplay {

    @Volatile
    private var publicText: String = "New notification"

    override suspend fun ensureChannels(labels: ChannelLabels) {
        publicText = labels.publicText
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Idempotent: a second call renames and re-describes, which is how a language change reaches the channels. Never deleted (FR-022).
        manager.createNotificationChannels(
            labels.channels.map { (category, label) ->
                NotificationChannel(category.wire, label.name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = label.description
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    setShowBadge(true)
                }
            },
        )
    }

    override fun post(notification: PostedNotification): Boolean {
        if (!permission.notificationsAllowed()) return false
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        PushTapExtras.put(launch, notification.tap)
        val tap = PendingIntent.getActivity(
            context,
            notification.tag.hashCode(),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public = NotificationCompat.Builder(context, notification.channel)
            .setSmallIcon(smallIcon)
            .setContentTitle(publicText)
            .build()
        val built = NotificationCompat.Builder(context, notification.channel)
            .setSmallIcon(smallIcon)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(tap)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(notification.tag, 0, built)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    override fun cancelAll() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
