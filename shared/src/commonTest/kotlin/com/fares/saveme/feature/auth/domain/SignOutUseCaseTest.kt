package com.fares.saveme.feature.auth.domain

import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.auth.signInSuccessBody
import com.fares.saveme.feature.auth.domain.usecase.SignInUseCase
import com.fares.saveme.feature.auth.domain.usecase.SignOutUseCase
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignOutUseCaseTest {

    private enum class Server { NoContent, Unauthenticated, Offline, Broken }

    private suspend fun signOutWith(server: Server): AuthTestEnv {
        var signedIn = false
        val env = AuthTestEnv { request ->
            if (!signedIn) {
                signedIn = true
                return@AuthTestEnv jsonResponse(200, signInSuccessBody())
            }
            when (server) {
                Server.NoContent -> respond("", HttpStatusCode.NoContent)
                Server.Unauthenticated -> jsonResponse(401, problemBody(401, "UNAUTHENTICATED"))
                Server.Offline -> throw IOException("no route")
                Server.Broken -> jsonResponse(500, problemBody(500, "INTERNAL_ERROR"))
            }
        }
        return env
    }

    private fun alwaysClearsThisPhone(server: Server) = runTest {
        val env = signOutWith(server)
        val manager = SessionManager(env.repository, env.entitlementsApi, env.events, backgroundScope)
        SignInUseCase(env.clientInfo, DeviceIdentityStore(env.keyValues), env.repository, manager)(
            "sara@example.com", "pw", "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70",
        )

        // A timer on runTest's virtual time would fire before the mock engine (a real dispatcher) answers.
        SignOutUseCase(env.repository, manager, serverTimeout = null)()

        val logout = env.requests.last()
        assertEquals(HttpMethod.Post, logout.method)
        assertTrue(logout.url.encodedPath.endsWith("/auth/logout"), "${env.requests.map { it.method.value + " " + it.url.encodedPath }}")
        assertTrue(logout.headers["Authorization"]!!.startsWith("Bearer "))
        assertNull(env.secureStore.read())
        assertNull(env.repository.readSnapshot())
        assertIs<AuthState.SignedOut>(manager.state.value)
    }

    @Test
    fun noContent() = alwaysClearsThisPhone(Server.NoContent)

    @Test
    fun unauthenticated() = alwaysClearsThisPhone(Server.Unauthenticated)

    @Test
    fun offline() = alwaysClearsThisPhone(Server.Offline)

    @Test
    fun serverError() = alwaysClearsThisPhone(Server.Broken)
}
