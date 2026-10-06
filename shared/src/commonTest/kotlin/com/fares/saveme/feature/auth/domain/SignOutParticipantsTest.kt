package com.fares.saveme.feature.auth.domain

import com.fares.saveme.core.session.SignOutParticipant
import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.domain.usecase.SignInUseCase
import com.fares.saveme.feature.auth.domain.usecase.SignOutUseCase
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.signInSuccessBody
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spec 017 R-07: participants run before the server sign-out and the local wipe, bounded, each guarded. */
class SignOutParticipantsTest {

    private val log = mutableListOf<String>()

    private suspend fun TestScope.signedIn(): Pair<AuthTestEnv, SessionManager> {
        var signedIn = false
        val env = AuthTestEnv { request ->
            if (!signedIn) {
                signedIn = true
                jsonResponse(200, signInSuccessBody())
            } else {
                log += "server:" + request.url.encodedPath.substringAfterLast("/api/v1/")
                respond("", HttpStatusCode.NoContent)
            }
        }
        val manager = SessionManager(env.repository, env.entitlementsApi, env.events, backgroundScope)
        SignInUseCase(env.clientInfo, DeviceIdentityStore(env.keyValues), env.repository, manager)(
            "sara@example.com", "pw", "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70",
        )
        return env to manager
    }

    @Test
    fun participantsRunBeforeTheServerSignOutAndTheWipeWhileTheSessionIsReadable() = runTest {
        val (env, manager) = signedIn()
        var sessionSeen: Any? = null
        var stateSeen: AuthState? = null
        val participant = SignOutParticipant {
            sessionSeen = env.secureStore.read()
            stateSeen = manager.state.value
            log += "participant"
        }
        SignOutUseCase(env.repository, manager, serverTimeout = null, participants = { listOf(participant) })()
        assertEquals(listOf("participant", "server:auth/logout"), log)
        assertNotNull(sessionSeen)
        assertIs<AuthState.SignedIn>(stateSeen)
        assertNull(env.secureStore.read())
        assertIs<AuthState.SignedOut>(manager.state.value)
    }

    @Test
    fun aParticipantThatHangsIsCutAtTwoSecondsAndSignOutStillCompletes() = runTest {
        val (env, manager) = signedIn()
        val hanging = SignOutParticipant { awaitCancellation() }
        val before = currentTime
        SignOutUseCase(env.repository, manager, serverTimeout = null, participants = { listOf(hanging) })()
        assertEquals(2_000L, currentTime - before)
        assertIs<AuthState.SignedOut>(manager.state.value)
        assertTrue(log.contains("server:auth/logout"))
    }

    @Test
    fun aThrowingParticipantDoesNotSkipTheOthers() = runTest {
        val (env, manager) = signedIn()
        val failing = SignOutParticipant { error("boom") }
        val other = SignOutParticipant { log += "other" }
        SignOutUseCase(env.repository, manager, serverTimeout = null, participants = { listOf(failing, other) })()
        assertTrue("other" in log)
        assertIs<AuthState.SignedOut>(manager.state.value)
    }

    @Test
    fun withNoParticipantsBehaviourIsUnchanged() = runTest {
        val (env, manager) = signedIn()
        SignOutUseCase(env.repository, manager, serverTimeout = null)()
        assertEquals(listOf("server:auth/logout"), log)
        assertIs<AuthState.SignedOut>(manager.state.value)
    }
}
