package com.fares.saveme.feature.sharedbudgets.domain

import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetRepository
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetLineEntity
import com.fares.saveme.feature.transactions.domain.BudgetChoice
import com.fares.saveme.feature.transactions.domain.SharedBudgetLinks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** How many budgets show as frequent picks. */
private const val FREQUENT_LIMIT = 3

/**
 * What transactions needs from Shared Budgets (as `RecurringLinksImpl`). A budget is offered while the person's membership is
 * live and writable and the budget is not archived. Frequent picks come from the person's own recent expenses on this phone,
 * given as [recentBudgetUse] (budget ids, most used first); nothing is asked of the server.
 */
class SharedBudgetLinksImpl(
    private val repository: SharedBudgetRepository,
    private val accountId: () -> String?,
    private val recentBudgetUse: (String) -> Flow<List<String>>,
) : SharedBudgetLinks {
    override fun observeChoices(): Flow<List<BudgetChoice>> {
        val userId = accountId() ?: return emptyFlow()
        return combine(
            repository.observeMine(userId),
            repository.observeOwnMembers(userId),
            repository.observeAllLines(),
            recentBudgetUse(userId),
        ) { budgets, members, lines, recent ->
            val writable = members.filterNot { it.readOnly }.map { it.sharedBudgetId }.toSet()
            val frequent = recent.filter { it in writable }.take(FREQUENT_LIMIT)
            val choices = budgets
                .filter { it.id in writable && it.archivedAt == null && it.version > 0 }
                .map { budget ->
                    BudgetChoice(
                        id = budget.id, name = budget.name, currency = budget.currency, frequent = budget.id in frequent,
                        categories = lines.filter { it.sharedBudgetId == budget.id }.map { it.toDisplay() },
                    )
                }
            choices.sortedWith(compareBy<BudgetChoice> { if (it.id in frequent) frequent.indexOf(it.id) else Int.MAX_VALUE }.thenBy { it.name.lowercase() })
        }
    }
}

private fun SharedBudgetLineEntity.toDisplay() = CategoryDisplay(
    id = categoryId, name = name.orEmpty(), iconKey = icon, colorHex = color, kindWire = "expense",
    source = CategorySource.Partner, archived = false,
)
