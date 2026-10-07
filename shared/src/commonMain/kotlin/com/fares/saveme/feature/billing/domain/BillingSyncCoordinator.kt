package com.fares.saveme.feature.billing.domain

import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiEvents
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.billing.data.play.PlayPurchaseUpdate
import com.fares.saveme.feature.billing.domain.play.PassTrigger
import com.fares.saveme.feature.billing.domain.play.PlayPurchaseVerifier
import com.fares.saveme.feature.billing.domain.play.PlayRestoreService
import com.fares.saveme.feature.billing.domain.play.RestoreTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Refresh and Google Play triggers outside any screen: sign-in, app start, foreground, a refusal for the plan and a purchase
 * update from Google Play (spec 005 R-05, R-06; spec 018 R-4).
 */
class BillingSyncCoordinator(
    private val sessions: SessionManager,
    private val refresher: EntitlementsRefresher,
    private val apiEvents: ApiEvents,
    private val verifier: PlayPurchaseVerifier,
    private val restorer: PlayRestoreService,
    private val purchaseUpdates: Flow<PlayPurchaseUpdate>,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private var signedInUserId: String? = null
    private var sawSignedOut = false
    private var lastForegroundPass: Instant? = null

    fun start() {
        sessions.state
            .onEach { state -> onAuthState(state) }
            .launchIn(scope)
        apiEvents.events
            .filterIsInstance<ApiEvent.FeatureNotEntitled>()
            .onEach { if (sessions.state.value is AuthState.SignedIn) refresher.refresh(RefreshReason.NotEntitled) }
            .launchIn(scope)
        purchaseUpdates
            .onEach { update ->
                // A cancelled or failed sheet sends nothing; a purchase is read back from Google Play by the pass (FR-044).
                if (update is PlayPurchaseUpdate.Purchased && sessions.state.value is AuthState.SignedIn) verifier.pass(PassTrigger.Update)
            }
            .launchIn(scope)
    }

    private fun onAuthState(state: AuthState) {
        when (state) {
            AuthState.Unknown -> Unit
            is AuthState.SignedOut -> {
                signedInUserId = null
                sawSignedOut = true
            }
            is AuthState.SignedIn -> {
                val userId = state.account.userId
                if (userId == signedInUserId) return
                // A real sign-in follows a signed-out state (or another person); a cold start resumes a stored session (R-4).
                val realSignIn = sawSignedOut || signedInUserId != null
                signedInUserId = userId
                sawSignedOut = false
                scope.launch { refresher.refresh(RefreshReason.SignIn) }
                scope.launch {
                    if (realSignIn) restorer.restore(RestoreTrigger.SignIn) else verifier.pass(PassTrigger.ColdStart)
                }
            }
        }
    }

    fun onForeground() {
        if (sessions.state.value !is AuthState.SignedIn) return
        scope.launch { refresher.refresh(RefreshReason.Foreground) }
        val now = clock.now()
        val last = lastForegroundPass
        if (last == null || now - last >= FOREGROUND_FLOOR) {
            lastForegroundPass = now
            scope.launch { verifier.pass(PassTrigger.Foreground) }
        }
    }

    private companion object {
        val FOREGROUND_FLOOR = 60.seconds
    }
}
