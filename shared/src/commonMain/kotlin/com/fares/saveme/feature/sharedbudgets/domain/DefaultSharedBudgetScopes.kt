package com.fares.saveme.feature.sharedbudgets.domain

import com.fares.saveme.core.sync.SharedBudgetScopes
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetRepository

/** What the feed puller asks of Shared Budgets; placeholders are never read or removed by a read. */
class DefaultSharedBudgetScopes(
    private val repository: SharedBudgetRepository,
    private val accountId: () -> String?,
) : SharedBudgetScopes {
    override suspend fun followed(): List<String> = repository.followedIds(accountId().orEmpty())

    override suspend fun isAccepted(budgetId: String): Boolean = repository.isAccepted(budgetId, accountId().orEmpty())

    override suspend fun unconfirmed(seenBudgetIds: Set<String>): List<String> = repository.heldIds().filter { it !in seenBudgetIds }

    override suspend fun accessLost(budgetId: String) = repository.accessLost(budgetId)
}
