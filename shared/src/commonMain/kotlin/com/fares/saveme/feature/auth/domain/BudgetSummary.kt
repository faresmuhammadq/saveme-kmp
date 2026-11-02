package com.fares.saveme.feature.auth.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/**
 * Up to a few active budgets of the current period for Home's "Budgets" card (spec 012 FR-017, research R-11). Auth owns
 * the interface so Home never imports budgets (P-10.4); the budgets module rebinds [NoBudgetSummary].
 */
interface BudgetSummary {
    /** Active budgets, highest share spent first then oldest; at most [limit]. Empty when there is none. */
    fun observeTop(limit: Int): Flow<List<BudgetGlimpse>>

    /** What the person typically spends in a period in [currency] (the average of earlier periods); null when unknown. */
    fun observeTypicalSpend(currency: String): Flow<Long?> = flowOf(null)
}

/**
 * One budget on the card. [categoryName] is null for "All expenses" and empty when the category cannot be resolved
 * ("Category unavailable"). [spentMinor] is null while the period is unknown ("Progress appears after sync").
 * Amounts are never printed (P-09.2).
 */
data class BudgetGlimpse(
    val id: String,
    val categoryName: String?,
    val spentMinor: Long?,
    val limitMinor: Long,
    val currency: String,
    val includesWaiting: Boolean,
    val iconKey: String? = null,
    val colorHex: String? = null,
    /** The current period's first and last day; null while the period is unknown. */
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
    val daysLeft: Int? = null,
    /** Where spending is heading by the period's end; null when it cannot be derived. */
    val projectedMinor: Long? = null,
) {
    override fun toString() = "BudgetGlimpse($id)"
}

object NoBudgetSummary : BudgetSummary {
    override fun observeTop(limit: Int): Flow<List<BudgetGlimpse>> = flowOf(emptyList())
}
