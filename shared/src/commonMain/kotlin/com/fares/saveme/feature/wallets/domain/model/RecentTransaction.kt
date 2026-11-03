package com.fares.saveme.feature.wallets.domain.model

import kotlinx.datetime.LocalDate

enum class RecentKind { Expense, Income, Transfer, Adjustment, Other }

enum class RecentDirection { In, Out, None }

/** Who added a recent transaction, as the wallet detail words it. */
sealed interface RecentAuthor {
    data object You : RecentAuthor
    data class Member(val name: String) : RecentAuthor
    data object Another : RecentAuthor
    data object Former : RecentAuthor
}

/**
 * One line of the wallet detail's "Recent" card, built by the transactions feature's service so wallets never imports its types.
 * [title] is the category name; [counterpart] the other wallet of a transfer.
 */
data class RecentTransaction(
    val id: String,
    val kind: RecentKind,
    val direction: RecentDirection,
    val title: String?,
    val iconKey: String?,
    val counterpart: String?,
    val author: RecentAuthor,
    val date: LocalDate,
    val amountMinor: Long,
    val currency: String,
    /** The transaction's description; null when none. */
    val note: String? = null,
    val walletId: String = "",
)
