package com.fares.saveme.feature.sharedbudgets

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod
import com.fares.saveme.feature.sharedbudgets.domain.model.SharedBudgetProgress
import com.fares.saveme.feature.sharedbudgets.ui.detail.SharedBudgetDetailEffect
import com.fares.saveme.feature.sharedbudgets.ui.detail.SharedBudgetDetailIntent
import com.fares.saveme.feature.sharedbudgets.ui.detail.SharedBudgetDetailViewModel
import com.fares.saveme.feature.sharedbudgets.ui.list.SharedBudgetsEffect
import com.fares.saveme.feature.sharedbudgets.ui.list.SharedBudgetsIntent
import com.fares.saveme.feature.sharedbudgets.ui.list.SharedBudgetsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun progress(id: String, archived: Boolean = false) = SharedBudgetProgress(
    id = id, name = "Home", currency = "EGP", period = BudgetPeriod(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), 31, 10),
    limitMinor = 100, spentMinor = 10, projectedMinor = null, lines = emptyList(), unlistedMinor = 0, members = emptyList(),
    isOwner = true, archived = archived, refreshedAt = null,
)

class SharedBudgetViewModelsTest {
    @Test
    fun theListSplitsArchivedFromActiveAndOpensOnlyBudgetsItHolds() = runMviTest {
        val store = SharedBudgetsViewModel(flowOf(listOf(progress("b1"), progress("b2", archived = true))))
        assertTrue(store.state.value.loaded)
        assertEquals(listOf("b1"), store.state.value.active.map { it.id })
        assertEquals(listOf("b2"), store.state.value.archived.map { it.id })
        store.effects.test {
            store.onIntent(SharedBudgetsIntent.BudgetClicked("nope"))
            store.onIntent(SharedBudgetsIntent.BudgetClicked("b1"))
            assertEquals(SharedBudgetsEffect.OpenBudget("b1"), awaitItem())
        }
    }

    @Test
    fun anEmptyListIsEmptyOnlyOnceLoaded() = runMviTest {
        assertTrue(SharedBudgetsViewModel(flowOf(emptyList())).state.value.isEmpty)
        assertFalse(SharedBudgetsViewModel(kotlinx.coroutines.flow.emptyFlow()).state.value.isEmpty)
    }

    @Test
    fun theDetailClosesWhenTheBudgetLeavesThePhone() = runMviTest {
        val source = MutableStateFlow<SharedBudgetProgress?>(progress("b1"))
        val store = SharedBudgetDetailViewModel(source)
        assertNotNull(store.state.value.progress)
        store.effects.test {
            source.value = null
            assertEquals(SharedBudgetDetailEffect.NavigateBack, awaitItem())
        }
        store.onIntent(SharedBudgetDetailIntent.Back)
    }
}
