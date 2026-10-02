package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.pageOf
import com.fares.saveme.feature.wallets.signedInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun theServiceAsksTheRunnerForEverythingWithTheCallersForce() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, pageOf(emptyList())) }
        env.signedIn()
        assertEquals(RefreshOutcome.Done, env.service.refresh(force = true))
        assertEquals(1, env.scheduler.enqueued.size, "a forced read also sends what waits")
        assertEquals(RefreshOutcome.Done, env.service.refresh(force = false))
        assertEquals(1, env.scheduler.enqueued.size, "an unforced one does not")
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.Everything to true, ReadTarget.Everything to false), env.runner.refreshes)
        assertEquals(0, env.requests.size, "the wallets come from the change feeds, never from a list read")
    }

    @Test
    fun theRunnersOutcomeIsTheScreensOutcome() = runTest {
        val env = WalletTestEnv(backgroundScope) { throw kotlinx.io.IOException("unused") }
        env.runner.outcome = ReadOutcome.Offline
        assertEquals(RefreshOutcome.Offline, env.service.refresh())
        env.runner.outcome = ReadOutcome.Failed
        assertEquals(RefreshOutcome.Failed, env.service.refresh())
    }

    @Test
    fun aWalletsMembersAreReadWithoutAFloorThroughItsFeed() = runTest {
        val env = WalletTestEnv(backgroundScope) { throw kotlinx.io.IOException("unused") }
        assertEquals(RefreshOutcome.Done, env.service.refreshMembers("w1"))
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.Wallet("w1") to true), env.runner.refreshes)
    }
}
