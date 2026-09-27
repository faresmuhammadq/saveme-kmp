package com.fares.saveme.feature.auth.ui.home

import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.AccountSnapshot
import com.fares.saveme.feature.auth.domain.model.AccountState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

private val account = AccountSnapshot("u1", "sara@example.com", false, AccountState.Active, null, "free", emptyMap())

class HomeReducerTest {
    @Test
    fun accountAndDismiss() {
        val state = reduce(HomeState(), HomeChange.Account(AuthState.SignedIn(account, deletionCancelled = true)))
        assertEquals(HomeState("sara@example.com", emailVerified = false, deletionCancelledNotice = true), state)
        assertEquals(false, reduce(state, HomeChange.DeletionNoticeDismissed).deletionCancelledNotice)
    }
}

class HomeViewModelTest {
    @Test
    fun followsTheSession() = runMviTest {
        val session = MutableStateFlow<AuthState>(AuthState.SignedIn(account))
        val vm = HomeViewModel(session)
        assertEquals("sara@example.com", vm.state.value.email)
        session.value = AuthState.SignedIn(account.copy(emailVerified = true))
        assertEquals(true, vm.state.value.emailVerified)
    }
}
