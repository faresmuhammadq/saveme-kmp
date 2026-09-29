package com.fares.saveme.feature.account.data

import app.cash.turbine.test
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.data.dto.DeletionScheduleDto
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.model.DeletionOutcome
import com.fares.saveme.feature.account.domain.model.PasswordField
import com.fares.saveme.feature.account.domain.usecase.CancelDeletionUseCase
import com.fares.saveme.feature.account.domain.usecase.RequestDeletionUseCase
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class DeletionContractTest {

    @Test
    fun requestSendsOnlyThePasswordAndReturnsTheDate() = runTest {
        val env = AccountTestEnv {
            jsonResponse(200, """{"data":{"state":"pending_deletion","deletion_scheduled_for":"2026-10-28T10:00:00Z"},"meta":{}}""")
        }
        env.signedIn()
        val schedule = assertIs<ApiResult.Success<DeletionScheduleDto>>(env.api.requestDeletion("secret")).value
        assertEquals("2026-10-28T10:00:00Z", schedule.deletionScheduledFor)
        val request = env.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/account/deletion-request"))
        assertEquals(setOf("password"), request.jsonBody().keys)
    }

    @Test
    fun cancelIsADelete() = runTest {
        val env = AccountTestEnv { respond("", HttpStatusCode.NoContent) }
        env.signedIn()
        assertIs<ApiResult.Success<Unit>>(env.api.cancelDeletion())
        val request = env.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/account/deletion-request"))
    }

    @Test
    fun wrongPasswordKeepsTheSession() = runTest {
        val env = AccountTestEnv { jsonResponse(401, problemBody(401, "INVALID_CREDENTIALS")) }
        env.signedIn()
        val sessions = SessionManager(env.auth.repository, env.events, backgroundScope)
        env.events.events.test {
            assertEquals(DeletionOutcome.WrongPassword, RequestDeletionUseCase(env.api, sessions)("nope"))
            expectNoEvents()
        }
    }

    @Test
    fun refusalsMapToOutcomes() = runTest {
        suspend fun outcome(block: suspend io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData): DeletionOutcome {
            val env = AccountTestEnv { block() }
            env.signedIn()
            return RequestDeletionUseCase(env.api, SessionManager(env.auth.repository, env.events, backgroundScope))("secret")
        }
        assertEquals(DeletionOutcome.Locked(900), outcome { jsonResponse(423, problemBody(423, "ACCOUNT_LOCKED"), "Retry-After" to "900") })
        assertEquals(
            DeletionOutcome.Validation(mapOf(PasswordField.Current to "REQUIRED")),
            outcome { jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/password","code":"REQUIRED"}]""")) },
        )
        assertEquals(DeletionOutcome.Offline, outcome { throw IOException("offline") })
    }

    @Test
    fun cancelOfflineIsReportedNotRetried() = runTest {
        val env = AccountTestEnv { throw IOException("offline") }
        env.signedIn()
        val outcome = CancelDeletionUseCase(env.api, SessionManager(env.auth.repository, env.events, backgroundScope))()
        assertEquals(ActionOutcome.Offline, outcome)
        assertEquals(1, env.requests.size)
    }
}
