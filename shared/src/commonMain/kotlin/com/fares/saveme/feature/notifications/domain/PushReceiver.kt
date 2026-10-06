package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.notifications.data.ShownNotificationsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The only entry point a platform push service calls (FR-045): a new push address, or a message received while the app
 * is in the foreground. A message is shown exactly as received, de-duplicated by `notification_id` and collapsed by the
 * contract's key, then triggers a background read of what it concerns (R-09). Nothing is composed from records (FR-028).
 */
class PushReceiver(
    private val sessions: SessionManager,
    private val shown: ShownNotificationsStore,
    private val display: NotificationDisplay,
    private val labels: suspend () -> ChannelLabels,
    private val coordinator: () -> PushCoordinator,
    private val runner: () -> SyncRunner,
    private val scope: CoroutineScope,
) {
    fun onNewToken() = coordinator().onTokenChanged()

    suspend fun onMessage(data: Map<String, String>, title: String?, body: String?) {
        if (sessions.state.value == AuthState.Unknown) sessions.restore()
        val userId = (sessions.state.value as? AuthState.SignedIn)?.account?.userId ?: return
        if (title.isNullOrBlank() || body.isNullOrBlank()) return

        val message = PushPayloadParser.parse(data)
        message.notificationId?.let { if (!shown.markShown(userId, it)) return }

        display.ensureChannels(labels())
        val channel = (message.category ?: FALLBACK).wire
        display.post(PostedNotification(channel, message.collapseKey, title, body, PushTapKeys.filter(data)))
        pushLog.i { "received:${message.typeName}" }

        if (message is PushMessage.Unknown || message is PushMessage.Malformed) return
        when (val read = PushRouter.route(message).read) {
            PushRead.User -> scope.launch { runner().refresh(ReadTarget.User, force = true) }
            is PushRead.Wallet -> scope.launch { runner().refresh(ReadTarget.Wallet(read.id), force = true) }
            PushRead.Everything -> scope.launch { runner().refresh(ReadTarget.Everything, force = true) }
            // Entitlements and the account are cheap and read on tap (R-09).
            PushRead.None, PushRead.Entitlements, PushRead.Account -> Unit
        }
    }

    private companion object {
        /** An unknown type that names no category goes to the first channel; a channel that does not exist would drop it (assumed default). */
        val FALLBACK = NotificationCategory.RecurringDue
    }
}
