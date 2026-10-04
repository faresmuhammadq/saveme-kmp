package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.auth.domain.BudgetGlimpse
import com.fares.saveme.feature.auth.domain.BudgetSummary
import com.fares.saveme.feature.budgets.domain.model.BudgetView
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
            .take(limit)
            .map { it.toGlimpse() }
    }

    private fun BudgetView.toGlimpse() = BudgetGlimpse(
        id = id,
        // Null = all expenses; an empty name = a category that cannot be resolved ("Category unavailable").
        categoryName = category?.name,
        spentMinor = figures?.spentMinor,
        limitMinor = limitMinor,
        currency = currency,
        includesWaiting = figures?.includesWaiting == true,
    )
}
