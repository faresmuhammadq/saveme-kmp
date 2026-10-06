package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.ReportsRig
import com.fares.saveme.feature.reports.line
import com.fares.saveme.feature.reports.reportWalletView
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.ReportState
import com.fares.saveme.feature.transactions.domain.LedgerSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-003, FR-017 and the wallet scope of FR-013: one wallet, every member's rows, its own currency. */
class ReportsServiceWalletTest {

    private val wallet = ReportScope.Wallet("shared")

    @Test
    fun aWalletReportCountsEveryMembersTransactionsInTheWalletsCurrency() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("shared", "USD", name = "Trip"), reportWalletView("other", "EGP"))
        rig.ledger.snapshot.value = LedgerSnapshot(
            listOf(
                line("mine", "expense", "2026-10-02", 100_000, wallet = "shared", category = "c"),
                line("partners", "expense", "2026-10-03", 50_000, wallet = "shared", category = "c"),
            ),
            emptyMap(), emptyList(), emptyMap(), emptyList(),
        )
        val report = rig.report(wallet)
        assertEquals(150_000, report.summary!!.expenses)
        assertEquals("USD", report.currency)
        assertEquals("USD", report.summary!!.currency)
        assertEquals("Trip", report.title)
        assertEquals(setOf("shared"), rig.ledger.asks.last().first)
        assertEquals(emptyList(), report.covered.excluded)
        assertEquals(emptyList(), report.currencies, "a wallet report has no currency choice")
    }

    @Test
    fun transfersCountByDirection() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("shared"), reportWalletView("other"))
        rig.ledger.snapshot.value = LedgerSnapshot(
            listOf(
                line("out", "transfer", "2026-10-02", 70_000, wallet = "shared", counter = "other"),
                line("in", "transfer", "2026-10-03", 30_000, wallet = "other", counter = "shared"),
            ),
            emptyMap(), emptyList(), emptyMap(), emptyList(),
        )
        val summary = rig.report(wallet).summary!!
        assertEquals(70_000, summary.transfersOut)
        assertEquals(30_000, summary.transfersIn)
        assertEquals(0, summary.income)
        assertEquals(0, summary.expenses)
    }

    @Test
    fun aLiabilityWalletIsFlaggedAndItsBalancesStayNegative() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("shared", type = "credit_card", opening = -300_000))
        rig.ledger.snapshot.value = LedgerSnapshot(
            listOf(line("a", "expense", "2026-10-02", 420_000, wallet = "shared", category = "c")), emptyMap(), emptyList(), emptyMap(), emptyList(),
        )
        val report = rig.report(wallet)
        assertTrue(report.isLiability)
        assertEquals(-300_000, report.summary!!.balanceStart)
        assertEquals(-720_000, report.summary!!.balanceEnd)
    }

    @Test
    fun anArchivedWalletIsReportedLikeAnyOther() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("shared", archived = true, opening = 5_000))
        val report = rig.report(wallet)
        assertEquals(5_000, report.summary!!.balanceEnd)
    }

    @Test
    fun aPurgedWalletIsGone() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("other"))
        assertEquals(ReportState.WalletGone, rig.service.observe(wallet).first())
    }

    @Test
    fun aWalletReportKeepsItsOwnChoices() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("shared"), reportWalletView("other"))
        rig.service.setTrendLength(wallet, 12)
        rig.service.setTrendLength(ReportScope.AllWallets, 3)
        assertEquals(12, rig.report(wallet).choices.trendLength)
        assertEquals(3, rig.report(ReportScope.AllWallets).choices.trendLength)
        assertNull(rig.report(wallet).choices.currency)
    }
}
