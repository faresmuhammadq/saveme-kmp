package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.core.session.SignOutParticipant
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.core.storage.SecureStore
import com.fares.saveme.feature.notifications.data.NotificationsApi
import com.fares.saveme.feature.notifications.data.PushRegistrationStore
import com.fares.saveme.feature.notifications.data.ShownNotificationsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Sign-out, step (a): while the stored session is still valid, one best-effort `DELETE` for this session when it is
 * registered. Any answer is ignored; the bound is the sign-out's (FR-007). Never runs for a remote session end.
 */
class PushSignOutParticipant(
    private val api: NotificationsApi,
    private val store: PushRegistrationStore,
    private val secureStore: SecureStore,
) : SignOutParticipant {
    override suspend fun beforeSignOut() {
        val session = secureStore.read() ?: return
        val record = store.read() ?: return
        if (record.sessionId != session.sessionId || record.stoppedFor != null) return
        api.unregisterDevice(session.sessionId)
    }
}

/**
 * Sign-out step (c) and session end alike: nothing of one person may reach the next on a shared phone (FR-043). The
 * address is discarded on the app scope with a bound, so the sign-out never waits for the push service.
 */
class PushDataCleaner(
    private val display: NotificationDisplay,
    private val store: PushRegistrationStore,
    private val shown: ShownNotificationsStore,
    private val scheduler: PushRegistrationScheduler,
    private val provider: PushTokenProvider,
    private val scope: CoroutineScope,
    private val discardBound: Duration = 10.seconds,
) : UserDataCleaner {
    override suspend fun clear() {
        display.cancelAll()
        store.clear()
        shown.clear()
        scheduler.cancel()
        if (provider.isAvailable) {
            scope.launch { withTimeoutOrNull(discardBound) { provider.discard() } }
        }
    }
}
