package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.feature.notifications.FakeScheduler
import com.fares.saveme.feature.notifications.NotificationsTestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushCoordinatorTest {

    @Test
    fun theTransitionToSignedInRequestsOneReconcilePerSignIn() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        val scheduler = FakeScheduler()
        PushCoordinator(env.sessions, scheduler, backgroundScope).start()
        testScheduler.runCurrent()
        assertTrue(scheduler.requests.isEmpty(), "nobody is signed in yet")
        env.signIn()
        testScheduler.runCurrent()
        assertEquals(listOf(ReconcileTrigger.SignedIn), scheduler.requests)
        // The same person staying signed in (an account refresh) is not a new sign-in.
        env.sessions.markEmailVerified()
        testScheduler.runCurrent()
        assertEquals(1, scheduler.requests.size)
    }

    @Test
    fun foregroundRequestsOnlyWhileSignedIn() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        val scheduler = FakeScheduler()
        val coordinator = PushCoordinator(env.sessions, scheduler, backgroundScope)
        coordinator.onForeground()
        assertTrue(scheduler.requests.isEmpty())
        env.signIn()
        coordinator.onForeground()
        assertEquals(listOf(ReconcileTrigger.Foreground), scheduler.requests)
    }

    @Test
    fun aNewAddressAndAPermissionChangeAlwaysRequest() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        val scheduler = FakeScheduler()
        val coordinator = PushCoordinator(env.sessions, scheduler, backgroundScope)
        coordinator.onTokenChanged()
        coordinator.onPermissionChanged()
        assertEquals(listOf(ReconcileTrigger.TokenChanged, ReconcileTrigger.PermissionChanged), scheduler.requests)
    }
}
