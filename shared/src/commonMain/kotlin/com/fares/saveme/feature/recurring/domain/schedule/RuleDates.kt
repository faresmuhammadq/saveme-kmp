package com.fares.saveme.feature.recurring.domain.schedule

import com.fares.saveme.feature.recurring.domain.model.Frequency
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** One round of a series: its 1-based number and its scheduled date. */
data class Round(val ordinal: Int, val date: LocalDate)

/**
 * The dates a rule gives, ported from the backend's `RuleDates` (spec 010 R-08, FR-006): pure calendar arithmetic.
 * Every date is computed from the origin index k, never from the previous date, so a month-end clamp never drags later
 * months with it. Dates before the start (only the first monthly or yearly date can be) are dropped and rounds are
 * numbered from 1 over what remains. The series stops after the end date or the count-th round.
 */
object RuleDates {
    val FIRST_DATE = LocalDate(1900, 1, 1)
    val LAST_DATE = LocalDate(2999, 12, 31)
    private const val MAX_STEPS = 400_000

    /** The first [limit] rounds dated on or after [from] (the start when null). An unknown frequency gives none. */
    fun dates(
        frequency: Frequency,
        interval: Int,
        anchorDay: Int?,
        start: LocalDate,
        end: LocalDate?,
        count: Int?,
        limit: Int,
        from: LocalDate? = null,
    ): List<Round> {
        if (limit < 1 || interval < 1 || frequency == Frequency.Unknown) return emptyList()
        val origin = Origin(frequency, interval, anchorDay ?: start.day, start)
        val firstDropped = (origin.dateAt(0) ?: LAST_DATE) < start
        val result = ArrayList<Round>(minOf(limit, 128))
        var index = 0
        while (index < MAX_STEPS) {
            val date = origin.dateAt(index) ?: break
            if (end != null && date > end) break
            if (date >= start) {
                val ordinal = index - (if (firstDropped) 1 else 0) + 1
                if (count != null && ordinal > count) break
                if (from == null || date >= from) {
                    result += Round(ordinal, date)
                    if (result.size >= limit) break
                }
            }
            index++
        }
        return result
    }

    private class Origin(val frequency: Frequency, val interval: Int, val anchor: Int, val start: LocalDate) {
        fun dateAt(index: Int): LocalDate? {
            val steps = index.toLong() * interval
            return when (frequency) {
                Frequency.Daily -> addDays(steps)
                Frequency.Weekly -> addDays(steps * 7)
                Frequency.Monthly -> {
                    val months = start.year.toLong() * 12 + (start.month.ordinal) + steps
                    clamped((months / 12).toInt(), (months % 12).toInt() + 1, anchor)
                }
                Frequency.Yearly -> clamped((start.year + steps).coerceAtMost(10_000).toInt(), start.month.ordinal + 1, anchor)
                Frequency.Unknown -> null
            }
        }

        private fun addDays(days: Long): LocalDate? {
            if (days > start.daysUntil(LAST_DATE)) return null
            return start.plus(days, DateTimeUnit.DAY)
        }

        private fun clamped(year: Int, month: Int, day: Int): LocalDate? {
            if (year < 1900 || year > 2999) return null
            return LocalDate(year, month, minOf(day, daysInMonth(year, month)))
        }
    }

    internal fun daysInMonth(year: Int, month: Int): Int {
        val first = LocalDate(year, month, 1)
        return first.daysUntil(first.plus(1, DateTimeUnit.MONTH))
    }
}
