package com.fares.saveme.feature.auth.domain

import app.cash.turbine.test
import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.domain.model.AuthField
import com.fares.saveme.feature.auth.domain.model.SignInOutcome
import com.fares.saveme.feature.auth.domain.usecase.SignInUseCase
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.auth.signInSuccessBody
import com.fares.saveme.core.network.FakeClientInfo
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerTest {

    private fun TestScope.manager(env: AuthTestEnv) = SessionManager(env.repository, env.entitlementsApi, env.events, backgroundScope)

    private fun useCase(env: AuthTestEnv, manager: SessionManager, info: FakeClientInfo = env.clientInfo) =
        SignInUseCase(info, DeviceIdentityStore(env.keyValues), env.repository, manager)

    @Test
    fun restoresSignedOutWithEmptyStore() = runTest {
        val env = AuthTestEnv { error("no calls") }
        val manager = manager(env)
        assertEquals(AuthState.Unknown, manager.state.value)
        manager.restore()
        assertEquals(AuthState.SignedOut(SignOutReason.None), manager.state.value)
    }

    @Test
    fun signInPersistsOnlyThroughSecureStoreAndRestoresSignedIn() = runTest {
        val env = AuthTestEnv { jsonResponse(200, signInSuccessBody(deletionCancelled = true)) }
        val manager = manager(env)

        val outcome = useCase(env, manager)("sara@example.com", "pw", "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70")

        assertEquals(SignInOutcome.Success(deletionCancelled = true), outcome)
        assertEquals(CREDENTIAL, env.secureStore.read()!!.credential)
        assertTrue(env.keyValues.values.values.none { CREDENTIAL in it }, "credential must not leak into plain storage")
        val signedIn = assertIs<AuthState.SignedIn>(manager.state.value)
        assertEquals("sara@example.com", signedIn.account.email)
        assertTrue(signedIn.deletionCancelled)

        val relaunched = manager(env)
        relaunched.restore()
        assertIs<AuthState.SignedIn>(relaunched.state.value)
    }

    @Test
    fun sessionEndedEventClearsEverything() = runTest {
        val env = AuthTestEnv { jsonResponse(200, signInSuccessBody()) }
        val manager = manager(env)
        useCase(env, manager)("sara@example.com", "pw", "id")
        runCurrent()

        manager.state.test {
            skipItems(1)
            env.events.publish(ApiEvent.SessionEnded)
            assertEquals(AuthState.SignedOut(SignOutReason.SessionEnded), awaitItem())
        }
        assertNull(env.secureStore.read())
        assertNull(env.snapshots.read())
    }

    @Test
    fun failuresMapToOutcomes() = runTest {
        suspend fun outcomeFor(status: Int, code: String, extra: String = "", vararg headers: Pair<String, String>): SignInOutcome {
            val env = AuthTestEnv { jsonResponse(status, problemBody(status, code, extra), *headers) }
            return useCase(env, manager(env))("sara@example.com", "pw", "id")
        }
        assertEquals(SignInOutcome.InvalidCredentials, outcomeFor(401, "INVALID_CREDENTIALS"))
        assertEquals(SignInOutcome.Locked(900), outcomeFor(423, "ACCOUNT_LOCKED", "", "Retry-After" to "900"))
        assertEquals(SignInOutcome.RateLimited(60), outcomeFor(429, "RATE_LIMITED", ""","retry_after_seconds":60"""))
        assertEquals(SignInOutcome.UpdateRequired, outcomeFor(426, "CLIENT_VERSION_UNSUPPORTED"))
        assertEquals(
            SignInOutcome.Validation(mapOf(AuthField.Email to "REQUIRED")),
            outcomeFor(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/email","code":"REQUIRED"},{"pointer":"/other","code":"X"}]"""),
        )
        assertEquals(SignInOutcome.Error(ApiErrorCode.INTERNAL_ERROR, "req-42"), outcomeFor(500, "INTERNAL_ERROR"))
    }

    @Test
    fun unsupportedPlatformNeverCallsTheApi() = runTest {
        val env = AuthTestEnv { error("must not be called") }
        val outcome = useCase(env, manager(env), FakeClientInfo(liveApiSupported = false))("sara@example.com", "pw", "id")
        assertEquals(SignInOutcome.PlatformUnsupported, outcome)
        assertTrue(env.requests.isEmpty())
    }
}
