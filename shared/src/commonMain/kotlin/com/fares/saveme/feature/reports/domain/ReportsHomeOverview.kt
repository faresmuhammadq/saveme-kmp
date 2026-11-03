package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.auth.domain.HomeCategory
import com.fares.saveme.feature.auth.domain.HomeOverview
import com.fares.saveme.feature.auth.domain.HomeOverviewSummary
import com.fares.saveme.feature.auth.domain.HomeRecent
import com.fares.saveme.feature.auth.domain.HomeRecentKind
import com.fares.saveme.feature.auth.domain.HomeTotal
import com.fares.saveme.feature.auth.domain.HomeWallet
import com.fares.saveme.feature.reports.domain.model.HomePeriod
import com.fares.saveme.feature.reports.domain.model.Share
import com.fares.saveme.feature.wallets.domain.RecentTransactionsSource
import com.fares.saveme.feature.wallets.domain.WalletsService
import com.fares.saveme.feature.wallets.domain.model.RecentDirection
import com.fares.saveme.feature.wallets.domain.model.RecentKind
import com.fares.saveme.feature.wallets.domain.model.RecentTransaction
import com.fares.saveme.feature.wallets.domain.model.WalletList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

private const val TOP_CATEGORIES = 3
private const val RECENT_COUNT = 3

/** Home's overview (the period's figures, wallets and newest transactions) from the reports and wallets services. */
class ReportsHomeOverview(
    private val reports: ReportsService,
    private val wallets: WalletsService,
    private val recents: RecentTransactionsSource,
) : HomeOverviewSummary {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<HomeOverview> {
        val list = wallets.observeList()
        val recent = list.flatMapLatest { l ->
            val visible = l.active.filter { !it.hidden }
            if (visible.isEmpty()) flowOf(emptyList()) else combine(visible.map { recents.observe(it.id) }) { parts -> mergeRecent(parts.toList()) }
        }
        return combine(reports.observeHomePeriod(), list, recent) { period, walletList, newest -> overviewOf(period, walletList, newest) }
    }

    companion object {
        /** Newest first across wallets; a transfer is in both its wallets and shown once. */
        internal fun mergeRecent(parts: List<List<RecentTransaction>>): List<RecentTransaction> =
            parts.flatten().distinctBy { it.id }.sortedByDescending { it.date }.take(RECENT_COUNT)

        internal fun overviewOf(period: HomePeriod, list: WalletList, newest: List<RecentTransaction>): HomeOverview {
            val visible = list.active.filter { !it.hidden }
            val walletRows = visible.map { view ->
                HomeWallet(
                    id = view.id, name = view.shown.name, typeWire = view.shown.type, iconKey = view.shown.icon,
                    balanceMinor = view.displayedBalanceMinor, currency = view.shown.currency, liability = view.isLiability,
                    waiting = view.waiting,
                )
            }
            val range = period.range
            val summary = period.summary
            val expenses = period.breakdown?.expenses
            val items = expenses?.items.orEmpty().filter { it.valueMinor > 0L }
            val top = items.take(TOP_CATEGORIES)
            val largest = top.firstOrNull()?.valueMinor ?: 0L
            val rest = items.drop(TOP_CATEGORIES)
            return HomeOverview(
                loaded = true,
                noWallets = period.noWallets,
                currency = period.currency,
                periodStart = range?.from,
                periodEnd = range?.to,
                today = period.today,
                daysLeft = range?.let { (period.today.daysUntil(it.to) + 1).coerceAtLeast(0) },
                spentMinor = summary?.expenses,
                incomeMinor = summary?.income,
                leftOverMinor = summary?.net,
                changePercent = period.changePercent,
                dailySpend = period.dailyExpenses,
                todayIndex = period.todayIndex,
                categories = top.map { item ->
                    val display = period.labels.display(item.categoryId)
                    HomeCategory(
                        name = display.name,
                        iconKey = display.iconKey,
                        colorHex = display.colorHex,
                        amountMinor = item.valueMinor,
                        percent = (Shares.of(item.valueMinor, expenses?.totalMinor ?: 0L) as? Share.Percent)?.percent
                            ?: if (Shares.of(item.valueMinor, expenses?.totalMinor ?: 0L) == Share.UnderOne) 1 else null,
                        fraction = Shares.fraction(item.valueMinor, largest),
                    )
                },
                moreCategories = rest.size,
                moreCategoriesMinor = rest.sumOf { it.valueMinor },
                wallets = walletRows,
                totals = list.totals.map { HomeTotal(it.currency, it.totalMinor) },
                recent = newest.map { tx ->
                    HomeRecent(
                        id = tx.id,
                        walletId = tx.walletId,
                        kind = when (tx.kind) {
                            RecentKind.Expense -> HomeRecentKind.Expense
                            RecentKind.Income -> HomeRecentKind.Income
                            RecentKind.Transfer -> HomeRecentKind.Transfer
                            RecentKind.Adjustment, RecentKind.Other -> HomeRecentKind.Other
                        },
                        title = tx.note ?: tx.title,
                        categoryName = tx.title,
                        iconKey = tx.iconKey,
                        date = tx.date,
                        amountMinor = tx.amountMinor,
                        currency = tx.currency,
                        incoming = tx.direction == RecentDirection.In,
                    )
                },
                otherCurrencies = period.excluded.map { it.currency }.distinct(),
                includesWaiting = period.includesWaiting,
                tooLarge = period.tooLarge,
            )
        }
    }
}
