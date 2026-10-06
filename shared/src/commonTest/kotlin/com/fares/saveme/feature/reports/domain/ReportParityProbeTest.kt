package com.fares.saveme.feature.reports.domain

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.reports.ReportServerJson
import com.fares.saveme.feature.reports.ReportsRig
import com.fares.saveme.feature.reports.RecordingProbe
import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.display
import com.fares.saveme.feature.reports.line
import com.fares.saveme.feature.reports.reportWalletView
import com.fares.saveme.feature.reports.data.ReportsApi
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.TrendSlot
import com.fares.saveme.feature.transactions.domain.LedgerSnapshot
import com.fares.saveme.feature.transactions.domain.LedgerTouch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FR-028, R-12: the development build compares synced-only figures with the server's, logging member names only. */
class ReportParityProbeTest {

    private val all = ReportScope.AllWallets
    private val captured = mutableListOf<String>()

    private fun capture() {
        captured.clear()
        Logger.setLogWriters(object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                captured += "${severity.name} $tag: $message"
            }
        })
        Logger.setMinSeverity(Severity.Verbose)
    }

    private fun parity() = captured.filter { "report.parity" in it }

    private fun ReportsRig.seed() {
        wallets.views.value = listOf(reportWalletView("w1", opening = 1_000_000), reportWalletView("usd", "USD", name = "Secret Savings"))
        categories.systemKeys["p-g"] = "groceries"
        categories.names["p-g"] = display("p-g", "Groceries")
        val lines = listOf(
            line("a", "income", "2026-10-02", 7_654_321, category = "p-g"),
            line("b", "expense", "2026-10-03", 1_234_567, category = "p-g"),
        )
        ledger.snapshot.value = LedgerSnapshot(lines, emptyMap(), lines, emptyMap(), emptyList())
    }

    /** What the server would say for the rig's data: the phone's own synced computation, as JSON. */
    private suspend fun ReportsRig.serverAnswers(
        tamper: (kind: String, json: String) -> String = { _, json -> json },
        excluded: List<Pair<String, String>> = listOf("usd" to "USD"),
    ): suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData {
        val report = report()
        val breakdown = report.breakdown!!
        val points = report.trend.map { (it as TrendSlot.Known).point }
        return { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/summary") -> jsonResponse(200, tamper("summary", ReportServerJson.summary(report.summary!!, excluded = excluded)))
                path.endsWith("/by-category") -> jsonResponse(200, tamper("by-category", ReportServerJson.byCategory(breakdown.expenses, breakdown.income, excluded = excluded)))
                else -> jsonResponse(200, tamper("trend", ReportServerJson.trend(points, excluded = excluded)))
            }
        }
    }

    private suspend fun probeRig(scope: kotlinx.coroutines.CoroutineScope, tamper: (String, String) -> String = { _, json -> json }, excluded: List<Pair<String, String>> = listOf("usd" to "USD")): ReportsRig {
        val seedRig = ReportsRig(scope)
        seedRig.seed()
        val answers = seedRig.serverAnswers(tamper, excluded)
        lateinit var holder: ReportsRig
        val probeApiRig = ReportsRig(scope, handler = answers)
        probeApiRig.seed()
        holder = ReportsRig(scope, probe = DevReportParityProbe(ReportsApi(probeApiRig.auth.client)))
        holder.seed()
        probeRequests = probeApiRig.requests
        return holder
    }

    private var probeRequests: List<io.ktor.client.request.HttpRequestData> = emptyList()

    @Test
    fun matchingAnswersLogOkForAllThreeReportsAndNothingElse() = runTest {
        capture()
        try {
            val rig = probeRig(backgroundScope)
            rig.service.refresh(all, force = true)
            val lines = parity()
            assertEquals(3, lines.size, "$captured")
            assertTrue(lines.all { "report.parity ok" in it }, "$lines")
            assertEquals(listOf("summary", "by-category", "trend"), lines.map { Regex("kind=([a-z-]+)").find(it)!!.groupValues[1] })
            val requests = probeRequests
            assertEquals(3, requests.size)
            requests.forEach { request ->
                assertEquals("EGP", request.url.parameters["currency"])
                assertEquals("false", request.url.parameters["include_planned"])
            }
            assertEquals("2026-10-01", requests[0].url.parameters["from"])
            assertEquals("6", requests[2].url.parameters["months"])
            assertEquals("2026-10-31", requests[2].url.parameters["date"])
        } finally {
            Logger.setLogWriters(emptyList())
        }
    }

    @Test
    fun aDifferingMemberIsLoggedByNameNeverByValue() = runTest {
        capture()
        try {
            val rig = probeRig(
                backgroundScope,
                tamper = { kind, json -> if (kind == "summary") json.replace("\"amount_minor\":7654321", "\"amount_minor\":7654322") else json },
            )
            rig.service.refresh(all, force = true)
            val mismatch = parity().single { "mismatch" in it }
            assertTrue("kind=summary" in mismatch && "members=income" in mismatch, mismatch)
            assertTrue(parity().filter { "ok" in it }.map { it }.size == 2)
            assertTrue("7654321" !in captured.joinToString() && "7654322" !in captured.joinToString())
        } finally {
            Logger.setLogWriters(emptyList())
        }
    }

    @Test
    fun excludedWalletsAreComparedAsASetAndAWrongOneIsAMember() = runTest {
        capture()
        try {
            val rig = probeRig(backgroundScope, excluded = listOf("another" to "USD"))
            rig.service.refresh(all, force = true)
            val mismatch = parity().first { "kind=summary" in it }
            assertTrue("mismatch" in mismatch && "excluded_wallets" in mismatch, mismatch)
        } finally {
            Logger.setLogWriters(emptyList())
        }
    }

    @Test
    fun anyWaitingChangeInTheCoveredWalletsSkipsTheProbe() = runTest {
        capture()
        try {
            val rig = probeRig(backgroundScope)
            rig.ledger.snapshot.value = rig.ledger.snapshot.value.copy(waitingTouches = listOf(LedgerTouch("w1", date("2026-10-03"))))
            rig.service.refresh(all, force = true)
            assertEquals(emptyList(), probeRequests)
            rig.ledger.snapshot.value = LedgerSnapshot.Empty
            rig.wallets.views.value = listOf(reportWalletView("w1", waiting = true))
            rig.service.refresh(all, force = true)
            assertEquals(emptyList(), probeRequests)
            rig.wallets.views.value = listOf(reportWalletView("w1"))
            rig.scheduled.waiting.value = true
            rig.service.refresh(all, force = true)
            assertEquals(emptyList(), probeRequests)
        } finally {
            Logger.setLogWriters(emptyList())
        }
    }

    @Test
    fun aGoneWalletOrABadIdSkipsAndABadQueryIsAClientDefect() = runTest {
        capture()
        try {
            val notFound = ReportsRig(backgroundScope) { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
            val invalidId = ReportsRig(backgroundScope) { jsonResponse(422, problemBody(422, "INVALID_ID")) }
            val badQuery = ReportsRig(backgroundScope) { jsonResponse(400, problemBody(400, "INVALID_QUERY_PARAMETER")) }
            val offline = ReportsRig(backgroundScope)
            listOf(notFound, invalidId, badQuery, offline).forEach { api ->
                val rig = ReportsRig(backgroundScope, probe = DevReportParityProbe(ReportsApi(api.auth.client)))
                rig.wallets.views.value = listOf(reportWalletView("w1"))
                rig.service.refresh(ReportScope.Wallet("w1"), force = true)
            }
            val text = parity().joinToString("\n")
            assertTrue("reason=not_found" in text)
            assertTrue("reason=invalid_id" in text)
            assertTrue("client defect" in text && "INVALID_QUERY_PARAMETER" in text)
            assertTrue("reason=offline" in text)
        } finally {
            Logger.setLogWriters(emptyList())
        }
    }

    @Test
    fun theReleaseBindingNeverCallsTheFigureEndpoints() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.seed()
        rig.service.refresh(all, force = true)
        rig.service.refresh(ReportScope.Wallet("w1"), force = true)
        assertEquals(emptyList(), rig.requests)
        assertEquals(false, NoParityProbe.enabled)
    }

    @Test
    fun theProbeIsHandedTheSyncedOnlyFiguresOfTheShownScope() = runTest {
        val probe = RecordingProbe()
        val rig = ReportsRig(backgroundScope, probe = probe)
        rig.seed()
        rig.service.setTrendLength(all, 3)
        rig.service.setIncludeScheduled(all, true)
        rig.service.refresh(all, force = true)
        val (request, computed) = probe.compared.single()
        assertEquals(date("2026-10-01"), request.from)
        assertEquals(date("2026-10-31"), request.to)
        assertEquals("EGP", request.currency)
        assertEquals(3, request.trendLength)
        assertEquals(true, request.includeScheduled)
        assertEquals(1_000_000 + 7_654_321 - 1_234_567L, computed.summary.balanceEnd)
        assertEquals(setOf("usd" to "USD"), computed.excluded)
        assertEquals(3, computed.trend.size)
        assertEquals(0, computed.planned!!.expenses)
    }
}
