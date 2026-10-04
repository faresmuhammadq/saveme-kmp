package com.fares.saveme.feature.recurring.ui.occurrence

import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.recurring.domain.model.ConfirmField
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.OccurrenceStatus
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.transactions.domain.TransactionAmount
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import kotlinx.datetime.LocalDate

/** Which form is open in place: confirming (date, amount, category, texts) or editing this one. */
enum class OccurrenceForm { None, Confirm, Edit }

enum class OccurrenceMessage { Gone, NotAllowed, ConfirmedFinal }

/** What the screen needs besides the occurrence itself, so the reducer stays pure. */
data class OccurrenceDetails(
    val category: CategoryDisplay?,
    val destinationName: String?,
)

data class OccurrenceScreenState(
    val occurrenceId: String = "",
    val loaded: Boolean = false,
    val view: OccurrenceView? = null,
    val category: CategoryDisplay? = null,
    val destinationName: String? = null,
    /** The "already confirmed" notice as it was when the screen opened (it is cleared on open, FR-025). */
    val notice: String? = null,
    val form: OccurrenceForm = OccurrenceForm.None,
    val amountText: String = "",
    val date: LocalDate? = null,
    val datePickerOpen: Boolean = false,
    val formCategoryId: String? = null,
    val formCategory: CategoryDisplay? = null,
    val categoryChoices: CategoryChoices? = null,
    val categorySheetOpen: Boolean = false,
    val note: String = "",
    val details: String = "",
    val person: String = "",
    val errors: Map<ConfirmField, RuleFieldProblem> = emptyMap(),
    val saving: Boolean = false,
    val busy: Boolean = false,
) {
    val kind: TransactionKind get() = view?.occurrence?.kind ?: TransactionKind.Unknown
    val currency: String get() = view?.currency.orEmpty()
    private val syncState: RecurringSyncState get() = view?.syncState ?: RecurringSyncState.None

    /** Only an owner or editor, and never once the server holds it as confirmed (FR-026). */
    private val writable: Boolean get() = view?.canWrite == true && kind != TransactionKind.Unknown

    private val scheduled: Boolean
        get() = view?.state.let { it == OccurrenceState.Upcoming || it == OccurrenceState.Due || it == OccurrenceState.Missed }

    private val blockedBySync: Boolean get() = syncState is RecurringSyncState.Clash || syncState is RecurringSyncState.Refused

    /** The server holds it confirmed: nothing changes any more. */
    val serverConfirmed: Boolean get() = view?.occurrence?.status == OccurrenceStatus.Confirmed
    val confirmationWaiting: Boolean get() = view?.confirmationWaiting == true
    val isUnknown: Boolean get() = view?.state == OccurrenceState.Unknown
    val skipped: Boolean get() = view?.state == OccurrenceState.Skipped

    val canConfirm: Boolean get() = writable && !serverConfirmed && !confirmationWaiting && !isUnknown && !blockedBySync && (scheduled || skipped)
    val canChangeConfirmation: Boolean get() = writable && confirmationWaiting && !blockedBySync
    val canSkip: Boolean get() = writable && !serverConfirmed && !confirmationWaiting && !isUnknown && !blockedBySync && scheduled
    val canUnskip: Boolean get() = writable && !serverConfirmed && !confirmationWaiting && !isUnknown && !blockedBySync && skipped
    val canEdit: Boolean get() = writable && !serverConfirmed && !confirmationWaiting && !isUnknown && !blockedBySync && scheduled
    val canOpenTransaction: Boolean get() = serverConfirmed && view?.occurrence?.transactionId != null
    val formOpen: Boolean get() = form != OccurrenceForm.None
    val categoriesEmpty: Boolean get() = categoryChoices?.isEmpty == true && formCategoryId == null
    val needsCategory: Boolean get() = kind == TransactionKind.Income || kind == TransactionKind.Expense

    val canSave: Boolean get() = formOpen && !saving && !busy && !(needsCategory && categoriesEmpty)
}

sealed interface OccurrenceIntent {
    data object ConfirmClicked : OccurrenceIntent
    data object EditClicked : OccurrenceIntent
    data object FormCancelled : OccurrenceIntent
    data class AmountChanged(val text: String) : OccurrenceIntent
    data object DateClicked : OccurrenceIntent
    data class DateChosen(val date: LocalDate) : OccurrenceIntent
    data object DatePickerDismissed : OccurrenceIntent
    data object CategoryClicked : OccurrenceIntent
    data class CategoryChosen(val id: String) : OccurrenceIntent
    data object SheetDismissed : OccurrenceIntent
    data class NoteChanged(val text: String) : OccurrenceIntent
    data class DetailsChanged(val text: String) : OccurrenceIntent
    data class PersonChanged(val text: String) : OccurrenceIntent
    data object SaveClicked : OccurrenceIntent
    data object SkipClicked : OccurrenceIntent
    data object UnskipClicked : OccurrenceIntent
    data object CancelConfirmationClicked : OccurrenceIntent
    data object OpenTransactionClicked : OccurrenceIntent
    data object OpenRuleClicked : OccurrenceIntent
    data object KeepMine : OccurrenceIntent
    data object UseTheirs : OccurrenceIntent
    data object AcceptDeletion : OccurrenceIntent
    data object TryAgain : OccurrenceIntent
    data object Discard : OccurrenceIntent
    data object Back : OccurrenceIntent
}

sealed interface OccurrenceEffect {
    data object Close : OccurrenceEffect
    data class OpenTransaction(val transactionId: String, val walletId: String) : OccurrenceEffect
    data class OpenRule(val ruleId: String) : OccurrenceEffect
    data class ShowMessage(val message: OccurrenceMessage) : OccurrenceEffect
    data object RequireVerification : OccurrenceEffect
}

sealed interface OccurrenceChange {
    /** The first view: also what the notice shows. */
    data class Loaded(val view: OccurrenceView, val details: OccurrenceDetails) : OccurrenceChange

    /** Later views: status and rights follow; typed fields do not. */
    data class Stored(val view: OccurrenceView, val details: OccurrenceDetails) : OccurrenceChange
    data class FormOpened(val form: OccurrenceForm) : OccurrenceChange
    data object FormClosed : OccurrenceChange
    data class Amount(val text: String) : OccurrenceChange
    data class DatePicker(val open: Boolean) : OccurrenceChange
    data class Date(val date: LocalDate) : OccurrenceChange
    data class CategorySheet(val open: Boolean) : OccurrenceChange
    data class Category(val display: CategoryDisplay) : OccurrenceChange
    data class Choices(val choices: CategoryChoices) : OccurrenceChange
    data class Note(val text: String) : OccurrenceChange
    data class Details(val text: String) : OccurrenceChange
    data class Person(val text: String) : OccurrenceChange
    data class Errors(val errors: Map<ConfirmField, RuleFieldProblem>) : OccurrenceChange
    data class Saving(val saving: Boolean) : OccurrenceChange
    data class Busy(val busy: Boolean) : OccurrenceChange
}

const val MAX_NOTE = 100
const val MAX_DETAILS = 1000
const val MAX_PERSON = 100

fun reduce(state: OccurrenceScreenState, change: OccurrenceChange): OccurrenceScreenState = when (change) {
    is OccurrenceChange.Loaded -> state.copy(
        loaded = true,
        view = change.view,
        category = change.details.category,
        destinationName = change.details.destinationName,
        notice = change.view.notice,
    )
    is OccurrenceChange.Stored -> state.copy(view = change.view, category = change.details.category, destinationName = change.details.destinationName)
    is OccurrenceChange.FormOpened -> state.fromView(change.form)
    OccurrenceChange.FormClosed -> state.copy(form = OccurrenceForm.None, errors = emptyMap(), datePickerOpen = false, categorySheetOpen = false)
    is OccurrenceChange.Amount -> state.copy(amountText = change.text, errors = state.errors - ConfirmField.Amount)
    is OccurrenceChange.DatePicker -> state.copy(datePickerOpen = change.open)
    is OccurrenceChange.Date -> state.copy(date = change.date, datePickerOpen = false, errors = state.errors - ConfirmField.Date)
    is OccurrenceChange.CategorySheet -> state.copy(categorySheetOpen = change.open)
    is OccurrenceChange.Category -> state.copy(
        formCategoryId = change.display.id, formCategory = change.display, categorySheetOpen = false, errors = state.errors - ConfirmField.Category,
    )
    is OccurrenceChange.Choices -> state.copy(categoryChoices = change.choices)
    is OccurrenceChange.Note -> state.copy(note = change.text, errors = state.errors.withLength(ConfirmField.Note, change.text, MAX_NOTE))
    is OccurrenceChange.Details -> state.copy(details = change.text, errors = state.errors.withLength(ConfirmField.Details, change.text, MAX_DETAILS))
    is OccurrenceChange.Person -> state.copy(person = change.text, errors = state.errors.withLength(ConfirmField.Person, change.text, MAX_PERSON))
    is OccurrenceChange.Errors -> state.copy(errors = change.errors)
    is OccurrenceChange.Saving -> state.copy(saving = change.saving)
    is OccurrenceChange.Busy -> state.copy(busy = change.busy)
}

/** Opens a form on the values the occurrence shows now (a waiting change's, else the server's). */
private fun OccurrenceScreenState.fromView(form: OccurrenceForm): OccurrenceScreenState {
    val view = view ?: return this
    return copy(
        form = form,
        amountText = TransactionAmount.format(view.shownAmountMinor, view.currency),
        date = view.shownDate,
        formCategoryId = view.shownCategoryId,
        formCategory = category,
        note = view.shownDescription.orEmpty(),
        details = view.shownMemo.orEmpty(),
        person = view.shownCounterparty.orEmpty(),
        errors = emptyMap(),
        datePickerOpen = false,
        categorySheetOpen = false,
    )
}

/** Text length is counted in code points after trimming, as the server counts it. */
private fun Map<ConfirmField, RuleFieldProblem>.withLength(field: ConfirmField, text: String, max: Int): Map<ConfirmField, RuleFieldProblem> =
    if (text.trim().count { !it.isLowSurrogate() } > max) this + (field to RuleFieldProblem.TooLong(max)) else this - field
