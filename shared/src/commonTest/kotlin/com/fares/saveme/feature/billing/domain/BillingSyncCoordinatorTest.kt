package com.fares.saveme.feature.billing.domain

import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.feature.billing.BillingRoutes
import com.fares.saveme.feature.billing.BillingTestEnv
import com.fares.saveme.feature.billing.MutableClock
import com.fares.saveme.feature.billing.count
import com.fares.saveme.feature.billing.signIn
import com.fares.saveme.feature.billing.waitUntil
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BillingSyncCoordinatorTest {

    private class Built(
        val env: BillingTestEnv,
        val sessions: com.fares.saveme.feature.auth.domain.SessionManager,
        val coordinator: BillingSyncCoordinator,
        val refresher: EntitlementsRefresher,
    ) {
        operator fun component1() = env
        operator fun component2() = sessions
        operator fun component3() = coordinator
    }

    private fun TestScope.build(): Built {
        val routes = BillingRoutes()
        val env = BillingTestEnv { with(routes) { answer(it) } }
        val sessions = env.sessionManager(backgroundScope)
        val refresher = EntitlementsRefresher(sessions, env.subscriptions, backgroundScope, MutableClock())
        val coordinator = BillingSyncCoordinator(sessions, refresher, env.events, backgroundScope).also { it.start() }
        return Built(env, sessions, coordinator, refresher)
    }

    @Test
    fun aNewSignedInPersonTriggersASubscriptionReadOnly() = runTest {
        val (env, sessions, _) = build()
        runCurrent()
        env.signIn(sessions)
        runCurrent()
        waitUntil { env.requests.count("/me/subscription") == 1 }
        runCurrent()
        assertEquals(1, env.requests.count("/me/subscription"))
        assertEquals(0, env.requests.count("/me/entitlements"))
    }

    @Test
    fun foregroundWhileSignedOutDoesNothing() = runTest {
        val (env, _, coordinator) = build()
        runCurrent()
        coordinator.onForeground()
        runCurrent()
        assertEquals(0, env.requests.size)
    }

    @Test
    fun aFeatureNotEntitledRefusalTriggersANeverSkippedRefresh() = runTest {
        val built = build()
        val (env, sessions, _) = built
        env.signIn(sessions)
        runCurrent()
        waitUntil { env.requests.count("/me/subscription") == 1 }
        // The request is recorded before its answer is handled. A refusal arriving while the sign-in read is still
        // in flight would (by design, FR-006) join it and read nothing more, so settle that read first: a foreground
        // trigger awaits it, or is a no-op inside the floor once it succeeded.
        built.refresher.refresh(RefreshReason.Foreground)
        runCurrent()
        assertEquals(1, env.requests.count("/me/subscription"))

        env.events.publish(ApiEvent.FeatureNotEntitled)
        runCurrent()
        waitUntil { env.requests.count("/me/entitlements") == 1 && env.requests.count("/me/subscription") == 2 }
        runCurrent()
        assertEquals(1, env.requests.count("/me/entitlements"))
        assertEquals(2, env.requests.count("/me/subscription"))
    }
}
