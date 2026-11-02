package com.fares.saveme.feature.budgets.ui.list

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.budgets.domain.model.BudgetsOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** The budgets screen: always the phone's copy, refreshed from the server on open and on pull (P-04.1, FR-018). */
class BudgetsViewModel(
    overview: Flow<BudgetsOverview>,
    private val refresh: suspend (Boolean) -> RefreshOutcome,
    private val isEmailVerified: () -> Boolean,
    timeZoneId: Flow<String> = emptyFlow(),
) : MviViewModel<BudgetsState, BudgetsIntent, BudgetsEffect>(BudgetsState()) {

    init {
        overview.onEach { stored -> setState { reduce(it, BudgetsChange.Stored(stored)) } }.launchIn(viewModelScope)
        timeZoneId.onEach { id -> setState { reduce(it, BudgetsChange.TimeZone(id)) } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: BudgetsIntent) {
        when (intent) {
            BudgetsIntent.Opened -> read(force = false, spinner = false)
            BudgetsIntent.Refresh -> read(force = true, spinner = true)
            BudgetsIntent.AddClicked ->
                if (isEmailVerified()) sendEffect(BudgetsEffect.OpenEditor(null)) else sendEffect(BudgetsEffect.RequireVerification)
            is BudgetsIntent.BudgetClicked -> {
                if ((currentState.active + currentState.archived).any { it.id == intent.id }) sendEffect(BudgetsEffect.OpenEditor(intent.id))
            }
            BudgetsIntent.SharedClicked -> sendEffect(BudgetsEffect.OpenShared)
            BudgetsIntent.Back -> sendEffect(BudgetsEffect.NavigateBack)
        }
    }

    /** An open reads within the 60 second floor; a pull always reads again (FR-018). */
    private fun read(force: Boolean, spinner: Boolean) {
        if (currentState.refreshing) return
        if (spinner) setState { reduce(it, BudgetsChange.Refreshing(true)) }
        viewModelScope.launch {
            val outcome = refresh(force)
            setState { reduce(it, BudgetsChange.RefreshDone(offline = outcome == RefreshOutcome.Offline, failed = outcome == RefreshOutcome.Failed)) }
        }
    }
}
