package com.fares.saveme.feature.billing.domain

import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiEvents
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Refresh triggers outside any screen: sign-in, foreground and a refusal for the plan (R-05, R-06). */
class BillingSyncCoordinator(
    private val sessions: SessionManager,
    private val refresher: EntitlementsRefresher,
    private val apiEvents: ApiEvents,
    private val scope: CoroutineScope,
) {
    fun start() {
        sessions.state
            .map { (it as? AuthState.SignedIn)?.account?.userId }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { refresher.refresh(RefreshReason.SignIn) }
            .launchIn(scope)
        apiEvents.events
            .filterIsInstance<ApiEvent.FeatureNotEntitled>()
            .onEach { if (sessions.state.value is AuthState.SignedIn) refresher.refresh(RefreshReason.NotEntitled) }
            .launchIn(scope)
    }

    fun onForeground() {
        if (sessions.state.value is AuthState.SignedIn) scope.launch { refresher.refresh(RefreshReason.Foreground) }
    }
}
