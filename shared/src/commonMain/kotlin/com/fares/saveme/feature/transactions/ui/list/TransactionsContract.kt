package com.fares.saveme.feature.transactions.ui.list

import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.transactions.domain.DatePresets
import com.fares.saveme.feature.transactions.domain.model.Counterpart
import com.fares.saveme.feature.transactions.domain.model.Direction
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.TransactionList
import com.fares.saveme.feature.transactions.domain.model.TransactionQuery
import com.fares.saveme.feature.transactions.domain.model.TransactionSyncState
import com.fares.saveme.feature.transactions.domain.model.TransactionView
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** A mark in words for what is happening to a row's change (FR-016, P-08.3). */
enum class RowStatus { None, Waiting, Changed, NotSaved }

/** One transaction row, ready to draw; the screen turns it into words in the app language. */
data class RowUi(
    val id: String,
    val kind: TransactionKind,
    val direction: Direction,
    val counterpart: Counterpart?,
    /** Null for a transfer or a transaction without a category. */
    val category: CategoryDisplay?,
    val note: String?,
    val amountMinor: Long,
    val status: RowStatus,
)

data class DayUi(val date: LocalDate, val netMinor: Long, val rows: List<RowUi>)

/** An active filter shown as a chip the person can clear (FR-028). */
sealed interface FilterChip {
    data class DateRange(val from: LocalDate?, val to: LocalDate?) : FilterChip
    data class Category(val id: String) : FilterChip
    data class Kind(val kind: TransactionKind) : FilterChip
}

data class TransactionsState(
    val walletId: String = "",
    val walletName: String = "",
    val currency: String = "",
    val balanceMinor: Long = 0,
    val isLiability: Boolean = false,
    val days: List<DayUi> = emptyList(),
    /** Everything the wallet holds, before search and filters. */
    val totalCount: Int = 0,
    val waitingCount: Int = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val refreshFailed: Boolean = false,
    val updatedAt: Instant? = null,
    val canAdd: Boolean = false,
    val query: TransactionQuery = TransactionQuery(),
    val searchText: String = "",
    val filterSheetOpen: Boolean = false,
    val filterChoices: List<CategoryDisplay> = emptyList(),
    /** "Today" in the profile's time zone, for the day labels and the month presets. */
    val today: LocalDate? = null,
    val timeZoneId: String = "UTC",
) {
    val chips: List<FilterChip>
        get() = buildList {
            if (query.from != null || query.to != null) add(FilterChip.DateRange(query.from, query.to))
            query.categoryIds.sorted().forEach { add(FilterChip.Category(it)) }
            TransactionKind.selectable.filter { it in query.kinds }.forEach { add(FilterChip.Kind(it)) }
        }

    val isEmpty: Boolean get() = !loading && totalCount == 0
    val noMatches: Boolean get() = !loading && totalCount > 0 && days.isEmpty()
    val filtersActive: Boolean get() = query.isActive
}

sealed interface TransactionsIntent {
    data object Opened : TransactionsIntent
    data object Foregrounded : TransactionsIntent
    data object PullToRefresh : TransactionsIntent
    data class SearchChanged(val text: String) : TransactionsIntent
    data object FiltersOpened : TransactionsIntent
    data object FiltersDismissed : TransactionsIntent
    data object ThisMonth : TransactionsIntent
    data object LastMonth : TransactionsIntent
    data class DateRange(val from: LocalDate?, val to: LocalDate?) : TransactionsIntent
    data class CategoryToggled(val id: String) : TransactionsIntent
    data class KindToggled(val kind: TransactionKind) : TransactionsIntent
    data class ChipCleared(val chip: FilterChip) : TransactionsIntent
    data object ClearAll : TransactionsIntent
    data class RowClicked(val id: String) : TransactionsIntent
    data object AddClicked : TransactionsIntent
    data object Back : TransactionsIntent
}

sealed interface TransactionsEffect {
    data class OpenEditor(val transactionId: String?, val walletId: String) : TransactionsEffect
    data object RequireVerification : TransactionsEffect
    data object Back : TransactionsEffect
}

sealed interface TransactionsChange {
    data class Wallet(val view: WalletView?) : TransactionsChange
    data class Data(val list: TransactionList, val days: List<DayUi>) : TransactionsChange
    data class FilterChoices(val choices: List<CategoryDisplay>) : TransactionsChange
    data class Refreshing(val busy: Boolean) : TransactionsChange
    data class RefreshDone(val outcome: RefreshOutcome) : TransactionsChange
    data class Query(val query: TransactionQuery, val searchText: String) : TransactionsChange
    data class FilterSheet(val open: Boolean) : TransactionsChange
    data class Today(val date: LocalDate) : TransactionsChange
    data class TimeZone(val id: String) : TransactionsChange
}

fun reduce(state: TransactionsState, change: TransactionsChange): TransactionsState = when (change) {
    is TransactionsChange.Wallet -> change.view?.let { view ->
        state.copy(
            walletName = view.shown.name,
            currency = view.shown.currency,
            balanceMinor = view.displayedBalanceMinor,
            isLiability = view.isLiability,
        )
    } ?: state
    is TransactionsChange.Data -> state.copy(
        days = change.days,
        totalCount = change.list.totalCount,
        waitingCount = change.list.waitingCount,
        canAdd = change.list.canAdd,
        updatedAt = change.list.updatedAt,
        // Rows (or a stored read) mean the phone has something to show; before that the screen says it is loading (FR-018).
        loading = state.loading && !change.list.loaded,
        currency = change.list.currency.ifEmpty { state.currency },
    )
    is TransactionsChange.FilterChoices -> state.copy(filterChoices = change.choices)
    is TransactionsChange.Refreshing -> state.copy(refreshing = change.busy)
    is TransactionsChange.RefreshDone -> state.copy(
        refreshing = false,
        loading = false,
        offline = change.outcome == RefreshOutcome.Offline,
        refreshFailed = change.outcome == RefreshOutcome.Failed,
    )
    is TransactionsChange.Query -> state.copy(query = change.query, searchText = change.searchText)
    is TransactionsChange.FilterSheet -> state.copy(filterSheetOpen = change.open)
    is TransactionsChange.Today -> state.copy(today = change.date)
    is TransactionsChange.TimeZone -> state.copy(timeZoneId = change.id)
}

internal fun TransactionView.toRow(category: CategoryDisplay?) = RowUi(
    id = id,
    kind = shown.kind,
    direction = direction,
    counterpart = counterpart,
    category = category,
    note = shown.description?.takeIf { it.isNotBlank() },
    amountMinor = shown.amountMinor,
    status = when (syncState) {
        TransactionSyncState.None -> RowStatus.None
        TransactionSyncState.Waiting -> RowStatus.Waiting
        is TransactionSyncState.Clash -> RowStatus.Changed
        is TransactionSyncState.Refused -> RowStatus.NotSaved
    },
)

/** The query after a month preset or a custom range; presets use "today" in the profile's time zone (research R-12). */
internal fun TransactionQuery.withRange(from: LocalDate?, to: LocalDate?) = copy(from = from, to = to)

internal fun presetRange(today: LocalDate, last: Boolean): Pair<LocalDate, LocalDate> =
    if (last) DatePresets.lastMonth(today) else DatePresets.thisMonth(today)

internal fun TransactionQuery.without(chip: FilterChip): TransactionQuery = when (chip) {
    is FilterChip.DateRange -> copy(from = null, to = null)
    is FilterChip.Category -> copy(categoryIds = categoryIds - chip.id)
    is FilterChip.Kind -> copy(kinds = kinds - chip.kind)
}
