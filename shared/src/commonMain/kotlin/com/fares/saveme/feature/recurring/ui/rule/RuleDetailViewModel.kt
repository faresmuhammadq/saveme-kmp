package com.fares.saveme.feature.recurring.ui.rule

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.recurring.domain.model.CatchUpOutcome
import com.fares.saveme.feature.recurring.domain.model.OccurrenceLists
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.RuleDraft
import com.fares.saveme.feature.recurring.domain.model.RuleResult
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import com.fares.saveme.feature.recurring.domain.model.RuleView
import com.fares.saveme.feature.recurring.domain.model.SchedulePreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * One rule's detail: summary, dates, status with clash and refusal choices, and the rule actions, through domain
 * lambdas only (P-16.3). The rule's dates are those the server sent; the phone's preview stands in until they arrive.
 */
class RuleDetailViewModel(
    private val ruleId: String,
    private val observe: (String) -> Flow<RuleView?>,
    private val observeOccurrences: (walletId: String, ruleId: String) -> Flow<OccurrenceLists>,
    private val ruleTitle: (String) -> Flow<String?>,
    private val preview: (RuleDraft) -> SchedulePreview,
    private val setStatus: suspend (String, RuleStatus, Long) -> RuleResult,
    private val delete: suspend (String) -> RuleResult,
    private val catchUp: suspend (String) -> CatchUpOutcome,
    private val keepMine: suspend (String) -> Unit,
    private val useTheirs: suspend (String) -> Unit,
    private val tryAgain: suspend (String) -> Unit,
    private val discard: suspend (String) -> Unit,
    private val displays: (String) -> Flow<CategoryDisplays> = { emptyFlow() },
) : MviViewModel<RuleDetailState, RuleDetailIntent, RuleDetailEffect>(RuleDetailState(ruleId = ruleId)) {

    private var leaving = false
    private var resolvedByPerson = false
    private var occurrencesWallet: String? = null
    private var shownCategories: CategoryDisplays? = null

    init {
        viewModelScope.launch { load() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun detailed(): Flow<Pair<RuleView?, String?>> = observe(ruleId).flatMapLatest { view ->
        val held = (view?.syncState as? RecurringSyncState.Held)?.onRuleId
        if (held == null) flowOf(view to null) else kotlinx.coroutines.flow.combine(flowOf(view), ruleTitle(held)) { v, title -> v to title }
    }

    private suspend fun load() {
        val first = detailed().first()
        val view = first.first
        if (view == null || view.hidden) {
            sendEffect(RuleDetailEffect.ShowMessage(RuleDetailMessage.Gone))
            sendEffect(RuleDetailEffect.Close)
            return
        }
        onView(view, first.second)
        detailed().distinctUntilChangedBy { it }.onEach { (current, title) -> onView(current, title) }.launchIn(viewModelScope)
    }

    private fun onView(view: RuleView?, heldTitle: String?) {
        if (view == null || view.hidden) {
            if (!leaving) {
                leaving = true
                if (!resolvedByPerson) sendEffect(RuleDetailEffect.ShowMessage(RuleDetailMessage.Gone))
                sendEffect(RuleDetailEffect.Close)
            }
            return
        }
        val shownPreview = if (view.rule.hasUnknownValues) SchedulePreview.Empty else preview(view.shown)
        setState { reduce(it, RuleDetailChange.Loaded(view, heldTitle, shownPreview)) }
        applyCategories(view)
        if (occurrencesWallet == null) {
            occurrencesWallet = view.rule.walletId
            observeOccurrences(view.rule.walletId, ruleId)
                .onEach { lists -> setState { reduce(it, RuleDetailChange.Occurrences(lists)) } }
                .launchIn(viewModelScope)
            displays(view.rule.walletId)
                .onEach { shown ->
                    shownCategories = shown
                    currentState.view?.let { applyCategories(it) }
                }
                .launchIn(viewModelScope)
        }
    }

    private fun applyCategories(view: RuleView) {
        val shown = shownCategories ?: return
        val category = view.shown.categoryId?.let { shown.resolve(it) }
        val payout = view.shown.payoutCategoryId?.let { shown.resolve(it) }
        setState { reduce(it, RuleDetailChange.Categories(category, payout)) }
    }

    override fun onIntent(intent: RuleDetailIntent) {
        when (intent) {
            RuleDetailIntent.EditClicked -> if (currentState.canEdit) openEditor(fromDate = false)
            RuleDetailIntent.ChangeFromClicked -> if (currentState.canChangeFrom) openEditor(fromDate = true)
            RuleDetailIntent.PauseClicked -> if (currentState.canPause) changeStatus(RuleStatus.Paused)
            RuleDetailIntent.ResumeClicked -> if (currentState.canResume) changeStatus(RuleStatus.Active)
            RuleDetailIntent.EndClicked -> if (currentState.canEnd) change(RuleDetailChange.Dialog(RuleDialog.End))
            RuleDetailIntent.DeleteClicked -> if (currentState.canDelete) change(RuleDetailChange.Dialog(RuleDialog.Delete))
            RuleDetailIntent.DialogDismissed -> change(RuleDetailChange.Dialog(RuleDialog.None))
            RuleDetailIntent.DialogConfirmed -> confirmDialog()
            RuleDetailIntent.CatchUpClicked -> if (currentState.canCatchUp) checkForNewDates()
            is RuleDetailIntent.OccurrenceClicked -> sendEffect(RuleDetailEffect.OpenOccurrence(intent.id))
            RuleDetailIntent.KeepMine -> resolve(keepMine)
            RuleDetailIntent.UseTheirs, RuleDetailIntent.AcceptDeletion -> resolve(useTheirs)
            RuleDetailIntent.TryAgain -> resolve(tryAgain)
            RuleDetailIntent.Discard -> resolve(discard)
            RuleDetailIntent.Back -> sendEffect(RuleDetailEffect.Close)
        }
    }

    private fun change(change: RuleDetailChange) = setState { reduce(it, change) }

    private fun openEditor(fromDate: Boolean) {
        val walletId = currentState.view?.rule?.walletId ?: return
        sendEffect(RuleDetailEffect.OpenEditor(walletId, ruleId, fromDate))
    }

    private fun confirmDialog() {
        val dialog = currentState.dialog
        change(RuleDetailChange.Dialog(RuleDialog.None))
        when (dialog) {
            RuleDialog.End -> changeStatus(RuleStatus.Ended)
            RuleDialog.Delete -> remove()
            RuleDialog.None -> Unit
        }
    }

    private fun changeStatus(status: RuleStatus) {
        val view = currentState.view ?: return
        if (currentState.busy) return
        change(RuleDetailChange.Busy(true))
        viewModelScope.launch {
            when (val result = setStatus(ruleId, status, view.rule.version)) {
                is RuleResult.Saved, is RuleResult.Created -> Unit
                else -> report(result)
            }
            change(RuleDetailChange.Busy(false))
        }
    }

    private fun remove() {
        if (currentState.busy) return
        resolvedByPerson = true
        change(RuleDetailChange.Busy(true))
        viewModelScope.launch {
            val result = delete(ruleId)
            if (result != RuleResult.Saved) {
                resolvedByPerson = false
                report(result)
            }
            change(RuleDetailChange.Busy(false))
        }
    }

    private fun report(result: RuleResult) {
        when (result) {
            RuleResult.NotVerified -> sendEffect(RuleDetailEffect.RequireVerification)
            RuleResult.NotAllowed -> sendEffect(RuleDetailEffect.ShowMessage(RuleDetailMessage.NotAllowed))
            RuleResult.ReadOnly -> sendEffect(RuleDetailEffect.ShowMessage(RuleDetailMessage.ReadOnly))
            RuleResult.NoLongerAvailable -> {
                sendEffect(RuleDetailEffect.ShowMessage(RuleDetailMessage.Gone))
                sendEffect(RuleDetailEffect.Close)
            }
            else -> Unit
        }
    }

    private fun checkForNewDates() {
        if (currentState.catchingUp) return
        change(RuleDetailChange.CatchingUp(true))
        viewModelScope.launch {
            val note = when (val outcome = catchUp(ruleId)) {
                CatchUpOutcome.Done -> CatchUpNote.Done
                CatchUpOutcome.NeedsConnection -> CatchUpNote.Offline
                CatchUpOutcome.NotAllowed -> CatchUpNote.NotAllowed
                CatchUpOutcome.NotVerified -> {
                    sendEffect(RuleDetailEffect.RequireVerification)
                    null
                }
                is CatchUpOutcome.Failed -> CatchUpNote.Failed(outcome.requestId)
            }
            change(RuleDetailChange.CatchingUp(false))
            change(RuleDetailChange.CatchUp(note))
        }
    }

    /** A clash or refusal choice: the person's own act, so a vanished row is not "gone". */
    private fun resolve(action: suspend (String) -> Unit) {
        if (currentState.busy) return
        resolvedByPerson = true
        change(RuleDetailChange.Busy(true))
        viewModelScope.launch {
            action(ruleId)
            change(RuleDetailChange.Busy(false))
        }
    }
}
