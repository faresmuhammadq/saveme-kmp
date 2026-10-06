package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.ReportsRig
import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.line
import com.fares.saveme.feature.reports.reportWalletView
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.transactions.domain.LedgerSnapshot
import com.fares.saveme.feature.transactions.domain.LedgerTouch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** FR-001, FR-002, SC-003: waiting changes count at once, are noted, and accepting them changes no figure. */
class ReportsServiceWaitingTest {

    private fun waitingSnapshot() = LedgerSnapshot(
        lines = listOf(line("a", "expense", "2026-10-03", 20_000, category = "c", waiting = true), line("b", "expense", "2026-10-04", 5_000, category = "c")),
        before = emptyMap(), syncedLines = listOf(line("b", "expense", "2026-10-04", 5_000, category = "c")), syncedBefore = emptyMap(),
        waitingTouches = listOf(LedgerTouch("w1", date("2026-10-03"))),
    )

    private fun settledSnapshot() = LedgerSnapshot(
        lines = listOf(line("a", "expense", "2026-10-03", 20_000, category = "c"), line("b", "expense", "2026-10-04", 5_000, category = "c")),
        before = emptyMap(), syncedLines = listOf(line("a", "expense", "2026-10-03", 20_000, category = "c"), line("b", "expense", "2026-10-04", 5_000, category = "c")),
        syncedBefore = emptyMap(), waitingTouches = emptyList(),
    )

    @Test
    fun aWaitingChangeIsCountedAtOnceAndNoted() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("w1"))
        rig.ledger.snapshot.value = waitingSnapshot()
        val report = rig.report()
        assertEquals(25_000, report.summary!!.expenses)
        assertTrue(report.includesWaiting)
    }

    @Test
    fun acceptingTheChangeLeavesEveryFigureEqualAndClearsTheNote() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("w1"))
        rig.ledger.snapshot.value = waitingSnapshot()
        val before = rig.report()
        rig.ledger.snapshot.value = settledSnapshot()
        val after = rig.report()
        assertEquals(before.summary, after.summary)
        assertEquals(before.breakdown, after.breakdown)
        assertEquals(before.trend, after.trend)
        assertTrue(before.includesWaiting)
        assertFalse(after.includesWaiting)
    }

    @Test
    fun aTouchAfterTheRangeEndDoesNotCountButOneBeforeItDoes() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("w1"))
        rig.ledger.snapshot.value = LedgerSnapshot.Empty.copy(waitingTouches = listOf(LedgerTouch("w1", date("2026-11-05"))))
        assertFalse(rig.report().includesWaiting)
        rig.ledger.snapshot.value = LedgerSnapshot.Empty.copy(waitingTouches = listOf(LedgerTouch("w1", date("2026-08-05"))))
        assertTrue(rig.report().includesWaiting, "a change before the range moves the balances")
    }

    @Test
    fun aWaitingChangeToACoveredWalletItselfIsNoted() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("w1", waiting = true))
        assertTrue(rig.report().includesWaiting)
        rig.wallets.views.value = listOf(reportWalletView("w1"), reportWalletView("usd", "USD", waiting = true))
        assertFalse(rig.report().includesWaiting, "an excluded wallet's change is not in the figures")
    }

    @Test
    fun anOccurrenceChangeCountsOnlyWhileTheSwitchIsOn() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("w1"))
        rig.scheduled.waiting.value = true
        assertFalse(rig.report().includesWaiting)
        rig.service.setIncludeScheduled(ReportScope.AllWallets, true)
        assertTrue(rig.report().includesWaiting)
    }
}
