package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.notifications.NotificationsTestEnv
import com.fares.saveme.feature.notifications.SESSION_ID
import com.fares.saveme.feature.notifications.TOKEN_A
import com.fares.saveme.feature.notifications.TOKEN_B
import com.fares.saveme.feature.notifications.bodyText
import com.fares.saveme.feature.notifications.data.PushRegistrationRecord
import com.fares.saveme.feature.notifications.problem
import com.fares.saveme.feature.notifications.registeredBody
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** Research R-04, row by row (FR-001 to FR-006, FR-009). */
class PushRegistrationServiceTest {

    @Test
    fun nobodySignedInDoesNothingAndCallsNothing() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        assertEquals(ReconcileOutcome.Nothing, env.service.reconcile(ReconcileTrigger.SignedIn))
        assertTrue(env.deviceRequests.isEmpty())
    }

    @Test
    fun noProviderDoesNothing() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.provider.isAvailable = false
        assertEquals(ReconcileOutcome.Nothing, env.service.reconcile(ReconcileTrigger.SignedIn))
        assertTrue(env.deviceRequests.isEmpty())
    }

    @Test
    fun notAllowedWithNoRecordDoesNothing() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.permission.allowed = false
        assertEquals(ReconcileOutcome.Nothing, env.service.reconcile(ReconcileTrigger.Foreground))
        assertTrue(env.deviceRequests.isEmpty())
    }

    @Test
    fun aNullTokenDoesNothingWaitingForTheProviderToAnnounceOne() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.provider.token = null
        assertEquals(ReconcileOutcome.Nothing, env.service.reconcile(ReconcileTrigger.SignedIn))
        assertTrue(env.deviceRequests.isEmpty())
    }

    @Test
    fun theFirstRegistrationSendsTheStoredSessionAndKeepsOnlyAFingerprint() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.SignedIn))
        val request = env.deviceRequests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertTrue(request.url.encodedPath.endsWith("/me/devices/$SESSION_ID"))
        assertEquals("""{"push_token":"$TOKEN_A"}""", request.bodyText())
        val record = assertNotNull(env.store.read())
        assertEquals(fingerprint(TOKEN_A), record.fingerprint)
        assertEquals(SESSION_ID, record.sessionId)
        assertTrue(env.records.values.values.none { TOKEN_A in it }, "the address is never stored (FR-004)")
    }

    @Test
    fun registrationNeedsNeitherAVerifiedEmailNorAPlan() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        // The signed-in fixture's entitlements are the Free plan; the reconcile never reads them.
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.SignedIn))
        assertTrue(env.env.requests.none { it.url.encodedPath.contains("entitlements") })
    }

    @Test
    fun theSameAddressWithinADayIsNotSentAgainOnForeground() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.service.reconcile(ReconcileTrigger.SignedIn)
        env.clock.instant += 23.hours
        assertEquals(ReconcileOutcome.Nothing, env.service.reconcile(ReconcileTrigger.Foreground))
        assertEquals(ReconcileOutcome.Nothing, env.service.reconcile(ReconcileTrigger.Foreground))
        assertEquals(1, env.deviceRequests.size)
    }

    @Test
    fun otherTriggersRegisterAgainEvenWithinADay() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.service.reconcile(ReconcileTrigger.SignedIn)
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.SignedIn))
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.TokenChanged))
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.PermissionChanged))
        assertEquals(4, env.deviceRequests.size)
    }

    @Test
    fun aDayOldRegistrationIsRefreshedOnForeground() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.service.reconcile(ReconcileTrigger.SignedIn)
        env.clock.instant += 24.hours
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.Foreground))
        assertEquals(2, env.deviceRequests.size)
    }

    @Test
    fun aNewAddressRegistersAtOnceEvenOnForeground() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.service.reconcile(ReconcileTrigger.SignedIn)
        env.provider.token = TOKEN_B
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.Foreground))
        assertEquals("""{"push_token":"$TOKEN_B"}""", env.deviceRequests.last().bodyText())
        assertEquals(fingerprint(TOKEN_B), env.store.read()?.fingerprint)
    }

    @Test
    fun aRecordOfAnotherSessionDoesNotSuppressRegistration() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.store.write(PushRegistrationRecord(USER, "other-session", fingerprint(TOKEN_A), env.clock.now().epochSeconds))
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.Foreground))
        assertEquals(SESSION_ID, env.store.read()?.sessionId)
    }

    @Test
    fun permissionRevokedRemovesTheRegistrationOnceAndForgetsIt() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.service.reconcile(ReconcileTrigger.SignedIn)
        env.permission.allowed = false
        env.devices = { respond("", HttpStatusCode.NoContent) }
        assertEquals(ReconcileOutcome.Unregistered, env.service.reconcile(ReconcileTrigger.Foreground))
        assertEquals(HttpMethod.Delete, env.deviceRequests.last().method)
        assertNull(env.store.read())
        // Nothing left to remove: no second call.
        assertEquals(ReconcileOutcome.Nothing, env.service.reconcile(ReconcileTrigger.Foreground))
        assertEquals(2, env.deviceRequests.size)
        // Allowed again: registers again.
        env.permission.allowed = true
        env.devices = { jsonResponse(200, registeredBody()) }
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.PermissionChanged))
    }

    @Test
    fun anUnregisterThatFindsNothingStillClears() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.service.reconcile(ReconcileTrigger.SignedIn)
        env.permission.allowed = false
        env.devices = { jsonResponse(404, problem(404, "NOT_FOUND")) }
        assertEquals(ReconcileOutcome.Unregistered, env.service.reconcile(ReconcileTrigger.Foreground))
        assertNull(env.store.read())
    }

    @Test
    fun notFoundWithAnUnchangedSessionStopsUntilANewSessionOrAddress() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.devices = { jsonResponse(404, problem(404, "NOT_FOUND")) }
        assertEquals(ReconcileOutcome.Stopped, env.service.reconcile(ReconcileTrigger.SignedIn))
        assertEquals(SESSION_ID, env.store.read()?.stoppedFor)
        assertIs<com.fares.saveme.feature.auth.domain.AuthState.SignedIn>(env.sessions.state.value, "NOT_FOUND never signs the person out (FR-005)")
        // Later foregrounds and permission changes are skipped without a call.
        assertEquals(ReconcileOutcome.Nothing, env.service.reconcile(ReconcileTrigger.Foreground))
        assertEquals(ReconcileOutcome.Nothing, env.service.reconcile(ReconcileTrigger.PermissionChanged))
        assertEquals(1, env.deviceRequests.size)
        // A new address clears the marker.
        env.provider.token = TOKEN_B
        env.devices = { jsonResponse(200, registeredBody()) }
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.TokenChanged))
        assertNull(env.store.read()?.stoppedFor)
    }

    @Test
    fun aNewSessionClearsTheStopMarker() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.store.write(PushRegistrationRecord(USER, "older-session", fingerprint(TOKEN_A), 1, stoppedFor = "older-session"))
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.SignedIn))
    }

    @Test
    fun notFoundWithAChangedStoredSessionRetriesOnceForIt() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        var calls = 0
        val newSession = "0192f7a0-1c3d-7abc-9f01-aaaaaaaaaaaa"
        env.devices = {
            calls++
            if (calls == 1) {
                // The stored session changes while the first call is in flight.
                env.env.secureStore.stored = env.env.secureStore.stored!!.copy(sessionId = newSession)
                jsonResponse(404, problem(404, "NOT_FOUND"))
            } else {
                jsonResponse(200, registeredBody(newSession))
            }
        }
        assertEquals(ReconcileOutcome.Registered, env.service.reconcile(ReconcileTrigger.SignedIn))
        assertEquals(2, env.deviceRequests.size)
        assertTrue(env.deviceRequests.last().url.encodedPath.endsWith(newSession))
        assertEquals(newSession, env.store.read()?.sessionId)
    }

    @Test
    fun rateLimitedAsksToRetryAfterTheServersWaitOrASensibleDefault() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.devices = { jsonResponse(429, problem(429, "RATE_LIMITED"), "Retry-After" to "300") }
        assertEquals(ReconcileOutcome.RetryAfter(300), env.service.reconcile(ReconcileTrigger.SignedIn))
        env.devices = { jsonResponse(429, problem(429, "RATE_LIMITED")) }
        assertEquals(ReconcileOutcome.RetryAfter(60), env.service.reconcile(ReconcileTrigger.SignedIn))
        assertNull(env.store.read())
    }

    @Test
    fun offlineAndServerErrorsRetryLaterAndWriteNothing() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.devices = { throw io.ktor.utils.io.errors.IOException("no route") }
        assertEquals(ReconcileOutcome.RetryLater, env.service.reconcile(ReconcileTrigger.SignedIn))
        env.devices = { jsonResponse(500, problem(500, "INTERNAL_ERROR")) }
        assertEquals(ReconcileOutcome.RetryLater, env.service.reconcile(ReconcileTrigger.SignedIn))
        assertNull(env.store.read())
    }

    @Test
    fun validationFailedStopsAsAContractBug() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.devices = { jsonResponse(422, problem(422, "VALIDATION_FAILED")) }
        assertEquals(ReconcileOutcome.Stopped, env.service.reconcile(ReconcileTrigger.SignedIn))
    }

    @Test
    fun anUnauthenticatedAnswerIsLeftToTheAppWideSessionEnd() = runTest {
        val env = NotificationsTestEnv(backgroundScope)
        env.signIn()
        env.devices = { jsonResponse(401, problem(401, "UNAUTHENTICATED")) }
        assertEquals(ReconcileOutcome.Nothing, env.service.reconcile(ReconcileTrigger.SignedIn))
        assertFalse(env.store.read() != null)
        assertIs<ReconcileOutcome.Nothing>(ReconcileOutcome.Nothing)
    }

    private companion object {
        const val USER = "0192f7a0-0000-7000-8000-000000000001"
    }
}
