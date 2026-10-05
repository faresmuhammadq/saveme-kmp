package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.date
import com.fares.saveme.feature.reports.domain.model.BudgetPeriodSpan
import com.fares.saveme.feature.reports.domain.model.PeriodLookup
import com.fares.saveme.feature.reports.domain.model.PeriodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** FR-008, FR-010, FR-011, SC-004: the phone's periods equal the server's, and nothing is guessed. */
class ReportPeriodsTest {

    private fun span(from: String, to: String) = BudgetPeriodSpan(date(from), date(to))

    private fun known(from: String, to: String, source: PeriodSource = PeriodSource.Derived) = PeriodLookup.Known(span(from, to), source)

    private fun profile(day: Int?, from: String? = null) = if (day == null) PeriodProfile.None else PeriodProfile(day, from?.let(::date))

    private fun containing(on: String, profile: PeriodProfile, learned: List<BudgetPeriodSpan> = emptyList()) =
        ReportPeriods.containing(date(on), profile, learned)

    @Test
    fun startDaysAreDerivedAsBudgetsDeriveThemAcrossShortAndLongMonths() {
        val table = listOf(
            Triple(1, "2026-10-15", span("2026-10-01", "2026-10-31")),
            Triple(1, "2026-02-10", span("2026-02-01", "2026-02-28")),
            Triple(15, "2026-10-14", span("2026-09-15", "2026-10-14")),
            Triple(15, "2026-10-15", span("2026-10-15", "2026-11-14")),
            Triple(25, "2026-10-02", span("2026-09-25", "2026-10-24")),
            Triple(25, "2026-12-30", span("2026-12-25", "2027-01-24")),
            Triple(28, "2026-02-27", span("2026-01-28", "2026-02-27")),
            Triple(28, "2026-02-28", span("2026-02-28", "2026-03-27")),
            Triple(28, "2024-02-29", span("2024-02-28", "2024-03-27")),
            Triple(28, "2026-03-01", span("2026-02-28", "2026-03-27")),
            Triple(28, "2026-07-31", span("2026-07-28", "2026-08-27")),
        )
        table.forEach { (day, on, expected) ->
            assertEquals(PeriodLookup.Known(expected, PeriodSource.Derived), containing(on, profile(day)), "day $day on $on")
        }
    }

    @Test
    fun noProfileMeansStartDayOne() {
        assertEquals(known("2026-10-01", "2026-10-31"), containing("2026-10-20", PeriodProfile.None))
    }

    @Test
    fun aPastChangeIsDerivableOnlyForPeriodsThatStartOnOrAfterIt() {
        val p = profile(25, from = "2026-08-25")
        assertEquals(known("2026-09-25", "2026-10-24"), containing("2026-10-02", p))
        assertEquals(known("2026-08-25", "2026-09-24"), containing("2026-08-30", p))
        assertEquals(PeriodLookup.Unknown, containing("2026-08-20", p))
    }

    @Test
    fun theTransitionalPeriodIsNotDerivableEvenAfterTheChangeDate() {
        val p = profile(25, from = "2026-09-10")
        assertEquals(PeriodLookup.Unknown, containing("2026-09-12", p))
        assertEquals(known("2026-09-25", "2026-10-24"), containing("2026-09-26", p))
    }

    @Test
    fun aPendingChangeLeavesTodaysPeriodUnknownUnlessLearned() {
        val p = profile(1, from = "2026-11-01")
        assertEquals(PeriodLookup.Unknown, containing("2026-10-02", p))
        val learned = listOf(span("2026-10-01", "2026-10-31"))
        assertEquals(known("2026-10-01", "2026-10-31", PeriodSource.Server), containing("2026-10-02", p, learned))
        assertEquals(known("2026-11-01", "2026-11-30"), containing("2026-11-05", p))
    }

    @Test
    fun learnedPeriodsAreUsedOnlyWhenTheyContainTheDate() {
        val p = profile(25, from = "2026-09-25")
        val learned = listOf(span("2026-08-10", "2026-09-24"))
        assertEquals(known("2026-08-10", "2026-09-24", PeriodSource.Server), containing("2026-09-01", p, learned))
        assertEquals(PeriodLookup.Unknown, containing("2026-07-01", p, learned))
    }

    @Test
    fun previousAndNextWalkAcrossDerivedAndLearnedPeriods() {
        val p = profile(25, from = "2026-09-25")
        val learned = listOf(span("2026-08-10", "2026-09-24"))
        val current = span("2026-09-25", "2026-10-24")
        assertEquals(known("2026-08-10", "2026-09-24", PeriodSource.Server), ReportPeriods.previous(current, p, learned))
        assertEquals(PeriodLookup.Unknown, ReportPeriods.previous(current, p, emptyList()))
        assertEquals(known("2026-10-25", "2026-11-24"), ReportPeriods.next(current, p, learned))
        assertEquals(known("2026-09-25", "2026-10-24"), ReportPeriods.next(span("2026-08-10", "2026-09-24"), p, learned))
    }

    @Test
    fun nextStopsAtThePeriodContainingTodayPlusThirtyFiveDays() {
        val today = date("2026-10-02")
        val p = profile(1)
        assertEquals(known("2026-11-01", "2026-11-30"), ReportPeriods.forwardLimit(today, p, emptyList()))
        assertTrue(ReportPeriods.canGoNext(date("2026-10-31"), today, p, emptyList()))
        assertFalse(ReportPeriods.canGoNext(date("2026-11-30"), today, p, emptyList()))
        // Today plus 35 days lands in the next period only after the 26th.
        assertFalse(ReportPeriods.canGoNext(date("2026-10-31"), date("2026-09-20"), p, emptyList()))
    }

    @Test
    fun theTrendEndsWithThePeriodContainingTheDateOldestFirst() {
        val slots = ReportPeriods.trend(date("2026-10-20"), 3, profile(1), emptyList())
        assertEquals(
            listOf(known("2026-08-01", "2026-08-31"), known("2026-09-01", "2026-09-30"), known("2026-10-01", "2026-10-31")),
            slots,
        )
    }

    @Test
    fun theTrendHasUnknownSlotsWhereThePhoneCannotNameAPeriod() {
        val slots = ReportPeriods.trend(date("2026-10-20"), 4, profile(1, from = "2026-09-01"), emptyList())
        assertEquals(listOf(PeriodLookup.Unknown, PeriodLookup.Unknown, known("2026-09-01", "2026-09-30"), known("2026-10-01", "2026-10-31")), slots)
        val learned = listOf(span("2026-07-01", "2026-07-31"), span("2026-08-01", "2026-08-31"))
        assertEquals(
            listOf(
                known("2026-07-01", "2026-07-31", PeriodSource.Server), known("2026-08-01", "2026-08-31", PeriodSource.Server),
                known("2026-09-01", "2026-09-30"), known("2026-10-01", "2026-10-31"),
            ),
            ReportPeriods.trend(date("2026-10-20"), 4, profile(1, from = "2026-09-01"), learned),
        )
    }

    @Test
    fun anUnknownLastPeriodMakesEveryOlderSlotUnknown() {
        val slots = ReportPeriods.trend(date("2026-10-02"), 3, profile(1, from = "2026-11-01"), emptyList())
        assertEquals(List(3) { PeriodLookup.Unknown }, slots)
    }
}
