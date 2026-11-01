package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.ReportsRig
import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.display
import com.fares.saveme.feature.reports.line
import com.fares.saveme.feature.reports.reportWalletView
import com.fares.saveme.feature.reports.domain.model.RangeChoice
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.transactions.domain.DaySum
import com.fares.saveme.feature.transactions.domain.LedgerSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Home's figures: the current period over every wallet in the profile's currency, whatever Reports chose. */
class ReportsServiceHomeTest {

    private fun ReportsRig.wallet() {
        wallets.views.value = listOf(reportWalletView("w1"), reportWalletView("usd", "USD"))
        categories.systemKeys += mapOf("p-groceries" to "groceries")
        categories.names += mapOf("p-groceries" to display("p-groceries", "Groceries"))
    }

    private fun snapshot(lines: List<com.fares.saveme.feature.transactions.domain.LedgerLine>, daily: List<DaySum> = emptyList()) =
        LedgerSnapshot(lines, emptyMap(), lines, emptyMap(), emptyList(), daily)

    @Test
    fun spendingIsPerDayAndComparedWithTheSameDaysOfThePeriodBefore() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallet()
        rig.ledger.snapshot.value = snapshot(
            listOf(
                line("a", "expense", "2026-10-01", 40_000, wallet = "w1", category = "p-groceries"),
                line("b", "expense", "2026-10-02", 60_000, wallet = "w1", category = "p-groceries"),
                line("c", "income", "2026-10-02", 500_000, wallet = "w1"),
            ),
            // September 1 and 2 count (today is the 2nd), the 3rd does not.
            daily = listOf(DaySum(date("2026-09-01"), 0, 100_000), DaySum(date("2026-09-02"), 0, 100_000), DaySum(date("2026-09-03"), 0, 900_000)),
        )
        val home = rig.service.observeHomePeriod().first()
        assertEquals("EGP", home.currency)
        assertEquals(100_000, home.summary!!.expenses)
        assertEquals(31, home.dailyExpenses.size)
        assertEquals(40_000, home.dailyExpenses[0])
        assertEquals(60_000, home.dailyExpenses[1])
        assertEquals(1, home.todayIndex)
        assertEquals(-50, home.changePercent, "100,000 against 200,000 over the same two days")
        assertEquals(listOf("USD"), home.excluded.map { it.currency }, "the other currency is named, not summed")
        assertEquals(listOf("groceries"), home.breakdown!!.expenses.items.map { it.label })
    }

    @Test
    fun noSpendingBeforeMeansNothingToCompare() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallet()
        rig.ledger.snapshot.value = snapshot(listOf(line("a", "expense", "2026-10-01", 40_000, wallet = "w1")))
        assertNull(rig.service.observeHomePeriod().first().changePercent)
    }

    @Test
    fun theReportsScreensRangeNeverChangesHome() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallet()
        rig.ledger.snapshot.value = snapshot(listOf(line("a", "expense", "2026-10-01", 40_000, wallet = "w1")))
        rig.report()
        rig.service.setRange(ReportScope.AllWallets, RangeChoice.Previous)
        val home = rig.service.observeHomePeriod().first()
        assertEquals(date("2026-10-01"), home.range!!.from)
        assertEquals(40_000, home.summary!!.expenses)
    }

    @Test
    fun noWalletsIsSaidSoAndHasNoFigures() = runTest {
        val rig = ReportsRig(backgroundScope)
        val home = rig.service.observeHomePeriod().first()
        assertTrue(home.noWallets)
        assertNull(home.summary)
    }
}
