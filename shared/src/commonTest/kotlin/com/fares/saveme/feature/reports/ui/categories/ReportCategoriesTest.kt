package com.fares.saveme.feature.reports.ui.categories

import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.categories.domain.model.ReportLabels
import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.domain.model.CategoryBreakdown
import com.fares.saveme.feature.reports.domain.model.CategoryGroup
import com.fares.saveme.feature.reports.domain.model.CategoryItem
import com.fares.saveme.feature.reports.domain.model.PlannedSummary
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.ReportState
import com.fares.saveme.feature.reports.domain.model.Share
import com.fares.saveme.feature.reports.sampleReport
import com.fares.saveme.feature.reports.ui.reports.CategoryGroupKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FR-022: the Spending by category screen lists every item of the chosen group, from the same phone figures. */
class ReportCategoriesTest {

    private val labels = ReportLabels({ it }, CategoryDisplays({ id -> CategoryDisplay(id, "Name $id", "cart", "#2E7D32", "expense", CategorySource.Own, false) }))

    private fun withIncome(): ReportState.Ready {
        val report = sampleReport(planned = PlannedSummary(0, 0, 0, 0), includeScheduled = true)
        return ReportState.Ready(
            report.copy(
                breakdown = CategoryBreakdown(
                    report.breakdown!!.expenses.copy(plannedMinor = 500_000),
                    CategoryGroup(1_000_000, 0, listOf(CategoryItem("salary", "p-s", "salary", 1_000_000, 0))),
                ),
            ),
        )
    }

    @Test
    fun expensesAreTheDefaultAndShowEveryItemWithShareAndTotal() = runMviTest {
        val vm = ReportCategoriesViewModel(CategoryGroupKind.Expenses, MutableStateFlow(withIncome()), flowOf(labels))
        val state = vm.state.value
        assertEquals(ReportCategoriesStatus.Ready, state.status)
        assertEquals(CategoryGroupKind.Expenses, state.kind)
        assertEquals(550_000, state.totalMinor)
        assertEquals(500_000, state.plannedMinor)
        assertEquals(listOf("Name p-g", "Name p-t", "Name own-1"), state.rows.map { it.name })
        assertEquals(listOf(Share.Percent(64), Share.Percent(22), Share.Percent(15)), state.rows.map { it.share })
        assertEquals(date("2026-10-01"), state.range!!.from)
        assertEquals("EGP", state.currency)
    }

    @Test
    fun theIncomeSwitchShowsTheIncomeGroup() = runMviTest {
        val vm = ReportCategoriesViewModel(CategoryGroupKind.Expenses, MutableStateFlow(withIncome()), flowOf(labels))
        vm.onIntent(ReportCategoriesIntent.KindPicked(CategoryGroupKind.Income))
        val state = vm.state.value
        assertEquals(CategoryGroupKind.Income, state.kind)
        assertEquals(1_000_000, state.totalMinor)
        assertEquals(listOf(Share.Percent(100)), state.rows.map { it.share })
    }

    @Test
    fun theInitialKindComesFromTheKey() = runMviTest {
        val vm = ReportCategoriesViewModel(CategoryGroupKind.fromWire("income"), MutableStateFlow(withIncome()), flowOf(labels))
        assertEquals(CategoryGroupKind.Income, vm.state.value.kind)
        assertEquals(CategoryGroupKind.Expenses, CategoryGroupKind.fromWire("anything else"))
    }

    @Test
    fun anEmptyGroupHasNoRowsAndATotalOfZero() = runMviTest {
        val vm = ReportCategoriesViewModel(CategoryGroupKind.Expenses, MutableStateFlow(ReportState.Ready(sampleReport(expenses = emptyList()))), flowOf(labels))
        assertEquals(emptyList(), vm.state.value.rows)
        assertEquals(0, vm.state.value.totalMinor)
    }

    @Test
    fun aWalletReportsTitleAndAPurgedWalletClosesTheScreen() = runMviTest {
        val report = MutableStateFlow<ReportState>(ReportState.Ready(sampleReport(scope = ReportScope.Wallet("w7"), title = "Cash", includesWaiting = true)))
        val vm = ReportCategoriesViewModel(CategoryGroupKind.Expenses, report, flowOf(labels))
        assertEquals("Cash", vm.state.value.title)
        assertTrue(vm.state.value.includesWaiting)
        report.value = ReportState.WalletGone
        assertEquals(ReportCategoriesEffect.Close, vm.effects.first())
        assertEquals(ReportCategoriesStatus.Gone, vm.state.value.status)
    }

    @Test
    fun backCloses() = runMviTest {
        val vm = ReportCategoriesViewModel(CategoryGroupKind.Expenses, MutableStateFlow(withIncome()), flowOf(labels))
        vm.onIntent(ReportCategoriesIntent.BackClicked)
        assertEquals(ReportCategoriesEffect.Close, vm.effects.first())
    }

    @Test
    fun anOverflowShowsTooLarge() = runMviTest {
        val vm = ReportCategoriesViewModel(CategoryGroupKind.Expenses, MutableStateFlow(ReportState.Ready(sampleReport(tooLarge = true, summary = null))), flowOf(labels))
        assertTrue(vm.state.value.tooLarge)
    }
}
