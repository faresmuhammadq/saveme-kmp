package com.fares.saveme.feature.auth.ui.home

import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.AccountSnapshot
import com.fares.saveme.feature.auth.domain.model.AccountState
import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant
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

class HomeWalletsRowTest {
    @Test
    fun walletsClickedNavigatesToTheWallets() = runMviTest {
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)))
        vm.effects.test {
            vm.onIntent(HomeIntent.WalletsClicked)
            assertEquals(HomeEffect.NavigateWallets, awaitItem())
        }
    }
}

class HomeCategoriesRowTest {
    @Test
    fun categoriesClickedNavigatesToTheCategories() = runMviTest {
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)))
        vm.effects.test {
            vm.onIntent(HomeIntent.CategoriesClicked)
            assertEquals(HomeEffect.NavigateCategories, awaitItem())
        }
    }
}

class HomeSignOutAndDeletionTest {
    private val pending = account.copy(state = AccountState.PendingDeletion, deletionScheduledFor = Instant.parse("2026-10-28T10:00:00Z"))

    @Test
    fun signOutAsksFirstAndCallsOnce() = runMviTest {
        var calls = 0
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)), signOut = { calls++ })
        vm.onIntent(HomeIntent.SignOutClicked)
        assertEquals(true, vm.state.value.confirmSignOutVisible)
        vm.onIntent(HomeIntent.SignOutDismissed)
        assertEquals(false, vm.state.value.confirmSignOutVisible)
        assertEquals(0, calls)
        vm.onIntent(HomeIntent.SignOutClicked)
        vm.onIntent(HomeIntent.SignOutConfirmed)
        vm.onIntent(HomeIntent.SignOutConfirmed)
        assertEquals(1, calls)
    }

    @Test
    fun deletionNoticeShowsExactlyWhilePending() = runMviTest {
        val session = MutableStateFlow<AuthState>(AuthState.SignedIn(pending))
        val vm = HomeViewModel(session)
        assertEquals(pending.deletionScheduledFor, vm.state.value.deletionPendingOn)
        session.value = AuthState.SignedIn(account)
        assertEquals(null, vm.state.value.deletionPendingOn)
    }

    @Test
    fun cancelDeletionCallsOnceAndConfirms() = runMviTest {
        var calls = 0
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(pending)), cancelDeletion = { calls++; CancelDeletionResult.Done })
        vm.effects.test {
            vm.onIntent(HomeIntent.CancelDeletion)
            assertEquals(HomeEffect.ShowMessage(HomeMessage.DeletionCancelled), awaitItem())
        }
        assertEquals(1, calls)
        assertEquals(false, vm.state.value.cancellingDeletion)
    }

    @Test
    fun accountEntryNavigates() = runMviTest {
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)))
        vm.effects.test {
            vm.onIntent(HomeIntent.AccountClicked)
            assertEquals(HomeEffect.NavigateAccount, awaitItem())
        }
    }
}

/** Spec 004 US5: Home's sign-out sheet knows what hasn't synced. */
class HomeSyncStatusTest {
    @Test
    fun carriesThePendingCountAndSyncsNow() = runMviTest {
        val status = MutableStateFlow(com.fares.saveme.core.sync.SyncStatus(waiting = 1, needsDecision = 1))
        var syncs = 0
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)), syncStatus = status, syncNow = { syncs++ })
        assertEquals(2, vm.state.value.sync.pending)
        vm.onIntent(HomeIntent.SyncNowClicked)
        assertEquals(1, syncs)
    }
}

class HomeAddTransactionTest {
    @Test
    fun addTransactionClickedNavigatesToTheEditor() = runMviTest {
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)))
        vm.effects.test {
            vm.onIntent(HomeIntent.AddTransactionClicked)
            assertEquals(HomeEffect.NavigateAddTransaction, awaitItem())
        }
    }
}

class HomeDueCardTest {
    @Test
    fun theCardShowsWhenSomethingIsDueAndHidesAtZero() = runMviTest {
        val count = MutableStateFlow(2)
        val first = MutableStateFlow<com.fares.saveme.feature.auth.domain.DueGlimpse?>(
            com.fares.saveme.feature.auth.domain.DueGlimpse("Rent", kotlinx.datetime.LocalDate(2026, 10, 1)),
        )
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)), dueCount = count, firstDue = first)
        assertEquals(2, vm.state.value.dueCount)
        assertEquals("Rent", vm.state.value.firstDue?.title)
        count.value = 0
        first.value = null
        assertEquals(0, vm.state.value.dueCount)
        assertEquals(null, vm.state.value.firstDue)
    }

    @Test
    fun withoutRecurringTheCountStaysZero() = runMviTest {
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)))
        assertEquals(0, vm.state.value.dueCount)
    }

    @Test
    fun tappingTheCardOpensTheDueList() = runMviTest {
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)), dueCount = MutableStateFlow(1))
        vm.effects.test {
            vm.onIntent(HomeIntent.DueClicked)
            assertEquals(HomeEffect.OpenDue, awaitItem())
        }
    }
}
