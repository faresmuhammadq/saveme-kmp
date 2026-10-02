package com.fares.saveme.feature.auth.domain

import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.auth.domain.usecase.SignInUseCase
import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.signInSuccessBody
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class SignOutCleanupTest {

    @Test
    fun everyCleanerRunsOnceEvenWhenOneFails() = runTest {
        val env = AuthTestEnv { jsonResponse(200, signInSuccessBody()) }
        val calls = mutableListOf<String>()
        val manager = SessionManager(
            repository = env.repository,
            entitlementsApi = env.entitlementsApi,
            apiEvents = env.events,
            scope = backgroundScope,
            cleaners = listOf(
                UserDataCleaner { calls += "db" },
                UserDataCleaner { calls += "broken"; error("disk full") },
                UserDataCleaner { calls += "language" },
            ),
        )

        manager.signOutLocally(SignOutReason.SessionEnded)

        assertEquals(listOf("db", "broken", "language"), calls)
        assertEquals(AuthState.SignedOut(SignOutReason.SessionEnded), manager.state.value)
    }

    @Test
    fun updatesKeepThePersonSignedInWithNewAccountState() = runTest {
        val env = AuthTestEnv { jsonResponse(200, signInSuccessBody()) }
        val manager = SessionManager(env.repository, env.entitlementsApi, env.events, backgroundScope)
        SignInUseCase(env.clientInfo, DeviceIdentityStore(env.keyValues), env.repository, manager)(
            "sara@example.com", "pw", "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70",
        )
        val date = Instant.parse("2026-10-28T10:00:00Z")

        manager.updateDeletion(AccountState.PendingDeletion, date)

        val signedIn = assertIs<AuthState.SignedIn>(manager.state.value)
        assertEquals(AccountState.PendingDeletion, signedIn.account.state)
        assertEquals(date, signedIn.account.deletionScheduledFor)
        assertEquals("pending_deletion", env.repository.readSnapshot()!!.account.state)

        manager.updateDeletion(AccountState.Active, null)
        assertEquals(AccountState.Active, assertIs<AuthState.SignedIn>(manager.state.value).account.state)
    }
}
