package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.ReportsRig
import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.display
import com.fares.saveme.feature.reports.line
import com.fares.saveme.feature.reports.reportWalletView
import com.fares.saveme.feature.reports.scheduledLine
import com.fares.saveme.feature.reports.domain.model.RangeChoice
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.TrendSlot
import com.fares.saveme.feature.transactions.domain.LedgerSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** FR-016, US7: scheduled money beside the actual figures, never added to them. */
class PlannedFiguresTest {

    private val all = ReportScope.AllWallets

    private fun ReportsRig.rent() {
        wallets.views.value = listOf(reportWalletView("w1", opening = 1_000_000), reportWalletView("w2"))
        categories.systemKeys["p-rent"] = "rent"
        categories.names["p-rent"] = display("p-rent", "Rent")
        scheduled.lines.value = listOf(
            scheduledLine("rent", "expense", "2026-10-28", 500_000, category = "p-rent"),
            scheduledLine("pay", "income", "2026-10-25", 100_000, wallet = "w2", category = "p-rent"),
            scheduledLine("inner", "transfer", "2026-10-26", 70_000, wallet = "w1", counter = "w2"),
            scheduledLine("usd", "expense", "2026-10-28", 999, currency = "USD"),
        )
    }

    @Test
    fun theSwitchIsOffByDefaultAndNothingScheduledShowsAnywhere() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.rent()
        val report = rig.report()
        assertNull(report.planned)
        assertNull(report.breakdown!!.expenses.plannedMinor)
        assertEquals(listOf(null), report.trend.takeLast(1).map { (it as TrendSlot.Known).point.plannedExpensesMinor })
        assertEquals(false, report.includeScheduled)
    }

    @Test
    fun withTheSwitchOnPlannedSumsAreSeparateAndTheActualFiguresAndBalancesAreUnchanged() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.rent()
        val off = rig.report()
        rig.service.setIncludeScheduled(all, true)
        val on = rig.report()
        assertEquals(off.summary, on.summary, "balances and actuals do not move")
        assertEquals(0, on.summary!!.expenses)
        assertEquals(500_000, on.planned!!.expenses)
        assertEquals(100_000, on.planned!!.income)
        assertEquals(0, on.planned!!.transfersOut, "a transfer between covered wallets appears nowhere")
        val rentItem = on.breakdown!!.expenses.items.single()
        assertEquals("rent", rentItem.label)
        assertEquals(0, rentItem.valueMinor)
        assertEquals(500_000, rentItem.plannedMinor)
        assertEquals(500_000, on.breakdown!!.expenses.plannedMinor)
        assertEquals(0, on.breakdown!!.expenses.totalMinor)
        val october = (on.trend.last() as TrendSlot.Known).point
        assertEquals(500_000, october.plannedExpensesMinor)
        assertEquals(0, october.expensesMinor)
    }

    @Test
    fun zerosNotAbsenceWhenOnAndNothingIsScheduled() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("w1"))
        rig.service.setIncludeScheduled(all, true)
        val report = rig.report()
        assertEquals(PlannedSummaryZero, report.planned)
        assertEquals(0, report.breakdown!!.expenses.plannedMinor)
        assertEquals(0, (report.trend.last() as TrendSlot.Known).point.plannedIncomeMinor)
    }

    @Test
    fun aWaitingConfirmOrSkipRemovesTheScheduledMoneyThroughTheHook() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.rent()
        rig.service.setIncludeScheduled(all, true)
        rig.scheduled.lines.value = emptyList()
        assertEquals(0, rig.report().planned!!.expenses)
    }

    @Test
    fun theWindowOfTheScheduledAskIsTheSameAsTheLedgers() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.rent()
        rig.service.setIncludeScheduled(all, true)
        rig.service.setRange(all, RangeChoice.Next)
        val report = rig.report()
        assertEquals(date("2026-11-06"), report.knownUntil, "known up to today plus 35 days")
        assertEquals(date("2026-11-30"), rig.ledger.asks.last().third)
    }

    @Test
    fun actualMoneyIsNeverTakenFromTheScheduledLines() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.rent()
        rig.ledger.snapshot.value = LedgerSnapshot(listOf(line("e", "expense", "2026-10-03", 10_000, category = "p-rent")), emptyMap(), emptyList(), emptyMap(), emptyList())
        rig.service.setIncludeScheduled(all, true)
        val report = rig.report()
        assertEquals(10_000, report.summary!!.expenses)
        assertEquals(10_000, report.breakdown!!.expenses.items.single().valueMinor)
        assertEquals(500_000, report.breakdown!!.expenses.items.single().plannedMinor)
    }

    private companion object {
        val PlannedSummaryZero = com.fares.saveme.feature.reports.domain.model.PlannedSummary(0, 0, 0, 0)
    }
}
