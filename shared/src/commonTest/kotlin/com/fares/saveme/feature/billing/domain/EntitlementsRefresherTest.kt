package com.fares.saveme.feature.billing.domain

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.billing.BillingRoutes
import com.fares.saveme.feature.billing.BillingTestEnv
import com.fares.saveme.feature.billing.MutableClock
import com.fares.saveme.feature.billing.count
import com.fares.saveme.feature.billing.signIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class EntitlementsRefresherTest {

    private suspend fun TestScope.setup(gated: Set<String> = emptySet()): Triple<BillingTestEnv, BillingRoutes, Pair<EntitlementsRefresher, MutableClock>> {
        val routes = BillingRoutes(gatedPaths = gated)
        val env = BillingTestEnv { with(routes) { answer(it) } }
        val sessions = env.sessionManager(backgroundScope)
        env.signIn(sessions)
        val clock = MutableClock()
        return Triple(env, routes, EntitlementsRefresher(sessions, env.subscriptions, backgroundScope, clock) to clock)
    }

    @Test
    fun concurrentTriggersShareOneCallToEachEndpoint() = runTest {
        val (env, routes, pair) = setup(gated = setOf("/me/entitlements"))
        val refresher = pair.first
        val results = listOf(RefreshReason.PlanScreen, RefreshReason.NotEntitled, RefreshReason.Foreground).map { reason ->
            async { refresher.refresh(reason) }
        }
        routes.started.await()
        routes.gate.complete(Unit)

        assertEquals(List(3) { RefreshOutcome.Done }, results.awaitAll())
        assertEquals(1, env.requests.count("/me/entitlements"))
        assertEquals(1, env.requests.count("/me/subscription"))
    }

    @Test
    fun foregroundWithinSixtySecondsIsSkippedAfterwardsItRuns() = runTest {
        val (env, _, pair) = setup()
        val (refresher, clock) = pair

        refresher.refresh(RefreshReason.Foreground)
        assertEquals(1, env.requests.count("/me/entitlements"))

        clock.instant += 59.seconds
        refresher.refresh(RefreshReason.Foreground)
        assertEquals(1, env.requests.count("/me/entitlements"), "within the floor")

        clock.instant += 2.seconds
        refresher.refresh(RefreshReason.Foreground)
        assertEquals(2, env.requests.count("/me/entitlements"), "after the floor")
    }

    @Test
    fun otherReasonsAreNeverSkipped() = runTest {
        val (env, _, pair) = setup()
        val refresher = pair.first
        refresher.refresh(RefreshReason.Foreground)
        listOf(RefreshReason.PlanScreen, RefreshReason.BillingAction, RefreshReason.NotEntitled).forEach { refresher.refresh(it) }
        assertEquals(4, env.requests.count("/me/entitlements"))
        assertEquals(4, env.requests.count("/me/subscription"))
    }

    @Test
    fun signInReadsOnlyTheSubscription() = runTest {
        val (env, _, pair) = setup()
        pair.first.refresh(RefreshReason.SignIn)
        assertEquals(0, env.requests.count("/me/entitlements"))
        assertEquals(1, env.requests.count("/me/subscription"))
    }

    @Test
    fun theSubscriptionIsStoredOnSuccess() = runTest {
        val (env, _, pair) = setup()
        pair.first.refresh(RefreshReason.PlanScreen)
        assertEquals("active", env.subscriptions.stored.value!!.status)
    }

    @Test
    fun theSubscriptionIsNotWrittenWhenThePersonSignedOutMidFlight() = runTest {
        val routes = BillingRoutes(gatedPaths = setOf("/me/subscription"))
        val env = BillingTestEnv { with(routes) { answer(it) } }
        val sessions = env.sessionManager(backgroundScope)
        env.signIn(sessions)
        val refresher = EntitlementsRefresher(sessions, env.subscriptions, backgroundScope, MutableClock())

        val result = async { refresher.refresh(RefreshReason.PlanScreen) }
        routes.started.await()
        sessions.signOutLocally(SignOutReason.UserSignedOut)
        routes.gate.complete(Unit)

        assertEquals(RefreshOutcome.Failed, result.await())
        assertNull(env.subscriptions.stored.value)
        assertNull(env.auth.keyValues.values["billing_subscription"])
        assertIs<AuthState.SignedOut>(sessions.state.value)
    }
}
