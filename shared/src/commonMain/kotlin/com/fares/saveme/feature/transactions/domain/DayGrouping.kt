package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.data.BalanceEffect
import com.fares.saveme.feature.transactions.domain.model.DayGroup
import com.fares.saveme.feature.transactions.domain.model.TransactionView

/**
 * The effect of one shown transaction on [walletId]'s balance: money in is positive, money out
 * negative, a transfer by its direction in this wallet (FR-015, research R-09).
 */
fun TransactionView.effectIn(walletId: String): Long = BalanceEffect.of(
    kind = shown.kindWire, amountMinor = shown.amountMinor, walletId = shown.walletId,
    counterWalletId = shown.counterWalletId, deleted = false, forWallet = walletId,
)

/** Newest date first, ties by creation time newest first, then id (FR-015); each day carries its net total in [walletId]. */
object DayGrouping {
    fun group(views: List<TransactionView>, walletId: String): List<DayGroup> {
        val ordered = views.sortedWith(
            compareByDescending<TransactionView> { it.shown.date }.thenByDescending { it.createdAt }.thenByDescending { it.id },
        )
        return ordered.groupBy { it.shown.date }.map { (date, items) ->
            DayGroup(date, items.sumOf { it.effectIn(walletId) }, items)
        }
    }
}
