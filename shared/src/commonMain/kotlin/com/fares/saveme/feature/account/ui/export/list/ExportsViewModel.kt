package com.fares.saveme.feature.account.ui.export.list

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.account.domain.export.PageOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The person's exports of the last 30 days (spec 016 FR-005). Every open and "Refresh" restarts from the first page; a refused
 * page position restarts once. Domain lambdas only (P-16.3).
 */
class ExportsViewModel(
    private val page: suspend (cursor: String?) -> PageOutcome,
    walletNames: Flow<Map<String, String>> = emptyFlow(),
    signedIn: Flow<Boolean> = emptyFlow(),
    timeZoneId: Flow<String> = emptyFlow(),
) : MviViewModel<ExportsState, ExportsIntent, ExportsEffect>(ExportsState()) {

    private var loadJob: Job? = null

    init {
        walletNames.onEach { names -> setState { reduce(it, ExportsChange.WalletNames(names)) } }.launchIn(viewModelScope)
        timeZoneId.onEach { id -> setState { reduce(it, ExportsChange.TimeZone(id)) } }.launchIn(viewModelScope)
        signedIn.onEach { isSignedIn ->
            if (!isSignedIn) {
                loadJob?.cancel()
                setState { reduce(it, ExportsChange.SignedOut) }
            }
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: ExportsIntent) {
        when (intent) {
            ExportsIntent.Opened, ExportsIntent.Refresh -> loadFirst()
            ExportsIntent.ShowMore -> showMore()
            is ExportsIntent.RowClicked -> sendEffect(ExportsEffect.OpenDetail(intent.id))
            ExportsIntent.NewClicked -> sendEffect(ExportsEffect.OpenRequest)
            ExportsIntent.Back -> sendEffect(ExportsEffect.NavigateBack)
        }
    }

    private fun loadFirst() {
        loadJob?.cancel()
        setState { reduce(it, ExportsChange.LoadingFirst) }
        loadJob = viewModelScope.launch {
            var outcome = page(null)
            // A refused position restarts from the first page, once (FR-005); from the first page it cannot happen.
            if (outcome == PageOutcome.CursorInvalid) outcome = page(null)
            when (outcome) {
                is PageOutcome.Page -> setState { reduce(it, ExportsChange.First(outcome)) }
                else -> fail(outcome)
            }
        }
    }

    private fun showMore() {
        val cursor = currentState.nextCursor
        if (!currentState.hasMore || cursor == null || currentState.loadingMore || currentState.loading) return
        setState { reduce(it, ExportsChange.LoadingMore) }
        loadJob = viewModelScope.launch {
            when (val outcome = page(cursor)) {
                is PageOutcome.Page -> setState { reduce(it, ExportsChange.More(outcome)) }
                PageOutcome.CursorInvalid -> loadFirst()
                else -> fail(outcome)
            }
        }
    }

    private fun fail(outcome: PageOutcome) {
        val banner = when (outcome) {
            PageOutcome.Offline -> ExportsBanner.Offline
            is PageOutcome.Failed -> ExportsBanner.Error(outcome.code, outcome.requestId)
            else -> ExportsBanner.Error(ApiErrorCode.UNKNOWN, null)
        }
        setState { reduce(it, ExportsChange.Failed(banner)) }
    }
}
