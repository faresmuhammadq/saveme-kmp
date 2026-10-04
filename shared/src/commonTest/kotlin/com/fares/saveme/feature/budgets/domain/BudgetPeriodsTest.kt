package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod
import com.fares.saveme.feature.budgets.domain.model.PeriodState
import com.fares.saveme.feature.budgets.domain.model.StoredPeriod
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The phone's current period equals the server's (SC-004, FR-009, FR-010; backend spec 015 US5). */
class BudgetPeriodsTest {

    private fun d(text: String) = LocalDate.parse(text)

    private fun current(today: String, startDay: Int?, appliesFrom: String? = null, stored: List<StoredPeriod> = emptyList()): PeriodState =
        BudgetPeriods.current(d(today), startDay, appliesFrom?.let { d(it) }, stored)

    private fun known(state: PeriodState): BudgetPeriod = assertIs<PeriodState.Known>(state).period

    private fun assertPeriod(start: String, end: String, days: Int, elapsed: Int?, actual: BudgetPeriod) {
        assertEquals(BudgetPeriod(d(start), d(end), days, elapsed), actual)
    }

    @Test
    fun startDayOneIsTheCalendarMonth() {
        assertPeriod("2026-10-01", "2026-10-31", 31, 2, known(current("2026-10-02", 1)))
        assertPeriod("2026-11-01", "2026-11-30", 30, 30, known(current("2026-11-30", 1)))
        assertPeriod("2026-02-01", "2026-02-28", 28, 28, known(current("2026-02-28", 1)))
        assertPeriod("2028-02-01", "2028-02-29", 29, 29, known(current("2028-02-29", 1)))
    }

    @Test
    fun startDayFifteenSwitchesOnTheFifteenth() {
        assertPeriod("2026-09-15", "2026-10-14", 30, 30, known(current("2026-10-14", 15)))
        assertPeriod("2026-10-15", "2026-11-14", 31, 1, known(current("2026-10-15", 15)))
    }

    @Test
    fun startDayTwentyFiveRunsTwentyFifthToTwentyFourth() {
        // Backend spec 015 US5 scenario 1: 2 Oct with day 25 is day 8 of 25 Sep - 24 Oct.
        assertPeriod("2026-09-25", "2026-10-24", 30, 8, known(current("2026-10-02", 25)))
        assertPeriod("2025-12-25", "2026-01-24", 31, 17, known(current("2026-01-10", 25)))
        assertPeriod("2026-10-25", "2026-11-24", 31, 1, known(current("2026-10-25", 25)))
    }

    @Test
    fun startDayTwentyEightWorksInEveryMonthIncludingFebruary() {
        assertPeriod("2026-01-28", "2026-02-27", 31, 31, known(current("2026-02-27", 28)))
        assertPeriod("2026-02-28", "2026-03-27", 28, 1, known(current("2026-02-28", 28)))
        assertPeriod("2028-02-28", "2028-03-27", 29, 2, known(current("2028-02-29", 28)))
        assertPeriod("2026-12-28", "2027-01-27", 31, 1, known(current("2026-12-28", 28)))
    }

    @Test
    fun everyStartDayAndEveryDayOfAYearYieldsAPeriodContainingToday() {
        var day = d("2026-01-01")
        while (day <= d("2028-03-31")) {
            for (startDay in 1..28) {
                val period = known(current(day.toString(), startDay))
                assertEquals(true, day >= period.start && day <= period.end, "$day with $startDay: $period")
                assertEquals(startDay, period.start.day)
                assertEquals(period.daysInPeriod.toLong(), period.end.toEpochDays() - period.start.toEpochDays() + 1)
                assertEquals(day.toEpochDays() - period.start.toEpochDays() + 1, period.daysElapsed?.toLong())
            }
            day = LocalDate.fromEpochDays(day.toEpochDays() + 1)
        }
    }

    @Test
    fun withoutAProfileTheStartDayIsOne() {
        assertPeriod("2026-10-01", "2026-10-31", 31, 2, known(current("2026-10-02", null)))
    }

    @Test
    fun anAppliesFromDateThatIsAbsentTodayOrPastGivesTheCalendarPeriod() {
        val stored = listOf(StoredPeriod(d("2026-09-25"), d("2026-10-31"), 37))
        assertPeriod("2026-10-01", "2026-10-31", 31, 10, known(current("2026-10-10", 1, appliesFrom = null, stored = stored)))
        assertPeriod("2026-11-01", "2026-11-30", 30, 1, known(current("2026-11-01", 1, appliesFrom = "2026-11-01", stored = stored)))
        assertPeriod("2026-11-01", "2026-11-30", 30, 5, known(current("2026-11-05", 1, appliesFrom = "2026-11-01", stored = stored)))
    }

    @Test
    fun aPendingChangeUsesTheStoredServerPeriodContainingToday() {
        // Backend spec 015 US5 scenario 2: day 25 changed to 1 on 10 Oct, applies from 1 Nov; the period under way ends 31 Oct.
        val stored = listOf(StoredPeriod(d("2026-08-25"), d("2026-09-24"), 31), StoredPeriod(d("2026-09-25"), d("2026-10-31"), 37))
        assertPeriod("2026-09-25", "2026-10-31", 37, 16, known(current("2026-10-10", 1, appliesFrom = "2026-11-01", stored = stored)))
        assertPeriod("2026-09-25", "2026-10-31", 37, 37, known(current("2026-10-31", 1, appliesFrom = "2026-11-01", stored = stored)))
    }

    @Test
    fun aPendingChangeWithNoStoredPeriodForTodayIsUnknown() {
        assertEquals(PeriodState.Unknown, current("2026-10-10", 1, appliesFrom = "2026-11-01"))
        val older = listOf(StoredPeriod(d("2026-08-25"), d("2026-09-24"), 31))
        assertEquals(PeriodState.Unknown, current("2026-10-10", 1, appliesFrom = "2026-11-01", stored = older))
    }

    @Test
    fun theNewPeriodsApplyOnceTheDateIsReached() {
        val stored = listOf(StoredPeriod(d("2026-09-25"), d("2026-10-31"), 37))
        assertEquals(PeriodState.Unknown, current("2026-10-31", 1, appliesFrom = "2026-11-01", stored = emptyList()))
        assertPeriod("2026-11-01", "2026-11-30", 30, 1, known(current("2026-11-01", 1, appliesFrom = "2026-11-01", stored = stored)))
    }
}
