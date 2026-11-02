package com.fares.saveme.feature.sharedbudgets.ui.editor

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.budgets.domain.BudgetValidation
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.sharedbudgets.domain.ActionResult
import com.fares.saveme.feature.sharedbudgets.domain.BudgetWriteResult
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetDraft
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetField
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetsManager
import com.fares.saveme.feature.sharedbudgets.domain.model.EditorBudget
import com.fares.saveme.feature.sharedbudgets.domain.model.EditorLine
import com.fares.saveme.feature.transactions.domain.TransactionAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

enum class EditorSheet { None, Currency, StartDay, AddCategory, LineLimit }

/** What the person is told after a call that did not go through; the screen words each one. */
enum class EditorMessage { Offline, NotAllowed, PlanFull, Stale, Failed }

data class SharedBudgetEditorState(
    /** Null while creating. */
    val budgetId: String? = null,
    val loaded: Boolean = false,
    val name: String = "",
    val currency: String = "",
    val currencies: List<String> = emptyList(),
    val startDay: Int = 1,
    val limitText: String = "",
    val baseVersion: Long = 0,
    val isOwner: Boolean = true,
    val archived: Boolean = false,
    val lines: List<EditorLine> = emptyList(),
    val categoryChoices: CategoryChoices? = null,
    val sheet: EditorSheet = EditorSheet.None,
    /** The line whose limit is being typed, and what is typed. */
    val lineEditing: EditorLine? = null,
    val lineLimitText: String = "",
    val errors: Set<SharedBudgetField> = emptySet(),
    val lineLimitInvalid: Boolean = false,
    val confirmDelete: Boolean = false,
    val saving: Boolean = false,
    val busy: Boolean = false,
) {
    val isCreate: Boolean get() = budgetId == null

    /** Only the owner changes the budget's name, limit and categories; anyone can open a budget they belong to. */
    val canEdit: Boolean get() = loaded && isOwner && !archived
    val canSave: Boolean get() = canEdit && !saving && !busy && name.isNotBlank() && currency.isNotEmpty()
}

sealed interface SharedBudgetEditorIntent {
    data class NameChanged(val text: String) : SharedBudgetEditorIntent
    data class LimitChanged(val text: String) : SharedBudgetEditorIntent
    data object CurrencyClicked : SharedBudgetEditorIntent
    data class CurrencyChosen(val code: String) : SharedBudgetEditorIntent
    data object StartDayClicked : SharedBudgetEditorIntent
    data class StartDayChosen(val day: Int) : SharedBudgetEditorIntent
    data object SheetDismissed : SharedBudgetEditorIntent
    data object SaveClicked : SharedBudgetEditorIntent
    data object AddCategoryClicked : SharedBudgetEditorIntent
    data class CategoryChosen(val categoryId: String) : SharedBudgetEditorIntent
    data class LineClicked(val lineId: String) : SharedBudgetEditorIntent
    data class LineLimitChanged(val text: String) : SharedBudgetEditorIntent
    data object LineLimitSaved : SharedBudgetEditorIntent
    data class LineRemoved(val lineId: String) : SharedBudgetEditorIntent
    data object ArchiveClicked : SharedBudgetEditorIntent
    data object DeleteClicked : SharedBudgetEditorIntent
    data object DeleteConfirmed : SharedBudgetEditorIntent
    data object DeleteCancelled : SharedBudgetEditorIntent
    data object Back : SharedBudgetEditorIntent
}

sealed interface SharedBudgetEditorEffect {
    /** [budgetId] is the budget just made, so its screen can open; null when it only closes. */
    data class Close(val budgetId: String? = null) : SharedBudgetEditorEffect
    data object RequireVerification : SharedBudgetEditorEffect
    data class ShowMessage(val message: EditorMessage) : SharedBudgetEditorEffect

    /** The budget was deleted or archived here: both screens of it close. */
    data object Gone : SharedBudgetEditorEffect
}

/**
 * Makes or edits a Shared Budget. The budget's own fields are saved through the sync queue and work offline; the category list,
 * archiving and deleting by their owner go straight to the server. A budget the person only belongs to opens read-only.
 */
class SharedBudgetEditorViewModel(
    private val budgetId: String?,
    private val manager: SharedBudgetsManager,
    private val budget: (String) -> Flow<EditorBudget?>,
    private val currencies: Flow<List<String>>,
    private val categoryChoices: Flow<CategoryChoices>,
) : MviViewModel<SharedBudgetEditorState, SharedBudgetEditorIntent, SharedBudgetEditorEffect>(SharedBudgetEditorState(budgetId = budgetId)) {

    init {
        viewModelScope.launch { if (budgetId == null) loadCreate() else loadEdit(budgetId) }
        viewModelScope.launch { categoryChoices.collect { choices -> setState { it.copy(categoryChoices = choices) } } }
    }

    private suspend fun loadCreate() {
        val list = currencies.first()
        setState { it.copy(loaded = true, currencies = list, currency = list.firstOrNull().orEmpty()) }
        currencies.collect { all -> setState { it.copy(currencies = all, currency = it.currency.ifEmpty { all.firstOrNull().orEmpty() }) } }
    }

    private suspend fun loadEdit(id: String) {
        val first = budget(id).first()
        if (first == null) {
            sendEffect(SharedBudgetEditorEffect.Close())
            return
        }
        setState {
            it.copy(
                loaded = true, name = first.name, currency = first.currency, startDay = first.startDay,
                limitText = first.limitMinor?.let { minor -> TransactionAmount.format(minor, first.currency) }.orEmpty(),
                baseVersion = first.version, isOwner = first.isOwner, archived = first.archived, lines = first.lines,
            )
        }
        budget(id).collect { current ->
            if (current == null) {
                sendEffect(SharedBudgetEditorEffect.Gone)
            } else {
                // Typed fields are never overwritten; what other people changed shows up in the lines and the rights.
                setState { it.copy(isOwner = current.isOwner, archived = current.archived, lines = current.lines, baseVersion = maxOf(it.baseVersion, current.version)) }
            }
        }
    }

    override fun onIntent(intent: SharedBudgetEditorIntent) {
        when (intent) {
            is SharedBudgetEditorIntent.NameChanged -> setState { it.copy(name = intent.text, errors = it.errors - SharedBudgetField.Name) }
            is SharedBudgetEditorIntent.LimitChanged -> setState { it.copy(limitText = intent.text, errors = it.errors - SharedBudgetField.Limit) }
            SharedBudgetEditorIntent.CurrencyClicked -> if (currentState.isCreate && currentState.currencies.size > 1) setState { it.copy(sheet = EditorSheet.Currency) }
            is SharedBudgetEditorIntent.CurrencyChosen -> if (currentState.isCreate) setState { it.copy(currency = intent.code, sheet = EditorSheet.None, errors = it.errors - SharedBudgetField.Currency - SharedBudgetField.Limit) }
            SharedBudgetEditorIntent.StartDayClicked -> if (currentState.canEdit) setState { it.copy(sheet = EditorSheet.StartDay) }
            is SharedBudgetEditorIntent.StartDayChosen -> setState { it.copy(startDay = intent.day, sheet = EditorSheet.None) }
            SharedBudgetEditorIntent.SheetDismissed -> setState { it.copy(sheet = EditorSheet.None, lineEditing = null, lineLimitInvalid = false) }
            SharedBudgetEditorIntent.SaveClicked -> save()
            SharedBudgetEditorIntent.AddCategoryClicked -> if (currentState.canEdit && !currentState.isCreate) setState { it.copy(sheet = EditorSheet.AddCategory) }
            is SharedBudgetEditorIntent.CategoryChosen -> addLine(intent.categoryId)
            is SharedBudgetEditorIntent.LineClicked -> currentState.lines.firstOrNull { it.id == intent.lineId }?.takeIf { currentState.canEdit }?.let { line ->
                setState {
                    it.copy(
                        sheet = EditorSheet.LineLimit, lineEditing = line, lineLimitInvalid = false,
                        lineLimitText = line.limitMinor?.let { minor -> TransactionAmount.format(minor, it.currency) }.orEmpty(),
                    )
                }
            }
            is SharedBudgetEditorIntent.LineLimitChanged -> setState { it.copy(lineLimitText = intent.text, lineLimitInvalid = false) }
            SharedBudgetEditorIntent.LineLimitSaved -> saveLineLimit()
            is SharedBudgetEditorIntent.LineRemoved -> removeLine(intent.lineId)
            SharedBudgetEditorIntent.ArchiveClicked -> archive()
            SharedBudgetEditorIntent.DeleteClicked -> if (currentState.canEdit && !currentState.isCreate) setState { it.copy(confirmDelete = true) }
            SharedBudgetEditorIntent.DeleteCancelled -> if (!currentState.busy) setState { it.copy(confirmDelete = false) }
            SharedBudgetEditorIntent.DeleteConfirmed -> delete()
            SharedBudgetEditorIntent.Back -> sendEffect(SharedBudgetEditorEffect.Close())
        }
    }

    /** Empty text means no limit; anything else must be a positive amount in the budget's currency. */
    private fun parseLimit(text: String, currency: String): Result<Long?> {
        if (text.isBlank()) return Result.success(null)
        return when (val parsed = BudgetValidation.parseLimit(text, currency)) {
            is BudgetValidation.Result.Valid -> Result.success(parsed.minor)
            is BudgetValidation.Result.Invalid -> Result.failure(IllegalArgumentException())
        }
    }

    private fun save() {
        val state = currentState
        if (!state.canSave) return
        val limit = parseLimit(state.limitText, state.currency)
        if (limit.isFailure) {
            setState { it.copy(errors = it.errors + SharedBudgetField.Limit) }
            return
        }
        val draft = SharedBudgetDraft(state.name, state.currency, state.startDay, limit.getOrNull())
        setState { it.copy(saving = true) }
        viewModelScope.launch {
            val id = state.budgetId
            val result = if (id == null) manager.create(draft) else manager.save(id, draft, state.baseVersion)
            setState { it.copy(saving = false) }
            when (result) {
                is BudgetWriteResult.Saved -> sendEffect(SharedBudgetEditorEffect.Close(result.id.takeIf { id == null }))
                BudgetWriteResult.NotVerified -> sendEffect(SharedBudgetEditorEffect.RequireVerification)
                BudgetWriteResult.NoLongerAvailable -> sendEffect(SharedBudgetEditorEffect.Gone)
                is BudgetWriteResult.Invalid -> setState { it.copy(errors = result.fields) }
            }
        }
    }

    private fun addLine(categoryId: String) {
        val id = budgetId ?: return
        setState { it.copy(sheet = EditorSheet.None) }
        act { manager.addLine(id, categoryId, null) }
    }

    private fun saveLineLimit() {
        val id = budgetId ?: return
        val line = currentState.lineEditing ?: return
        val limit = parseLimit(currentState.lineLimitText, currentState.currency)
        if (limit.isFailure) {
            setState { it.copy(lineLimitInvalid = true) }
            return
        }
        setState { it.copy(sheet = EditorSheet.None, lineEditing = null) }
        act { manager.setLineLimit(id, line.id, limit.getOrNull()) }
    }

    private fun removeLine(lineId: String) {
        val id = budgetId ?: return
        if (currentState.canEdit) act { manager.removeLine(id, lineId) }
    }

    private fun archive() {
        val id = budgetId ?: return
        if (!currentState.canEdit) return
        act(onDone = { sendEffect(SharedBudgetEditorEffect.Close()) }) { manager.archive(id) }
    }

    private fun delete() {
        val id = budgetId ?: return
        if (currentState.busy) return
        setState { it.copy(busy = true) }
        viewModelScope.launch {
            val result = manager.delete(id)
            setState { it.copy(busy = false, confirmDelete = false) }
            when (result) {
                is BudgetWriteResult.Saved, BudgetWriteResult.NoLongerAvailable -> sendEffect(SharedBudgetEditorEffect.Gone)
                BudgetWriteResult.NotVerified -> sendEffect(SharedBudgetEditorEffect.RequireVerification)
                is BudgetWriteResult.Invalid -> Unit
            }
        }
    }

    /** One call that needs the server: busy while it runs, then either [onDone] or the word for what went wrong. */
    private fun act(onDone: () -> Unit = {}, call: suspend () -> ActionResult) {
        if (currentState.busy) return
        setState { it.copy(busy = true) }
        viewModelScope.launch {
            val result = call()
            setState { it.copy(busy = false) }
            when (result) {
                ActionResult.Done -> onDone()
                ActionResult.NotVerified -> sendEffect(SharedBudgetEditorEffect.RequireVerification)
                ActionResult.Offline -> sendEffect(SharedBudgetEditorEffect.ShowMessage(EditorMessage.Offline))
                ActionResult.NotAllowed -> sendEffect(SharedBudgetEditorEffect.ShowMessage(EditorMessage.NotAllowed))
                ActionResult.PlanFull -> sendEffect(SharedBudgetEditorEffect.ShowMessage(EditorMessage.PlanFull))
                ActionResult.Stale -> sendEffect(SharedBudgetEditorEffect.ShowMessage(EditorMessage.Stale))
                is ActionResult.Failed -> sendEffect(SharedBudgetEditorEffect.ShowMessage(EditorMessage.Failed))
            }
        }
    }
}
