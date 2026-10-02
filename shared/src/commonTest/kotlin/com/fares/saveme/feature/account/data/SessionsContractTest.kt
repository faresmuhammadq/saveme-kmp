package com.fares.saveme.feature.account.data

import app.cash.turbine.test
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.pageBody
import com.fares.saveme.feature.account.sessionJson
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionsContractTest {

    @Test
    fun endOneAndEndOthers() = runTest {
        val env = AccountTestEnv { respond("", HttpStatusCode.NoContent) }
        env.signedIn()
        assertIs<ApiResult.Success<Unit>>(env.api.endSession("b"))
        assertIs<ApiResult.Success<Unit>>(env.api.endOtherSessions())
        val (one, others) = env.requests
        assertEquals(HttpMethod.Delete, one.method)
        assertTrue(one.url.encodedPath.endsWith("/api/v1/auth/sessions/b"))
        assertEquals(HttpMethod.Delete, others.method)
        assertTrue(others.url.encodedPath.endsWith("/api/v1/auth/sessions"))
    }

    @Test
    fun notFoundIsReportedByCode() = runTest {
        val env = AccountTestEnv { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
        env.signedIn()
        assertEquals(ApiErrorCode.NOT_FOUND, assertIs<ApiResult.Failure>(env.api.endSession("gone")).code)
    }

    @Test
    fun unauthenticatedEndsTheLocalSession() = runTest {
        val env = AccountTestEnv { jsonResponse(401, problemBody(401, "UNAUTHENTICATED")) }
        env.signedIn()
        env.events.events.test {
            env.api.endSession("x")
            assertEquals(ApiEvent.SessionEnded, awaitItem())
        }
    }
}
