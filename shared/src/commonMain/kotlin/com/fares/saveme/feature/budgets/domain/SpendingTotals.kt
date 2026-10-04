package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod
import com.fares.saveme.feature.budgets.domain.model.SpendingSums
import com.fares.saveme.feature.transactions.domain.OwnExpense
import com.fares.saveme.feature.transactions.domain.SpendingKey
import com.fares.saveme.feature.transactions.domain.SpendingSnapshot

/**
 * Sums a snapshot of the person's own expenses for one budget (FR-012): the budget's currency counts; matching spending
 * in other currencies is named, never summed. A budget with no category matches any category.
 */
object SpendingTotals {
    /** The phone's own figures: expenses with waiting changes applied, and whether a waiting change matches (FR-014 b). */
    fun withWaiting(snapshot: SpendingSnapshot, categoryId: String?, currency: String, period: BudgetPeriod): SpendingSums =
        sums(snapshot.expenses, categoryId, currency, period, matches(snapshot.waitingTouches, categoryId, currency, period))

    /** The phone's synced-only figures: the stored rows without any overlay (FR-014 c). */
    fun syncedOnly(snapshot: SpendingSnapshot, categoryId: String?, currency: String, period: BudgetPeriod): SpendingSums =
        sums(snapshot.syncedExpenses, categoryId, currency, period, includesWaiting = false)

    /** True when a waiting change touches the budget on either its old or its new values (period, currency, category). */
    fun matches(touches: List<SpendingKey>, categoryId: String?, currency: String, period: BudgetPeriod): Boolean =
        touches.any { it.currency == currency && it.date >= period.start && it.date <= period.end && (categoryId == null || it.categoryId == categoryId) }

    private fun sums(expenses: List<OwnExpense>, categoryId: String?, currency: String, period: BudgetPeriod, includesWaiting: Boolean): SpendingSums {
        var spent = 0L
        val others = mutableSetOf<String>()
        for (e in expenses) {
            if (e.date < period.start || e.date > period.end) continue
            if (categoryId != null && e.categoryId != categoryId) continue
            if (e.currency == currency) spent += e.amountMinor else others += e.currency
        }
        return SpendingSums(spent, others.sorted(), includesWaiting)
    }
}
