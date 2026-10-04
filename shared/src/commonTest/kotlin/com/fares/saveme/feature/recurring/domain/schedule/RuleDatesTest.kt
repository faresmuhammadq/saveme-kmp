package com.fares.saveme.feature.recurring.domain.schedule

import com.fares.saveme.feature.recurring.domain.model.Frequency
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The phone's preview equals the server's schedule (SC-002, FR-006, research R-08). */
class RuleDatesTest {
    private fun d(s: String) = LocalDate.parse(s)

    private fun dates(
        frequency: Frequency,
        interval: Int = 1,
        anchor: Int? = null,
        start: String,
        end: String? = null,
        count: Int? = null,
        limit: Int = 5,
        from: String? = null,
    ) = RuleDates.dates(frequency, interval, anchor, d(start), end?.let(::d), count, limit, from?.let(::d)).map { it.date.toString() }

    @Test
    fun dailyStepsByTheInterval() {
        assertEquals(listOf("2026-10-01", "2026-10-02", "2026-10-03"), dates(Frequency.Daily, start = "2026-10-01", limit = 3))
        assertEquals(listOf("2026-10-01", "2026-10-03", "2026-10-05"), dates(Frequency.Daily, 2, start = "2026-10-01", limit = 3))
        assertEquals(listOf("2026-10-01", "2026-10-04", "2026-10-07"), dates(Frequency.Daily, 3, start = "2026-10-01", limit = 3))
    }

    @Test
    fun weeklyKeepsTheStartsWeekday() {
        assertEquals(listOf("2026-10-05", "2026-10-12", "2026-10-19"), dates(Frequency.Weekly, start = "2026-10-05", limit = 3))
        assertEquals(listOf("2026-10-05", "2026-10-19", "2026-11-02"), dates(Frequency.Weekly, 2, start = "2026-10-05", limit = 3))
        assertEquals(listOf("2026-10-05", "2026-10-26"), dates(Frequency.Weekly, 3, start = "2026-10-05", limit = 2))
    }

    @Test
    fun monthlyDay31ClampsFromTheOriginNeverChained() {
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31"),
            dates(Frequency.Monthly, start = "2026-01-31"),
        )
        assertEquals(listOf("2028-01-31", "2028-02-29", "2028-03-31"), dates(Frequency.Monthly, start = "2028-01-31", limit = 3))
        assertEquals(listOf("2026-01-31", "2026-03-31", "2026-05-31"), dates(Frequency.Monthly, 2, start = "2026-01-31", limit = 3))
        assertEquals(listOf("2026-01-31", "2026-04-30", "2026-07-31"), dates(Frequency.Monthly, 3, start = "2026-01-31", limit = 3))
    }

    @Test
    fun monthlyDays29And30InFebruary() {
        assertEquals(listOf("2026-01-29", "2026-02-28", "2026-03-29"), dates(Frequency.Monthly, start = "2026-01-29", limit = 3))
        assertEquals(listOf("2028-01-29", "2028-02-29", "2028-03-29"), dates(Frequency.Monthly, start = "2028-01-29", limit = 3))
        assertEquals(listOf("2026-01-30", "2026-02-28", "2026-03-30"), dates(Frequency.Monthly, start = "2026-01-30", limit = 3))
    }

    @Test
    fun yearly29FebruaryFallsBackTo28InNonLeapYears() {
        assertEquals(
            listOf("2028-02-29", "2029-02-28", "2030-02-28", "2031-02-28", "2032-02-29"),
            dates(Frequency.Yearly, start = "2028-02-29"),
        )
        assertEquals(listOf("2026-10-05", "2028-10-05", "2030-10-05"), dates(Frequency.Yearly, 2, start = "2026-10-05", limit = 3))
    }

    @Test
    fun anAnchorBeforeTheStartDayInTheFirstMonthIsDroppedAndRoundsAreNumberedOverWhatRemains() {
        val rounds = RuleDates.dates(Frequency.Monthly, 1, 5, d("2026-10-20"), null, null, 3)
        assertEquals(listOf("2026-11-05", "2026-12-05", "2027-01-05"), rounds.map { it.date.toString() })
        assertEquals(listOf(1, 2, 3), rounds.map { it.ordinal })
    }

    @Test
    fun theAnchorDefaultsToTheStartsDayAndAnAnchorOverridesIt() {
        assertEquals(listOf("2026-10-15", "2026-11-15"), dates(Frequency.Monthly, start = "2026-10-15", limit = 2))
        assertEquals(listOf("2026-10-01", "2026-11-01"), dates(Frequency.Monthly, anchor = 1, start = "2026-10-01", limit = 2))
    }

    @Test
    fun stopsAtTheEndDateAndAfterTheCount() {
        assertEquals(listOf("2026-10-01", "2026-11-01", "2026-12-01"), dates(Frequency.Monthly, start = "2026-10-01", end = "2026-12-01", limit = 10))
        assertEquals(listOf("2026-10-01", "2026-11-01"), dates(Frequency.Monthly, start = "2026-10-01", count = 2, limit = 10))
        assertEquals(listOf("2026-10-01"), dates(Frequency.Monthly, start = "2026-10-01", end = "2026-10-31", limit = 10))
    }

    @Test
    fun fromSkipsEarlierRoundsButKeepsTheirNumbers() {
        val rounds = RuleDates.dates(Frequency.Monthly, 1, null, d("2026-01-15"), null, null, 2, d("2026-03-01"))
        assertEquals(listOf("2026-03-15", "2026-04-15"), rounds.map { it.date.toString() })
        assertEquals(listOf(3, 4), rounds.map { it.ordinal })
    }

    @Test
    fun anUnknownFrequencyGivesNothing() {
        assertTrue(dates(Frequency.Unknown, start = "2026-10-01").isEmpty())
    }

    @Test
    fun nothingPastTheLastSupportedDate() {
        assertEquals(listOf("2999-11-30", "2999-12-30"), dates(Frequency.Monthly, anchor = 30, start = "2999-11-30", limit = 5))
        assertEquals(listOf("2999-12-30", "2999-12-31"), dates(Frequency.Daily, start = "2999-12-30", limit = 5))
    }

    @Test
    fun aHundredAndTwentyRoundsAreQuick() {
        assertEquals(120, dates(Frequency.Monthly, start = "2026-01-31", limit = 120).size)
    }
}
