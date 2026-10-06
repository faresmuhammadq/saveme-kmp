package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.ReportsRig
import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.display
import com.fares.saveme.feature.reports.line
import com.fares.saveme.feature.reports.reportWalletView
import com.fares.saveme.feature.reports.domain.model.RangeKind
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.ReportState
import com.fares.saveme.feature.reports.domain.model.Share
import com.fares.saveme.feature.reports.domain.model.TrendSlot
import com.fares.saveme.feature.transactions.domain.LedgerSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-001 to FR-007, FR-015, FR-023: the cross-wallet report of the current period from the phone's own rows. */
class ReportsServiceSummaryTest {

    private fun ReportsRig.threeWallets() {
        wallets.views.value = listOf(reportWalletView("w1"), reportWalletView("w2"), reportWalletView("w3"))
        categories.systemKeys += mapOf("p-groceries" to "groceries", "p-transport" to "transport", "p-salary" to "salary")
        categories.names += mapOf(
            "p-groceries" to display("p-groceries", "Groceries"), "p-transport" to display("p-transport", "Transport"),
            "own-1" to display("own-1", "Pets"), "p-salary" to display("p-salary", "Salary"),
        )
    }

    private val october = listOf(
        line("salary", "income", "2026-10-01", 1_000_000, wallet = "w1", category = "p-salary"),
        line("g", "expense", "2026-10-03", 350_000, wallet = "w1", category = "p-groceries"),
        line("t", "expense", "2026-10-04", 120_000, wallet = "w2", category = "p-transport"),
        line("own", "expense", "2026-10-05", 80_000, wallet = "w3", category = "own-1"),
    )

    private fun snapshot(lines: List<com.fares.saveme.feature.transactions.domain.LedgerLine>) = LedgerSnapshot(lines, emptyMap(), lines, emptyMap(), emptyList())

    @Test
    fun theCurrentPeriodsSummaryAndSharesMatchTheBriefsExample() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.threeWallets()
        rig.ledger.snapshot.value = snapshot(october)
        val report = rig.report()
        assertEquals(ReportScope.AllWallets, report.scope)
        assertEquals("EGP", report.currency)
        val range = report.range!!
        assertEquals(date("2026-10-01"), range.from)
        assertEquals(date("2026-10-31"), range.to)
        assertEquals(RangeKind.Period, range.kind)
        val summary = report.summary!!
        assertEquals(1_000_000, summary.income)
        assertEquals(550_000, summary.expenses)
        assertEquals(450_000, summary.net)
        val items = report.breakdown!!.expenses.items
        assertEquals(listOf("groceries", "transport", "own-1"), items.map { it.label })
        assertEquals(550_000, report.breakdown!!.expenses.totalMinor)
        assertEquals(
            listOf(Share.Percent(64), Share.Percent(22), Share.Percent(15)),
            items.map { Shares.of(it.valueMinor, report.breakdown!!.expenses.totalMinor) },
        )
        assertEquals(listOf("salary"), report.breakdown!!.income.items.map { it.label })
        assertEquals(false, report.includesWaiting)
        assertEquals(false, report.tooLarge)
    }

    @Test
    fun theLedgerIsAskedForTheCoveredWalletsOverTheRangeAndTheTrendWindow() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.threeWallets()
        rig.wallets.views.value += reportWalletView("usd", "USD")
        rig.report()
        val (ids, from, to) = rig.ledger.asks.last()
        assertEquals(setOf("w1", "w2", "w3"), ids)
        assertEquals(date("2026-05-01"), from, "six periods end with the current one")
        assertEquals(date("2026-10-31"), to)
        assertEquals(setOf("w1", "w2", "w3"), rig.categories.labelAsks.last())
    }

    @Test
    fun theTrendEndsWithTheCurrentPeriodAndIsZeroFilled() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.threeWallets()
        rig.ledger.snapshot.value = snapshot(october)
        val trend = rig.report().trend
        assertEquals(6, trend.size)
        val points = trend.map { (it as TrendSlot.Known).point }
        assertEquals(date("2026-05-01"), points.first().period.start)
        assertEquals(date("2026-10-01"), points.last().period.start)
        assertEquals(1_000_000, points.last().incomeMinor)
        assertEquals(0, points.first().incomeMinor)
    }

    @Test
    fun withNoWalletTheStateIsNoWallets() = runTest {
        val rig = ReportsRig(backgroundScope)
        assertEquals(ReportState.NoWallets, rig.service.observe(ReportScope.AllWallets).first())
    }

    @Test
    fun labelsResolveThroughTheCategoriesHook() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.threeWallets()
        val labels = rig.service.observeLabels(ReportScope.AllWallets).first()
        assertEquals("Groceries", labels.display("p-groceries").name)
        assertEquals("groceries", labels.keyOf("p-groceries"))
    }

    @Test
    fun aNewRowRecomputesTheReport() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.threeWallets()
        rig.ledger.snapshot.value = snapshot(october)
        val flow = rig.service.observe(ReportScope.AllWallets)
        assertEquals(550_000, (flow.first() as ReportState.Ready).report.summary!!.expenses)
        rig.ledger.snapshot.value = snapshot(october + line("n", "expense", "2026-10-06", 20_000, category = "own-1"))
        assertEquals(570_000, (flow.first() as ReportState.Ready).report.summary!!.expenses)
    }

    @Test
    fun theBalancesStartFromTheOpeningsAndTheBeforeEffect() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("w1", opening = 1_000), reportWalletView("w2", opening = 500))
        rig.ledger.snapshot.value = LedgerSnapshot(
            listOf(line("a", "expense", "2026-10-03", 100, wallet = "w1")), mapOf("w1" to -200L, "w2" to 50L), emptyList(), emptyMap(), emptyList(),
        )
        val summary = rig.report().summary!!
        assertEquals(1_000 + 500 - 200 + 50L, summary.balanceStart)
        assertEquals(summary.balanceStart - 100, summary.balanceEnd)
    }

    @Test
    fun anOverflowShowsTooLargeInsteadOfAWrongNumber() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("w1"))
        rig.ledger.snapshot.value = snapshot(listOf(line("a", "income", "2026-10-03", Long.MAX_VALUE), line("b", "income", "2026-10-04", 5)))
        val report = rig.report()
        assertTrue(report.tooLarge)
        assertNull(report.summary)
    }

    @Test
    fun theDayTurningMovesTheCurrentPeriod() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.threeWallets()
        assertEquals(date("2026-10-31"), rig.report().range!!.to)
        rig.now.value = kotlin.time.Instant.parse("2026-11-01T00:05:00Z")
        val next = (rig.service.observe(ReportScope.AllWallets).first() as ReportState.Ready).report
        assertEquals(date("2026-11-01"), next.range!!.from)
        assertEquals(date("2026-11-01"), next.today)
    }
}
