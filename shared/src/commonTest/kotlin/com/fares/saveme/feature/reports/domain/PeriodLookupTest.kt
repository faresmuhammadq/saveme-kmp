package com.fares.saveme.feature.reports.domain

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.budgets.profileView
import com.fares.saveme.feature.reports.ReportsRig
import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.reportWalletView
import com.fares.saveme.feature.reports.domain.model.PeriodSource
import com.fares.saveme.feature.reports.domain.model.RangeChoice
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.TrendSlot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-011, FR-019, FR-027: a period the phone cannot derive is looked up through the trend endpoint, never guessed. */
class PeriodLookupTest {

    private val all = ReportScope.AllWallets

    private fun money() = """{"amount_minor":0,"currency":"EGP"}"""

    private fun trendBody(vararg periods: Pair<String, String>) = buildString {
        val points = periods.joinToString(",", "[", "]") { (from, _) -> """{"label":"$from","value":${money()}}""" }
        append("""{"data":{"wallet_id":null,"currency":"EGP","periods":[""")
        append(periods.joinToString(",") { (from, to) -> """{"label":"$from","from":"$from","to":"$to"}""" })
        append("""],"income":$points,"expenses":$points,"net":$points,"planned_income":null,"planned_expenses":null},"meta":{"excluded_wallets":[]}}""")
    }

    private fun ReportsRig.pastChange() {
        wallets.views.value = listOf(reportWalletView("w1"))
        profile.value = profileView(startDay = 25, from = date("2026-09-25"))
    }

    @Test
    fun anUnknownOlderPeriodStartsOneLookupEndingTheDayBeforeTheOldestKnownPeriod() = runTest {
        val rig = ReportsRig(backgroundScope) {
            jsonResponse(200, trendBody("2026-07-10" to "2026-08-09", "2026-08-10" to "2026-09-24"))
        }
        rig.pastChange()
        rig.service.setTrendLength(all, 3)
        val request = rig.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/reports/trend"))
        assertEquals(setOf("months", "date"), request.url.parameters.names())
        assertEquals("2", request.url.parameters["months"])
        assertEquals("2026-09-24", request.url.parameters["date"])
        val starts = rig.report().trend.map { (it as TrendSlot.Known).point.period.start.toString() }
        assertEquals(listOf("2026-07-10", "2026-08-10", "2026-09-25"), starts)
    }

    @Test
    fun learnedPeriodsAreMarkedAsTheServersAndStoredForTheAccount() = runTest {
        val rig = ReportsRig(backgroundScope) { jsonResponse(200, trendBody("2026-08-10" to "2026-09-24")) }
        rig.pastChange()
        rig.service.setTrendLength(all, 3)
        rig.service.setRange(all, RangeChoice.Previous)
        // The previous period (ending 24 Sep) is not derivable: the lookup came from stepping to it.
        val stored = rig.learned.get("u1", PeriodProfile(25, date("2026-09-25")))
        assertEquals(listOf(date("2026-08-10")), stored.map { it.start })
        val report = rig.report()
        assertEquals(PeriodSource.Server, report.range!!.source)
        assertEquals(date("2026-08-10"), report.range!!.from)
    }

    @Test
    fun aPendingChangeLeavesTheWholeTrendUnknownUntilTheServerAnswers() = runTest {
        val rig = ReportsRig(backgroundScope) {
            jsonResponse(200, trendBody("2026-05-01" to "2026-05-31", "2026-06-01" to "2026-06-30", "2026-07-01" to "2026-07-31", "2026-08-01" to "2026-08-31", "2026-09-01" to "2026-09-30", "2026-10-01" to "2026-10-31"))
        }
        rig.wallets.views.value = listOf(reportWalletView("w1"))
        rig.profile.value = profileView(startDay = 1, from = date("2026-11-01"))
        val before = rig.report()
        assertNull(before.range)
        assertNull(before.summary)
        assertNull(before.breakdown)
        assertTrue(before.trend.all { it is TrendSlot.Unknown })
        rig.service.refresh(all, force = true)
        val request = rig.requests.single()
        assertEquals("6", request.url.parameters["months"])
        assertEquals("2026-10-02", request.url.parameters["date"], "the day the screen needs")
        val after = rig.report()
        assertEquals(date("2026-10-01"), after.range!!.from)
        assertTrue(after.summary != null)
    }

    @Test
    fun offlineAFailureOrABadRequestLeaveThePeriodUnknown() = runTest {
        val offline = ReportsRig(backgroundScope)
        offline.pastChange()
        offline.service.setTrendLength(all, 3)
        offline.service.refresh(all, force = true)
        assertTrue(offline.report().trend.take(2).all { it is TrendSlot.Unknown })

        val refused = ReportsRig(backgroundScope) { jsonResponse(500, problemBody(500, "INTERNAL_ERROR")) }
        refused.pastChange()
        refused.service.setTrendLength(all, 3)
        refused.service.refresh(all, force = true)
        assertTrue(refused.report().trend.take(2).all { it is TrendSlot.Unknown })

        val captured = mutableListOf<String>()
        Logger.setLogWriters(object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                captured += "$severity $tag: $message"
            }
        })
        try {
            val invalid = ReportsRig(backgroundScope) { jsonResponse(400, problemBody(400, "INVALID_QUERY_PARAMETER")) }
            invalid.pastChange()
            invalid.service.setTrendLength(all, 3)
            invalid.service.refresh(all, force = true)
            assertTrue(invalid.report().trend.take(2).all { it is TrendSlot.Unknown })
            assertTrue(captured.any { it.startsWith("Error") && "client defect" in it && "req-42" in it }, "a 400 is logged as a client defect: $captured")
        } finally {
            Logger.setLogWriters(emptyList())
        }
    }

    @Test
    fun theSameLookupIsNotRepeatedUntilTheNextOpenOrPull() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.pastChange()
        rig.service.setTrendLength(all, 3)
        rig.service.setTrendLength(all, 3)
        rig.service.setTrendLength(all, 3)
        assertEquals(1, rig.requests.size)
        rig.service.refresh(all, force = true)
        assertEquals(2, rig.requests.size, "a pull asks again")
    }

    @Test
    fun anAnswerForASignedOutAccountIsDropped() = runTest {
        lateinit var rig: ReportsRig
        rig = ReportsRig(backgroundScope) {
            rig.authState.value = AuthState.SignedOut(SignOutReason.None)
            jsonResponse(200, trendBody("2026-08-10" to "2026-09-24"))
        }
        rig.pastChange()
        rig.service.setTrendLength(all, 3)
        assertEquals(emptyList(), rig.learned.get("u1", PeriodProfile(25, date("2026-09-25"))))
    }

    @Test
    fun aDerivablePeriodNeverAsksTheServer() = runTest {
        val rig = ReportsRig(backgroundScope)
        rig.wallets.views.value = listOf(reportWalletView("w1"))
        rig.service.setTrendLength(all, 12)
        rig.service.refresh(all, force = true)
        assertEquals(emptyList(), rig.requests)
    }
}
