package com.fares.saveme.feature.transactions.data

/**
 * The effect of one transaction state on one wallet's balance (research R-04, R-09). Primitives only,
 * so the sync handler in `data` and the domain's projections share one rule (P-10.6).
 */
object BalanceEffect {
    const val INCOME = "income"
    const val EXPENSE = "expense"
    const val TRANSFER = "transfer"

    /**
     * +[amountMinor] for an income in [forWallet] or a transfer into it, −[amountMinor] for an expense in it or a
     * transfer out of it; 0 for a deleted state, another wallet or a kind this app does not know (research R-22).
     */
    fun of(
        kind: String,
        amountMinor: Long,
        walletId: String,
        counterWalletId: String?,
        deleted: Boolean,
        forWallet: String,
    ): Long {
        if (deleted) return 0
        return when (kind) {
            INCOME -> if (forWallet == walletId) amountMinor else 0
            EXPENSE -> if (forWallet == walletId) -amountMinor else 0
            TRANSFER -> when (forWallet) {
                walletId -> -amountMinor
                counterWalletId -> amountMinor
                else -> 0
            }
            else -> 0
        }
    }
}
