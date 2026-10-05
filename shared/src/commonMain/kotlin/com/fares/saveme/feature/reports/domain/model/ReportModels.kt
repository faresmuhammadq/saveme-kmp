package com.fares.saveme.feature.reports.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * What a report is about: one wallet (its own currency) or every wallet on the phone in one currency. The currency of a
 * cross-wallet report is a choice kept in [ReportChoices], and the resolved one is on the [Report].
 */
sealed interface ReportScope {
    data class Wallet(val walletId: String) : ReportScope
    data object AllWallets : ReportScope

    /** The key choices are kept under, per account. */
    val key: String get() = when (this) {
        is Wallet -> "wallet:$walletId"
        AllWallets -> "all"
    }
}

/** A Budget Period's first and last day; its label is [start]. */
data class BudgetPeriodSpan(val start: LocalDate, val end: LocalDate)

/** A period the phone can name, or one it can only learn from the server (never guessed, FR-011). */
sealed interface PeriodLookup {
    data class Known(val span: BudgetPeriodSpan, val source: PeriodSource) : PeriodLookup
    data object Unknown : PeriodLookup
}

enum class PeriodSource { Derived, Server }

enum class RangeKind { Period, Custom }

/** The dates a report covers, inclusive. At most 366 days. */
data class ReportRange(val from: LocalDate, val to: LocalDate, val kind: RangeKind, val source: PeriodSource = PeriodSource.Derived)

/** What the person picked for the range; a period is kept by a date inside it so an unknown one can still be stepped from. */
sealed interface RangeSelection {
    data object CurrentPeriod : RangeSelection
    data class PeriodContaining(val anchor: LocalDate) : RangeSelection
    data class Custom(val from: LocalDate, val to: LocalDate) : RangeSelection
}

/** The range intents of the service (contracts/domain-apis.md). */
sealed interface RangeChoice {
    data object CurrentPeriod : RangeChoice
    data object Previous : RangeChoice
    data object Next : RangeChoice
    data class Custom(val from: LocalDate, val to: LocalDate) : RangeChoice
}

enum class RangeProblem { Order, TooLong, OutOfBounds }

/** The session's choices for one scope; memory only (FR-012). */
data class ReportChoices(
    val range: RangeSelection = RangeSelection.CurrentPeriod,
    val trendLength: Int = DEFAULT_TREND_LENGTH,
    /** Cross-wallet only; null means the profile's default currency. */
    val currency: String? = null,
    val includeScheduled: Boolean = false,
) {
    companion object {
        const val DEFAULT_TREND_LENGTH = 6
        val TREND_LENGTHS = listOf(3, 6, 12)
    }
}

/** A wallet as reports need it: the shown values (a waiting change applied) and whether a change to the wallet waits. */
data class ReportWallet(
    val id: String,
    val name: String,
    val currency: String,
    val openingMinor: Long,
    val isLiability: Boolean,
    val archived: Boolean,
    val waiting: Boolean,
) {
    override fun toString() = "ReportWallet($id)"
}

data class ExcludedWallet(val walletId: String, val name: String, val currency: String) {
    override fun toString() = "ExcludedWallet($walletId, $currency)"
}

/** The wallets a report covers and the ones it names as not included (FR-004, FR-014). */
data class Covered(
    val wallets: List<ReportWallet>,
    val excluded: List<ExcludedWallet>,
    val currency: String,
) {
    val walletIds: Set<String> = wallets.mapTo(HashSet()) { it.id }
    val openings: Map<String, Long> = wallets.associate { it.id to it.openingMinor }
}

/** Every figure in minor units of [currency]; income, expenses and transfers are never negative. */
data class SummaryFigures(
    val income: Long,
    val expenses: Long,
    val net: Long,
    val transfersIn: Long,
    val transfersOut: Long,
    val balanceStart: Long,
    val balanceEnd: Long,
    val currency: String,
) {
    override fun toString() = "SummaryFigures($currency)"
}

/** Scheduled sums beside the actual ones; never added to them (FR-016). */
data class PlannedSummary(val income: Long, val expenses: Long, val transfersIn: Long, val transfersOut: Long) {
    override fun toString() = "PlannedSummary"
}

/** One category's sum under its grouping [label] (a platform `system_key`, else the id). */
data class CategoryItem(
    val label: String,
    val categoryId: String,
    val systemKey: String?,
    val valueMinor: Long,
    val plannedMinor: Long?,
) {
    override fun toString() = "CategoryItem"
}

data class CategoryGroup(val totalMinor: Long, val plannedMinor: Long?, val items: List<CategoryItem>) {
    override fun toString() = "CategoryGroup(${items.size} items)"
}

data class CategoryBreakdown(val expenses: CategoryGroup, val income: CategoryGroup) {
    override fun toString() = "CategoryBreakdown"
}

data class TrendPoint(
    val period: BudgetPeriodSpan,
    val incomeMinor: Long,
    val expensesMinor: Long,
    val netMinor: Long,
    val plannedIncomeMinor: Long?,
    val plannedExpensesMinor: Long?,
) {
    override fun toString() = "TrendPoint"
}

/** One slot of the trend: a period with figures, or one the phone cannot name offline. */
sealed interface TrendSlot {
    data class Known(val point: TrendPoint) : TrendSlot
    data object Unknown : TrendSlot
}

/** The share of a total, never from the server (FR-007). */
sealed interface Share {
    data class Percent(val percent: Int) : Share
    data object UnderOne : Share
    data object None : Share
}

/**
 * One emission per change of rows, queue, profile, labels or day. [range] is null while the period is one the phone
 * cannot name (offline); the summary and the categories then say so (FR-011).
 */
data class Report(
    val scope: ReportScope,
    val title: String?,
    val currency: String,
    val currencies: List<String>,
    /** Today in the profile's zone: the year of a range is shown only when it is not this one. */
    val today: LocalDate,
    val range: ReportRange?,
    val canGoNext: Boolean,
    val covered: Covered,
    val summary: SummaryFigures?,
    val planned: PlannedSummary?,
    val breakdown: CategoryBreakdown?,
    val trend: List<TrendSlot>,
    val includeScheduled: Boolean,
    val knownUntil: LocalDate?,
    val isLiability: Boolean,
    val includesWaiting: Boolean,
    val updatedAt: Instant?,
    val tooLarge: Boolean,
    val choices: ReportChoices,
) {
    override fun toString() = "Report(${scope.key})"
}

/** What the Reports screen receives (contracts/domain-apis.md). */
sealed interface ReportState {
    data object Loading : ReportState
    data object NoWallets : ReportState
    data object WalletGone : ReportState
    data class Ready(val report: Report) : ReportState
}
