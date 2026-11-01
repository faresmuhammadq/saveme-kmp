package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.auth.domain.BudgetGlimpse
import com.fares.saveme.feature.auth.domain.BudgetSummary
import com.fares.saveme.feature.budgets.domain.model.BudgetView
import com.fares.saveme.feature.budgets.domain.model.BudgetsOverview
import com.fares.saveme.feature.budgets.domain.model.PeriodState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Home's "Budgets" card (FR-017, research R-11): up to [limit] active budgets, the highest share spent first, then the
 * oldest; budgets whose period is unknown follow the others. Archived budgets never appear.
 */
class BudgetsHomeSummary(private val service: BudgetsService) : BudgetSummary {
    override fun observeTop(limit: Int): Flow<List<BudgetGlimpse>> = service.observeOverview().map { overview ->
        overview.active
            .sortedWith(compareBy<BudgetView> { it.figures == null }.thenByDescending { it.figures?.percent ?: 0 }.thenBy { it.server.createdAt }.thenBy { it.id })
            .let { sorted ->
                // The all-expenses budget leads Home's ring card, so it stays even when others rank higher.
                val allExpenses = sorted.firstOrNull { it.category == null && it.figures != null }
                val top = sorted.take(limit)
                if (allExpenses == null || allExpenses in top) top else top.dropLast(1) + allExpenses
            }
            .map { it.toGlimpse(overview) }
    }

    override fun observeTypicalSpend(currency: String): Flow<Long?> =
        service.observeSpendHistory(null, currency).map { it.averageMinor ?: it.lastPeriodMinor }

    private fun BudgetView.toGlimpse(overview: BudgetsOverview) = BudgetGlimpse(
        id = id,
        // Null = all expenses; an empty name = a category that cannot be resolved ("Category unavailable").
        categoryName = category?.name,
        spentMinor = figures?.spentMinor,
        limitMinor = limitMinor,
        currency = currency,
        includesWaiting = figures?.includesWaiting == true,
        iconKey = category?.iconKey,
        colorHex = category?.colorHex,
        periodStart = (overview.period as? PeriodState.Known)?.period?.start,
        periodEnd = (overview.period as? PeriodState.Known)?.period?.end,
        daysLeft = overview.daysLeft,
        projectedMinor = figures?.projectedMinor,
    )
}
