package com.fares.saveme.feature.billing.domain

import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.billing.BillingRoutes
import com.fares.saveme.feature.billing.BillingTestEnv
import com.fares.saveme.feature.billing.MutableClock
import com.fares.saveme.feature.billing.RecordingEntitlements
import com.fares.saveme.feature.billing.count
import com.fares.saveme.feature.billing.data.play.PlayPurchaseUpdate
import com.fares.saveme.feature.billing.domain.play.PlayBillingNotes
import com.fares.saveme.feature.billing.domain.play.PlayPassLock
import com.fares.saveme.feature.billing.domain.play.PlayPurchaseVerifier
import com.fares.saveme.feature.billing.domain.play.PlayRestoreService
import com.fares.saveme.feature.billing.endsWithPath
import com.fares.saveme.feature.billing.play.FakePlayBillingClient
import com.fares.saveme.feature.billing.play.GooglePlayFixtures
import com.fares.saveme.feature.billing.play.SchedulerClock
import com.fares.saveme.feature.billing.play.playPurchase
import com.fares.saveme.feature.billing.signIn
import com.fares.saveme.feature.billing.waitUntil
import kotlin.time.Duration.Companion.seconds
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
        val port: FakePlayBillingClient,
        val clock: SchedulerClock,
    ) {
        operator fun component1() = env
        operator fun component2() = sessions
        operator fun component3() = coordinator
    }

    private fun TestScope.build(): Built {
        val routes = BillingRoutes()
        val env = BillingTestEnv { request ->
            when {
                request.endsWithPath("/billing/google/purchases") -> jsonResponse(200, GooglePlayFixtures.active())
                request.endsWithPath("/billing/google/restore") -> jsonResponse(200, GooglePlayFixtures.restoreOf("linked"))
                else -> with(routes) { answer(request) }
            }
        }
        val sessions = env.sessionManager(backgroundScope)
        val refresher = EntitlementsRefresher(sessions, env.subscriptions, backgroundScope, MutableClock())
        val port = FakePlayBillingClient()
        val clock = SchedulerClock(testScheduler)
        val notes = PlayBillingNotes()
        val lock = PlayPassLock()
        val applier = BillingAnswerApplier(sessions, env.subscriptions, RecordingEntitlements(), backgroundScope)
        val verifier = PlayPurchaseVerifier(port, env.api, sessions, applier, notes, {}, lock, backgroundScope, clock)
        val restorer = PlayRestoreService(port, env.api, sessions, applier, notes, lock, backgroundScope)
        val coordinator = BillingSyncCoordinator(sessions, refresher, env.events, verifier, restorer, port.purchaseUpdates, backgroundScope, clock)
            .also { it.start() }
        return Built(env, sessions, coordinator, refresher, port, clock)
    }

    private fun Built.purchaseListings() = port.calls.count { it == "purchases" }

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

    @Test
    fun aColdStartRunsAVerifyPassNotARestore() = runTest {
        val built = build()
        val (env, sessions, _) = built
        built.port.own(playPurchase("t-1"))
        runCurrent()
        env.signIn(sessions)
        runCurrent()
        waitUntil { env.requests.count("/billing/google/purchases") == 1 }
        assertEquals(1, env.requests.count("/billing/google/purchases"))
        assertEquals(0, env.requests.count("/billing/google/restore"))
    }

    @Test
    fun aRealSignInAfterBeingSignedOutRunsARestoreInsteadOfTheVerifyPass() = runTest {
        val built = build()
        val (env, sessions, _) = built
        built.port.own(playPurchase("t-1"))
        runCurrent()
        sessions.signOutLocally(SignOutReason.UserSignedOut)
        runCurrent()
        env.signIn(sessions)
        runCurrent()
        waitUntil { env.requests.count("/billing/google/restore") == 1 }
        assertEquals(1, env.requests.count("/billing/google/restore"))
        assertEquals(0, env.requests.count("/billing/google/purchases"))
    }

    @Test
    fun foregroundRunsAPassAtMostOncePerMinute() = runTest {
        val built = build()
        val (env, sessions, coordinator) = built
        env.signIn(sessions)
        runCurrent()
        waitUntil { built.purchaseListings() == 1 }
        coordinator.onForeground()
        runCurrent()
        waitUntil { built.purchaseListings() == 2 }
        coordinator.onForeground()
        runCurrent()
        assertEquals(2, built.purchaseListings(), "the second foreground is inside the 60 s floor")

        built.clock.skip(61.seconds)
        coordinator.onForeground()
        runCurrent()
        waitUntil { built.purchaseListings() == 3 }
        assertEquals(3, built.purchaseListings())
    }

    @Test
    fun aPurchaseUpdateRunsAPassAndACancelledOneDoesNot() = runTest {
        val built = build()
        val (env, sessions, _) = built
        env.signIn(sessions)
        runCurrent()
        waitUntil { built.purchaseListings() == 1 }
        built.port.updates.emit(PlayPurchaseUpdate.Cancelled)
        runCurrent()
        assertEquals(1, built.purchaseListings())
        built.port.updates.emit(PlayPurchaseUpdate.Purchased(emptyList()))
        runCurrent()
        waitUntil { built.purchaseListings() == 2 }
        assertEquals(2, built.purchaseListings())
    }

    @Test
    fun signedOutNothingRunsForForegroundOrUpdates() = runTest {
        val built = build()
        runCurrent()
        built.coordinator.onForeground()
        built.port.updates.emit(PlayPurchaseUpdate.Purchased(emptyList()))
        runCurrent()
        assertEquals(0, built.purchaseListings())
        assertEquals(0, built.env.requests.size)
    }
}
