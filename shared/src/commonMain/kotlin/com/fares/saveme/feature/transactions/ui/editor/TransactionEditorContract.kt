package com.fares.saveme.feature.transactions.ui.editor

import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.transactions.domain.BudgetChoice
import com.fares.saveme.feature.transactions.domain.DueItem
import com.fares.saveme.feature.transactions.domain.MakeRecurringDraft
import com.fares.saveme.feature.transactions.domain.TransactionAmount
import com.fares.saveme.feature.transactions.domain.model.AddedBy
import com.fares.saveme.feature.transactions.domain.model.RefusalReason
import com.fares.saveme.feature.transactions.domain.model.Transaction
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.TransactionSyncState
import com.fares.saveme.feature.transactions.domain.model.TransactionView
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.fares.saveme.feature.transactions.ui.asProblems
import kotlinx.datetime.LocalDate

/** The fields a clash can differ in (FR-023). */
enum class ClashField { Amount, Date, Category, Note, Details, Person, Cleared }

sealed interface ClashValue {
    data class Text(val value: String?) : ClashValue
    data class Money(val minor: Long, val currency: String) : ClashValue
    data class Day(val date: LocalDate) : ClashValue

    /** [display] is null for a transaction without a category. */
    data class Category(val display: CategoryDisplay?) : ClashValue
    data class Cleared(val cleared: Boolean) : ClashValue
}

data class ClashLine(val field: ClashField, val mine: ClashValue, val theirs: ClashValue)

/** Only the fields where this phone's change and the other device's copy differ (FR-023). */
fun clashLines(mine: TransactionDraft, theirs: Transaction, categoryOf: (String?) -> CategoryDisplay?): List<ClashLine> {
    val other = theirs.toDraft()
    return listOfNotNull(
        ClashLine(ClashField.Amount, ClashValue.Money(mine.amountMinor, mine.currency), ClashValue.Money(other.amountMinor, other.currency))
            .takeIf { mine.amountMinor != other.amountMinor },
        ClashLine(ClashField.Date, ClashValue.Day(mine.date), ClashValue.Day(other.date)).takeIf { mine.date != other.date },
        ClashLine(ClashField.Category, ClashValue.Category(categoryOf(mine.categoryId)), ClashValue.Category(categoryOf(other.categoryId)))
            .takeIf { mine.categoryId != other.categoryId },
        ClashLine(ClashField.Note, ClashValue.Text(mine.description), ClashValue.Text(other.description)).takeIf { mine.description != other.description },
        ClashLine(ClashField.Details, ClashValue.Text(mine.memo), ClashValue.Text(other.memo)).takeIf { mine.memo != other.memo },
        ClashLine(ClashField.Person, ClashValue.Text(mine.counterparty), ClashValue.Text(other.counterparty)).takeIf { mine.counterparty != other.counterparty },
        ClashLine(ClashField.Cleared, ClashValue.Cleared(mine.cleared), ClashValue.Cleared(other.cleared)).takeIf { mine.cleared != other.cleared },
    )
}

/** What the editor's status area shows (data-model §5). */
sealed interface EditorSync {
    data object None : EditorSync
    data object Waiting : EditorSync

    /** [isDelete]: the phone's change is a deletion, so the choices are "Delete anyway" / "Keep the transaction". */
    data class Clash(val lines: List<ClashLine>, val deletedElsewhere: Boolean, val isDelete: Boolean) : EditorSync
    data class Refused(val reason: RefusalReason) : EditorSync
}

enum class EditorMessage { Gone, NotAllowed }

/** Create, edit (owner or editor) or detail only (viewer, unknown kind). */
enum class EditorMode { Create, Edit, ReadOnly }

data class TransactionEditorState(
    /** Null while creating; the transaction's id while editing. */
    val transactionId: String? = null,
    val loaded: Boolean = false,
    val walletId: String = "",
    val walletChoices: List<WalletChoice> = emptyList(),
    val noWallet: Boolean = false,
    /** The wire value of the kind, so an unknown kind is kept unchanged. */
    val kindWire: String = TransactionKind.Expense.wire,
    val amountText: String = "",
    val currency: String = "",
    val date: LocalDate? = null,
    val datePickerOpen: Boolean = false,
    val categoryId: String? = null,
    val category: CategoryDisplay? = null,
    val categoryChoices: CategoryChoices? = null,
    val categorySheetOpen: Boolean = false,
    /** The Shared Budget this expense counts toward; null = none (backend ADR-0027). */
    val budgetId: String? = null,
    val budgetChoices: List<BudgetChoice> = emptyList(),
    val budgetSheetOpen: Boolean = false,
    /** True when this phone's change to the budget was refused: the person is asked again (FR-budget). */
    val budgetRefused: Boolean = false,
    val destinationId: String? = null,
    val destinationName: String? = null,
    val destinationChoices: List<WalletChoice> = emptyList(),
    val destinationSheetOpen: Boolean = false,
    val walletSheetOpen: Boolean = false,
    val note: String = "",
    val details: String = "",
    val person: String = "",
    val cleared: Boolean = false,
    val moreOpen: Boolean = false,
    val errors: Map<TransactionField, TransactionFieldProblem> = emptyMap(),
    /** The version shown when editing began; the save is sent on top of it (FR-005). */
    val baseVersion: Long = 0,
    val sync: EditorSync = EditorSync.None,
    val addedBy: AddedBy? = null,
    val fromRecurringRule: Boolean = false,
    val canEdit: Boolean = true,
    val canDelete: Boolean = false,
    /** After "the wallet's currency changed" the amount is typed again in the present currency (FR-026). */
    val currencyChanged: Boolean = false,
    /** The amount and date the transaction had when the editor opened: what the delete confirmation names (FR-022). */
    val shownAmountMinor: Long = 0,
    val shownDate: LocalDate? = null,
    val confirmDelete: Boolean = false,
    val saving: Boolean = false,
    val busy: Boolean = false,
    /** Received, unconfirmed occurrences this entry may duplicate: "Confirm … instead" (spec 010 FR-031); create mode only. */
    val matches: List<DueItem> = emptyList(),
    /** The recurring rule a produced transaction came from, and whether it is on this phone (FR-038). */
    val ruleId: String? = null,
    val ruleOnPhone: Boolean = false,
) {
    val isCreate: Boolean get() = transactionId == null
    val kind: TransactionKind get() = TransactionKind.fromWire(kindWire)
    val mode: EditorMode
        get() = when {
            isCreate -> EditorMode.Create
            canEdit -> EditorMode.Edit
            else -> EditorMode.ReadOnly
        }

    /** The kind is chosen only while recording (FR-012). */
    val kindEditable: Boolean get() = isCreate

    /** The wallet is chosen only while recording; a transfer's wallets never change (FR-008, FR-012). */
    val walletEditable: Boolean get() = isCreate

    /** "Make recurring" is offered while recording, to people who can record (spec 010 FR-003). */
    val canMakeRecurring: Boolean
        get() = loaded && isCreate && canEdit && !noWallet && walletId.isNotEmpty() && kind != TransactionKind.Unknown

    /** The budgets that take this wallet's currency; only expenses qualify, and only their author may change it. */
    val budgetsForWallet: List<BudgetChoice> get() = budgetChoices.filter { it.currency == currency }
    val chosenBudget: BudgetChoice? get() = budgetChoices.firstOrNull { it.id == budgetId }
    val budgetEditable: Boolean
        get() = loaded && canEdit && kind == TransactionKind.Expense && (isCreate || addedBy == AddedBy.You)

    /** The picker shows when there is something to pick or something already picked. */
    val budgetVisible: Boolean get() = budgetEditable && (budgetsForWallet.isNotEmpty() || budgetId != null)

    /** The categories on offer: all of them, or the chosen budget's list plus the platform's Other (backend ADR-0027). */
    val visibleCategoryChoices: CategoryChoices? get() = categoryChoices?.let { choices -> chosenBudget?.let { choices.limitedTo(it) } ?: choices }

    val canOpenRule: Boolean get() = !isCreate && ruleId != null && ruleOnPhone

    val destinationsEmpty: Boolean get() = kind == TransactionKind.Transfer && destinationChoices.isEmpty() && destinationId == null
    val categoriesEmpty: Boolean get() = visibleCategoryChoices?.isEmpty == true && categoryId == null

    val canSave: Boolean
        get() = loaded && canEdit && !saving && !busy && !noWallet &&
            when (kind) {
                TransactionKind.Transfer -> !destinationsEmpty
                TransactionKind.Unknown -> false
                else -> !categoriesEmpty
            }
}

sealed interface TransactionEditorIntent {
    data class WalletChosen(val id: String) : TransactionEditorIntent
    data object WalletClicked : TransactionEditorIntent
    data object WalletSheetDismissed : TransactionEditorIntent
    data class KindChosen(val kind: TransactionKind) : TransactionEditorIntent
    data class AmountChanged(val text: String) : TransactionEditorIntent
    data object DateClicked : TransactionEditorIntent
    data class DateChosen(val date: LocalDate) : TransactionEditorIntent
    data object DatePickerDismissed : TransactionEditorIntent
    data object CategoryClicked : TransactionEditorIntent
    data class CategoryChosen(val id: String) : TransactionEditorIntent
    data object SheetDismissed : TransactionEditorIntent
    data object BudgetClicked : TransactionEditorIntent
    /** A null id takes the expense out of any budget. */
    data class BudgetChosen(val id: String?) : TransactionEditorIntent
    data object DestinationClicked : TransactionEditorIntent
    data class DestinationChosen(val id: String) : TransactionEditorIntent
    data class NoteChanged(val text: String) : TransactionEditorIntent
    data class DetailsChanged(val text: String) : TransactionEditorIntent
    data class PersonChanged(val text: String) : TransactionEditorIntent
    data class ClearedToggled(val cleared: Boolean) : TransactionEditorIntent
    data object MoreToggled : TransactionEditorIntent
    data object SaveClicked : TransactionEditorIntent
    data object DeleteClicked : TransactionEditorIntent
    data object DeleteConfirmed : TransactionEditorIntent
    data object DeleteCancelled : TransactionEditorIntent
    data object KeepMine : TransactionEditorIntent
    data object UseTheirs : TransactionEditorIntent
    data object RestoreWithMine : TransactionEditorIntent
    data object AcceptDeletion : TransactionEditorIntent
    data object DeleteAnyway : TransactionEditorIntent
    data object KeepTransaction : TransactionEditorIntent
    data object TryAgain : TransactionEditorIntent
    data object Discard : TransactionEditorIntent
    data object CreateWalletClicked : TransactionEditorIntent
    data object MakeRecurringClicked : TransactionEditorIntent
    data class MatchClicked(val occurrenceId: String) : TransactionEditorIntent
    data object OpenRuleClicked : TransactionEditorIntent
    data object Back : TransactionEditorIntent
}

sealed interface TransactionEditorEffect {
    data object Close : TransactionEditorEffect
    data class ShowMessage(val message: EditorMessage) : TransactionEditorEffect
    data object RequireVerification : TransactionEditorEffect
    data object OpenWalletEditor : TransactionEditorEffect

    /** Replaces the editor with the recurring rule editor, prefilled from what was typed (spec 010 FR-003). */
    data class MakeRecurring(val draft: MakeRecurringDraft) : TransactionEditorEffect

    /** Replaces the editor with the occurrence it confirmed instead (FR-031). */
    data class OpenOccurrence(val id: String) : TransactionEditorEffect
    data class OpenRule(val id: String) : TransactionEditorEffect
}

sealed interface TransactionEditorChange {
    /** The initial values: the preselection when creating, the transaction as shown when editing. */
    data class Loaded(val state: TransactionEditorState) : TransactionEditorChange

    /** The transaction's current view: status, rights and captions follow it; typed fields do not. */
    data class Stored(val view: TransactionView, val lines: List<ClashLine>) : TransactionEditorChange
    data class Wallets(val choices: List<WalletChoice>) : TransactionEditorChange
    data class Destinations(val choices: List<WalletChoice>) : TransactionEditorChange
    data class Choices(val choices: CategoryChoices) : TransactionEditorChange
    data class Budgets(val choices: List<BudgetChoice>) : TransactionEditorChange
    data class BudgetSheet(val open: Boolean) : TransactionEditorChange
    data class Budget(val id: String?) : TransactionEditorChange
    data class Wallet(val choice: WalletChoice) : TransactionEditorChange
    data class Kind(val kind: TransactionKind) : TransactionEditorChange
    data class Amount(val text: String) : TransactionEditorChange
    data class DatePicker(val open: Boolean) : TransactionEditorChange
    data class Date(val date: LocalDate) : TransactionEditorChange
    data class CategorySheet(val open: Boolean) : TransactionEditorChange
    data class Category(val display: CategoryDisplay) : TransactionEditorChange
    data class DestinationSheet(val open: Boolean) : TransactionEditorChange
    data class WalletSheet(val open: Boolean) : TransactionEditorChange
    data class Destination(val choice: WalletChoice) : TransactionEditorChange
    data class Note(val text: String) : TransactionEditorChange
    data class Details(val text: String) : TransactionEditorChange
    data class Person(val text: String) : TransactionEditorChange
    data class Cleared(val cleared: Boolean) : TransactionEditorChange
    data object More : TransactionEditorChange
    data class Errors(val errors: Map<TransactionField, TransactionFieldProblem>) : TransactionEditorChange
    data class ConfirmDelete(val open: Boolean) : TransactionEditorChange
    data class Saving(val busy: Boolean) : TransactionEditorChange
    data class Busy(val busy: Boolean) : TransactionEditorChange
    data class NoWallet(val none: Boolean) : TransactionEditorChange
    data class Matches(val items: List<DueItem>) : TransactionEditorChange
    data class RuleOnPhone(val onPhone: Boolean) : TransactionEditorChange
}

const val MAX_NOTE = 100
const val MAX_DETAILS = 1000
const val MAX_PERSON = 100

fun reduce(state: TransactionEditorState, change: TransactionEditorChange): TransactionEditorState = when (change) {
    is TransactionEditorChange.Loaded -> change.state.copy(loaded = true)
    is TransactionEditorChange.Stored -> {
        val view = change.view
        val refused = (view.syncState as? TransactionSyncState.Refused)?.fields.orEmpty()
        state.copy(
            sync = view.syncState.toEditorSync(view, change.lines),
            canEdit = view.canEdit,
            canDelete = view.canDelete && !view.pendingDelete,
            addedBy = view.addedBy,
            fromRecurringRule = view.fromRecurringRule,
            errors = if (refused.isNotEmpty()) refused.asProblems() else state.errors,
        )
    }
    is TransactionEditorChange.Wallets -> state.copy(walletChoices = change.choices, noWallet = state.isCreate && state.loaded && change.choices.isEmpty())
    is TransactionEditorChange.Destinations -> state.copy(
        destinationChoices = change.choices,
        // A destination that is no longer offered is dropped (it can only be chosen from the list, FR-010).
        destinationId = if (state.isCreate && state.destinationId != null && change.choices.none { it.id == state.destinationId }) null else state.destinationId,
    )
    is TransactionEditorChange.Choices -> state.copy(categoryChoices = change.choices)
    is TransactionEditorChange.Budgets -> state.copy(budgetChoices = change.choices)
    is TransactionEditorChange.BudgetSheet -> state.copy(budgetSheetOpen = change.open && state.budgetEditable)
    is TransactionEditorChange.Budget ->
        if (state.budgetEditable) state.withBudget(change.id) else state
    is TransactionEditorChange.Wallet ->
        if (state.walletEditable) {
            // Switching keeps the amount, re-checks it against the new wallet's currency and clears the destination (FR-008).
            state.copy(
                walletId = change.choice.id, currency = change.choice.currency, walletSheetOpen = false,
                destinationId = null, destinationName = null, errors = state.errors - TransactionField.Destination,
            ).withAmountProblem().withBudgetKeptOnlyIfFits()
        } else {
            state
        }
    is TransactionEditorChange.Kind ->
        if (state.kindEditable && change.kind != TransactionKind.Unknown) {
            // What no longer applies is cleared: a category of the other kind is never kept (FR-002).
            state.copy(
                kindWire = change.kind.wire,
                categoryId = if (change.kind == state.kind) state.categoryId else null,
                category = if (change.kind == state.kind) state.category else null,
                destinationId = if (change.kind == TransactionKind.Transfer) state.destinationId else null,
                destinationName = if (change.kind == TransactionKind.Transfer) state.destinationName else null,
                categoryChoices = if (change.kind == state.kind) state.categoryChoices else null,
                budgetId = if (change.kind == TransactionKind.Expense) state.budgetId else null,
                errors = state.errors - TransactionField.Category - TransactionField.Destination,
            )
        } else {
            state
        }
    is TransactionEditorChange.Amount -> state.copy(amountText = change.text, currencyChanged = false).withAmountProblem()
    is TransactionEditorChange.DatePicker -> state.copy(datePickerOpen = change.open)
    is TransactionEditorChange.Date -> state.copy(date = change.date, datePickerOpen = false, errors = state.errors - TransactionField.Date)
    is TransactionEditorChange.CategorySheet -> state.copy(categorySheetOpen = change.open)
    is TransactionEditorChange.Category -> state.copy(
        categoryId = change.display.id, category = change.display, categorySheetOpen = false, errors = state.errors - TransactionField.Category,
    )
    is TransactionEditorChange.DestinationSheet -> state.copy(destinationSheetOpen = change.open)
    is TransactionEditorChange.WalletSheet -> state.copy(walletSheetOpen = change.open)
    is TransactionEditorChange.Destination -> state.copy(
        destinationId = change.choice.id, destinationName = change.choice.name, destinationSheetOpen = false,
        errors = state.errors - TransactionField.Destination,
    )
    is TransactionEditorChange.Note -> state.copy(note = change.text, errors = state.errors.withLengthProblem(TransactionField.Note, change.text, MAX_NOTE))
    is TransactionEditorChange.Details -> state.copy(details = change.text, errors = state.errors.withLengthProblem(TransactionField.Details, change.text, MAX_DETAILS))
    is TransactionEditorChange.Person -> state.copy(person = change.text, errors = state.errors.withLengthProblem(TransactionField.Person, change.text, MAX_PERSON))
    is TransactionEditorChange.Cleared -> state.copy(cleared = change.cleared)
    TransactionEditorChange.More -> state.copy(moreOpen = !state.moreOpen)
    is TransactionEditorChange.Errors -> state.copy(errors = change.errors)
    is TransactionEditorChange.ConfirmDelete -> state.copy(confirmDelete = change.open)
    is TransactionEditorChange.Saving -> state.copy(saving = change.busy)
    is TransactionEditorChange.Busy -> state.copy(busy = change.busy)
    is TransactionEditorChange.NoWallet -> state.copy(noWallet = change.none)
    is TransactionEditorChange.Matches -> state.copy(matches = if (state.isCreate) change.items.take(3) else emptyList())
    is TransactionEditorChange.RuleOnPhone -> state.copy(ruleOnPhone = change.onPhone)
}

internal fun TransactionSyncState.toEditorSync(view: TransactionView, lines: List<ClashLine>): EditorSync = when (this) {
    TransactionSyncState.None -> EditorSync.None
    TransactionSyncState.Waiting -> EditorSync.Waiting
    is TransactionSyncState.Clash -> EditorSync.Clash(
        lines = if (view.pendingDelete) emptyList() else lines,
        deletedElsewhere = deletedElsewhere,
        isDelete = view.pendingDelete,
    )
    is TransactionSyncState.Refused -> EditorSync.Refused(reason)
}

/** Text length is counted in code points after trimming, as the server counts it (FR-002). */
fun codePoints(text: String): Int = text.count { !it.isLowSurrogate() }

private fun Map<TransactionField, TransactionFieldProblem>.withLengthProblem(
    field: TransactionField,
    text: String,
    max: Int,
): Map<TransactionField, TransactionFieldProblem> =
    if (codePoints(text.trim()) > max) this + (field to TransactionFieldProblem.TooLong(max)) else this - field

/** Re-checks what is typed whenever the text or the currency changes; empty text shows no error yet. */
private fun TransactionEditorState.withAmountProblem(): TransactionEditorState {
    if (amountText.isBlank()) return copy(errors = errors - TransactionField.Amount)
    return when (val result = TransactionAmount.parse(amountText, currency)) {
        is TransactionAmount.Result.Valid -> copy(errors = errors - TransactionField.Amount)
        is TransactionAmount.Result.Invalid -> copy(errors = errors + (TransactionField.Amount to result.problem))
    }
}

/** The platform's "Other" for expenses: always on offer, so a budget never leaves an expense without a category. */
const val OTHER_EXPENSE_KEY = "other_expense"

/**
 * Only the [budget]'s categories and the platform's Other (backend ADR-0027). The budget's list may name a partner's category;
 * one that is also the person's own keeps the person's own display.
 */
fun CategoryChoices.limitedTo(budget: BudgetChoice): CategoryChoices {
    val allowed = budget.categories.map { it.id }.toSet()
    val ownKept = own.filter { it.id in allowed }
    val partners = budget.categories.filter { line -> own.none { it.id == line.id } && defaults.none { it.id == line.id } }
    val defaultsKept = defaults.filter { it.id in allowed || it.systemKey == OTHER_EXPENSE_KEY }
    return CategoryChoices(
        current = current?.takeIf { it.id in allowed || it.systemKey == OTHER_EXPENSE_KEY },
        own = (ownKept + partners).sortedBy { it.name.lowercase() },
        defaults = defaultsKept,
    )
}

/** Choosing a budget limits the categories; a category that no longer fits is dropped so the person picks again. */
private fun TransactionEditorState.withBudget(id: String?): TransactionEditorState {
    val budget = budgetChoices.firstOrNull { it.id == id }
    val kept = category?.takeIf { shown ->
        when {
            budget != null -> budget.categories.any { it.id == shown.id } || shown.systemKey == OTHER_EXPENSE_KEY
            // Out of any budget a partner's category is not the person's to use.
            else -> shown.source != CategorySource.Partner
        }
    }
    return copy(
        budgetId = budget?.id, budgetSheetOpen = false, budgetRefused = false,
        categoryId = kept?.id ?: if (category == null) categoryId else null, category = kept,
        errors = errors - TransactionField.Budget - TransactionField.Category,
    )
}

/** A budget in another currency than the wallet's is dropped when the wallet changes. */
private fun TransactionEditorState.withBudgetKeptOnlyIfFits(): TransactionEditorState =
    if (budgetId != null && budgetChoices.none { it.id == budgetId && it.currency == currency }) withBudget(null) else this
