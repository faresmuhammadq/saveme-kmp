package com.fares.saveme.feature.budgets.ui.editor

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.budgets.domain.BudgetValidation
import com.fares.saveme.feature.budgets.domain.model.BudgetChoices
import com.fares.saveme.feature.budgets.domain.model.BudgetDraft
import com.fares.saveme.feature.budgets.domain.model.BudgetField
import com.fares.saveme.feature.budgets.domain.model.BudgetFieldProblem
import com.fares.saveme.feature.budgets.domain.model.BudgetView
import com.fares.saveme.feature.budgets.domain.model.CreateResult
import com.fares.saveme.feature.budgets.domain.model.SaveResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Creates or edits one budget through domain lambdas only (P-16.3); in edit mode it is also the budget's detail: progress,
 * status, clash and refusal choices, archive and delete. Edit mode starts from what the phone shows (a waiting change or
 * the server copy) and remembers that version as the base. Category, currency and period are read-only after creation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetEditorViewModel(
    private val budgetId: String?,
    private val observeBudget: (String) -> Flow<BudgetView?>,
    private val observeChoices: (String, String?) -> Flow<BudgetChoices>,
    private val observeStartDay: Flow<Int> = emptyFlow(),
    private val create: suspend (BudgetDraft) -> CreateResult,
    private val saveLimit: suspend (String, Long, Long) -> SaveResult,
    private val archive: suspend (String) -> SaveResult,
    private val delete: suspend (String) -> SaveResult,
    private val keepMine: suspend (String) -> Unit,
    private val useTheirs: suspend (String) -> Unit,
    private val tryAgain: suspend (String) -> Unit,
    private val discard: suspend (String) -> Unit,
    /** Reads the budget's progress when the server holds it (FR-018); the phone's own figures stand meanwhile. */
    private val refreshOne: suspend (String) -> Unit = {},
) : MviViewModel<BudgetEditorState, BudgetEditorIntent, BudgetEditorEffect>(BudgetEditorState(budgetId = budgetId)) {

    /** True from a confirmed delete or archive on, so the budget vanishing is not reported as "gone". */
    private var leaving = false

    /** True once the person chose a clash or refusal action: if the budget then leaves the phone, that is their choice. */
    private var resolvedByPerson = false

    private val currency = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (budgetId == null) {
            setState { reduce(it, BudgetEditorChange.Loaded(it)) }
            viewModelScope.launch { currency.flatMapLatest { observeChoices(it.orEmpty(), null) }.collect { choices -> applyChoices(choices) } }
            observeStartDay.onEach { day -> setState { reduce(it, BudgetEditorChange.StartDay(day)) } }.launchIn(viewModelScope)
            return
        }
        val first = observeBudget(budgetId).first()
        if (first == null) {
            sendEffect(BudgetEditorEffect.ShowMessage(EditorMessage.Gone))
            sendEffect(BudgetEditorEffect.NavigateBack)
            return
        }
        setState {
            reduce(
                it,
                BudgetEditorChange.Loaded(
                    it.copy(
                        categoryId = first.server.categoryId,
                        categoryChosen = true,
                        currency = first.currency,
                        limitText = BudgetValidation.format(first.limitMinor, first.currency),
                        shownLimitMinor = first.limitMinor,
                        baseVersion = first.server.version,
                    ),
                ),
            )
        }
        observeStartDay.onEach { day -> setState { reduce(it, BudgetEditorChange.StartDay(day)) } }.launchIn(viewModelScope)
        viewModelScope.launch { refreshOne(budgetId) }
        observeBudget(budgetId).collect { view -> onView(view) }
    }

    private fun applyChoices(choices: BudgetChoices) {
        setState { reduce(it, BudgetEditorChange.Choices(choices)) }
        // The first choices carry the profile's default currency; from then on the choices follow the chosen currency.
        if (currency.value == null) currency.value = currentState.currency.ifEmpty { choices.defaultCurrency }
    }

    private fun onView(view: BudgetView?) {
        if (view == null) {
            if (!leaving) {
                leaving = true
                if (!resolvedByPerson) sendEffect(BudgetEditorEffect.ShowMessage(EditorMessage.Gone))
                sendEffect(BudgetEditorEffect.NavigateBack)
            }
            return
        }
        setState { reduce(it, BudgetEditorChange.Stored(view)) }
    }

    override fun onIntent(intent: BudgetEditorIntent) {
        when (intent) {
            BudgetEditorIntent.CategoryClicked -> if (currentState.categoryEditable) setState { reduce(it, BudgetEditorChange.CategorySheet(true)) }
            is BudgetEditorIntent.CategoryChosen -> {
                // A category that already has an active budget in this currency can't be taken (FR-004).
                val blocked = currentState.choices?.takeIf { it.forCurrency == currentState.currency }?.isAlreadyBudgeted(intent.id) == true
                if (!blocked) setState { reduce(it, BudgetEditorChange.Category(intent.id)) }
            }
            BudgetEditorIntent.CurrencyClicked -> if (currentState.isCreate) setState { reduce(it, BudgetEditorChange.CurrencySheet(true)) }
            is BudgetEditorIntent.CurrencyChosen -> {
                setState { reduce(it, BudgetEditorChange.Currency(intent.code)) }
                if (currentState.isCreate) currency.value = intent.code
            }
            BudgetEditorIntent.SheetDismissed -> {
                setState { reduce(it, BudgetEditorChange.CategorySheet(false)) }
                setState { reduce(it, BudgetEditorChange.CurrencySheet(false)) }
            }
            is BudgetEditorIntent.LimitChanged -> setState { reduce(it, BudgetEditorChange.Limit(intent.text)) }
            BudgetEditorIntent.SaveClicked -> submit()
            BudgetEditorIntent.ArchiveClicked -> if (currentState.canArchive) confirm(EditorConfirm.Archive)
            BudgetEditorIntent.DeleteClicked -> if (currentState.canDelete && !currentState.isCreate) confirm(EditorConfirm.Delete)
            BudgetEditorIntent.DismissConfirm -> if (!currentState.busy) confirm(EditorConfirm.None)
            BudgetEditorIntent.ConfirmArchive -> confirmed {
                leaving = true
                val result = archive(it)
                if (result == SaveResult.Saved) sendEffect(BudgetEditorEffect.NavigateBack) else leaving = false
                result
            }
            BudgetEditorIntent.ConfirmDelete -> confirmed {
                leaving = true
                val result = delete(it)
                if (result == SaveResult.Saved) sendEffect(BudgetEditorEffect.NavigateBack) else leaving = false
                result
            }
            BudgetEditorIntent.KeepMine -> resolve(keepMine)
            BudgetEditorIntent.UseTheirs, BudgetEditorIntent.AcceptDeletion -> resolve(useTheirs)
            BudgetEditorIntent.TryAgain -> resolve(tryAgain)
            BudgetEditorIntent.Discard -> resolve(discard)
            BudgetEditorIntent.Back -> sendEffect(BudgetEditorEffect.NavigateBack)
        }
    }

    private fun confirm(confirm: EditorConfirm) = setState { reduce(it, BudgetEditorChange.Confirm(confirm)) }

    private fun submit() {
        val state = currentState
        if (!state.canSave) return
        val limit = when (val parsed = BudgetValidation.parseLimit(state.limitText, state.currency)) {
            is BudgetValidation.Result.Valid -> parsed.minor
            is BudgetValidation.Result.Invalid -> {
                setState { reduce(it, BudgetEditorChange.Errors(mapOf(BudgetField.Limit to parsed.problem))) }
                return
            }
        }
        setState { reduce(it, BudgetEditorChange.Saving(true)) }
        viewModelScope.launch {
            val id = state.budgetId
            if (id == null) {
                when (val result = create(BudgetDraft(state.categoryId, state.currency, limit))) {
                    is CreateResult.Created -> sendEffect(BudgetEditorEffect.Saved)
                    CreateResult.NotVerified -> sendEffect(BudgetEditorEffect.RequireVerification)
                    is CreateResult.Invalid -> setState { reduce(it, BudgetEditorChange.Errors(result.errors)) }
                    CreateResult.AlreadyBudgeted ->
                        setState { reduce(it, BudgetEditorChange.Errors(mapOf(BudgetField.Category to BudgetFieldProblem.AlreadyBudgeted))) }
                }
            } else {
                when (val result = saveLimit(id, limit, state.baseVersion)) {
                    SaveResult.Saved -> sendEffect(BudgetEditorEffect.Saved)
                    SaveResult.NotVerified -> sendEffect(BudgetEditorEffect.RequireVerification)
                    SaveResult.NoLongerAvailable, SaveResult.ReadOnly -> sendEffect(BudgetEditorEffect.NavigateBack)
                    is SaveResult.Invalid -> setState { reduce(it, BudgetEditorChange.Errors(result.errors)) }
                }
            }
            setState { reduce(it, BudgetEditorChange.Saving(false)) }
        }
    }

    private fun confirmed(action: suspend (String) -> SaveResult) {
        val id = budgetId ?: return
        if (currentState.busy) return
        setState { reduce(it, BudgetEditorChange.Busy(true)) }
        viewModelScope.launch {
            val result = action(id)
            setState { reduce(it, BudgetEditorChange.Busy(false)) }
            confirm(EditorConfirm.None)
            if (result == SaveResult.NotVerified) sendEffect(BudgetEditorEffect.RequireVerification)
        }
    }

    private fun resolve(action: suspend (String) -> Unit) {
        val id = budgetId ?: return
        if (currentState.busy) return
        resolvedByPerson = true
        setState { reduce(it, BudgetEditorChange.Busy(true)) }
        viewModelScope.launch {
            action(id)
            setState { reduce(it, BudgetEditorChange.Busy(false)) }
        }
    }
}
