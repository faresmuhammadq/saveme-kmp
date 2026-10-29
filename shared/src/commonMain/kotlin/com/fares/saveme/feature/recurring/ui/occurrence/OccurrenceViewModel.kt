package com.fares.saveme.feature.recurring.ui.occurrence

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.recurring.domain.model.ConfirmDraft
import com.fares.saveme.feature.recurring.domain.model.ConfirmField
import com.fares.saveme.feature.recurring.domain.model.OccurrenceResult
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.ui.asRuleProblem
import com.fares.saveme.feature.transactions.domain.TransactionAmount
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * One occurrence: its state, the confirm and edit forms in place, skip and undo, and the clash and refusal choices,
 * through domain lambdas only (P-16.3). With [openConfirm] the confirm form opens as soon as the occurrence is loaded.
 */
class OccurrenceViewModel(
    private val occurrenceId: String,
    private val openConfirm: Boolean,
    private val observe: (String) -> Flow<OccurrenceView?>,
    private val displays: (String) -> Flow<CategoryDisplays>,
    private val categoryChoices: (TransactionKind, String?) -> Flow<CategoryChoices>,
    private val walletName: (String) -> Flow<String?>,
    private val confirm: suspend (String, ConfirmDraft) -> OccurrenceResult,
    private val edit: suspend (String, ConfirmDraft) -> OccurrenceResult,
    private val skip: suspend (String) -> OccurrenceResult,
    private val unskip: suspend (String) -> OccurrenceResult,
    private val cancelConfirmation: suspend (String) -> Unit,
    private val dismissNotice: suspend (String) -> Unit,
    private val keepMine: suspend (String) -> Unit,
    private val useTheirs: suspend (String) -> Unit,
    private val tryAgain: suspend (String) -> Unit,
    private val discard: suspend (String) -> Unit,
) : MviViewModel<OccurrenceScreenState, OccurrenceIntent, OccurrenceEffect>(OccurrenceScreenState(occurrenceId = occurrenceId)) {

    private var leaving = false
    private var resolvedByPerson = false

    init {
        viewModelScope.launch { load() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun detailed(): Flow<Pair<OccurrenceView?, OccurrenceDetails>> = observe(occurrenceId).flatMapLatest { view ->
        if (view == null) {
            flowOf(null to OccurrenceDetails(null, null))
        } else {
            combine(
                displays(view.occurrence.walletId),
                view.occurrence.counterWalletId?.let { walletName(it) } ?: flowOf(null),
                walletName(view.occurrence.walletId),
            ) { shown, destination, wallet ->
                view to OccurrenceDetails(view.shownCategoryId?.let { shown.resolve(it) }, destination, wallet)
            }
        }
    }

    private suspend fun load() {
        val first = detailed().first()
        val view = first.first
        if (view == null) {
            sendEffect(OccurrenceEffect.ShowMessage(OccurrenceMessage.Gone))
            sendEffect(OccurrenceEffect.Close)
            return
        }
        setState { reduce(it, OccurrenceChange.Loaded(view, first.second)) }
        if (view.notice != null) dismissNotice(occurrenceId)
        val kind = view.occurrence.kind
        if (kind == TransactionKind.Income || kind == TransactionKind.Expense) {
            viewModelScope.launch {
                categoryChoices(kind, view.shownCategoryId).collect { choices -> setState { reduce(it, OccurrenceChange.Choices(choices)) } }
            }
        }
        if (openConfirm && currentState.canConfirm) setState { reduce(it, OccurrenceChange.FormOpened(OccurrenceForm.Confirm)) }
        detailed().collect { (current, details) -> onView(current, details) }
    }

    private fun onView(view: OccurrenceView?, details: OccurrenceDetails) {
        if (view == null) {
            if (!leaving) {
                leaving = true
                if (!resolvedByPerson) sendEffect(OccurrenceEffect.ShowMessage(OccurrenceMessage.Gone))
                sendEffect(OccurrenceEffect.Close)
            }
            return
        }
        setState { reduce(it, OccurrenceChange.Stored(view, details)) }
    }

    override fun onIntent(intent: OccurrenceIntent) {
        when (intent) {
            OccurrenceIntent.ConfirmClicked ->
                if (currentState.canConfirm) change(OccurrenceChange.FormOpened(OccurrenceForm.Confirm))
                else if (currentState.canChangeConfirmation) change(OccurrenceChange.FormOpened(OccurrenceForm.Confirm))
            OccurrenceIntent.EditClicked -> if (currentState.canEdit) change(OccurrenceChange.FormOpened(OccurrenceForm.Edit))
            OccurrenceIntent.FormCancelled -> change(OccurrenceChange.FormClosed)
            is OccurrenceIntent.AmountChanged -> change(OccurrenceChange.Amount(intent.text))
            OccurrenceIntent.DateClicked -> change(OccurrenceChange.DatePicker(true))
            OccurrenceIntent.DatePickerDismissed -> change(OccurrenceChange.DatePicker(false))
            is OccurrenceIntent.DateChosen -> change(OccurrenceChange.Date(intent.date))
            OccurrenceIntent.CategoryClicked -> change(OccurrenceChange.CategorySheet(true))
            is OccurrenceIntent.CategoryChosen -> chooseCategory(intent.id)
            OccurrenceIntent.SheetDismissed -> change(OccurrenceChange.CategorySheet(false))
            is OccurrenceIntent.NoteChanged -> change(OccurrenceChange.Note(intent.text))
            is OccurrenceIntent.DetailsChanged -> change(OccurrenceChange.Details(intent.text))
            is OccurrenceIntent.PersonChanged -> change(OccurrenceChange.Person(intent.text))
            OccurrenceIntent.SaveClicked -> submit()
            OccurrenceIntent.SkipClicked -> if (currentState.canSkip) act { skip(occurrenceId) }
            OccurrenceIntent.UnskipClicked -> if (currentState.canUnskip) act { unskip(occurrenceId) }
            OccurrenceIntent.CancelConfirmationClicked -> if (currentState.confirmationWaiting) resolve(cancelConfirmation)
            OccurrenceIntent.OpenTransactionClicked -> {
                val transactionId = currentState.view?.occurrence?.transactionId
                val walletId = currentState.view?.occurrence?.walletId
                if (currentState.canOpenTransaction && transactionId != null && walletId != null) {
                    sendEffect(OccurrenceEffect.OpenTransaction(transactionId, walletId))
                }
            }
            OccurrenceIntent.OpenRuleClicked -> currentState.view?.occurrence?.ruleId?.let { sendEffect(OccurrenceEffect.OpenRule(it)) }
            OccurrenceIntent.KeepMine -> resolve(keepMine)
            OccurrenceIntent.UseTheirs, OccurrenceIntent.AcceptDeletion -> resolve(useTheirs)
            OccurrenceIntent.TryAgain -> resolve(tryAgain)
            OccurrenceIntent.Discard -> resolve(discard)
            OccurrenceIntent.Back -> sendEffect(OccurrenceEffect.Close)
        }
    }

    private fun change(change: OccurrenceChange) = setState { reduce(it, change) }

    private fun chooseCategory(id: String) {
        val choices = currentState.categoryChoices ?: return
        val display = (listOfNotNull(choices.current) + choices.own + choices.defaults).firstOrNull { it.id == id } ?: return
        change(OccurrenceChange.Category(display))
    }

    private fun draftOrNull(): ConfirmDraft? {
        val state = currentState
        val amount = TransactionAmount.parse(state.amountText, state.currency)
        if (amount !is TransactionAmount.Result.Valid) {
            val problem = (amount as TransactionAmount.Result.Invalid).problem
            change(OccurrenceChange.Errors(state.errors + (ConfirmField.Amount to problem.asRuleProblem())))
            return null
        }
        return ConfirmDraft(
            date = state.date ?: state.view?.shownDate ?: return null,
            amountMinor = amount.minor,
            categoryId = state.formCategoryId,
            description = state.note,
            memo = state.details,
            counterparty = state.person,
        )
    }

    private fun submit() {
        val state = currentState
        if (!state.canSave) return
        val draft = draftOrNull() ?: return
        val confirming = state.form == OccurrenceForm.Confirm
        change(OccurrenceChange.Saving(true))
        viewModelScope.launch {
            val result = if (confirming) confirm(occurrenceId, draft) else edit(occurrenceId, draft)
            when (result) {
                OccurrenceResult.Done -> {
                    change(OccurrenceChange.FormClosed)
                    sendEffect(OccurrenceEffect.Close)
                }
                OccurrenceResult.NotVerified -> sendEffect(OccurrenceEffect.RequireVerification)
                OccurrenceResult.NotAllowed -> sendEffect(OccurrenceEffect.ShowMessage(OccurrenceMessage.NotAllowed))
                OccurrenceResult.ReadOnly -> sendEffect(OccurrenceEffect.ShowMessage(OccurrenceMessage.ConfirmedFinal))
                OccurrenceResult.NoLongerAvailable -> {
                    sendEffect(OccurrenceEffect.ShowMessage(OccurrenceMessage.Gone))
                    sendEffect(OccurrenceEffect.Close)
                }
                is OccurrenceResult.Invalid -> change(OccurrenceChange.Errors(result.errors))
            }
            change(OccurrenceChange.Saving(false))
        }
    }

    /** Skip and undo: the occurrence stays on screen with its new state. */
    private fun act(action: suspend () -> OccurrenceResult) {
        if (currentState.busy) return
        change(OccurrenceChange.Busy(true))
        viewModelScope.launch {
            when (action()) {
                OccurrenceResult.NotVerified -> sendEffect(OccurrenceEffect.RequireVerification)
                OccurrenceResult.NotAllowed -> sendEffect(OccurrenceEffect.ShowMessage(OccurrenceMessage.NotAllowed))
                OccurrenceResult.ReadOnly -> sendEffect(OccurrenceEffect.ShowMessage(OccurrenceMessage.ConfirmedFinal))
                OccurrenceResult.NoLongerAvailable -> {
                    sendEffect(OccurrenceEffect.ShowMessage(OccurrenceMessage.Gone))
                    sendEffect(OccurrenceEffect.Close)
                }
                OccurrenceResult.Done, is OccurrenceResult.Invalid -> Unit
            }
            change(OccurrenceChange.Busy(false))
        }
    }

    /** A clash or refusal choice, or the cancelling of a confirmation: the person's own act, so a vanished row is not "gone". */
    private fun resolve(action: suspend (String) -> Unit) {
        if (currentState.busy) return
        resolvedByPerson = true
        change(OccurrenceChange.Busy(true))
        viewModelScope.launch {
            action(occurrenceId)
            change(OccurrenceChange.Busy(false))
        }
    }
}
