package com.fares.saveme.feature.sharing.ui.activity

import com.fares.saveme.feature.sharing.domain.model.ActivityItem

/** Why the list is not (fully) shown. Nothing stale is shown as current (US6-S5). */
sealed interface ActivityError {
    data object Offline : ActivityError
    data class Failed(val requestId: String?) : ActivityError
}

data class ActivityState(
    val walletId: String = "",
    val items: List<ActivityItem> = emptyList(),
    val nextCursor: String? = null,
    /** The first page is being read. */
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: ActivityError? = null,
    val timeZoneId: String = "UTC",
    val walletName: String = "",
) {
    val isEmpty: Boolean get() = !loading && error == null && items.isEmpty()
    val canLoadMore: Boolean get() = nextCursor != null && !loading && !loadingMore && error == null
}

sealed interface ActivityIntent {
    data object Opened : ActivityIntent
    data object LoadMore : ActivityIntent
    data object Retry : ActivityIntent
    data class ItemClicked(val id: String) : ActivityIntent
    data object Back : ActivityIntent
}

sealed interface ActivityEffect {
    data class OpenTransaction(val transactionId: String, val walletId: String) : ActivityEffect
    data object NavigateBack : ActivityEffect
}

sealed interface ActivityChange {
    /** The first page of a read, or the list after the position was refused and the walk started again. */
    data class First(val items: List<ActivityItem>, val nextCursor: String?) : ActivityChange
    data class More(val items: List<ActivityItem>, val nextCursor: String?) : ActivityChange
    data class LoadingFirst(val loading: Boolean) : ActivityChange
    data class LoadingMore(val loading: Boolean) : ActivityChange
    data class Error(val error: ActivityError?) : ActivityChange
    data class TimeZone(val id: String) : ActivityChange
    data class WalletName(val name: String) : ActivityChange
}

fun reduce(state: ActivityState, change: ActivityChange): ActivityState = when (change) {
    is ActivityChange.First -> state.copy(items = change.items, nextCursor = change.nextCursor, loading = false, loadingMore = false, error = null)
    is ActivityChange.More -> state.copy(
        items = (state.items + change.items).distinctBy { it.id },
        nextCursor = change.nextCursor,
        loadingMore = false,
        error = null,
    )
    is ActivityChange.LoadingFirst -> state.copy(loading = change.loading, error = if (change.loading) null else state.error)
    is ActivityChange.LoadingMore -> state.copy(loadingMore = change.loading, error = if (change.loading) null else state.error)
    is ActivityChange.Error -> state.copy(error = change.error, loading = false, loadingMore = false)
    is ActivityChange.TimeZone -> state.copy(timeZoneId = change.id)
    is ActivityChange.WalletName -> state.copy(walletName = change.name)
}
