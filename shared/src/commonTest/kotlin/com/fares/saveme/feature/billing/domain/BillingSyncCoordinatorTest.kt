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

    private fun TestScope.build(): Triple<BillingTestEnv, com.fares.saveme.feature.auth.domain.SessionManager, BillingSyncCoordinator> {
        val routes = BillingRoutes()
        val env = BillingTestEnv { with(routes) { answer(it) } }
        val sessions = env.sessionManager(backgroundScope)
        val refresher = EntitlementsRefresher(sessions, env.subscriptions, backgroundScope, MutableClock())
        val coordinator = BillingSyncCoordinator(sessions, refresher, env.events, backgroundScope).also { it.start() }
        return Triple(env, sessions, coordinator)
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
        val (env, sessions, _) = build()
        env.signIn(sessions)
        runCurrent()
        waitUntil { env.requests.count("/me/subscription") == 1 }
        runCurrent()

        env.events.publish(ApiEvent.FeatureNotEntitled)
        runCurrent()
        waitUntil { env.requests.count("/me/entitlements") == 1 && env.requests.count("/me/subscription") == 2 }
        runCurrent()
        assertEquals(1, env.requests.count("/me/entitlements"))
        assertEquals(2, env.requests.count("/me/subscription"))
    }
}
