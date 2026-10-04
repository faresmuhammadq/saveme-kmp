package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BudgetsSyncCoordinatorTest {

    private fun service(env: BudgetTestEnv, calls: MutableList<Boolean>) = object : BudgetsService by env.service {
        override suspend fun refresh(force: Boolean): RefreshOutcome { calls += force; return RefreshOutcome.Done }
    }

    @Test
    fun signInReadsOnceWithoutForceAndAsksTheEngineToSend() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        val state = MutableStateFlow<AuthState>(AuthState.SignedOut(SignOutReason.None))
        val calls = mutableListOf<Boolean>()
        val scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        BudgetsSyncCoordinator(state, service(env, calls), scope, env.scheduler).start()
        assertEquals(emptyList(), calls)
        state.value = AuthState.SignedIn(budgetAccount)
        assertEquals(listOf(false), calls)
        assertEquals(1, env.scheduler.enqueued.size)
    }

    @Test
    fun foregroundReadsWithoutForceOnlyWhenSignedIn() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        val state = MutableStateFlow<AuthState>(AuthState.SignedOut(SignOutReason.None))
        val calls = mutableListOf<Boolean>()
        val scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = BudgetsSyncCoordinator(state, service(env, calls), scope, null)
        coordinator.onForeground()
        assertEquals(emptyList(), calls)
        state.value = AuthState.SignedIn(budgetAccount)
        coordinator.onForeground()
        assertEquals(listOf(false), calls)
    }

    @Test
    fun nothingHappensAfterSignOut() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        val state = MutableStateFlow<AuthState>(AuthState.SignedIn(budgetAccount))
        val calls = mutableListOf<Boolean>()
        val scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = BudgetsSyncCoordinator(state, service(env, calls), scope, env.scheduler)
        coordinator.start()
        assertEquals(listOf(false), calls)
        state.value = AuthState.SignedOut(SignOutReason.None)
        coordinator.onForeground()
        assertEquals(listOf(false), calls)
    }
}
