package com.fares.saveme.feature.budgets.ui.editor

import com.fares.saveme.feature.budgets.domain.BudgetValidation
import com.fares.saveme.feature.budgets.domain.model.Budget
import com.fares.saveme.feature.budgets.domain.model.BudgetChoices
import com.fares.saveme.feature.budgets.domain.model.BudgetField
import com.fares.saveme.feature.budgets.domain.model.BudgetFieldProblem
import com.fares.saveme.feature.budgets.domain.model.BudgetView
import com.fares.saveme.feature.budgets.domain.model.RefusalReason
import com.fares.saveme.feature.budgets.domain.model.SyncState
import com.fares.saveme.feature.budgets.ui.BudgetRowUi
import com.fares.saveme.feature.budgets.ui.asProblems
import com.fares.saveme.feature.budgets.ui.toRow

/** Members a clash can differ in. */
enum class ClashField { Limit, Archived }

sealed interface ClashValue {
    data class Limit(val minor: Long, val currency: String) : ClashValue
    data class Archived(val archived: Boolean) : ClashValue
}

data class ClashLine(val field: ClashField, val mine: ClashValue, val theirs: ClashValue)

/** Only the members where this phone's change and the other device's copy differ; category and currency never change. */
fun clashLines(view: BudgetView, theirs: Budget): List<ClashLine> = listOfNotNull(
    ClashLine(ClashField.Limit, ClashValue.Limit(view.limitMinor, view.currency), ClashValue.Limit(theirs.limitMinor, theirs.currency))
        .takeIf { view.limitMinor != theirs.limitMinor },
    ClashLine(ClashField.Archived, ClashValue.Archived(view.archived), ClashValue.Archived(theirs.archivedAt != null))
        .takeIf { view.archived != (theirs.archivedAt != null) },
)

/** What the editor's status area shows. */
sealed interface EditorSync {
    data object None : EditorSync
    data object Waiting : EditorSync

    /** [isDelete]: the phone's change is a deletion, so the choice is "Delete anyway" / "Use the other device's". */
    data class Clash(val lines: List<ClashLine>, val deletedElsewhere: Boolean, val isDelete: Boolean) : EditorSync
    data class Refused(val reason: RefusalReason) : EditorSync
}

/** Which confirmation, if any, is open. */
enum class EditorConfirm { None, Archive, Delete }

enum class EditorMessage { Gone }

data class BudgetEditorState(
    /** Null while creating; the budget's id while editing. */
    val budgetId: String? = null,
    val loaded: Boolean = false,
    /** True once a category or "All expenses" was chosen (create mode). */
    val categoryChosen: Boolean = false,
    val categoryId: String? = null,
    val currency: String = "",
    val limitText: String = "",
    /** The version shown when editing began; the save is sent on top of it (FR-005). */
    val baseVersion: Long = 0,
    /** The limit shown when editing began, in minor units: Save is enabled only when the typed limit differs. */
    val shownLimitMinor: Long? = null,
    val choices: BudgetChoices? = null,
    val categorySheetOpen: Boolean = false,
    val currencySheetOpen: Boolean = false,
    val errors: Map<BudgetField, BudgetFieldProblem> = emptyMap(),
    /** Edit mode: the budget as the list draws it (progress at the top). */
    val row: BudgetRowUi? = null,
    val sync: EditorSync = EditorSync.None,
    val archived: Boolean = false,
    val canArchive: Boolean = false,
    val canDelete: Boolean = false,
    /** A period type this version does not know: nothing is editable. */
    val readOnly: Boolean = false,
    val startDay: Int = 1,
    val confirm: EditorConfirm = EditorConfirm.None,
    val saving: Boolean = false,
    val busy: Boolean = false,
) {
    val isCreate: Boolean get() = budgetId == null

    private val typedLimit: BudgetValidation.Result get() = BudgetValidation.parseLimit(limitText, currency)

    val canSave: Boolean get() {
        if (!loaded || saving || readOnly || currency.isEmpty()) return false
        val typed = typedLimit as? BudgetValidation.Result.Valid ?: return false
        return if (isCreate) categoryChosen else typed.minor != shownLimitMinor
    }

    /** Whether the category is read-only text rather than a chooser. */
    val categoryEditable: Boolean get() = isCreate
}

sealed interface BudgetEditorIntent {
    data object CategoryClicked : BudgetEditorIntent
    data class CategoryChosen(val id: String?) : BudgetEditorIntent
    data object CurrencyClicked : BudgetEditorIntent
    data class CurrencyChosen(val code: String) : BudgetEditorIntent
    data object SheetDismissed : BudgetEditorIntent
    data class LimitChanged(val text: String) : BudgetEditorIntent
    data object SaveClicked : BudgetEditorIntent
    data object ArchiveClicked : BudgetEditorIntent
    data object DeleteClicked : BudgetEditorIntent
    data object ConfirmArchive : BudgetEditorIntent
    data object ConfirmDelete : BudgetEditorIntent
    data object DismissConfirm : BudgetEditorIntent
    data object KeepMine : BudgetEditorIntent
    data object UseTheirs : BudgetEditorIntent
    data object AcceptDeletion : BudgetEditorIntent
    data object TryAgain : BudgetEditorIntent
    data object Discard : BudgetEditorIntent
    data object Back : BudgetEditorIntent
}

sealed interface BudgetEditorEffect {
    data object Saved : BudgetEditorEffect
    data object NavigateBack : BudgetEditorEffect
    data class ShowMessage(val message: EditorMessage) : BudgetEditorEffect
    data object RequireVerification : BudgetEditorEffect
}

sealed interface BudgetEditorChange {
    /** The initial values: the defaults when creating, the budget as shown when editing. */
    data class Loaded(val state: BudgetEditorState) : BudgetEditorChange

    /** The budget's current view: status, progress and the offered actions follow it; typed fields do not. */
    data class Stored(val view: BudgetView) : BudgetEditorChange
    data class Choices(val choices: BudgetChoices) : BudgetEditorChange
    data class StartDay(val day: Int) : BudgetEditorChange
    data class Category(val id: String?) : BudgetEditorChange
    data class Currency(val code: String) : BudgetEditorChange
    data class Limit(val text: String) : BudgetEditorChange
    data class CategorySheet(val open: Boolean) : BudgetEditorChange
    data class CurrencySheet(val open: Boolean) : BudgetEditorChange
    data class Errors(val errors: Map<BudgetField, BudgetFieldProblem>) : BudgetEditorChange
    data class Confirm(val confirm: EditorConfirm) : BudgetEditorChange
    data class Saving(val busy: Boolean) : BudgetEditorChange
    data class Busy(val busy: Boolean) : BudgetEditorChange
}

fun reduce(state: BudgetEditorState, change: BudgetEditorChange): BudgetEditorState = when (change) {
    is BudgetEditorChange.Loaded -> change.state.copy(loaded = true)
    is BudgetEditorChange.Stored -> {
        val refused = (change.view.syncState as? SyncState.Refused)?.fields.orEmpty()
        state.copy(
            row = change.view.toRow(),
            sync = change.view.syncState.toEditorSync(change.view),
            archived = change.view.archived,
            canArchive = !change.view.archived && !change.view.pendingDelete && !change.view.readOnly,
            canDelete = !change.view.pendingDelete,
            readOnly = change.view.readOnly,
            errors = if (refused.isNotEmpty()) refused.asProblems() else state.errors,
        )
    }
    is BudgetEditorChange.Choices -> reduceChoices(state, change.choices)
    is BudgetEditorChange.StartDay -> state.copy(startDay = change.day)
    // Choosing another category (or "All expenses") clears a duplicate error (FR-004).
    is BudgetEditorChange.Category -> if (state.categoryEditable) {
        state.copy(categoryId = change.id, categoryChosen = true, categorySheetOpen = false, errors = state.errors - BudgetField.Category)
    } else {
        state
    }
    // Changing the currency re-reads which categories are already budgeted in it; the limit text is kept (FR-004).
    is BudgetEditorChange.Currency -> if (state.isCreate && change.code != state.currency) {
        state.copy(currency = change.code, currencySheetOpen = false, errors = state.errors - BudgetField.Currency - BudgetField.Limit - BudgetField.Category)
    } else {
        state.copy(currencySheetOpen = false)
    }
    is BudgetEditorChange.Limit -> state.copy(limitText = change.text, errors = state.errors - BudgetField.Limit)
    is BudgetEditorChange.CategorySheet -> state.copy(categorySheetOpen = change.open)
    is BudgetEditorChange.CurrencySheet -> state.copy(currencySheetOpen = change.open)
    is BudgetEditorChange.Errors -> state.copy(errors = change.errors)
    is BudgetEditorChange.Confirm -> state.copy(confirm = change.confirm)
    is BudgetEditorChange.Saving -> state.copy(saving = change.busy)
    is BudgetEditorChange.Busy -> state.copy(busy = change.busy)
}

/**
 * New choices for the currency in [state]: the first ones also pick the profile's default currency (FR-003), and a chosen
 * category already budgeted in the chosen currency is cleared (FR-004). Choices computed for another currency are ignored
 * for the duplicate rule.
 */
private fun reduceChoices(state: BudgetEditorState, choices: BudgetChoices): BudgetEditorState {
    if (!state.isCreate) return state.copy(choices = choices, startDay = choices.startDay)
    val currency = state.currency.ifEmpty { choices.defaultCurrency }
    val current = state.copy(choices = choices, currency = currency, startDay = choices.startDay)
    return if (choices.forCurrency == currency && current.categoryChosen && choices.isAlreadyBudgeted(current.categoryId)) {
        current.copy(categoryChosen = false, categoryId = null)
    } else {
        current
    }
}

internal fun SyncState.toEditorSync(view: BudgetView): EditorSync = when (this) {
    SyncState.None -> EditorSync.None
    SyncState.Waiting -> EditorSync.Waiting
    is SyncState.Clash -> EditorSync.Clash(
        lines = theirs?.takeIf { !view.pendingDelete }?.let { clashLines(view, it) }.orEmpty(),
        deletedElsewhere = deletedElsewhere,
        isDelete = view.pendingDelete,
    )
    is SyncState.Refused -> EditorSync.Refused(reason)
}
