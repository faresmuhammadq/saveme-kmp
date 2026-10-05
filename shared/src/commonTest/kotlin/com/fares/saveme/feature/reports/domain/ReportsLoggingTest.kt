package com.fares.saveme.feature.reports.domain

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.budgets.profileView
import com.fares.saveme.feature.reports.ReportServerJson
import com.fares.saveme.feature.reports.ReportsRig
import com.fares.saveme.feature.reports.data.ReportsApi
import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.display
import com.fares.saveme.feature.reports.line
import com.fares.saveme.feature.reports.reportWalletView
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.TrendSlot
import com.fares.saveme.feature.transactions.domain.LedgerSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** P-09.2, FR-025: refresh, period lookup and probe (ok and mismatch) log ids, counts, outcomes and member names, never values. */
class ReportsLoggingTest {

    @Test
    fun noAmountShareCategoryOrWalletNameReachesTheLog() = runTest {
        val captured = mutableListOf<String>()
        Logger.setLogWriters(object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                captured += "$tag: $message"
            }
        })
        Logger.setMinSeverity(Severity.Verbose)
        try {
            fun seed(rig: ReportsRig) {
                rig.wallets.views.value = listOf(reportWalletView("w1", name = "Hidden Vault Wallet", opening = 4_242_424), reportWalletView("usd", "USD", name = "Secret Savings"))
                rig.categories.systemKeys["p-1"] = "groceries"
                rig.categories.names["p-1"] = display("p-1", "Truffle Category")
                val lines = listOf(line("a", "expense", "2026-10-03", 9_876_543, category = "p-1"), line("b", "income", "2026-10-04", 1_357_911, category = "p-1"))
                rig.ledger.snapshot.value = LedgerSnapshot(lines, emptyMap(), lines, emptyMap(), emptyList())
            }
            val source = ReportsRig(backgroundScope)
            seed(source)
            val report = source.report()
            val points = report.trend.filterIsInstance<TrendSlot.Known>().map { it.point }
            // The lookup answers with periods; the probe answers differ from the phone (a mismatch is logged).
            val api = ReportsRig(backgroundScope) { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/summary") -> jsonResponse(200, ReportServerJson.summary(report.summary!!.copy(income = 1)))
                    path.endsWith("/by-category") -> jsonResponse(200, ReportServerJson.byCategory(report.breakdown!!.expenses, report.breakdown!!.income))
                    else -> jsonResponse(200, ReportServerJson.trend(points.takeLast(2).takeIf { it.isNotEmpty() } ?: points))
                }
            }
            val rig = ReportsRig(backgroundScope, probe = DevReportParityProbe(ReportsApi(api.auth.client)))
            seed(rig)
            rig.service.setTrendLength(ReportScope.AllWallets, 3)
            rig.service.refresh(ReportScope.AllWallets, force = true)
            rig.service.refresh(ReportScope.Wallet("w1"), force = true)

            // A period lookup (start day changed in the past) logs only how many periods it learned.
            val lookup = ReportsRig(backgroundScope) {
                jsonResponse(200, ReportServerJson.trend(points.take(2).map { it.copy(period = com.fares.saveme.feature.reports.domain.model.BudgetPeriodSpan(date("2026-08-10"), date("2026-09-24"))) }))
            }
            seed(lookup)
            lookup.profile.value = profileView(startDay = 25, from = date("2026-09-25"))
            lookup.service.setTrendLength(ReportScope.AllWallets, 3)
            assertTrue(captured.any { "report.lookup" in it }, "the lookup logs its outcome: $captured")

            val log = captured.joinToString("\n")
            assertTrue(captured.any { "report.refresh" in it }, "the refresh logs its outcome: $captured")
            assertTrue(captured.any { "report.parity" in it })
            listOf("9876543", "98765.43", "1357911", "13579.11", "4242424", "42424.24", "Hidden Vault Wallet", "Secret Savings", "Truffle Category", "groceries").forEach { secret ->
                assertFalse(secret in log, "'$secret' reached the log")
            }
        } finally {
            Logger.setLogWriters(emptyList())
        }
    }
}
