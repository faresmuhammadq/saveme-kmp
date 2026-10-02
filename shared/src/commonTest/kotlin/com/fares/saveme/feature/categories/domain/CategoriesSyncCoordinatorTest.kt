package com.fares.saveme.feature.categories.domain

import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryPage
import com.fares.saveme.feature.categories.signedInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoriesSyncCoordinatorTest {

    @Test
    fun refreshesOnSignInAndAsksTheEngineToSend() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryPage(emptyList())) }
        val state = MutableStateFlow<AuthState>(AuthState.SignedOut(SignOutReason.None))
        val calls = mutableListOf<Boolean>()
        val categories = object : CategoriesService by env.service {
            override suspend fun refresh(force: Boolean): RefreshOutcome { calls += force; return RefreshOutcome.Done }
        }
        val scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        CategoriesSyncCoordinator(state, categories, scope, env.scheduler).start()
        assertEquals(emptyList(), calls)
        state.value = AuthState.SignedIn(signedInAccount)
        assertEquals(listOf(true), calls)
        assertEquals(1, env.scheduler.enqueued.size)
    }

    @Test
    fun foregroundRefreshesWithoutForceOnlyWhenSignedIn() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryPage(emptyList())) }
        val state = MutableStateFlow<AuthState>(AuthState.SignedOut(SignOutReason.None))
        val calls = mutableListOf<Boolean>()
        val categories = object : CategoriesService by env.service {
            override suspend fun refresh(force: Boolean): RefreshOutcome { calls += force; return RefreshOutcome.Done }
        }
        val scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = CategoriesSyncCoordinator(state, categories, scope, null)
        coordinator.onForeground()
        assertEquals(emptyList(), calls)
        state.value = AuthState.SignedIn(signedInAccount)
        coordinator.onForeground()
        assertEquals(listOf(false), calls)
    }
}
