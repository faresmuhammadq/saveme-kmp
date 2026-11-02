package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A Shared Budget the person can put an expense into (backend ADR-0027). [categories] are the budget's own list, which may
 * name a partner's category; [frequent] marks the budgets the person used most lately.
 */
data class BudgetChoice(
    val id: String,
    val name: String,
    val currency: String,
    val categories: List<CategoryDisplay>,
    val frequent: Boolean,
)

/**
 * What transactions needs from Shared Budgets without importing them (as [RecurringLinks]). Until the module binds it,
 * no budget is offered.
 */
interface SharedBudgetLinks {
    /** The budgets the person may record into: a live membership that is not read-only, not archived; frequent ones first. */
    fun observeChoices(): Flow<List<BudgetChoice>>
}

object NoSharedBudgetLinks : SharedBudgetLinks {
    override fun observeChoices(): Flow<List<BudgetChoice>> = flowOf(emptyList())
}
