package com.fares.saveme.feature.sharing.ui.activity

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.sharing.domain.model.ActivityPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Who did what in one wallet (spec 013 US6): read online, 50 entries a page, newest first, loading the next page when the list
 * reaches its end. Nothing is stored; the store holds the pages only while the screen is open (FR-024). Domain lambdas only.
 */
class ActivityViewModel(
    private val walletId: String,
    private val page: suspend (cursor: String?) -> ActivityPage,
    timeZoneId: Flow<String> = emptyFlow(),
    walletName: Flow<String?> = emptyFlow(),
) : MviViewModel<ActivityState, ActivityIntent, ActivityEffect>(ActivityState(walletId = walletId)) {

    private var started = false

    init {
        timeZoneId.onEach { id -> setState { reduce(it, ActivityChange.TimeZone(id)) } }.launchIn(viewModelScope)
        walletName.onEach { name -> setState { reduce(it, ActivityChange.WalletName(name.orEmpty())) } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: ActivityIntent) {
        when (intent) {
            ActivityIntent.Opened -> if (!started) {
                started = true
                loadFirst()
            }
            ActivityIntent.LoadMore -> if (currentState.canLoadMore) loadMore(currentState.nextCursor)
            ActivityIntent.Retry -> when {
                currentState.loading || currentState.loadingMore -> Unit
                currentState.items.isEmpty() -> loadFirst()
                else -> loadMore(currentState.nextCursor)
            }
            is ActivityIntent.ItemClicked -> currentState.items.firstOrNull { it.id == intent.id }?.transactionId?.let {
                sendEffect(ActivityEffect.OpenTransaction(it, walletId))
            }
            ActivityIntent.Back -> sendEffect(ActivityEffect.NavigateBack)
        }
    }

    private fun loadFirst() {
        setState { reduce(it, ActivityChange.LoadingFirst(true)) }
        viewModelScope.launch { apply(page(null), first = true) }
    }

    private fun loadMore(cursor: String?) {
        setState { reduce(it, ActivityChange.LoadingMore(true)) }
        viewModelScope.launch { apply(page(cursor), first = false) }
    }

    private fun apply(result: ActivityPage, first: Boolean) {
        when (result) {
            is ActivityPage.Loaded ->
                // A refused position starts the walk again: the new first page replaces what was shown (FR-026).
                setState { reduce(it, if (first || result.restarted) ActivityChange.First(result.items, result.nextCursor) else ActivityChange.More(result.items, result.nextCursor)) }
            ActivityPage.Offline -> setState { reduce(it, ActivityChange.Error(ActivityError.Offline)) }
            ActivityPage.Gone -> sendEffect(ActivityEffect.NavigateBack)
            is ActivityPage.Failed -> setState { reduce(it, ActivityChange.Error(ActivityError.Failed(result.requestId))) }
        }
    }
}
