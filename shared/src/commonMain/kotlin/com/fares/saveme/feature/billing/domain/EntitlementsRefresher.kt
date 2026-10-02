package com.fares.saveme.feature.billing.domain

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.billing.data.SubscriptionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

enum class RefreshReason { SignIn, Foreground, PlanScreen, BillingAction, NotEntitled }

/**
 * Runs one refresh at a time; a trigger during a refresh awaits the same result (FR-006). A
 * foreground return within [foregroundFloor] of the last success does nothing; every other
 * reason is never skipped. A result for a person who is no longer signed in is dropped (FR-008).
 */
class EntitlementsRefresher(
    private val sessions: SessionManager,
    private val subscriptions: SubscriptionRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val foregroundFloor: Duration = 60.seconds,
) {
    private val lock = Mutex()
    private var inFlight: Deferred<RefreshOutcome>? = null
    private var lastSuccess: Instant? = null

    suspend fun refresh(reason: RefreshReason): RefreshOutcome {
        val running = lock.withLock {
            inFlight ?: run {
                if (reason == RefreshReason.Foreground && withinFloor()) return RefreshOutcome.Done
                // Lazy: it must not run (and clear itself) while this call still holds the lock.
                scope.async(start = CoroutineStart.LAZY) { runRefresh(reason) }.also { inFlight = it }
            }
        }
        return running.await()
    }

    private fun withinFloor(): Boolean {
        val last = lastSuccess ?: return false
        return clock.now() - last < foregroundFloor
    }

    private suspend fun runRefresh(reason: RefreshReason): RefreshOutcome {
        try {
            val userId = (sessions.state.value as? AuthState.SignedIn)?.account?.userId ?: return RefreshOutcome.Failed
            val outcome = coroutineScope {
                // Sign-in already carried the entitlements; only the subscription is new then.
                val entitlements: Deferred<RefreshOutcome>? = if (reason == RefreshReason.SignIn) null else async { sessions.refreshEntitlements() }
                val subscription = async { refreshSubscription(userId) }
                combine(entitlements?.await(), subscription.await())
            }
            if (outcome == RefreshOutcome.Done) lastSuccess = clock.now()
            return outcome
        } catch (e: CancellationException) {
            throw e
        } finally {
            lock.withLock { inFlight = null }
        }
    }

    private suspend fun refreshSubscription(userId: String): RefreshOutcome = when (val result = subscriptions.fetch()) {
        is ApiResult.Success -> {
            if ((sessions.state.value as? AuthState.SignedIn)?.account?.userId == userId) {
                subscriptions.save(result.value)
                RefreshOutcome.Done
            } else {
                RefreshOutcome.Failed
            }
        }
        ApiResult.Offline, ApiResult.Timeout -> RefreshOutcome.Offline
        is ApiResult.Failure -> RefreshOutcome.Failed
    }

    private fun combine(entitlements: RefreshOutcome?, subscription: RefreshOutcome): RefreshOutcome {
        val all = listOfNotNull(entitlements, subscription)
        return when {
            all.all { it == RefreshOutcome.Done } -> RefreshOutcome.Done
            all.any { it == RefreshOutcome.Offline } -> RefreshOutcome.Offline
            else -> RefreshOutcome.Failed
        }
    }
}
