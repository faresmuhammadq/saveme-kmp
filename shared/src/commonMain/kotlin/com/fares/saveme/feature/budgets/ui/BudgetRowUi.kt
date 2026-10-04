package com.fares.saveme.feature.budgets.ui

import com.fares.saveme.feature.budgets.domain.model.BudgetView
import com.fares.saveme.feature.budgets.domain.model.SyncState

/** What a budget's change is doing, shown as an icon and words, never colour alone (P-08.3). */
enum class BudgetStatusUi { Waiting, Changed, DeletedElsewhere, NotSaved }

/** The figures a row writes under its bar; the bar, percentage, "spent of limit" and "left/over" come from the design system. */
data class RowFigures(
    val spentMinor: Long,
    /** Shown as "On pace for …" when present; the mapping drops it while nothing is spent (R-07). */
    val projectedMinor: Long?,
    val otherCurrencies: List<String>,
    val includesWaiting: Boolean,
) {
    override fun toString() = "RowFigures"
}

/**
 * What one budget row shows, mapped from the domain so the screen holds no business rules. Amounts are the person's own
 * and are never printed (P-09.2).
 */
data class BudgetRowUi(
    val id: String,
    /** Null = all expenses; empty = the category cannot be resolved ("Category unavailable"). */
    val categoryName: String?,
    val currency: String,
    val limitMinor: Long,
    /** Null while the period is unknown ("Progress appears after sync") and for a period type this version does not know. */
    val figures: RowFigures?,
    /** A period type this version does not know: the limit only, never edited (research R-15). */
    val readOnly: Boolean,
    val status: BudgetStatusUi?,
) {
    override fun toString() = "BudgetRowUi($id)"
}

internal fun BudgetView.toRow() = BudgetRowUi(
    id = id,
    categoryName = category?.name,
    currency = currency,
    limitMinor = limitMinor,
    figures = figures?.let {
        RowFigures(
            spentMinor = it.spentMinor,
            projectedMinor = it.projectedMinor.takeIf { _ -> it.spentMinor > 0 },
            otherCurrencies = it.otherCurrencies,
            includesWaiting = it.includesWaiting,
        )
    },
    readOnly = readOnly,
    status = when (val sync = syncState) {
        SyncState.None -> null
        SyncState.Waiting -> BudgetStatusUi.Waiting
        is SyncState.Clash -> if (sync.deletedElsewhere) BudgetStatusUi.DeletedElsewhere else BudgetStatusUi.Changed
        is SyncState.Refused -> BudgetStatusUi.NotSaved
    },
)
