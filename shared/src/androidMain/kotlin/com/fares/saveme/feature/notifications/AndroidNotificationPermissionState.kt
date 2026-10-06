package com.fares.saveme.feature.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fares.saveme.feature.notifications.domain.NotificationPermissionState

/** Allowed = the runtime permission (Android 13+) granted and the app's notifications not blocked in settings (R-14). */
class AndroidNotificationPermissionState(private val context: Context) : NotificationPermissionState {

    override fun notificationsAllowed(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    override fun canAskAtRuntime(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
