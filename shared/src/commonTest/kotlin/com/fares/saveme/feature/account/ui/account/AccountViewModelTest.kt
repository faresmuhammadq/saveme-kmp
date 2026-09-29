package com.fares.saveme.feature.account.ui.account

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.AccountSnapshot
import com.fares.saveme.feature.auth.domain.model.AccountState as AccountStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val account = AccountSnapshot("u1", "sara@example.com", false, AccountStatus.Active, null, "free", emptyMap())

class AccountReducerTest {
    @Test
    fun mirrorsTheSignedInAccount() {
        val date = Instant.parse("2026-10-28T10:00:00Z")
        val pending = account.copy(state = AccountStatus.PendingDeletion, deletionScheduledFor = date)
        val state = reduce(AccountState(), AccountChange.Account(AuthState.SignedIn(pending)))
        assertEquals(AccountState("sara@example.com", emailVerified = false, deletionPendingOn = date), state)
        assertEquals(null, reduce(state, AccountChange.Account(AuthState.SignedIn(account))).deletionPendingOn)
    }
}

class AccountViewModelTest {
    @Test
    fun rowsNavigateAndOpeningRefreshes() = runMviTest {
        var refreshed = 0
        val vm = AccountViewModel(MutableStateFlow(AuthState.SignedIn(account)), signOut = {}, refresh = { refreshed++ })
        assertEquals(1, refreshed)
        vm.effects.test {
            AccountDestination.entries.forEach { destination ->
                vm.onIntent(AccountIntent.Open(destination))
                assertEquals(AccountEffect.Navigate(destination), awaitItem())
            }
        }
    }

    @Test
    fun signOutAsksFirstAndCallsOnce() = runMviTest {
        var calls = 0
        val vm = AccountViewModel(MutableStateFlow(AuthState.SignedIn(account)), signOut = { calls++ })
        vm.onIntent(AccountIntent.SignOutClicked)
        assertEquals(true, vm.state.value.confirmSignOutVisible)
        vm.onIntent(AccountIntent.SignOutDismissed)
        assertEquals(false, vm.state.value.confirmSignOutVisible)
        vm.onIntent(AccountIntent.SignOutClicked)
        vm.onIntent(AccountIntent.SignOutConfirmed)
        vm.onIntent(AccountIntent.SignOutConfirmed)
        assertEquals(1, calls)
    }
}
