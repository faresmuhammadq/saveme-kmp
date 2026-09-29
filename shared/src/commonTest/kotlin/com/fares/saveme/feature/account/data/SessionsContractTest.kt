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
    fun listSessionsAsksForActiveSessionsPageByPage() = runTest {
        val env = AccountTestEnv { jsonResponse(200, pageBody(listOf(sessionJson("a", current = true)), nextCursor = "c2")) }
        env.signedIn()

        val first = assertIs<ApiResult.Success<*>>(env.api.listSessions(cursor = null))
        env.api.listSessions(cursor = "c2")

        val (one, two) = env.requests
        assertEquals(HttpMethod.Get, one.method)
        assertTrue(one.url.encodedPath.endsWith("/api/v1/auth/sessions"))
        assertEquals("active", one.url.parameters["filter[state]"])
        assertEquals("50", one.url.parameters["limit"])
        assertNull(one.url.parameters["cursor"])
        assertEquals("c2", two.url.parameters["cursor"])
        assertTrue(one.headers["Authorization"]!!.startsWith("Bearer "))
        val page = assertIs<ApiResult.Success<com.fares.saveme.core.network.Page<com.fares.saveme.feature.account.data.dto.DeviceSessionDto>>>(first).value
        assertEquals("a", page.items.single().id)
        assertEquals(true, page.items.single().current)
        assertEquals("c2", page.nextCursor)
    }

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
            env.api.listSessions(null)
            assertEquals(ApiEvent.SessionEnded, awaitItem())
        }
    }
}
