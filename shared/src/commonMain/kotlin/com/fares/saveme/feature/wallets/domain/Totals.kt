package com.fares.saveme.feature.wallets.domain

import com.fares.saveme.feature.wallets.domain.model.CurrencyTotal
import com.fares.saveme.feature.wallets.domain.model.Wallet
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletView

/**
 * What the phone shows as a wallet's balance (research R-10): the server's balance, moved by an
 * opening balance edit that hasn't synced, plus unsent transactions (C6). For a placeholder the
 * server balance equals the opening balance, so this is the draft's opening balance.
 */
fun displayedBalance(server: Wallet, pending: WalletDraft?, postingsDelta: Long): Long =
    server.balanceMinor + ((pending?.openingBalanceMinor ?: server.openingBalanceMinor) - server.openingBalanceMinor) + postingsDelta

/**
 * One total per currency over the visible, non-archived wallets (research R-11, P-03): sums in
 * `Long`, liabilities already negative, never across currencies. The default currency leads, the
 * rest follow by code.
 */
fun totalsOf(views: List<WalletView>, defaultCurrency: String?): List<CurrencyTotal> =
    views.filter { !it.hidden && !it.archived }
        .groupBy { it.shown.currency }
        .map { (currency, group) -> CurrencyTotal(currency, group.sumOf { it.displayedBalanceMinor }, group.size) }
        .sortedWith(compareBy({ it.currency != defaultCurrency }, { it.currency }))
