package com.fares.saveme.feature.account.domain

import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.domain.model.DeletionOutcome
import com.fares.saveme.feature.account.domain.usecase.CancelDeletionUseCase
import com.fares.saveme.feature.account.domain.usecase.RequestDeletionUseCase
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.auth.domain.usecase.SignInUseCase
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.signInSuccessBody
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class DeletionUseCasesTest {

    @Test
    fun requestAndCancelUpdateTheSignedInAccount() = runTest {
        val env = AccountTestEnv { request ->
            when {
                request.url.encodedPath.endsWith("auth/login") -> jsonResponse(200, signInSuccessBody())
                request.method == HttpMethod.Post ->
                    jsonResponse(200, """{"data":{"state":"pending_deletion","deletion_scheduled_for":"2026-10-28T10:00:00Z"},"meta":{}}""")
                else -> respond("", HttpStatusCode.NoContent)
            }
        }
        val sessions = SessionManager(env.auth.repository, env.events, backgroundScope)
        SignInUseCase(env.auth.clientInfo, DeviceIdentityStore(env.auth.keyValues), env.auth.repository, sessions)(
            "sara@example.com", "pw", "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70",
        )
        val date = Instant.parse("2026-10-28T10:00:00Z")

        assertEquals(DeletionOutcome.Scheduled(date), RequestDeletionUseCase(env.api, sessions)("pw"))
        val pending = assertIs<AuthState.SignedIn>(sessions.state.value).account
        assertEquals(AccountState.PendingDeletion, pending.state)
        assertEquals(date, pending.deletionScheduledFor)

        CancelDeletionUseCase(env.api, sessions)()
        assertEquals(AccountState.Active, assertIs<AuthState.SignedIn>(sessions.state.value).account.state)
    }
}
