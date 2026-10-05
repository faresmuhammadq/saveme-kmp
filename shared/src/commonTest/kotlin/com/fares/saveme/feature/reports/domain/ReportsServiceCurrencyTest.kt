package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.budgets.profileView
import com.fares.saveme.feature.reports.ReportsRig
import com.fares.saveme.feature.reports.line
import com.fares.saveme.feature.reports.reportWalletView
import com.fares.saveme.feature.reports.domain.model.PlannedSummary
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.TrendSlot
import com.fares.saveme.feature.transactions.domain.LedgerSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** FR-013, FR-014, US6: one currency per report; the others named, never converted. */
class ReportsServiceCurrencyTest {

    private val all = ReportScope.AllWallets

    private fun ReportsRig.mixed() {
        wallets.views.value = listOf(
            reportWalletView("cash", "EGP", "Cash", opening = 10_000), reportWalletView("bank", "EGP", "Bank", opening = 20_000),
            reportWalletView("savings", "USD", "Savings", opening = 900),
        )
        ledger.snapshot.value = LedgerSnapshot(
            listOf(line("e", "expense", "2026-10-03", 5_000, wallet = "cash", category = "c"), line("u", "expense", "2026-10-04", 100, wallet = "savings", category = "c")),
            emptyMap(), emptyList(), emptyMap(), emptyList(),
        )
    }

    @Test
    fun theReportStartsInTheProfilesDefaultCurrencyAndNamesTheOtherWallets() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.mixed()
        val report = rig.report()
        assertEquals("EGP", report.currency)
        assertEquals(setOf("cash", "bank"), report.covered.walletIds)
        assertEquals(listOf("Savings" to "USD"), report.covered.excluded.map { it.name to it.currency })
        assertEquals(5_000, report.summary!!.expenses)
        assertEquals(listOf("EGP", "USD"), report.currencies)
    }

    @Test
    fun withoutAProfileTheDefaultIsEgp() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.mixed()
        rig.profile.value = null
        assertEquals("EGP", rig.report().currency)
    }

    @Test
    fun aProfileDefaultOfUsdMakesTheUsdReportTheFirst() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.mixed()
        rig.profile.value = profileView(currency = "USD")
        val report = rig.report()
        assertEquals("USD", report.currency)
        assertEquals(setOf("savings"), report.covered.walletIds)
        assertEquals(100, report.summary!!.expenses)
    }

    @Test
    fun settingTheCurrencySwapsCoveredAndExcludedAndNeverSumsAcrossCurrencies() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.mixed()
        rig.service.setCurrency("USD")
        val usd = rig.report()
        assertEquals(setOf("savings"), usd.covered.walletIds)
        assertEquals(listOf("Bank" to "EGP", "Cash" to "EGP"), usd.covered.excluded.map { it.name to it.currency })
        assertEquals(100, usd.summary!!.expenses)
        assertEquals(900 - 100L, usd.summary!!.balanceEnd)
        assertEquals("USD", usd.summary!!.currency)
    }

    @Test
    fun aCurrencyWithNoWalletIsZerosAndEveryWalletIsNamed() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.mixed()
        rig.service.setCurrency("EUR")
        val report = rig.report()
        assertEquals(emptySet(), report.covered.walletIds)
        assertEquals(3, report.covered.excluded.size)
        assertEquals(0, report.summary!!.income)
        assertEquals(0, report.summary!!.balanceEnd)
        assertEquals(emptyList(), report.breakdown!!.expenses.items)
        assertEquals(listOf("EGP", "EUR", "USD"), report.currencies, "the chosen currency stays in the list")
    }

    @Test
    fun aCurrencyWithNoWalletShowsZeroScheduledFiguresWhenScheduledIsOnAsTheServerAnswers() = runTest {
        // Found by the live QA (spec 014 T089): the server answers `planned` zeros, never absent, for a currency with no wallet.
        val rig = ReportsRig(backgroundScope)
        rig.mixed()
        rig.service.setCurrency("EUR")
        rig.service.setIncludeScheduled(all, true)
        val report = rig.report()
        assertEquals(PlannedSummary(0, 0, 0, 0), report.planned)
        assertEquals(0L, report.breakdown!!.expenses.plannedMinor)
        assertEquals(0L, report.breakdown!!.income.plannedMinor)
        assertEquals(true, report.trend.all { it is TrendSlot.Known && it.point.plannedExpensesMinor == 0L && it.point.plannedIncomeMinor == 0L })
    }

    @Test
    fun theCurrenciesAreTheWalletsPlusTheProfileDefaultSorted() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.mixed()
        rig.profile.value = profileView(currency = "KWD")
        assertEquals(listOf("EGP", "KWD", "USD"), rig.service.observeCurrencies().first())
    }
}
