package com.fares.saveme.feature.reports.ui

import com.fares.saveme.feature.reports.domain.model.PlannedSummary
import com.fares.saveme.feature.reports.domain.model.Share
import com.fares.saveme.feature.reports.domain.model.SummaryFigures
import com.fares.saveme.feature.reports.ui.categories.GroupUi
import com.fares.saveme.feature.reports.ui.categories.ReportCategoriesState
import com.fares.saveme.feature.reports.ui.categories.ReportCategoriesStatus
import com.fares.saveme.feature.reports.ui.reports.ExcludedUi
import com.fares.saveme.feature.reports.ui.reports.RangeUi
import com.fares.saveme.feature.reports.ui.reports.ReportsState
import com.fares.saveme.feature.reports.ui.reports.ReportsStatus
import com.fares.saveme.feature.reports.ui.reports.SummaryUi
import com.fares.saveme.feature.reports.ui.reports.TrendItemUi
import com.fares.saveme.feature.reports.ui.reports.TrendUi
import com.fares.saveme.feature.reports.ui.reports.CategoryGroupKind
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Report screens' states with pinned dates: no golden depends on the real date (spec 014 SC-006). */
object ReportUiFixtures {
    val october = RangeUi(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), custom = false)
    val updated = Instant.parse("2026-10-02T10:42:00Z")

    fun row(id: String, name: String, minor: Long, total: Long, icon: String, color: String, planned: Long? = null, share: Share = Share.Percent(((minor * 100 + total / 2) / total).toInt())) =
        CategoryRowUi(id, name, icon, color, minor, planned, share, minor.toFloat() / total, "EGP")

    val groceries = row("g", "Groceries", 350_000, 550_000, "cart", "#2E7D32")
    val transport = row("t", "Transport", 120_000, 550_000, "bus", "#1F7F8C")
    val pets = row("p", "Pets", 80_000, 550_000, "heart", "#A9503A")
    val rows = listOf(groceries, transport, pets)

    private fun months(count: Int, scheduled: Boolean = false): List<TrendItemUi> {
        val starts = (0 until count).map { LocalDate(2025, 11, 1).let { first -> LocalDate((2025 + (10 + it) / 12), ((10 + it) % 12) + 1, 1) } }
        return starts.mapIndexed { i, start ->
            val end = LocalDate(start.year, start.month, 28)
            val income = if (i == 0 && count > 1) 0L else 1_000_000L
            val expenses = when {
                i == 0 && count > 1 -> 0L
                i % 3 == 1 -> 1_200_000L
                else -> 350_000L + i * 20_000L
            }
            TrendItemUi(start, end, income, expenses, income - expenses, if (scheduled && i == count - 1) 0L else null, if (scheduled && i == count - 1) 500_000L else null)
        }
    }

    val summary = SummaryFigures(1_000_000, 550_000, 450_000, 0, 50_000, 2_480_000, 2_880_000, "EGP")

    fun ready(trend: Int = 6, withCategories: Boolean = true) = ReportsState(
        status = ReportsStatus.Ready,
        currency = "EGP",
        currencies = listOf("EGP", "USD"),
        thisYear = 2026,
        range = october,
        canGoNext = true,
        summary = SummaryUi(summary, null, false),
        topExpenses = if (withCategories) rows else emptyList(),
        trendLength = trend,
        trend = TrendUi.Points(months(trend), hasUnknown = false),
        timeZoneId = "Africa/Cairo",
    )

    val noSpending = ready().copy(topExpenses = emptyList(), summary = SummaryUi(SummaryFigures(0, 0, 0, 0, 0, 2_480_000, 2_480_000, "EGP"), null, false))
    val noWallets = ReportsState(status = ReportsStatus.NoWallets)
    val loading = ReportsState(status = ReportsStatus.Loading)
    val offlineWaiting = ready().copy(offline = true, updatedAt = updated, includesWaiting = true)
    val failedRefresh = ready().copy(refreshFailed = true, updatedAt = updated)
    val trendNegative = ready(3).copy(
        trend = TrendUi.Points(
            listOf(
                TrendItemUi(LocalDate(2026, 8, 1), LocalDate(2026, 8, 31), 0, 0, 0, null, null),
                TrendItemUi(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30), 1_000_000, 1_200_000, -200_000, null, null),
                TrendItemUi(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), 1_000_000, 350_000, 650_000, null, null),
            ),
            hasUnknown = false,
        ),
    )
    val crossCurrency = ready().copy(
        excluded = listOf(ExcludedUi("Holiday fund", "KWD"), ExcludedUi("Savings", "USD")),
        otherCurrencies = listOf("KWD", "USD"),
    )
    val usdReport = ready().copy(
        currency = "USD",
        summary = SummaryUi(SummaryFigures(0, 0, 0, 0, 0, 90_000, 90_000, "USD"), null, false),
        topExpenses = emptyList(),
        excluded = listOf(ExcludedUi("Bank", "EGP"), ExcludedUi("Cash", "EGP")),
        otherCurrencies = listOf("EGP"),
        trend = TrendUi.Points(months(3).map { it.copy(incomeMinor = 0, expensesMinor = 0, netMinor = 0) }, hasUnknown = false),
        trendLength = 3,
    )
    val walletCash = ready().copy(walletId = "w1", title = "Cash", currencies = emptyList())
    val creditCard = ready().copy(
        walletId = "w3", title = "Visa", currencies = emptyList(),
        summary = SummaryUi(SummaryFigures(0, 420_000, -420_000, 300_000, 0, -300_000, -420_000, "EGP"), null, liabilityWallet = true),
    )
    val scheduled = ready().copy(
        includeScheduled = true,
        knownUntil = LocalDate(2026, 11, 6),
        summary = SummaryUi(SummaryFigures(0, 0, 0, 0, 0, 2_480_000, 2_480_000, "EGP"), PlannedSummary(0, 500_000, 0, 0), false),
        topExpenses = listOf(row("r", "Rent", 0, 1, "sofa", "#A9503A", planned = 500_000, share = Share.None).copy(fraction = 0f)),
        trend = TrendUi.Points(months(3, scheduled = true), hasUnknown = false),
        trendLength = 3,
    )
    val customProblem = ready().copy(customRangeOpen = true, customProblem = true)
    val unknownPeriod = ready().copy(range = null, summary = null, topExpenses = emptyList(), trend = TrendUi.Unknown, offline = true, updatedAt = updated)
    val tooLarge = ready().copy(summary = null, tooLarge = true, trend = TrendUi.Unknown, topExpenses = emptyList())
    val partlyUnknownTrend = ready(6).copy(trend = TrendUi.Points(months(2), hasUnknown = true))

    fun categories(kind: CategoryGroupKind = CategoryGroupKind.Expenses) = ReportCategoriesState(
        status = ReportCategoriesStatus.Ready,
        range = october,
        thisYear = 2026,
        currency = "EGP",
        kind = kind,
        expenses = GroupUi(550_000, null, rows + (4..8).map { row("x$it", "Category $it", 5_000L, 550_000, "gift", "#6B52B8") }),
        income = GroupUi(1_000_000, null, listOf(row("s", "Salary", 1_000_000, 1_000_000, "laptop", "#2C7A5B"))),
    )
    val categoriesScheduled = categories().copy(expenses = GroupUi(550_000, 500_000, listOf(groceries.copy(plannedMinor = 500_000), transport)), includesWaiting = true)
    val categoriesEmpty = categories().copy(expenses = GroupUi(), income = GroupUi())
}
