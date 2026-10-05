package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.recurring.domain.ScheduledLine
import com.fares.saveme.feature.reports.domain.model.BudgetPeriodSpan
import com.fares.saveme.feature.reports.domain.model.CategoryBreakdown
import com.fares.saveme.feature.reports.domain.model.CategoryGroup
import com.fares.saveme.feature.reports.domain.model.CategoryItem
import com.fares.saveme.feature.reports.domain.model.PlannedSummary
import com.fares.saveme.feature.reports.domain.model.SummaryFigures
import com.fares.saveme.feature.reports.domain.model.TrendPoint
import com.fares.saveme.feature.transactions.domain.DaySum
import com.fares.saveme.feature.transactions.domain.LedgerLine
import kotlinx.datetime.LocalDate

/**
 * The report rules of ADR-0022 on the phone, with no I/O and no clock (spec 014 research R-04): the same figures the
 * server computes for the same wallets and range. Money is `Long` minor units with checked addition; anything beyond a
 * `Long` throws [TooLarge] (shown as "Too large to show"). A wallet report is a cross-wallet report over one wallet, so a
 * transfer always counts in its direction there.
 *
 * Lines dated before the range's first day do not count in the figures; they only roll into the opening balance of the
 * range, on top of [before] (the effect of the lines before the first line the caller passes).
 */
object ReportCalculator {
    private const val INCOME = "income"
    private const val EXPENSE = "expense"
    private const val TRANSFER = "transfer"

    /**
     * @param covered the wallets the report covers
     * @param openings every covered wallet's opening balance (a waiting change to it applied)
     * @param before per wallet, the effect of every live line dated before the first of [lines]'s window
     */
    fun summary(
        currency: String,
        covered: Set<String>,
        openings: Map<String, Long>,
        from: LocalDate,
        to: LocalDate,
        lines: List<LedgerLine>,
        before: Map<String, Long>,
    ): SummaryFigures {
        var income = 0L
        var expenses = 0L
        var transfersIn = 0L
        var transfersOut = 0L
        var start = 0L
        var inRange = 0L
        for (id in covered) {
            start = addExact(start, openings[id] ?: 0L)
            start = addExact(start, before[id] ?: 0L)
        }
        for (line in lines) {
            if (line.date > to) continue
            val effect = coveredEffect(line, covered) ?: continue
            if (line.date < from) {
                start = addExact(start, effect)
                continue
            }
            inRange = addExact(inRange, effect)
            when (line.kind) {
                INCOME -> if (line.walletId in covered) income = addExact(income, line.amountMinor)
                EXPENSE -> if (line.walletId in covered) expenses = addExact(expenses, line.amountMinor)
                TRANSFER -> {
                    val sourceCovered = line.walletId in covered
                    val destinationCovered = line.counterWalletId != null && line.counterWalletId in covered
                    if (sourceCovered && !destinationCovered) transfersOut = addExact(transfersOut, line.amountMinor)
                    if (destinationCovered && !sourceCovered) transfersIn = addExact(transfersIn, line.amountMinor)
                }
            }
        }
        return SummaryFigures(
            income = income,
            expenses = expenses,
            net = subtractExact(income, expenses),
            transfersIn = transfersIn,
            transfersOut = transfersOut,
            balanceStart = start,
            balanceEnd = addExact(start, inRange),
            currency = currency,
        )
    }

    /** The signed effect of a line on the covered wallets together; null for a kind this app does not know. */
    private fun coveredEffect(line: LedgerLine, covered: Set<String>): Long? = when (line.kind) {
        INCOME -> if (line.walletId in covered) line.amountMinor else 0L
        EXPENSE -> if (line.walletId in covered) -line.amountMinor else 0L
        TRANSFER -> {
            var effect = 0L
            if (line.walletId in covered) effect = subtractExact(effect, line.amountMinor)
            if (line.counterWalletId != null && line.counterWalletId in covered) effect = addExact(effect, line.amountMinor)
            effect
        }
        else -> null
    }

    /** Scheduled sums beside the actual ones, in the report's currency only, with the same covered-transfer rule. */
    fun planned(
        currency: String,
        covered: Set<String>,
        from: LocalDate,
        to: LocalDate,
        scheduled: List<ScheduledLine>,
    ): PlannedSummary {
        var income = 0L
        var expenses = 0L
        var transfersIn = 0L
        var transfersOut = 0L
        for (line in scheduled) {
            if (line.currency != currency || line.date < from || line.date > to) continue
            when (line.kind) {
                INCOME -> if (line.walletId in covered) income = addExact(income, line.amountMinor)
                EXPENSE -> if (line.walletId in covered) expenses = addExact(expenses, line.amountMinor)
                TRANSFER -> {
                    val sourceCovered = line.walletId in covered
                    val destinationCovered = line.counterWalletId != null && line.counterWalletId in covered
                    if (sourceCovered && !destinationCovered) transfersOut = addExact(transfersOut, line.amountMinor)
                    if (destinationCovered && !sourceCovered) transfersIn = addExact(transfersIn, line.amountMinor)
                }
            }
        }
        return PlannedSummary(income, expenses, transfersIn, transfersOut)
    }

    private class Sum(val categoryId: String, var value: Long = 0L, var planned: Long = 0L)

    /**
     * Income and expenses grouped separately by [keyOf] (a platform category by its `system_key`, any other by its id; never
     * merged by name), zero items left out (kept when the scheduled sum is not zero and [scheduled] is given), sorted by sum
     * descending then key ascending in byte order (the backend's `strcmp`); each total is the exact sum of its items.
     * Transfers are in neither group. [scheduled] null means the switch is off: no planned member anywhere.
     */
    fun breakdown(
        currency: String,
        covered: Set<String>,
        from: LocalDate,
        to: LocalDate,
        lines: List<LedgerLine>,
        scheduled: List<ScheduledLine>?,
        keyOf: (String) -> String,
    ): CategoryBreakdown {
        val expenses = HashMap<String, Sum>()
        val income = HashMap<String, Sum>()
        for (line in lines) {
            if (line.date < from || line.date > to || line.walletId !in covered) continue
            val group = when (line.kind) {
                EXPENSE -> expenses
                INCOME -> income
                else -> continue
            }
            val id = line.categoryId.orEmpty()
            val sum = group.getOrPut(keyOf(id)) { Sum(id) }
            sum.value = addExact(sum.value, line.amountMinor)
        }
        if (scheduled != null) {
            for (line in scheduled) {
                if (line.currency != currency || line.date < from || line.date > to || line.walletId !in covered) continue
                val group = when (line.kind) {
                    EXPENSE -> expenses
                    INCOME -> income
                    else -> continue
                }
                val id = line.categoryId.orEmpty()
                val sum = group.getOrPut(keyOf(id)) { Sum(id) }
                sum.planned = addExact(sum.planned, line.amountMinor)
            }
        }
        return CategoryBreakdown(group(expenses, scheduled != null), group(income, scheduled != null))
    }

    private fun group(sums: Map<String, Sum>, withPlanned: Boolean): CategoryGroup {
        val items = sums.entries
            .filter { (_, sum) -> sum.value != 0L || (withPlanned && sum.planned != 0L) }
            .map { (key, sum) ->
                CategoryItem(
                    label = key,
                    categoryId = sum.categoryId,
                    systemKey = key.takeIf { it != sum.categoryId },
                    valueMinor = sum.value,
                    plannedMinor = if (withPlanned) sum.planned else null,
                )
            }
            .sortedWith(compareByDescending<CategoryItem> { it.valueMinor }.thenBy { it.label })
        var total = 0L
        var planned = 0L
        for (item in items) {
            total = addExact(total, item.valueMinor)
            planned = addExact(planned, item.plannedMinor ?: 0L)
        }
        return CategoryGroup(total, if (withPlanned) planned else null, items)
    }

    /**
     * Income, expenses and net per period, zero-filled; transfers are not in it. [spans] are consecutive and oldest first.
     * [lines] are the rows of the range and [daily] the sums per day of the days around it. With [scheduled] given, each point
     * also carries the scheduled income and expenses of its period.
     */
    fun trend(
        currency: String,
        covered: Set<String>,
        spans: List<BudgetPeriodSpan>,
        lines: List<LedgerLine>,
        scheduled: List<ScheduledLine>?,
        daily: List<DaySum> = emptyList(),
    ): List<TrendPoint> {
        val income = LongArray(spans.size)
        val expenses = LongArray(spans.size)
        val plannedIncome = LongArray(spans.size)
        val plannedExpenses = LongArray(spans.size)
        fun indexOf(date: LocalDate): Int {
            var low = 0
            var high = spans.size - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val span = spans[mid]
                when {
                    date < span.start -> high = mid - 1
                    date > span.end -> low = mid + 1
                    else -> return mid
                }
            }
            return -1
        }
        for (line in lines) {
            if (line.walletId !in covered) continue
            val index = indexOf(line.date)
            if (index < 0) continue
            when (line.kind) {
                INCOME -> income[index] = addExact(income[index], line.amountMinor)
                EXPENSE -> expenses[index] = addExact(expenses[index], line.amountMinor)
            }
        }
        for (day in daily) {
            val index = indexOf(day.date)
            if (index < 0) continue
            income[index] = addExact(income[index], day.incomeMinor)
            expenses[index] = addExact(expenses[index], day.expensesMinor)
        }
        if (scheduled != null) {
            for (line in scheduled) {
                if (line.currency != currency || line.walletId !in covered) continue
                val index = indexOf(line.date)
                if (index < 0) continue
                when (line.kind) {
                    INCOME -> plannedIncome[index] = addExact(plannedIncome[index], line.amountMinor)
                    EXPENSE -> plannedExpenses[index] = addExact(plannedExpenses[index], line.amountMinor)
                }
            }
        }
        return spans.mapIndexed { i, span ->
            TrendPoint(
                period = span,
                incomeMinor = income[i],
                expensesMinor = expenses[i],
                netMinor = subtractExact(income[i], expenses[i]),
                plannedIncomeMinor = if (scheduled != null) plannedIncome[i] else null,
                plannedExpensesMinor = if (scheduled != null) plannedExpenses[i] else null,
            )
        }
    }
}
