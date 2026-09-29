package com.fares.saveme.core.network

import app.cash.turbine.test
import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Instant

/** SessionEnded follows the code, not the status: 401 INVALID_CREDENTIALS keeps the session (R-04, FR-027). */
class SessionEndedEventTest {

    private suspend fun AuthTestEnv.signedIn() =
        secureStore.write(StoredSession(CREDENTIAL, "s1", Instant.parse("2026-09-26T10:00:00Z")))

    private suspend fun AuthTestEnv.post(public: Boolean = false) = client.call<Unit> {
        method = HttpMethod.Post
        url("auth/change-password")
        if (public) publicEndpoint()
    }

    @Test
    fun unauthenticatedCodeEndsTheSession() = runTest {
        val env = AuthTestEnv { jsonResponse(401, problemBody(401, "UNAUTHENTICATED")) }
        env.signedIn()
        env.events.events.test {
            env.post()
            kotlin.test.assertEquals(ApiEvent.SessionEnded, awaitItem())
        }
    }

    @Test
    fun invalidCredentialsOnALiveSessionDoesNotEndIt() = runTest {
        val env = AuthTestEnv { jsonResponse(401, problemBody(401, "INVALID_CREDENTIALS")) }
        env.signedIn()
        env.events.events.test {
            env.post()
            expectNoEvents()
        }
    }

    @Test
    fun credentialed401WithoutABodyEndsTheSession() = runTest {
        val env = AuthTestEnv { respond("", HttpStatusCode.Unauthorized) }
        env.signedIn()
        env.events.events.test {
            env.post()
            kotlin.test.assertEquals(ApiEvent.SessionEnded, awaitItem())
        }
    }

    @Test
    fun publicRequest401DoesNotEndTheSession() = runTest {
        val env = AuthTestEnv { jsonResponse(401, problemBody(401, "UNAUTHENTICATED")) }
        env.signedIn()
        env.events.events.test {
            env.post(public = true)
            expectNoEvents()
        }
    }
}
