package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.notifications.FakeDisplay
import com.fares.saveme.feature.notifications.FakeProvider
import com.fares.saveme.feature.notifications.FakeScheduler
import com.fares.saveme.feature.notifications.NotificationsTestEnv
import com.fares.saveme.feature.notifications.SESSION_ID
import com.fares.saveme.feature.notifications.TOKEN_A
import com.fares.saveme.feature.notifications.USER_ID
import com.fares.saveme.feature.notifications.data.PushRegistrationRecord
import com.fares.saveme.feature.notifications.data.ShownNotificationsStore
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Research R-07: the bounded DELETE before the wipe, the cleaner after it, and no DELETE for a remote session end. */
class PushSignOutTest {

    private suspend fun NotificationsTestEnv.registered() {
        signIn()
        service.reconcile(ReconcileTrigger.SignedIn)
    }

    @Test
    fun theParticipantDeletesOnlyWhenThisSessionIsRegistered() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        val participant = PushSignOutParticipant(env.api, env.store, env.env.secureStore)
        participant.beforeSignOut()
        assertTrue(env.deviceRequests.isEmpty(), "nothing registered, nothing to remove")

        env.registered()
        env.devices = { respond("", HttpStatusCode.NoContent) }
        participant.beforeSignOut()
        assertEquals(HttpMethod.Delete, env.deviceRequests.last().method)
        assertTrue(env.deviceRequests.last().url.encodedPath.endsWith("/me/devices/$SESSION_ID"))
    }

    @Test
    fun aRecordOfAnotherSessionOrAStoppedOneSendsNothing() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        val participant = PushSignOutParticipant(env.api, env.store, env.env.secureStore)
        env.store.write(PushRegistrationRecord(USER_ID, "other-session", "f", 1))
        participant.beforeSignOut()
        env.store.write(PushRegistrationRecord(USER_ID, SESSION_ID, "f", 1, stoppedFor = SESSION_ID))
        participant.beforeSignOut()
        assertTrue(env.deviceRequests.isEmpty())
    }

    @Test
    fun anyAnswerIsIgnored() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.registered()
        env.devices = { throw io.ktor.utils.io.errors.IOException("no route") }
        PushSignOutParticipant(env.api, env.store, env.env.secureStore).beforeSignOut()
        assertEquals(2, env.deviceRequests.size)
    }

    @Test
    fun aServerThatHangsIsCutByTheSignOutsTwoSecondBound() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.registered()
        val hanging = com.fares.saveme.core.session.SignOutParticipant { awaitCancellation() }
        val use = com.fares.saveme.feature.auth.domain.usecase.SignOutUseCase(
            env.env.repository, env.sessions, serverTimeout = null, participants = { listOf(hanging) },
        )
        val before = currentTime
        env.devices = { respond("", HttpStatusCode.NoContent) }
        use()
        assertEquals(2_000L, currentTime - before)
        assertIs<AuthState.SignedOut>(env.sessions.state.value)
    }

    @Test
    fun theCleanerCancelsTheShadeClearsBothRecordsStopsTheJobAndDiscardsTheAddress() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.registered()
        val display = FakeDisplay()
        val shown = ShownNotificationsStore(env.records)
        shown.markShown(USER_ID, "n-1")
        val scheduler = FakeScheduler()
        val provider = FakeProvider(TOKEN_A)
        PushDataCleaner(display, env.store, shown, scheduler, provider, backgroundScope).clear()
        testScheduler.runCurrent()
        assertEquals(listOf("cancelAll"), display.log)
        assertNull(env.store.read())
        assertTrue(shown.markShown(USER_ID, "n-1"), "the de-duplication list was cleared")
        assertEquals(1, scheduler.cancels)
        assertEquals(1, provider.discards)
    }

    @Test
    fun theCleanerNeverWaitsForTheProviderToDiscard() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        val slow = object : PushTokenProvider {
            override val isAvailable = true
            override suspend fun currentToken(): String? = null
            override suspend fun discard() = awaitCancellation()
        }
        val before = currentTime
        PushDataCleaner(FakeDisplay(), env.store, ShownNotificationsStore(env.records), FakeScheduler(), slow, backgroundScope).clear()
        assertEquals(before, currentTime)
    }

    @Test
    fun aRemoteSessionEndRunsTheCleanerAndNeverTheDelete() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.registered()
        val display = FakeDisplay()
        val cleaningSessions = com.fares.saveme.feature.auth.domain.SessionManager(
            env.env.repository, env.env.entitlementsApi, env.env.events, backgroundScope,
            cleaners = listOf(PushDataCleaner(display, env.store, ShownNotificationsStore(env.records), FakeScheduler(), FakeProvider(), backgroundScope)),
        )
        cleaningSessions.restore()
        testScheduler.runCurrent() // the session manager is now listening for the server's session end
        val requestsBefore = env.deviceRequests.size
        env.env.events.publish(ApiEvent.SessionEnded)
        testScheduler.runCurrent()
        assertEquals(AuthState.SignedOut(SignOutReason.SessionEnded), cleaningSessions.state.value)
        assertEquals(listOf("cancelAll"), display.log)
        assertNull(env.store.read())
        assertEquals(requestsBefore, env.deviceRequests.size, "no DELETE with a dead credential")
    }
}
