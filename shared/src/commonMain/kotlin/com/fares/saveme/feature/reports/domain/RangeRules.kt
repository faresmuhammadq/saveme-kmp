package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.domain.model.RangeProblem
import kotlinx.datetime.LocalDate

/** A custom Report Range as the server accepts it: first day on or before the last, at most 366 days, 1900..2999 (FR-009). */
object RangeRules {
    const val MAX_DAYS = 366
    val MIN_DATE = LocalDate(1900, 1, 1)
    val MAX_DATE = LocalDate(2999, 12, 31)

    fun validate(from: LocalDate, to: LocalDate): RangeProblem? = when {
        from > to -> RangeProblem.Order
        from < MIN_DATE || to > MAX_DATE -> RangeProblem.OutOfBounds
        daysInclusive(from, to) > MAX_DAYS -> RangeProblem.TooLong
        else -> null
    }

    fun daysInclusive(from: LocalDate, to: LocalDate): Long = to.toEpochDays() - from.toEpochDays() + 1
}
