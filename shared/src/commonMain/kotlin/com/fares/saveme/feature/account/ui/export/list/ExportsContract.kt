package com.fares.saveme.feature.account.ui.export.list

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.account.domain.export.Export
import com.fares.saveme.feature.account.domain.export.PageOutcome

/** A problem on the Exports screen; copy by code (P-02.4). */
sealed interface ExportsBanner {
    data object Offline : ExportsBanner
    data class Error(val code: ApiErrorCode, val requestId: String?) : ExportsBanner
}

/** Memory only (spec 016 R-05). Rows carry no download link: [Export] has none. */
data class ExportsState(
    val items: List<Export> = emptyList(),
    val walletNames: Map<String, String> = emptyMap(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** True once a first page has arrived, so "empty" is not shown before then. */
    val loaded: Boolean = false,
    val banner: ExportsBanner? = null,
    val timeZoneId: String = "UTC",
)

sealed interface ExportsIntent {
    data object Opened : ExportsIntent
    data object Refresh : ExportsIntent
    data object ShowMore : ExportsIntent
    data class RowClicked(val id: String) : ExportsIntent
    data object NewClicked : ExportsIntent
    data object Back : ExportsIntent
}

sealed interface ExportsEffect {
    data class OpenDetail(val id: String) : ExportsEffect
    data object OpenRequest : ExportsEffect
    data object NavigateBack : ExportsEffect
}

sealed interface ExportsChange {
    /** A first-page load began; the rows stay until the answer arrives. */
    data object LoadingFirst : ExportsChange
    data object LoadingMore : ExportsChange
    data class First(val page: PageOutcome.Page) : ExportsChange
    data class More(val page: PageOutcome.Page) : ExportsChange
    data class Failed(val banner: ExportsBanner) : ExportsChange
    data class WalletNames(val names: Map<String, String>) : ExportsChange
    data class TimeZone(val id: String) : ExportsChange
    data object SignedOut : ExportsChange
}

fun reduce(state: ExportsState, change: ExportsChange): ExportsState = when (change) {
    ExportsChange.LoadingFirst -> state.copy(loading = true, banner = null)
    ExportsChange.LoadingMore -> state.copy(loadingMore = true, banner = null)
    is ExportsChange.First -> state.copy(
        items = change.page.items, nextCursor = change.page.nextCursor, hasMore = change.page.hasMore,
        loading = false, loadingMore = false, loaded = true, banner = null,
    )
    is ExportsChange.More -> state.copy(
        items = (state.items + change.page.items).distinctBy { it.id },
        nextCursor = change.page.nextCursor, hasMore = change.page.hasMore, loadingMore = false, banner = null,
    )
    is ExportsChange.Failed -> state.copy(loading = false, loadingMore = false, banner = change.banner)
    is ExportsChange.WalletNames -> state.copy(walletNames = change.names)
    is ExportsChange.TimeZone -> state.copy(timeZoneId = change.id)
    ExportsChange.SignedOut -> ExportsState(timeZoneId = state.timeZoneId)
}
