package com.fares.saveme.core.sync

/**
 * What the feed puller and the runner need to know about Shared Budgets, implemented by the shared budgets feature so
 * `core.sync` imports no feature (as [WalletScopes] does for wallets). A budget's feed is read only by a member: the person's
 * own `shared_budget_member` rows on the user feed say which budgets they belong to, and a `404` on a budget's feed says they
 * no longer do (backend ADR-0027 rule 9).
 */
interface SharedBudgetScopes {
    /** Budgets to read: every one with a live own membership, and any made on this phone and already accepted. */
    suspend fun followed(): List<String>

    /** Whether the server knows the budget: it holds a server version, or a live own membership exists. */
    suspend fun isAccepted(budgetId: String): Boolean

    /**
     * After a complete read of the person's scope without a position: held budgets whose own membership was not among
     * [seenBudgetIds]. Their feeds are read in the same run; only a `404` removes one.
     */
    suspend fun unconfirmed(seenBudgetIds: Set<String>): List<String>

    /**
     * The person no longer belongs to the budget (an ended membership, or its feed answered `404`): removes it with its lines,
     * members and entries and clears its position, except a budget made on this phone and not yet accepted.
     */
    suspend fun accessLost(budgetId: String)
}
