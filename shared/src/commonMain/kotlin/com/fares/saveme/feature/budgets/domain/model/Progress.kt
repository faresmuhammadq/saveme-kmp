package com.fares.saveme.feature.budgets.domain.model

import kotlinx.datetime.LocalDate

/** The current Budget Period (research R-08): calendar dates, inclusive end. */
data class BudgetPeriod(
    val start: LocalDate,
    val end: LocalDate,
    val daysInPeriod: Int,
    /** Days from [start] through today inclusive, when today is inside the period. */
    val daysElapsed: Int?,
)

/** What the phone knows about the current period. */
sealed interface PeriodState {
    data class Known(val period: BudgetPeriod) : PeriodState

    /** A start day change is pending and the phone holds no server period for today ("Progress appears after sync"). */
    data object Unknown : PeriodState
}

/** A start day change whose "applies from" date is still ahead (FR-010). */
data class PendingStartDay(val day: Int, val from: LocalDate)

/** A period the server reported, kept from a progress read. */
data class StoredPeriod(val start: LocalDate, val end: LocalDate, val daysInPeriod: Int)

enum class FigureSource { Server, Phone }

/** The sums of one budget's matching spending (FR-012). Amounts are never printed (P-09.2). */
data class SpendingSums(
    val spentMinor: Long,
    /** Sorted codes of other currencies with matching spending; never summed. */
    val otherCurrencies: List<String>,
    /** True when a change waiting to sync matches the budget (FR-014 b). */
    val includesWaiting: Boolean,
) {
    override fun toString() = "SpendingSums"
}

/** The server's last figures for a budget and the phone's synced-only sums at that read (research R-06). */
data class StoredProgress(
    val period: StoredPeriod,
    val currency: String,
    val limitMinor: Long,
    val spentMinor: Long,
    val otherCurrencies: List<String>,
    val localSpentMinor: Long,
    val localOtherCurrencies: List<String>,
) {
    override fun toString() = "StoredProgress"
}

/** Which spent figure is shown and where it came from. */
data class Selection(
    val spentMinor: Long,
    val otherCurrencies: List<String>,
    val source: FigureSource,
    val includesWaiting: Boolean,
) {
    override fun toString() = "Selection($source)"
}

/** Everything a budget row shows about its progress (FR-013, FR-015). */
data class BudgetFigures(
    val spentMinor: Long,
    val limitMinor: Long,
    val remainingMinor: Long,
    val overLimit: Boolean,
    val percent: Int,
    /** Null when not derivable or spent is zero is decided by the screen; the math returns null only when it cannot (R-07). */
    val projectedMinor: Long?,
    val otherCurrencies: List<String>,
    val source: FigureSource,
    val includesWaiting: Boolean,
) {
    override fun toString() = "BudgetFigures($source)"
}
