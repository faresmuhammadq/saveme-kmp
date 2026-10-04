package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod
import com.fares.saveme.feature.budgets.domain.model.PeriodState
import com.fares.saveme.feature.budgets.domain.model.StoredPeriod
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * The current Budget Period on the phone, as the server computes it (research R-08, FR-009, FR-010). Pure: "today" is
 * derived by the caller in the profile's time zone and never written (P-05.3).
 */
object BudgetPeriods {
    /** The server's default without a profile (FR-009). */
    const val DEFAULT_START_DAY = 1

    /**
     * @param startDay the server copy's budget start day (1..28), or null while the phone holds no profile
     * @param appliesFrom the date a pending start day change takes over, or null / not after [today] when none is pending
     * @param stored periods the server reported in earlier progress reads, used while a change is pending
     */
    fun current(today: LocalDate, startDay: Int?, appliesFrom: LocalDate?, stored: List<StoredPeriod>): PeriodState {
        if (appliesFrom != null && appliesFrom > today) {
            val period = stored.firstOrNull { today >= it.start && today <= it.end } ?: return PeriodState.Unknown
            return PeriodState.Known(BudgetPeriod(period.start, period.end, period.daysInPeriod, elapsed(period.start, today)))
        }
        return PeriodState.Known(calendar(today, startDay ?: DEFAULT_START_DAY))
    }

    /** The period starting on the latest [startDay] on or before [today]; it ends the day before the next one. */
    fun calendar(today: LocalDate, startDay: Int): BudgetPeriod {
        val day = startDay.coerceIn(1, 28)
        val start = if (today.day >= day) {
            LocalDate(today.year, today.month, day)
        } else {
            val previous = LocalDate(today.year, today.month, 1).minus(DatePeriod(months = 1))
            LocalDate(previous.year, previous.month, day)
        }
        val end = start.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        return BudgetPeriod(start, end, daysBetween(start, end) + 1, elapsed(start, today))
    }

    private fun elapsed(start: LocalDate, today: LocalDate): Int = daysBetween(start, today) + 1

    private fun daysBetween(from: LocalDate, to: LocalDate): Int = (to.toEpochDays() - from.toEpochDays()).toInt()
}
