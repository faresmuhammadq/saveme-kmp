package com.fares.saveme.feature.notifications.domain

/**
 * The only part of the feature that knows the push service (FR-045). Implementations: `UnavailablePushTokenProvider`
 * (no push in this build), `DevFakePushTokenProvider` (development builds) and, in the gated phase, an FCM adapter that
 * lives only in `:androidApp`. The address itself never leaves the provider except into the request that sends it.
 */
interface PushTokenProvider {
    /** False when this build has no push provider: no Notifications row, no sheet, no request, no registration. */
    val isAvailable: Boolean

    /** The current address, or null until the push service has issued one (it then calls [PushReceiver.onNewToken]). */
    suspend fun currentToken(): String?

    /** Forgets the address on the phone (sign-out), so a signed-out phone stops receiving. */
    suspend fun discard()
}

/** Staging, production and iOS until the Firebase phase lands (FR-046). */
object UnavailablePushTokenProvider : PushTokenProvider {
    override val isAvailable: Boolean = false
    override suspend fun currentToken(): String? = null
    override suspend fun discard() = Unit
}

/** Names and descriptions of the six channels in the app language, plus the lock-screen text, resolved by common code (R-08). */
data class ChannelLabels(
    val channels: Map<NotificationCategory, ChannelLabel>,
    /** Shown instead of the text on a secure lock screen ("New notification"). */
    val publicText: String,
)

data class ChannelLabel(val name: String, val description: String)

/** One message to show; its text is exactly what was received (FR-023, FR-028). Prints the channel only (P-09.2). */
class PostedNotification(
    val channel: String,
    val tag: String,
    val title: String,
    val body: String,
    /** The contract's data keys only, for the tap (FR-044). */
    val tap: Map<String, String>,
) {
    override fun toString() = "PostedNotification(channel=$channel)"
}

interface NotificationDisplay {
    /** Creates the channels, or renames them when the language changed; never deletes (FR-022). */
    suspend fun ensureChannels(labels: ChannelLabels)

    /** False when the phone does not allow notifications for the app. */
    fun post(notification: PostedNotification): Boolean

    fun cancelAll()
}

interface NotificationPermissionState {
    fun notificationsAllowed(): Boolean

    /** True where the system can still show its dialog (Android 13+). */
    fun canAskAtRuntime(): Boolean
}

enum class ReconcileTrigger { SignedIn, TokenChanged, PermissionChanged, Foreground }

sealed interface ReconcileOutcome {
    data object Nothing : ReconcileOutcome
    data object Registered : ReconcileOutcome
    data object Unregistered : ReconcileOutcome
    data object Stopped : ReconcileOutcome
    data object RetryLater : ReconcileOutcome
    data class RetryAfter(val seconds: Long) : ReconcileOutcome
}

/** Runs one idempotent reconcile in the background, surviving process death (R-05). */
interface PushRegistrationScheduler {
    fun request(trigger: ReconcileTrigger)
    fun cancel()
}
