package com.fares.saveme.feature.recurring.ui.list

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.recurring.domain.model.ConfirmDraft
import com.fares.saveme.feature.recurring.domain.model.OccurrenceLists
import com.fares.saveme.feature.recurring.domain.model.OccurrenceResult
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.domain.model.RuleView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The recurring list of one wallet (or all): reads only the phone's copy, asks the server again on open, on pull and
 * when the screen returns (like the wallet's transactions), and takes domain lambdas only (P-16.3).
 */
class RecurringViewModel(
    private val walletId: String?,
    walletName: Flow<String?>,
    occurrences: Flow<OccurrenceLists>,
    rules: Flow<List<RuleView>>,
    canAdd: Flow<Boolean>,
    private val refresh: suspend (Boolean) -> RefreshOutcome,
    private val isEmailVerified: () -> Boolean,
    private val confirm: suspend (String, ConfirmDraft) -> OccurrenceResult = { _, _ -> OccurrenceResult.NotAllowed },
    private val skip: suspend (String) -> OccurrenceResult = { OccurrenceResult.NotAllowed },
) : MviViewModel<RecurringState, RecurringIntent, RecurringEffect>(RecurringState(walletId = walletId)) {

    init {
        walletName.onEach { name -> setState { reduce(it, RecurringChange.WalletName(name)) } }.launchIn(viewModelScope)
        occurrences.onEach { lists -> setState { reduce(it, RecurringChange.Occurrences(lists)) } }.launchIn(viewModelScope)
        rules.onEach { list -> setState { reduce(it, RecurringChange.Rules(list)) } }.launchIn(viewModelScope)
        canAdd.onEach { can -> setState { reduce(it, RecurringChange.CanAdd(can)) } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: RecurringIntent) {
        when (intent) {
            RecurringIntent.Opened, RecurringIntent.Foregrounded -> pull(force = false)
            RecurringIntent.PullToRefresh -> pull(force = true)
            RecurringIntent.AddClicked ->
                if (!currentState.canAdd) Unit
                else if (isEmailVerified()) sendEffect(RecurringEffect.OpenEditor(walletId))
                else sendEffect(RecurringEffect.RequireVerification)
            is RecurringIntent.RuleClicked -> sendEffect(RecurringEffect.OpenRule(intent.id))
            is RecurringIntent.OccurrenceClicked -> sendEffect(RecurringEffect.OpenOccurrence(intent.id))
            is RecurringIntent.ConfirmOccurrence -> act(intent.id) { view ->
                confirm(
                    view.id,
                    ConfirmDraft(
                        date = view.shownDate,
                        amountMinor = view.shownAmountMinor,
                        categoryId = view.shownCategoryId,
                        description = view.shownDescription,
                        memo = view.shownMemo,
                        counterparty = view.shownCounterparty,
                    ),
                )
            }
            is RecurringIntent.SkipOccurrence -> act(intent.id) { view -> skip(view.id) }
            RecurringIntent.Back -> sendEffect(RecurringEffect.Back)
        }
    }

    /** Confirm or skip straight from the list; whatever the list cannot settle opens the occurrence instead. */
    private fun act(id: String, action: suspend (OccurrenceView) -> OccurrenceResult) {
        val view = (currentState.due + currentState.upcoming).firstOrNull { it.id == id } ?: return
        if (id in currentState.busyIds) return
        if (!isEmailVerified()) {
            sendEffect(RecurringEffect.RequireVerification)
            return
        }
        setState { reduce(it, RecurringChange.Busy(id, true)) }
        viewModelScope.launch {
            when (action(view)) {
                OccurrenceResult.Done -> Unit
                OccurrenceResult.NotVerified -> sendEffect(RecurringEffect.RequireVerification)
                else -> sendEffect(RecurringEffect.OpenOccurrence(id))
            }
            setState { reduce(it, RecurringChange.Busy(id, false)) }
        }
    }

    private fun pull(force: Boolean) {
        if (currentState.refreshing) return
        setState { reduce(it, RecurringChange.Refreshing(true)) }
        viewModelScope.launch {
            val outcome = refresh(force)
            setState { reduce(it, RecurringChange.RefreshDone(outcome)) }
        }
    }
}
