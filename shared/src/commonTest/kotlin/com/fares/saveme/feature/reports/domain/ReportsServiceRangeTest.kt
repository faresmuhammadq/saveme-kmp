package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.budgets.profileView
import com.fares.saveme.feature.reports.ReportsRig
import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.reportWalletView
import com.fares.saveme.feature.reports.domain.model.RangeChoice
import com.fares.saveme.feature.reports.domain.model.RangeKind
import com.fares.saveme.feature.reports.domain.model.RangeProblem
import com.fares.saveme.feature.reports.domain.model.ReportScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** FR-008, FR-009, US5: previous and next across derived and learned periods, custom ranges, the forward limit, midnight. */
class ReportsServiceRangeTest {

    private val all = ReportScope.AllWallets

    private fun rig(scope: kotlinx.coroutines.CoroutineScope, startDay: Int = 1, from: String? = null) = ReportsRig(scope).also {
        it.wallets.views.value = listOf(reportWalletView("w1"))
        it.profile.value = profileView(startDay = startDay, from = from?.let(::date))
    }

    @Test
    fun startDayTwentyFiveOnTheSecondOfOctoberGivesTheBriefsPeriodAndPrevious() = runTest {
        val rig = rig(backgroundScope, startDay = 25)
        val current = rig.report().range!!
        assertEquals(date("2026-09-25"), current.from)
        assertEquals(date("2026-10-24"), current.to)
        rig.service.setRange(all, RangeChoice.Previous)
        val previous = rig.report().range!!
        assertEquals(date("2026-08-25"), previous.from)
        assertEquals(date("2026-09-24"), previous.to)
        rig.service.setRange(all, RangeChoice.Next)
        assertEquals(date("2026-09-25"), rig.report().range!!.from)
    }

    @Test
    fun nextStopsAtThePeriodContainingTodayPlusThirtyFiveDays() = runTest {
        val rig = rig(backgroundScope)
        assertTrue(rig.report().canGoNext)
        rig.service.setRange(all, RangeChoice.Next)
        val november = rig.report()
        assertEquals(date("2026-11-01"), november.range!!.from)
        assertFalse(november.canGoNext)
        rig.service.setRange(all, RangeChoice.Next)
        assertEquals(date("2026-11-01"), rig.report().range!!.from, "no further than the limit")
    }

    @Test
    fun aCustomRangeIsValidatedAndSetAndResetByCurrentPeriod() = runTest {
        val rig = rig(backgroundScope)
        assertNull(rig.service.validateCustom(date("2026-01-01"), date("2027-01-01")))
        assertEquals(RangeProblem.TooLong, rig.service.validateCustom(date("2026-01-01"), date("2027-01-02")))
        assertEquals(RangeProblem.Order, rig.service.validateCustom(date("2026-10-02"), date("2026-10-01")))
        rig.service.setRange(all, RangeChoice.Custom(date("2026-09-10"), date("2026-10-12")))
        val custom = rig.report()
        assertEquals(RangeKind.Custom, custom.range!!.kind)
        assertEquals(date("2026-09-10"), custom.range!!.from)
        rig.service.setRange(all, RangeChoice.Custom(date("2026-01-01"), date("2027-01-02")))
        assertEquals(date("2026-09-10"), rig.report().range!!.from, "an invalid range does not change the shown one")
        rig.service.setRange(all, RangeChoice.CurrentPeriod)
        assertEquals(date("2026-10-01"), rig.report().range!!.from)
    }

    @Test
    fun theTrendOfACustomRangeEndsWithThePeriodContainingItsLastDay() = runTest {
        val rig = rig(backgroundScope)
        rig.service.setRange(all, RangeChoice.Custom(date("2026-08-10"), date("2026-09-12")))
        val trend = rig.report().trend.map { (it as com.fares.saveme.feature.reports.domain.model.TrendSlot.Known).point.period.start.toString() }
        assertEquals("2026-09-01", trend.last())
    }

    @Test
    fun previousAndNextFromACustomRangeStepOutOfItToTheAdjoiningPeriods() = runTest {
        val rig = rig(backgroundScope)
        rig.service.setRange(all, RangeChoice.Custom(date("2026-09-10"), date("2026-10-12")))
        rig.service.setRange(all, RangeChoice.Previous)
        assertEquals(date("2026-09-01"), rig.report().range!!.from, "the period containing the day before the range")
        rig.service.setRange(all, RangeChoice.Custom(date("2026-09-10"), date("2026-10-12")))
        rig.service.setRange(all, RangeChoice.Next)
        assertEquals(date("2026-10-01"), rig.report().range!!.from, "the period containing the day after the range")
    }

    @Test
    fun midnightInTheProfilesZoneMovesTodayThePeriodAndTheLimit() = runTest {
        val rig = rig(backgroundScope)
        rig.now.value = Instant.parse("2026-10-31T21:30:00Z") // 23:30 in Cairo (UTC+2 after the clocks go back)
        val late = rig.report()
        assertEquals(date("2026-10-31"), late.today)
        rig.now.value = Instant.parse("2026-10-31T22:05:00Z") // 00:05 on 1 November in Cairo
        val after = rig.report()
        assertEquals(date("2026-11-01"), after.today)
        assertEquals(date("2026-11-01"), after.range!!.from)
        assertEquals("EGP", after.currency)
    }

    @Test
    fun theKnownUntilDateAppearsOnlyWhenTheSwitchIsOnAndTheRangeEndsAfterIt() = runTest {
        val rig = rig(backgroundScope)
        rig.service.setIncludeScheduled(all, true)
        assertNull(rig.report().knownUntil, "October ends before today plus 35 days")
        rig.service.setRange(all, RangeChoice.Next)
        assertEquals(date("2026-11-06"), rig.report().knownUntil)
    }

    @Test
    fun theChoicesLastWhileTheAppRunsAndNothingIsStored() = runTest {
        val rig = rig(backgroundScope)
        rig.service.setRange(all, RangeChoice.Previous)
        rig.service.setTrendLength(all, 3)
        rig.service.setCurrency("USD")
        rig.service.setIncludeScheduled(all, true)
        val choices = rig.report().choices
        assertEquals(3, choices.trendLength)
        assertEquals("USD", choices.currency)
        assertTrue(choices.includeScheduled)
    }
}
