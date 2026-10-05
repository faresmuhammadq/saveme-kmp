package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.ReportsRig
import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.line
import com.fares.saveme.feature.reports.reportWalletView
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.TrendSlot
import com.fares.saveme.feature.transactions.domain.LedgerSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FR-010: 3, 6 or 12 consecutive periods ending with the one containing the range's last day. */
class ReportsServiceTrendTest {

    private fun ReportsRig.august() {
        wallets.views.value = listOf(reportWalletView("w1"))
        ledger.snapshot.value = LedgerSnapshot(
            listOf(
                line("i9", "income", "2026-09-05", 1_000_000, category = "c"), line("e9", "expense", "2026-09-06", 1_200_000, category = "c"),
                line("i10", "income", "2026-10-05", 1_000_000, category = "c"), line("e10", "expense", "2026-10-06", 350_000, category = "c"),
                line("t", "transfer", "2026-10-07", 777, counter = "w9"),
            ),
            emptyMap(), emptyList(), emptyMap(), emptyList(),
        )
    }

    private fun points(trend: List<TrendSlot>) = trend.map { (it as TrendSlot.Known).point }

    @Test
    fun threePeriodsAreZeroFilledOldestFirstWithoutTransfers() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.august()
        rig.service.setTrendLength(ReportScope.AllWallets, 3)
        val points = points(rig.report().trend)
        assertEquals(listOf("2026-08-01", "2026-09-01", "2026-10-01"), points.map { it.period.start.toString() })
        assertEquals(listOf(0L, 1_000_000L, 1_000_000L), points.map { it.incomeMinor })
        assertEquals(listOf(0L, 1_200_000L, 350_000L), points.map { it.expensesMinor })
        assertEquals(listOf(0L, -200_000L, 650_000L), points.map { it.netMinor })
    }

    @Test
    fun changingTheLengthReEmitsAndAskedTheLedgerForTheWiderWindow() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.august()
        assertEquals(6, rig.report().trend.size)
        rig.service.setTrendLength(ReportScope.AllWallets, 12)
        val twelve = rig.report()
        assertEquals(12, twelve.trend.size)
        assertEquals(12, twelve.choices.trendLength)
        assertEquals(date("2025-11-01"), rig.ledger.asks.last().second)
    }

    @Test
    fun aLengthOtherThanThreeSixOrTwelveIsIgnored() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.august()
        rig.service.setTrendLength(ReportScope.AllWallets, 5)
        assertEquals(6, rig.report().choices.trendLength)
    }

    @Test
    fun theTrendEndsWithThePeriodContainingARangesLastDay() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.august()
        rig.service.setRange(ReportScope.AllWallets, com.fares.saveme.feature.reports.domain.model.RangeChoice.Previous)
        val report = rig.report()
        assertEquals(date("2026-09-01"), report.range!!.from)
        assertEquals(date("2026-09-01"), points(report.trend).last().period.start)
    }

    @Test
    fun unknownPeriodsCarryNoFigures() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.august()
        rig.profile.value = com.fares.saveme.feature.budgets.profileView(startDay = 1, from = date("2026-09-01"))
        val trend = rig.report().trend
        assertEquals(6, trend.size)
        assertEquals(List(4) { TrendSlot.Unknown }, trend.take(4))
        assertTrue(trend.drop(4).all { it is TrendSlot.Known })
    }
}
