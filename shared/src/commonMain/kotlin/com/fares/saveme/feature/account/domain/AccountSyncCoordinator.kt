package com.fares.saveme.feature.account.domain

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Refresh triggers outside any screen: signing in and coming back to the foreground (R-02). */
class AccountSyncCoordinator(
    private val sessions: SessionManager,
    private val refresher: AccountRefresher,
    private val scope: CoroutineScope,
) {
    fun start() {
        sessions.state
            .distinctUntilChangedBy { it is AuthState.SignedIn }
            .filter { it is AuthState.SignedIn }
            .onEach { refresher.refresh() }
            .launchIn(scope)
    }

    fun onForeground() {
        if (sessions.state.value is AuthState.SignedIn) scope.launch { refresher.refresh() }
    }
}
