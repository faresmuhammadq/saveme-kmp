package com.fares.saveme.feature.auth.ui.home

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.BudgetGlimpse
import com.fares.saveme.feature.auth.domain.NoBudgetSummary
import com.fares.saveme.feature.auth.domain.model.AccountSnapshot
import com.fares.saveme.feature.auth.domain.model.AccountState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val account = AccountSnapshot("u1", "sara@example.com", true, AccountState.Active, null, "free", emptyMap())

private fun glimpse(id: String, name: String? = "Groceries", spent: Long? = 350_000, waiting: Boolean = false) =
    BudgetGlimpse(id, name, spent, 600_000, "EGP", waiting)

/** Home's "Budgets" row and card (spec 012 FR-017): the card follows the summary, the row and the card open the screens. */
class HomeBudgetsTest {

    @Test
    fun theCardListsWhatTheSummaryGivesAndIsHiddenWhenEmpty() = runMviTest {
        val budgets = MutableStateFlow(emptyList<BudgetGlimpse>())
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)), budgets = budgets)
        assertTrue(vm.state.value.budgets.isEmpty(), "the card is hidden when the person has no active budget")
        budgets.value = listOf(glimpse("b1"), glimpse("b2", name = null, spent = null))
        assertEquals(listOf("b1", "b2"), vm.state.value.budgets.map { it.id })
        budgets.value = emptyList()
        assertTrue(vm.state.value.budgets.isEmpty())
    }

    @Test
    fun theRowAndSeeAllOpenTheBudgetsScreen() = runMviTest {
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)))
        vm.effects.test {
            vm.onIntent(HomeIntent.BudgetsClicked)
            assertEquals(HomeEffect.NavigateBudgets, awaitItem())
        }
    }

    @Test
    fun theReportsRowOpensTheReportsScreen() = runMviTest {
        // Spec 014 FR-021: a navigation-only row after "Budgets"; every role and plan.
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)))
        vm.effects.test {
            vm.onIntent(HomeIntent.ReportsClicked)
            assertEquals(HomeEffect.NavigateReports, awaitItem())
        }
    }

    @Test
    fun aBudgetOnTheCardOpensItsEditor() = runMviTest {
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)))
        vm.effects.test {
            vm.onIntent(HomeIntent.BudgetClicked("b7"))
            assertEquals(HomeEffect.OpenBudget("b7"), awaitItem())
        }
    }

    @Test
    fun theReducerCarriesTheGlimpses() {
        val state = reduce(HomeState(), HomeChange.Budgets(listOf(glimpse("b1", waiting = true))))
        assertEquals(listOf("b1"), state.budgets.map { it.id })
        assertTrue(state.budgets.single().includesWaiting)
        assertFalse(reduce(state, HomeChange.Budgets(emptyList())).budgets.isNotEmpty())
    }

    @Test
    fun withoutBudgetsInstalledTheDefaultSummaryShowsNothing() = runMviTest {
        assertEquals(emptyList(), NoBudgetSummary.observeTop(3).first())
    }

    @Test
    fun aGlimpseNeverPrintsItsAmounts() {
        assertEquals("BudgetGlimpse(b1)", glimpse("b1").toString())
    }
}
