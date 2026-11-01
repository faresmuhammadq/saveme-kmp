package com.fares.saveme.feature.auth.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/**
 * What Home shows besides budgets and due occurrences: the current period's spending, the wallets and the newest
 * transactions. Auth owns the interface so Home never imports reports, wallets or transactions (P-10.4); the reports
 * module rebinds [NoHomeOverview].
 */
interface HomeOverviewSummary {
    fun observe(): Flow<HomeOverview>
}

/** A top spending category of the period. [name] is empty when the category cannot be resolved. */
data class HomeCategory(
    val name: String,
    val iconKey: String?,
    val colorHex: String?,
    val amountMinor: Long,
    /** 0..100 of the period's spending; null when it cannot be given. */
    val percent: Int?,
    /** 0..1 against the largest category, for the bar. */
    val fraction: Float,
) {
    override fun toString() = "HomeCategory"
}

data class HomeWallet(
    val id: String,
    val name: String,
    val typeWire: String,
    val iconKey: String?,
    val balanceMinor: Long,
    val currency: String,
    val liability: Boolean,
    val waiting: Boolean,
) {
    override fun toString() = "HomeWallet($id)"
}

data class HomeTotal(val currency: String, val minor: Long) {
    override fun toString() = "HomeTotal($currency)"
}

enum class HomeRecentKind { Expense, Income, Transfer, Other }

data class HomeRecent(
    val id: String,
    val walletId: String,
    val kind: HomeRecentKind,
    /** The note when there is one, else the category; null for a transfer without either. */
    val title: String?,
    val categoryName: String?,
    val iconKey: String?,
    val date: LocalDate,
    val amountMinor: Long,
    val currency: String,
    /** True for money coming in (income, or a transfer into the wallet). */
    val incoming: Boolean,
) {
    override fun toString() = "HomeRecent($id)"
}

/**
 * [periodStart] and [periodEnd] are null while the period is one the phone cannot name. [spentMinor] is null in that
 * case, and when the figures are too large. Amounts are never printed (P-09.2).
 */
data class HomeOverview(
    val loaded: Boolean = false,
    val noWallets: Boolean = false,
    val currency: String? = null,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
    val today: LocalDate? = null,
    val daysLeft: Int? = null,
    val spentMinor: Long? = null,
    val incomeMinor: Long? = null,
    val leftOverMinor: Long? = null,
    /** Spending against the same days of the period before, in whole percent; null when not comparable. */
    val changePercent: Int? = null,
    /** Spending per day from [periodStart]; days after today are zero. */
    val dailySpend: List<Long> = emptyList(),
    val todayIndex: Int = -1,
    val categories: List<HomeCategory> = emptyList(),
    val moreCategories: Int = 0,
    val moreCategoriesMinor: Long = 0,
    val wallets: List<HomeWallet> = emptyList(),
    val totals: List<HomeTotal> = emptyList(),
    val recent: List<HomeRecent> = emptyList(),
    /** Currencies of wallets that are not in [currency]'s figures. */
    val otherCurrencies: List<String> = emptyList(),
    val includesWaiting: Boolean = false,
    val tooLarge: Boolean = false,
) {
    override fun toString() = "HomeOverview"
}

object NoHomeOverview : HomeOverviewSummary {
    override fun observe(): Flow<HomeOverview> = flowOf(HomeOverview())
}
