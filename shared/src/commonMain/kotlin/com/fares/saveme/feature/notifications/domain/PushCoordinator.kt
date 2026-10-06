package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The triggers outside any screen (research R-06): signing in, the foreground, a new push address and a permission
 * change. Each only asks the scheduler; the job decides what, if anything, to send.
 */
class PushCoordinator(
    private val sessions: SessionManager,
    private val scheduler: PushRegistrationScheduler,
    private val scope: CoroutineScope,
) {
    fun start() {
        sessions.state
            .distinctUntilChangedBy { it is AuthState.SignedIn }
            .filter { it is AuthState.SignedIn }
            .onEach { scheduler.request(ReconcileTrigger.SignedIn) }
            .launchIn(scope)
    }

    /** Back in the foreground, signed in: also where a permission revoked in system settings is noticed (FR-006). */
    fun onForeground() {
        if (sessions.state.value is AuthState.SignedIn) scheduler.request(ReconcileTrigger.Foreground)
    }

    /** Not gated on the session state: a new address can arrive in a fresh process before the session is restored. */
    fun onTokenChanged() = scheduler.request(ReconcileTrigger.TokenChanged)

    fun onPermissionChanged() = scheduler.request(ReconcileTrigger.PermissionChanged)
}
