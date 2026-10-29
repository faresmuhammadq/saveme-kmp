package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.feature.wallets.domain.model.RecentTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A wallet's newest transactions for the detail's "Recent" card. Wallets owns the interface so it never imports transactions
 * (P-10.4); the transactions module rebinds [NoRecentTransactions].
 */
fun interface RecentTransactionsSource {
    fun observe(walletId: String): Flow<List<RecentTransaction>>
}

object NoRecentTransactions : RecentTransactionsSource {
    override fun observe(walletId: String): Flow<List<RecentTransaction>> = flowOf(emptyList())
}
