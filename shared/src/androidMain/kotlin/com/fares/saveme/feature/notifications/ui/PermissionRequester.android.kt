package com.fares.saveme.feature.notifications.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fares.saveme.feature.notifications.domain.NotificationPermissionState
import com.fares.saveme.feature.notifications.domain.NotificationPromptGate
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
actual fun rememberNotificationPermissionRequester(): NotificationPermissionRequester {
    val context = LocalContext.current
    val permission: NotificationPermissionState = koinInject()
    val gate: NotificationPromptGate = koinInject()
    val scope = rememberCoroutineScope()
    // Re-evaluated whenever the app resumes: the person may have changed it in the phone's settings (FR-012).
    var resumes by remember { mutableStateOf(0) }
    var askedBefore by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumes) { askedBefore = gate.permissionAsked() }

    return remember(context, permission, gate, resumes, askedBefore) {
        object : NotificationPermissionRequester {
            override fun status(): PermissionStatus {
                if (permission.notificationsAllowed()) return PermissionStatus.Allowed
                if (!permission.canAskAtRuntime()) return PermissionStatus.Blocked
                val activity = context.findActivity()
                val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
                // Asked and refused for good: no rationale to show and the system will not show its dialog again.
                return if (!askedBefore || rationale) PermissionStatus.Askable else PermissionStatus.Blocked
            }

            override fun request(onResult: (Boolean) -> Unit) {
                val activity = context.findActivity() as? ComponentActivity
                if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    onResult(permission.notificationsAllowed())
                    return
                }
                scope.launch { gate.markPermissionAsked() }
                val registry = activity.activityResultRegistry
                var launcher: androidx.activity.result.ActivityResultLauncher<String>? = null
                launcher = registry.register("push-permission-${System.nanoTime()}", ActivityResultContracts.RequestPermission()) { granted ->
                    launcher?.unregister()
                    onResult(granted && permission.notificationsAllowed())
                }
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
actual fun rememberOpenAppNotificationSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { { openAppNotificationSettings(context) } }
}

internal fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
    }
    runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
