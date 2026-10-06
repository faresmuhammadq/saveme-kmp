package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.budgets.domain.BudgetPeriods
import com.fares.saveme.feature.reports.domain.model.BudgetPeriodSpan
import com.fares.saveme.feature.reports.domain.model.PeriodLookup
import com.fares.saveme.feature.reports.domain.model.PeriodSource
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * What the phone knows of the person's Budget Period rule: the server copy's start day and the date it applies from. A
 * null [startDay] means no profile on the phone yet: start day 1, nothing pending (spec 012 FR-009).
 */
data class PeriodProfile(val startDay: Int?, val appliesFrom: LocalDate?) {
    override fun toString() = "PeriodProfile"

    companion object {
        val None = PeriodProfile(null, null)
    }
}

/**
 * Budget Periods for reports (research R-07). A period is derived on the phone exactly as budgets derive it, through
 * [BudgetPeriods.calendar], and only where the server's start day history is not needed: with no "applies from" date,
 * or for a date on or after it whose derived period also starts on or after it. Anything else (a period before a past or
 * pending change, or the transitional one) is looked up in the periods the server reported, else [PeriodLookup.Unknown]:
 * dates are never guessed (FR-011, SC-004). Pure: "today" is the caller's.
 */
object ReportPeriods {
    /** How far ahead "Next" may go: the period containing today plus this many days (FR-008). */
    const val FORWARD_DAYS = 35

    fun containing(date: LocalDate, profile: PeriodProfile, learned: Collection<BudgetPeriodSpan>): PeriodLookup {
        derive(date, profile)?.let { return PeriodLookup.Known(it, PeriodSource.Derived) }
        val known = learned.firstOrNull { date >= it.start && date <= it.end }
        return if (known != null) PeriodLookup.Known(known, PeriodSource.Server) else PeriodLookup.Unknown
    }

    private fun derive(date: LocalDate, profile: PeriodProfile): BudgetPeriodSpan? {
        val calendar = BudgetPeriods.calendar(date, profile.startDay ?: BudgetPeriods.DEFAULT_START_DAY)
        val span = BudgetPeriodSpan(calendar.start, calendar.end)
        val from = profile.appliesFrom ?: return span
        return span.takeIf { date >= from && it.start >= from }
    }

    /** The period before [span]; unknown when it cannot be named. */
    fun previous(span: BudgetPeriodSpan, profile: PeriodProfile, learned: Collection<BudgetPeriodSpan>): PeriodLookup =
        containing(span.start.minus(DatePeriod(days = 1)), profile, learned)

    fun next(span: BudgetPeriodSpan, profile: PeriodProfile, learned: Collection<BudgetPeriodSpan>): PeriodLookup =
        containing(span.end.plus(DatePeriod(days = 1)), profile, learned)

    /** The last period "Next" may reach: the one containing [today] plus [FORWARD_DAYS]. */
    fun forwardLimit(today: LocalDate, profile: PeriodProfile, learned: Collection<BudgetPeriodSpan>): PeriodLookup =
        containing(today.plus(DatePeriod(days = FORWARD_DAYS)), profile, learned)

    /**
     * Whether stepping forward from a range ending on [rangeEnd] stays within the limit: the next period starts on or before
     * the limit period's start (or, while the limit period is unknown, on or before today plus [FORWARD_DAYS]).
     */
    fun canGoNext(rangeEnd: LocalDate, today: LocalDate, profile: PeriodProfile, learned: Collection<BudgetPeriodSpan>): Boolean {
        val nextStart = rangeEnd.plus(DatePeriod(days = 1))
        return when (val limit = forwardLimit(today, profile, learned)) {
            is PeriodLookup.Known -> nextStart <= limit.span.start
            PeriodLookup.Unknown -> nextStart <= today.plus(DatePeriod(days = FORWARD_DAYS))
        }
    }

    /**
     * [count] consecutive periods, oldest first, the last containing [to]. Walking back needs each period's start, so once
     * one cannot be named every older slot is unknown too.
     */
    fun trend(to: LocalDate, count: Int, profile: PeriodProfile, learned: Collection<BudgetPeriodSpan>): List<PeriodLookup> {
        val slots = ArrayList<PeriodLookup>(count)
        var current = containing(to, profile, learned)
        repeat(count) {
            slots += current
            current = when (current) {
                is PeriodLookup.Known -> previous(current.span, profile, learned)
                PeriodLookup.Unknown -> PeriodLookup.Unknown
            }
        }
        return slots.reversed()
    }
}
