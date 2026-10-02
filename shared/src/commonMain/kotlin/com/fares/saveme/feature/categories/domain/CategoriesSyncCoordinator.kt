package com.fares.saveme.feature.categories.domain

import com.fares.saveme.core.sync.SyncScheduler
import com.fares.saveme.feature.auth.domain.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Refresh triggers outside any screen: signing in and coming back to the foreground (research
 * R-08). Both also ask the sync engine to send whatever is waiting. The 60 second floor for
 * foreground returns lives in [CategoriesService.refresh].
 */
class CategoriesSyncCoordinator(
    private val authState: StateFlow<AuthState>,
    private val categories: CategoriesService,
    private val scope: CoroutineScope,
    private val scheduler: SyncScheduler? = null,
) {
    fun start() {
        authState
            .map { (it as? AuthState.SignedIn)?.account?.userId }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach {
                scheduler?.enqueue()
                categories.refresh(force = true)
            }
            .launchIn(scope)
    }

    fun onForeground() {
        if (authState.value !is AuthState.SignedIn) return
        scheduler?.enqueue()
        scope.launch { categories.refresh(force = false) }
    }
}
