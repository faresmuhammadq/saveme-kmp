package com.fares.saveme.feature.account.data

import app.cash.turbine.test
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.domain.SessionRefresher
import com.fares.saveme.feature.account.domain.model.ChangePasswordOutcome
import com.fares.saveme.feature.account.domain.model.PasswordField
import com.fares.saveme.feature.account.domain.usecase.ChangePasswordUseCase
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangePasswordContractTest {

    private suspend fun outcomeFor(answer: MockRequestHandleScope.() -> HttpResponseData): Pair<ChangePasswordOutcome, AccountTestEnv> {
        val env = AccountTestEnv { answer() }
        env.signedIn()
        return ChangePasswordUseCase(env.api, SessionRefresher(env.sessions, env.runner))("old-secret", "new-secret-1") to env
    }

    @Test
    fun sendsExactlyTheTwoPasswords() = runTest {
        val (outcome, env) = outcomeFor { respond("", HttpStatusCode.NoContent) }
        assertEquals(ChangePasswordOutcome.Success, outcome)
        val request = env.requests.first()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/auth/change-password"))
        assertTrue(request.headers["Authorization"]!!.startsWith("Bearer "))
        assertEquals(setOf("current_password", "new_password"), request.jsonBody().keys)
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.User to true), env.runner.refreshes, "the session list is refreshed after success")
    }

    @Test
    fun wrongCurrentPasswordKeepsTheSession() = runTest {
        val env = AccountTestEnv { jsonResponse(401, problemBody(401, "INVALID_CREDENTIALS")) }
        env.signedIn()
        env.events.events.test {
            assertEquals(ChangePasswordOutcome.WrongCurrentPassword, ChangePasswordUseCase(env.api, SessionRefresher(env.sessions, env.runner))("x", "new-secret-1"))
            expectNoEvents()
        }
    }

    @Test
    fun refusalsMapToOutcomes() = runTest {
        assertEquals(
            ChangePasswordOutcome.Locked(900),
            outcomeFor { jsonResponse(423, problemBody(423, "ACCOUNT_LOCKED"), "Retry-After" to "900") }.first,
        )
        assertEquals(
            ChangePasswordOutcome.Validation(mapOf(PasswordField.Current to "REQUIRED", PasswordField.New to "TOO_SHORT")),
            outcomeFor {
                jsonResponse(
                    422,
                    problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/current_password","code":"REQUIRED"},{"pointer":"/new_password","code":"TOO_SHORT"}]"""),
                )
            }.first,
        )
        assertEquals(ChangePasswordOutcome.RateLimited(60), outcomeFor { jsonResponse(429, problemBody(429, "RATE_LIMITED"), "Retry-After" to "60") }.first)
    }
}
