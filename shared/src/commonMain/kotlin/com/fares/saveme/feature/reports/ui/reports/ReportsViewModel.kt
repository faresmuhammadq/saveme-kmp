package com.fares.saveme.feature.reports.ui.reports

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.categories.domain.model.ReportLabels
import com.fares.saveme.feature.reports.domain.model.RangeChoice
import com.fares.saveme.feature.reports.domain.model.RangeProblem
import com.fares.saveme.feature.reports.domain.model.ReportState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * The Reports screen of one scope: always the phone's own figures (FR-001), read again from the server on open and on pull
 * (FR-020). The store holds no rules; everything it needs is a lambda over the reports domain (P-16).
 *
 * @param walletId null for the cross-wallet report.
 */
class ReportsViewModel(
    private val walletId: String?,
    report: Flow<ReportState>,
    labels: Flow<ReportLabels>,
    private val refresh: suspend (Boolean) -> RefreshOutcome,
    private val setRange: suspend (RangeChoice) -> Unit,
    private val setTrendLength: suspend (Int) -> Unit,
    private val setCurrency: (String) -> Unit,
    private val setIncludeScheduled: (Boolean) -> Unit,
    private val validateCustom: (LocalDate, LocalDate) -> RangeProblem?,
    timeZoneId: Flow<String> = emptyFlow(),
) : MviViewModel<ReportsState, ReportsIntent, ReportsEffect>(ReportsState(walletId = walletId)) {

    init {
        combine(report, labels) { stored, names -> ReportsChange.Stored(stored, names) }
            .onEach { change ->
                setState { reduce(it, change) }
                if (change.state == ReportState.WalletGone) sendEffect(ReportsEffect.Close)
            }
            .launchIn(viewModelScope)
        timeZoneId.onEach { id -> setState { reduce(it, ReportsChange.TimeZone(id)) } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: ReportsIntent) {
        when (intent) {
            ReportsIntent.Opened -> read(force = false, spinner = false)
            ReportsIntent.PullToRefresh -> read(force = true, spinner = true)
            ReportsIntent.PreviousClicked -> viewModelScope.launch { setRange(RangeChoice.Previous) }
            ReportsIntent.NextClicked -> if (currentState.canGoNext) viewModelScope.launch { setRange(RangeChoice.Next) }
            ReportsIntent.CurrentPeriodClicked -> viewModelScope.launch { setRange(RangeChoice.CurrentPeriod) }
            ReportsIntent.CustomRangeClicked -> setState { reduce(it, ReportsChange.CustomOpen(true)) }
            ReportsIntent.CustomRangeDismissed -> setState { reduce(it, ReportsChange.CustomOpen(false)) }
            is ReportsIntent.CustomRangePicked -> pickCustom(intent.from, intent.to)
            ReportsIntent.CurrencyClicked -> if (currentState.isCrossWallet) setState { reduce(it, ReportsChange.CurrencyPicker(true)) }
            ReportsIntent.CurrencyPickerDismissed -> setState { reduce(it, ReportsChange.CurrencyPicker(false)) }
            is ReportsIntent.CurrencyPicked -> {
                setState { reduce(it, ReportsChange.CurrencyPicker(false)) }
                if (currentState.isCrossWallet) setCurrency(intent.code)
            }
            is ReportsIntent.SeeCurrencyClicked -> if (currentState.isCrossWallet) setCurrency(intent.code)
            is ReportsIntent.TrendLengthPicked -> viewModelScope.launch { setTrendLength(intent.length) }
            is ReportsIntent.IncludeScheduledToggled -> setIncludeScheduled(intent.on)
            ReportsIntent.SeeAllClicked -> openCategories(CategoryGroupKind.Expenses)
            is ReportsIntent.CategoryClicked -> openCategories(intent.kind)
            ReportsIntent.AddWalletClicked -> sendEffect(ReportsEffect.OpenWallets)
            ReportsIntent.BackClicked -> sendEffect(ReportsEffect.Close)
        }
    }

    private fun pickCustom(from: LocalDate, to: LocalDate) {
        if (validateCustom(from, to) != null) {
            setState { reduce(it, ReportsChange.CustomProblem(true)) }
            return
        }
        setState { reduce(reduce(it, ReportsChange.CustomProblem(false)), ReportsChange.CustomOpen(false)) }
        viewModelScope.launch { setRange(RangeChoice.Custom(from, to)) }
    }

    private fun openCategories(kind: CategoryGroupKind) {
        val state = currentState
        val range = state.range ?: return
        sendEffect(ReportsEffect.OpenCategories(CategoriesArgs(state.walletId, range.from, range.to, state.currency, state.includeScheduled, kind)))
    }

    /** An open reads within the 60 second floor; a pull always reads again (FR-020). */
    private fun read(force: Boolean, spinner: Boolean) {
        if (currentState.refreshing) return
        if (spinner) setState { reduce(it, ReportsChange.Refreshing(true)) }
        viewModelScope.launch {
            val outcome = refresh(force)
            setState { reduce(it, ReportsChange.RefreshDone(offline = outcome == RefreshOutcome.Offline, failed = outcome == RefreshOutcome.Failed)) }
        }
    }
}
