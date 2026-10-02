package com.fares.saveme.feature.transactions.ui.editor

import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
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

    val destinationsEmpty: Boolean get() = kind == TransactionKind.Transfer && destinationChoices.isEmpty() && destinationId == null
    val categoriesEmpty: Boolean get() = categoryChoices?.isEmpty == true && categoryId == null

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
    data object Back : TransactionEditorIntent
}

sealed interface TransactionEditorEffect {
    data object Close : TransactionEditorEffect
    data class ShowMessage(val message: EditorMessage) : TransactionEditorEffect
    data object RequireVerification : TransactionEditorEffect
    data object OpenWalletEditor : TransactionEditorEffect
}

sealed interface TransactionEditorChange {
    /** The initial values: the preselection when creating, the transaction as shown when editing. */
    data class Loaded(val state: TransactionEditorState) : TransactionEditorChange

    /** The transaction's current view: status, rights and captions follow it; typed fields do not. */
    data class Stored(val view: TransactionView, val lines: List<ClashLine>) : TransactionEditorChange
    data class Wallets(val choices: List<WalletChoice>) : TransactionEditorChange
    data class Destinations(val choices: List<WalletChoice>) : TransactionEditorChange
    data class Choices(val choices: CategoryChoices) : TransactionEditorChange
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
    is TransactionEditorChange.Wallet ->
        if (state.walletEditable) {
            // Switching keeps the amount, re-checks it against the new wallet's currency and clears the destination (FR-008).
            state.copy(
                walletId = change.choice.id, currency = change.choice.currency, walletSheetOpen = false,
                destinationId = null, destinationName = null, errors = state.errors - TransactionField.Destination,
            ).withAmountProblem()
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
