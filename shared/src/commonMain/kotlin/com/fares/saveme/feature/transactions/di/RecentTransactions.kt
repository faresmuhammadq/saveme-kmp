package com.fares.saveme.feature.transactions.di

import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.transactions.domain.TransactionsService
import com.fares.saveme.feature.transactions.domain.model.AddedBy
import com.fares.saveme.feature.transactions.domain.model.Counterpart
import com.fares.saveme.feature.transactions.domain.model.Direction
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.wallets.domain.RecentTransactionsSource
import com.fares.saveme.feature.wallets.domain.model.RecentAuthor
import com.fares.saveme.feature.wallets.domain.model.RecentDirection
import com.fares.saveme.feature.wallets.domain.model.RecentKind
import com.fares.saveme.feature.wallets.domain.model.RecentTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

private const val RECENT_COUNT = 3

/** The wallet's newest transactions for the detail's "Recent" card, from the phone's copy (the transactions and categories services). */
internal class TransactionRecents(
    private val transactions: TransactionsService,
    private val categories: CategoriesService,
) : RecentTransactionsSource {
    override fun observe(walletId: String): Flow<List<RecentTransaction>> = recentOf(transactions, categories, walletId)
}

internal fun recentOf(transactions: TransactionsService, categories: CategoriesService, walletId: String): Flow<List<RecentTransaction>> =
    combine(transactions.observeList(walletId), categories.observeDisplays(walletId)) { list, displays ->
        list.days.flatMap { it.items }.filter { !it.hidden }.take(RECENT_COUNT).map { view ->
            val category = view.shown.categoryId?.let { displays.resolve(it) }
            RecentTransaction(
                id = view.id,
                kind = when (view.shown.kind) {
                    TransactionKind.Expense -> RecentKind.Expense
                    TransactionKind.Income -> RecentKind.Income
                    TransactionKind.Transfer -> RecentKind.Transfer
                    TransactionKind.Unknown -> RecentKind.Other
                },
                direction = when (view.direction) {
                    Direction.In -> RecentDirection.In
                    Direction.Out -> RecentDirection.Out
                    Direction.None -> RecentDirection.None
                },
                title = category?.name?.takeIf { it.isNotBlank() },
                iconKey = category?.iconKey,
                counterpart = (view.counterpart as? Counterpart.Named)?.name,
                author = when (val by = view.addedBy) {
                    AddedBy.You -> RecentAuthor.You
                    is AddedBy.Member -> RecentAuthor.Member(by.name)
                    AddedBy.AnotherMember -> RecentAuthor.Another
                    AddedBy.FormerMember -> RecentAuthor.Former
                },
                date = view.shown.date,
                amountMinor = view.shown.amountMinor,
                currency = view.walletCurrency,
            )
        }
    }
