package com.fares.saveme.feature.reports.ui.reports

import com.fares.saveme.feature.categories.domain.model.ReportLabels
import com.fares.saveme.feature.reports.domain.model.PlannedSummary
import com.fares.saveme.feature.reports.domain.model.Report
import com.fares.saveme.feature.reports.domain.model.ReportChoices
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.ReportState
import com.fares.saveme.feature.reports.domain.model.SummaryFigures
import com.fares.saveme.feature.reports.domain.model.TrendSlot
import com.fares.saveme.feature.reports.ui.CategoryRowUi
import com.fares.saveme.feature.reports.ui.categoryRows
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

enum class ReportsStatus { Loading, NoWallets, Ready, WalletGone }

/** The dates the report covers; [custom] is true for a range the person typed rather than a Budget Period. */
data class RangeUi(val from: LocalDate, val to: LocalDate, val custom: Boolean)

/** A wallet in another currency, named in the "Not included" notice (FR-014). */
data class ExcludedUi(val name: String, val currency: String)

/** The seven summary lines (FR-005) and, with the switch on, the scheduled ones. Amounts are never printed (P-09.2). */
data class SummaryUi(
    val figures: SummaryFigures,
    val planned: PlannedSummary?,
    /** A liability wallet's report shows negative balances as amounts owed (FR-017). */
    val liabilityWallet: Boolean,
) {
    override fun toString() = "SummaryUi"
}

/** One period of the trend the phone can name. */
data class TrendItemUi(
    val start: LocalDate,
    val end: LocalDate,
    val incomeMinor: Long,
    val expensesMinor: Long,
    val netMinor: Long,
    val plannedIncomeMinor: Long?,
    val plannedExpensesMinor: Long?,
) {
    override fun toString() = "TrendItemUi"
}

/** [hasUnknown] is true while older periods cannot be named offline: a note says so (FR-011). */
sealed interface TrendUi {
    data object Unknown : TrendUi
    data class Points(val items: List<TrendItemUi>, val hasUnknown: Boolean) : TrendUi
}

data class ReportsState(
    /** Null for the cross-wallet report. */
    val walletId: String? = null,
    val status: ReportsStatus = ReportsStatus.Loading,
    /** The wallet's name; null for the cross-wallet report. */
    val title: String? = null,
    val currency: String = "",
    val currencies: List<String> = emptyList(),
    val thisYear: Int = 1970,
    /** Null while the period is one the phone cannot name offline: "Shown when you're online" (FR-011). */
    val range: RangeUi? = null,
    val canGoNext: Boolean = false,
    val excluded: List<ExcludedUi> = emptyList(),
    /** Every other currency of the person's wallets: each has a "See <code> report" action. */
    val otherCurrencies: List<String> = emptyList(),
    val offline: Boolean = false,
    val refreshFailed: Boolean = false,
    val updatedAt: Instant? = null,
    val includesWaiting: Boolean = false,
    val summary: SummaryUi? = null,
    val topExpenses: List<CategoryRowUi> = emptyList(),
    val moreCategories: Boolean = false,
    val trendLength: Int = ReportChoices.DEFAULT_TREND_LENGTH,
    val trend: TrendUi = TrendUi.Unknown,
    val includeScheduled: Boolean = false,
    val knownUntil: LocalDate? = null,
    val customRangeOpen: Boolean = false,
    val customProblem: Boolean = false,
    val currencyPickerOpen: Boolean = false,
    val refreshing: Boolean = false,
    val tooLarge: Boolean = false,
    val timeZoneId: String = "UTC",
) {
    val isCrossWallet: Boolean get() = walletId == null
    val isCustomRange: Boolean get() = range?.custom == true
}

/** The groups of the Spending by category screen. */
enum class CategoryGroupKind(val wire: String) {
    Expenses("expense"),
    Income("income"),
    ;

    companion object {
        fun fromWire(value: String): CategoryGroupKind = entries.firstOrNull { it.wire == value } ?: Expenses
    }
}

sealed interface ReportsIntent {
    /** The screen opened: reads again, at most once a minute (FR-020). */
    data object Opened : ReportsIntent

    /** A pull always reads again. */
    data object PullToRefresh : ReportsIntent
    data object PreviousClicked : ReportsIntent
    data object NextClicked : ReportsIntent
    data object CustomRangeClicked : ReportsIntent
    data class CustomRangePicked(val from: LocalDate, val to: LocalDate) : ReportsIntent
    data object CustomRangeDismissed : ReportsIntent
    data object CurrentPeriodClicked : ReportsIntent
    data object CurrencyClicked : ReportsIntent
    data object CurrencyPickerDismissed : ReportsIntent
    data class CurrencyPicked(val code: String) : ReportsIntent
    data class SeeCurrencyClicked(val code: String) : ReportsIntent
    data class TrendLengthPicked(val length: Int) : ReportsIntent
    data class IncludeScheduledToggled(val on: Boolean) : ReportsIntent
    data object SeeAllClicked : ReportsIntent
    data class CategoryClicked(val kind: CategoryGroupKind) : ReportsIntent
    data object AddWalletClicked : ReportsIntent
    data object BackClicked : ReportsIntent
}

/** Everything the categories screen needs to open on the same report (the key carries it, FR-022). */
data class CategoriesArgs(
    val walletId: String?,
    val from: LocalDate,
    val to: LocalDate,
    val currency: String,
    val includeScheduled: Boolean,
    val kind: CategoryGroupKind,
)

sealed interface ReportsEffect {
    data class OpenCategories(val args: CategoriesArgs) : ReportsEffect
    data object OpenWallets : ReportsEffect
    data object Close : ReportsEffect
}

sealed interface ReportsChange {
    data class Stored(val state: ReportState, val labels: ReportLabels?) : ReportsChange
    data class TimeZone(val id: String) : ReportsChange
    data class Refreshing(val busy: Boolean) : ReportsChange
    data class RefreshDone(val offline: Boolean, val failed: Boolean) : ReportsChange
    data class CustomOpen(val open: Boolean) : ReportsChange
    data class CustomProblem(val problem: Boolean) : ReportsChange
    data class CurrencyPicker(val open: Boolean) : ReportsChange
}

/** How many expense categories the screen lists before "See all" (FR-022). */
const val TOP_CATEGORY_COUNT = 5

fun reduce(state: ReportsState, change: ReportsChange): ReportsState = when (change) {
    is ReportsChange.Stored -> when (val stored = change.state) {
        ReportState.Loading -> state.copy(status = ReportsStatus.Loading)
        ReportState.NoWallets -> state.copy(status = ReportsStatus.NoWallets)
        ReportState.WalletGone -> state.copy(status = ReportsStatus.WalletGone)
        is ReportState.Ready -> ready(state, stored.report, change.labels)
    }
    is ReportsChange.TimeZone -> state.copy(timeZoneId = change.id)
    is ReportsChange.Refreshing -> state.copy(refreshing = change.busy)
    is ReportsChange.RefreshDone -> state.copy(refreshing = false, offline = change.offline, refreshFailed = change.failed)
    is ReportsChange.CustomOpen -> state.copy(customRangeOpen = change.open, customProblem = if (change.open) state.customProblem else false)
    is ReportsChange.CustomProblem -> state.copy(customProblem = change.problem)
    is ReportsChange.CurrencyPicker -> state.copy(currencyPickerOpen = change.open)
}

private fun ready(state: ReportsState, report: Report, labels: ReportLabels?): ReportsState {
    val expenses = report.breakdown?.expenses?.let { group -> labels?.let { categoryRows(group, it, report.currency) } }.orEmpty()
    val known = report.trend.filterIsInstance<TrendSlot.Known>().map { it.point }
    val trend = when {
        report.tooLarge || known.isEmpty() -> TrendUi.Unknown
        else -> TrendUi.Points(
            items = known.map {
                TrendItemUi(it.period.start, it.period.end, it.incomeMinor, it.expensesMinor, it.netMinor, it.plannedIncomeMinor, it.plannedExpensesMinor)
            },
            hasUnknown = known.size < report.trend.size,
        )
    }
    return state.copy(
        walletId = (report.scope as? ReportScope.Wallet)?.walletId,
        status = ReportsStatus.Ready,
        title = report.title,
        currency = report.currency,
        currencies = report.currencies,
        thisYear = report.today.year,
        range = report.range?.let { RangeUi(it.from, it.to, custom = it.kind == com.fares.saveme.feature.reports.domain.model.RangeKind.Custom) },
        canGoNext = report.canGoNext,
        excluded = report.covered.excluded.map { ExcludedUi(it.name, it.currency) },
        otherCurrencies = report.covered.excluded.map { it.currency }.distinct().sorted(),
        updatedAt = report.updatedAt,
        includesWaiting = report.includesWaiting,
        summary = report.summary?.let { SummaryUi(it, report.planned, report.isLiability) },
        topExpenses = expenses.take(TOP_CATEGORY_COUNT),
        moreCategories = expenses.size > TOP_CATEGORY_COUNT,
        trendLength = report.choices.trendLength,
        trend = trend,
        includeScheduled = report.includeScheduled,
        knownUntil = report.knownUntil,
        tooLarge = report.tooLarge,
    )
}
