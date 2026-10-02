package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.pageOf
import com.fares.saveme.feature.wallets.signedInAccount
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class WalletsSyncCoordinatorTest {

    private fun coordinator(scope: kotlinx.coroutines.CoroutineScope, state: MutableStateFlow<AuthState>, wallets: WalletsService, scheduler: com.fares.saveme.core.sync.SyncScheduler?) =
        WalletsSyncCoordinator(state, wallets, scope, scheduler)

    @Test
    fun refreshesOnSignInAndAsksTheEngineToSend() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, pageOf(emptyList())) }
        val state = MutableStateFlow<AuthState>(AuthState.SignedOut(com.fares.saveme.feature.auth.domain.SignOutReason.None))
        val calls = mutableListOf<Boolean>()
        val wallets = object : WalletsService by env.service {
            override suspend fun refresh(force: Boolean): RefreshOutcome { calls += force; return RefreshOutcome.Done }
        }
        val scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        coordinator(scope, state, wallets, env.scheduler).start()
        assertEquals(emptyList(), calls)
        state.value = AuthState.SignedIn(signedInAccount)
        assertEquals(listOf(true), calls)
        assertEquals(1, env.scheduler.enqueued.size)
    }

    @Test
    fun foregroundRefreshesWithoutForceOnlyWhenSignedIn() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, pageOf(emptyList())) }
        val state = MutableStateFlow<AuthState>(AuthState.SignedOut(com.fares.saveme.feature.auth.domain.SignOutReason.None))
        val calls = mutableListOf<Boolean>()
        val wallets = object : WalletsService by env.service {
            override suspend fun refresh(force: Boolean): RefreshOutcome { calls += force; return RefreshOutcome.Done }
        }
        val scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = coordinator(scope, state, wallets, null)
        coordinator.onForeground()
        assertEquals(emptyList(), calls)
        state.value = AuthState.SignedIn(signedInAccount)
        coordinator.onForeground()
        assertEquals(listOf(false), calls)
    }

    @Test
    fun theServiceSkipsAForegroundReadWithinSixtySecondsButNotAForcedOne() = runTest {
        var reads = 0
        val env = WalletTestEnv(backgroundScope) { reads++; jsonResponse(200, pageOf(listOf(walletJson()))) }
        env.signedIn()
        assertEquals(RefreshOutcome.Done, env.service.refresh(force = true))
        assertEquals(1, reads)
        env.clock.now = env.clock.now + 30.seconds
        env.service.refresh(force = false)
        assertEquals(1, reads)
        env.service.refresh(force = true)
        assertEquals(2, reads)
        env.clock.now = env.clock.now + 61.seconds
        env.service.refresh(force = false)
        assertEquals(3, reads)
        assertNotNull(env.walletDao.rows.value["w1"])
    }

    @Test
    fun aFailedReadDoesNotStartTheFloor() = runTest {
        var reads = 0
        val env = WalletTestEnv(backgroundScope) { reads++; throw kotlinx.io.IOException("offline") }
        env.signedIn()
        assertEquals(RefreshOutcome.Offline, env.service.refresh())
        assertEquals(RefreshOutcome.Offline, env.service.refresh())
        assertEquals(2, reads)
        assertEquals(ApiResult.Offline, env.repository.refreshAll())
    }
}
