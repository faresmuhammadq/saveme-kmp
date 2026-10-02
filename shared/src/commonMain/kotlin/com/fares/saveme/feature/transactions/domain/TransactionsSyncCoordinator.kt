package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.auth.domain.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Refresh triggers outside any screen (research R-08): signing in starts the balance reconciler,
 * which settles carries as they appear, and coming back to the foreground asks it to try now. The
 * open transactions screen refreshes its own wallet when its lifecycle resumes (the `Foregrounded`
 * intent), because only it knows which wallet is open.
 */
class TransactionsSyncCoordinator(
    private val authState: StateFlow<AuthState>,
    private val reconciler: BalanceReconciler,
    private val scope: CoroutineScope,
) {
    fun start() {
        authState
            .map { (it as? AuthState.SignedIn)?.account?.userId }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { reconciler.start() }
            .launchIn(scope)
    }

    fun onForeground() {
        if (authState.value !is AuthState.SignedIn) return
        reconciler.kick()
    }
}
