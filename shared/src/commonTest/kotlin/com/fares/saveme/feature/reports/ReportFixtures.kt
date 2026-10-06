package com.fares.saveme.feature.reports

import com.fares.saveme.feature.recurring.domain.ScheduledLine
import com.fares.saveme.feature.reports.domain.model.Covered
import com.fares.saveme.feature.reports.domain.model.ReportWallet
import com.fares.saveme.feature.transactions.domain.LedgerLine
import kotlinx.datetime.LocalDate

/** Short builders for report tests; nothing here touches the clock. */
fun date(text: String): LocalDate = LocalDate.parse(text)

fun line(
    id: String,
    kind: String,
    on: String,
    amount: Long,
    wallet: String = "w1",
    counter: String? = null,
    category: String? = null,
    waiting: Boolean = false,
) = LedgerLine(id, kind, date(on), amount, "EGP", wallet, counter, category, waiting)

fun scheduledLine(
    id: String,
    kind: String,
    on: String,
    amount: Long,
    wallet: String = "w1",
    counter: String? = null,
    category: String? = null,
    currency: String = "EGP",
) = ScheduledLine(id, kind, date(on), amount, currency, wallet, counter, category)

fun reportWallet(
    id: String,
    name: String = id,
    currency: String = "EGP",
    opening: Long = 0,
    liability: Boolean = false,
    archived: Boolean = false,
    waiting: Boolean = false,
) = ReportWallet(id, name, currency, opening, liability, archived, waiting)

fun covered(vararg wallets: ReportWallet, currency: String = "EGP") = Covered(wallets.toList(), emptyList(), currency)

/** A ready report with every part given or defaulted; amounts are EGP minor units. */
fun sampleReport(
    scope: com.fares.saveme.feature.reports.domain.model.ReportScope = com.fares.saveme.feature.reports.domain.model.ReportScope.AllWallets,
    title: String? = null,
    range: com.fares.saveme.feature.reports.domain.model.ReportRange? = com.fares.saveme.feature.reports.domain.model.ReportRange(
        date("2026-10-01"), date("2026-10-31"), com.fares.saveme.feature.reports.domain.model.RangeKind.Period,
    ),
    summary: com.fares.saveme.feature.reports.domain.model.SummaryFigures? = com.fares.saveme.feature.reports.domain.model.SummaryFigures(
        income = 1_000_000, expenses = 550_000, net = 450_000, transfersIn = 0, transfersOut = 50_000, balanceStart = 2_480_000, balanceEnd = 2_880_000, currency = "EGP",
    ),
    planned: com.fares.saveme.feature.reports.domain.model.PlannedSummary? = null,
    expenses: List<com.fares.saveme.feature.reports.domain.model.CategoryItem> = listOf(
        com.fares.saveme.feature.reports.domain.model.CategoryItem("groceries", "p-g", "groceries", 350_000, null),
        com.fares.saveme.feature.reports.domain.model.CategoryItem("transport", "p-t", "transport", 120_000, null),
        com.fares.saveme.feature.reports.domain.model.CategoryItem("own-1", "own-1", null, 80_000, null),
    ),
    trend: List<com.fares.saveme.feature.reports.domain.model.TrendSlot> = emptyList(),
    excluded: List<com.fares.saveme.feature.reports.domain.model.ExcludedWallet> = emptyList(),
    includeScheduled: Boolean = false,
    includesWaiting: Boolean = false,
    canGoNext: Boolean = true,
    liability: Boolean = false,
    tooLarge: Boolean = false,
    choices: com.fares.saveme.feature.reports.domain.model.ReportChoices = com.fares.saveme.feature.reports.domain.model.ReportChoices(),
    updatedAt: kotlin.time.Instant? = null,
    knownUntil: kotlinx.datetime.LocalDate? = null,
): com.fares.saveme.feature.reports.domain.model.Report {
    val total = expenses.sumOf { it.valueMinor }
    return com.fares.saveme.feature.reports.domain.model.Report(
        scope = scope, title = title, currency = "EGP", currencies = listOf("EGP", "USD"), today = date("2026-10-02"), range = range,
        canGoNext = canGoNext, covered = covered(reportWallet("w1"), currency = "EGP").copy(excluded = excluded), summary = summary, planned = planned,
        breakdown = com.fares.saveme.feature.reports.domain.model.CategoryBreakdown(
            com.fares.saveme.feature.reports.domain.model.CategoryGroup(total, planned?.let { 0L }, expenses),
            com.fares.saveme.feature.reports.domain.model.CategoryGroup(0, null, emptyList()),
        ),
        trend = trend, includeScheduled = includeScheduled, knownUntil = knownUntil, isLiability = liability, includesWaiting = includesWaiting,
        updatedAt = updatedAt, tooLarge = tooLarge, choices = choices.copy(includeScheduled = includeScheduled),
    )
}

fun trendPoint(start: String, end: String, income: Long, expenses: Long, plannedIncome: Long? = null, plannedExpenses: Long? = null) =
    com.fares.saveme.feature.reports.domain.model.TrendSlot.Known(
        com.fares.saveme.feature.reports.domain.model.TrendPoint(
            com.fares.saveme.feature.reports.domain.model.BudgetPeriodSpan(date(start), date(end)), income, expenses, income - expenses, plannedIncome, plannedExpenses,
        ),
    )
