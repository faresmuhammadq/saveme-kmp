package com.fares.saveme.feature.recurring.ui.editor

import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.transactions.domain.BudgetChoice
import com.fares.saveme.feature.transactions.ui.editor.OTHER_EXPENSE_KEY
import com.fares.saveme.feature.transactions.ui.editor.limitedTo
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.FrozenMembers
import com.fares.saveme.feature.recurring.domain.model.Frequency
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.RuleDraft
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.domain.model.RulePrefill
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import com.fares.saveme.feature.recurring.domain.model.RuleView
import com.fares.saveme.feature.recurring.domain.model.SchedulePreview
import com.fares.saveme.feature.recurring.ui.asRuleProblem
import com.fares.saveme.feature.transactions.domain.TransactionAmount
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import kotlinx.datetime.LocalDate

/** Which rule to edit, or where to create one; one object so Koin never confuses nulls. */
data class RuleEditorArgs(val walletId: String?, val ruleId: String?, val fromDate: Boolean, val prefill: RulePrefill?)

enum class EndMode { Never, OnDate, After }

/** Create, edit a rule, edit "from a date" (a split), or look only (viewer, ended rule, values the app doesn't know). */
enum class RuleEditorMode { Create, Edit, FromDate, ReadOnly }

enum class ReadOnlyReason { Unknown, Ended, Viewer }

enum class DateTarget { Start, End, From }

enum class EditorMessage { Gone, NotAllowed, ReadOnly }

const val MAX_NOTE = 100
const val MAX_DETAILS = 1000
const val MAX_PERSON = 100

data class RuleEditorState(
    /** Null while creating; the rule's id while editing. */
    val ruleId: String? = null,
    val fromDateMode: Boolean = false,
    val loaded: Boolean = false,
    val walletId: String = "",
    val walletChoices: List<WalletChoice> = emptyList(),
    val walletSheetOpen: Boolean = false,
    val noWallet: Boolean = false,
    val preset: Preset = Preset.Custom,
    val kind: TransactionKind = TransactionKind.Expense,
    val amountText: String = "",
    val currency: String = "",
    val basis: AmountBasis = AmountBasis.PerOccurrence,
    val categoryId: String? = null,
    val category: CategoryDisplay? = null,
    val categoryChoices: CategoryChoices? = null,
    val categorySheetOpen: Boolean = false,
    /** The Shared Budget every expense of the rule counts toward; null for none (backend ADR-0027). */
    val budgetId: String? = null,
    val budgetChoices: List<BudgetChoice> = emptyList(),
    val budgetSheetOpen: Boolean = false,
    /** True when the server refused the budget: it is cleared and the person is asked again. */
    val budgetRefused: Boolean = false,
    val payoutCategoryId: String? = null,
    val payoutCategory: CategoryDisplay? = null,
    val payoutChoices: CategoryChoices? = null,
    val payoutSheetOpen: Boolean = false,
    val destinationId: String? = null,
    val destinationName: String? = null,
    val destinationChoices: List<WalletChoice> = emptyList(),
    val destinationSheetOpen: Boolean = false,
    /** Rounds of a gam'eya or installment, or "after N times" of a custom rule. */
    val countText: String = "",
    val payoutRoundText: String = "",
    val frequency: Frequency = Frequency.Monthly,
    val frequencySheetOpen: Boolean = false,
    val intervalText: String = "1",
    val dayText: String = "",
    /** True once the person typed a day: until then it follows the start date. */
    val dayTouched: Boolean = false,
    val start: LocalDate? = null,
    val endMode: EndMode = EndMode.Never,
    val endDate: LocalDate? = null,
    val from: LocalDate? = null,
    val datePicker: DateTarget? = null,
    val note: String = "",
    val details: String = "",
    val person: String = "",
    val autoConfirm: Boolean = false,
    val preview: SchedulePreview = SchedulePreview.Empty,
    val errors: Map<RuleField, RuleFieldProblem> = emptyMap(),
    val frozen: FrozenMembers = FrozenMembers(),
    /** The server version shown when editing began; the save is sent on top of it (FR-012). */
    val baseVersion: Long = 0,
    val readOnly: ReadOnlyReason? = null,
    val unsentRule: Boolean = false,
    val canSplit: Boolean = false,
    val saving: Boolean = false,
    val busy: Boolean = false,
) {
    val isCreate: Boolean get() = ruleId == null
    val mode: RuleEditorMode
        get() = when {
            ruleId == null -> RuleEditorMode.Create
            readOnly != null -> RuleEditorMode.ReadOnly
            fromDateMode -> RuleEditorMode.FromDate
            else -> RuleEditorMode.Edit
        }
    val canEdit: Boolean get() = mode == RuleEditorMode.Create || mode == RuleEditorMode.Edit || mode == RuleEditorMode.FromDate
    private val scheduleLocked: Boolean get() = frozen.schedule && mode == RuleEditorMode.Edit
    private val amountLocked: Boolean get() = frozen.amountAndCount && mode == RuleEditorMode.Edit

    val presetEditable: Boolean get() = isCreate
    val kindEditable: Boolean get() = isCreate && preset != Preset.Gameya
    val walletEditable: Boolean get() = isCreate
    val destinationEditable: Boolean get() = isCreate
    val scheduleEditable: Boolean get() = canEdit && !scheduleLocked
    val amountEditable: Boolean get() = canEdit && !amountLocked
    val countEditable: Boolean get() = canEdit && !amountLocked
    val basisEditable: Boolean get() = canEdit && !scheduleLocked && (isCreate || mode == RuleEditorMode.FromDate)
    val anyFrozen: Boolean get() = mode == RuleEditorMode.Edit && (frozen.schedule || frozen.amountAndCount)

    /** The kinds offered while creating: a gam'eya is an expense, an installment an expense or a transfer. */
    val kindOptions: List<TransactionKind>
        get() = when (preset) {
            Preset.Installment -> listOf(TransactionKind.Expense, TransactionKind.Transfer)
            Preset.Gameya -> listOf(TransactionKind.Expense)
            else -> TransactionKind.selectable
        }

    val presetShaped: Boolean get() = preset == Preset.Gameya || preset == Preset.Installment
    val isTransfer: Boolean get() = kind == TransactionKind.Transfer
    val showsDay: Boolean get() = !presetShaped && (frequency == Frequency.Monthly || frequency == Frequency.Yearly)
    val destinationsEmpty: Boolean get() = isTransfer && destinationChoices.isEmpty() && destinationId == null && isCreate
    val budgetsForWallet: List<BudgetChoice> get() = budgetChoices.filter { it.currency == currency }
    val chosenBudget: BudgetChoice? get() = budgetChoices.firstOrNull { it.id == budgetId }

    /** Only the rules of a person who may change them, that record expenses (a gam'eya's payout is not one). */
    val budgetEditable: Boolean get() = loaded && canEdit && kind == TransactionKind.Expense && preset != Preset.Gameya
    val budgetVisible: Boolean get() = budgetEditable && (budgetsForWallet.isNotEmpty() || budgetId != null)

    /** All categories, or the chosen budget's list plus the platform's Other. */
    val visibleCategoryChoices: CategoryChoices? get() = categoryChoices?.let { choices -> chosenBudget?.let { choices.limitedTo(it) } ?: choices }
    val categoriesEmpty: Boolean get() = !isTransfer && visibleCategoryChoices?.isEmpty == true && categoryId == null

    val canSave: Boolean
        get() = loaded && canEdit && !saving && !busy && !noWallet && !destinationsEmpty && !categoriesEmpty
}

sealed interface RuleEditorIntent {
    data class PresetChosen(val preset: Preset) : RuleEditorIntent
    data object WalletClicked : RuleEditorIntent
    data class WalletChosen(val id: String) : RuleEditorIntent
    data object WalletSheetDismissed : RuleEditorIntent
    data class KindChosen(val kind: TransactionKind) : RuleEditorIntent
    data class AmountChanged(val text: String) : RuleEditorIntent
    data class BasisChosen(val basis: AmountBasis) : RuleEditorIntent
    data object CategoryClicked : RuleEditorIntent
    data object BudgetClicked : RuleEditorIntent

    /** A null id takes the rule out of any budget. */
    data class BudgetChosen(val id: String?) : RuleEditorIntent
    data class CategoryChosen(val id: String) : RuleEditorIntent
    data object PayoutCategoryClicked : RuleEditorIntent
    data class PayoutCategoryChosen(val id: String) : RuleEditorIntent
    data object DestinationClicked : RuleEditorIntent
    data class DestinationChosen(val id: String) : RuleEditorIntent
    data object SheetDismissed : RuleEditorIntent
    data class CountChanged(val text: String) : RuleEditorIntent
    data class PayoutRoundChanged(val text: String) : RuleEditorIntent
    data object FrequencyClicked : RuleEditorIntent
    data class FrequencyChosen(val frequency: Frequency) : RuleEditorIntent
    data class IntervalChanged(val text: String) : RuleEditorIntent
    data class DayChanged(val text: String) : RuleEditorIntent
    data class DateClicked(val target: DateTarget) : RuleEditorIntent
    data class DateChosen(val date: LocalDate) : RuleEditorIntent
    data object DatePickerDismissed : RuleEditorIntent
    data class EndModeChosen(val mode: EndMode) : RuleEditorIntent
    data class NoteChanged(val text: String) : RuleEditorIntent
    data class DetailsChanged(val text: String) : RuleEditorIntent
    data class PersonChanged(val text: String) : RuleEditorIntent
    data class AutoConfirmToggled(val on: Boolean) : RuleEditorIntent
    data object ChangeFromDateClicked : RuleEditorIntent
    data object CreateWalletClicked : RuleEditorIntent
    data object SaveClicked : RuleEditorIntent
    data object Back : RuleEditorIntent
}

sealed interface RuleEditorEffect {
    data object Close : RuleEditorEffect

    /** A new rule was created (not an edit); sent just before [Close] so the app can offer notifications (spec 017 R-14). */
    data object Created : RuleEditorEffect

    /** Replaces this editor with the same rule's "from a date" editor. */
    data class OpenFromDate(val ruleId: String, val walletId: String) : RuleEditorEffect
    data object OpenWalletEditor : RuleEditorEffect
    data class ShowMessage(val message: EditorMessage) : RuleEditorEffect
    data object RequireVerification : RuleEditorEffect
}

sealed interface RuleEditorChange {
    /** The initial values: the preselection when creating, the rule as shown when editing. */
    data class Loaded(val state: RuleEditorState) : RuleEditorChange

    /** The rule's current view: rights, frozen members and the base follow it; typed fields do not. */
    data class Stored(val view: RuleView) : RuleEditorChange
    data class Wallets(val choices: List<WalletChoice>) : RuleEditorChange
    data class Destinations(val choices: List<WalletChoice>) : RuleEditorChange
    data class Choices(val choices: CategoryChoices) : RuleEditorChange
    data class PayoutChoices(val choices: CategoryChoices) : RuleEditorChange
    data class Wallet(val choice: WalletChoice) : RuleEditorChange
    data class PresetSet(val preset: Preset) : RuleEditorChange
    data class Kind(val kind: TransactionKind) : RuleEditorChange
    data class Amount(val text: String) : RuleEditorChange
    data class Basis(val basis: AmountBasis) : RuleEditorChange
    data class Sheet(val sheet: EditorSheet?) : RuleEditorChange
    data class Budgets(val choices: List<BudgetChoice>) : RuleEditorChange
    data class Budget(val id: String?) : RuleEditorChange
    data class Category(val display: CategoryDisplay) : RuleEditorChange
    data class PayoutCategory(val display: CategoryDisplay) : RuleEditorChange
    data class Destination(val choice: WalletChoice) : RuleEditorChange
    data class Count(val text: String) : RuleEditorChange
    data class PayoutRound(val text: String) : RuleEditorChange
    data class FrequencySet(val frequency: Frequency) : RuleEditorChange
    data class Interval(val text: String) : RuleEditorChange
    data class Day(val text: String) : RuleEditorChange
    data class DatePickerOpen(val target: DateTarget?) : RuleEditorChange
    data class Date(val date: LocalDate) : RuleEditorChange
    data class EndModeSet(val mode: EndMode) : RuleEditorChange
    data class Note(val text: String) : RuleEditorChange
    data class Details(val text: String) : RuleEditorChange
    data class Person(val text: String) : RuleEditorChange
    data class AutoConfirm(val on: Boolean) : RuleEditorChange
    data class Preview(val preview: SchedulePreview) : RuleEditorChange
    data class Errors(val errors: Map<RuleField, RuleFieldProblem>) : RuleEditorChange
    data class Saving(val saving: Boolean) : RuleEditorChange
    data class NoWallet(val none: Boolean) : RuleEditorChange
}

/** Which sheet is open (only one at a time). */
enum class EditorSheet { Wallet, Category, PayoutCategory, Destination, Frequency, Budget }

fun reduce(state: RuleEditorState, change: RuleEditorChange): RuleEditorState = when (change) {
    is RuleEditorChange.Loaded -> change.state.copy(loaded = true)
    is RuleEditorChange.Stored -> state.withView(change.view)
    is RuleEditorChange.Wallets -> state.copy(walletChoices = change.choices, noWallet = state.isCreate && state.loaded && change.choices.isEmpty())
    is RuleEditorChange.Destinations -> state.copy(
        destinationChoices = change.choices,
        destinationId = if (state.isCreate && state.destinationId != null && change.choices.none { it.id == state.destinationId }) null else state.destinationId,
        destinationName = if (state.isCreate && state.destinationId != null && change.choices.none { it.id == state.destinationId }) null else state.destinationName,
    )
    is RuleEditorChange.Choices -> state.copy(categoryChoices = change.choices)
    is RuleEditorChange.Budgets -> state.copy(budgetChoices = change.choices)
    is RuleEditorChange.Budget -> if (state.budgetEditable) state.withBudget(change.id) else state
    is RuleEditorChange.PayoutChoices -> state.copy(payoutChoices = change.choices)
    is RuleEditorChange.Wallet ->
        if (state.walletEditable) {
            state.copy(
                walletId = change.choice.id, currency = change.choice.currency, destinationId = null, destinationName = null,
                errors = state.errors - RuleField.Destination,
            ).withAmountProblem().withBudgetKeptOnlyIfFits()
        } else {
            state
        }
    is RuleEditorChange.PresetSet ->
        if (state.presetEditable) state.withPreset(change.preset) else state
    is RuleEditorChange.Kind ->
        if (state.kindEditable && change.kind != TransactionKind.Unknown && change.kind in state.kindOptions) {
            // What no longer applies is cleared: a category of the other kind is never kept.
            state.copy(
                kind = change.kind,
                categoryId = if (change.kind == state.kind) state.categoryId else null,
                category = if (change.kind == state.kind) state.category else null,
                categoryChoices = if (change.kind == state.kind) state.categoryChoices else null,
                budgetId = if (change.kind == TransactionKind.Expense) state.budgetId else null,
                destinationId = if (change.kind == TransactionKind.Transfer) state.destinationId else null,
                destinationName = if (change.kind == TransactionKind.Transfer) state.destinationName else null,
                errors = state.errors - RuleField.Category - RuleField.Destination,
            )
        } else {
            state
        }
    is RuleEditorChange.Amount -> state.copy(amountText = change.text).withAmountProblem()
    is RuleEditorChange.Basis -> if (state.basisEditable) state.copy(basis = change.basis, errors = state.errors - RuleField.Basis) else state
    is RuleEditorChange.Sheet -> state.copy(
        walletSheetOpen = change.sheet == EditorSheet.Wallet,
        categorySheetOpen = change.sheet == EditorSheet.Category,
        payoutSheetOpen = change.sheet == EditorSheet.PayoutCategory,
        destinationSheetOpen = change.sheet == EditorSheet.Destination,
        frequencySheetOpen = change.sheet == EditorSheet.Frequency,
        budgetSheetOpen = change.sheet == EditorSheet.Budget,
    )
    is RuleEditorChange.Category -> state.copy(
        categoryId = change.display.id, category = change.display, categorySheetOpen = false, errors = state.errors - RuleField.Category,
    )
    is RuleEditorChange.PayoutCategory -> state.copy(
        payoutCategoryId = change.display.id, payoutCategory = change.display, payoutSheetOpen = false, errors = state.errors - RuleField.PayoutCategory,
    )
    is RuleEditorChange.Destination -> state.copy(
        destinationId = change.choice.id, destinationName = change.choice.name, destinationSheetOpen = false, errors = state.errors - RuleField.Destination,
    )
    is RuleEditorChange.Count -> state.copy(countText = change.text.filter { it.isDigit() }.take(4), errors = state.errors - RuleField.Count - RuleField.Basis)
    is RuleEditorChange.PayoutRound -> state.copy(payoutRoundText = change.text.filter { it.isDigit() }.take(3), errors = state.errors - RuleField.PayoutRound)
    is RuleEditorChange.FrequencySet ->
        if (state.scheduleEditable) state.copy(frequency = change.frequency, frequencySheetOpen = false, errors = state.errors - RuleField.Frequency - RuleField.Day).withFollowingDay() else state
    is RuleEditorChange.Interval -> state.copy(intervalText = change.text.filter { it.isDigit() }.take(3), errors = state.errors - RuleField.Interval)
    is RuleEditorChange.Day -> state.copy(dayText = change.text.filter { it.isDigit() }.take(2), dayTouched = true, errors = state.errors - RuleField.Day)
    is RuleEditorChange.DatePickerOpen -> state.copy(datePicker = change.target)
    is RuleEditorChange.Date -> when (state.datePicker) {
        DateTarget.Start -> state.copy(start = change.date, datePicker = null, errors = state.errors - RuleField.Start).withFollowingDay()
        DateTarget.End -> state.copy(endDate = change.date, datePicker = null, errors = state.errors - RuleField.End)
        DateTarget.From -> state.copy(from = change.date, datePicker = null, errors = state.errors - RuleField.From).withFollowingDay()
        null -> state
    }
    is RuleEditorChange.EndModeSet -> state.copy(endMode = change.mode, errors = state.errors - RuleField.End - RuleField.Count)
    is RuleEditorChange.Note -> state.copy(note = change.text, errors = state.errors.withLength(RuleField.Note, change.text, MAX_NOTE))
    is RuleEditorChange.Details -> state.copy(details = change.text, errors = state.errors.withLength(RuleField.Details, change.text, MAX_DETAILS))
    is RuleEditorChange.Person -> state.copy(person = change.text, errors = state.errors.withLength(RuleField.Person, change.text, MAX_PERSON))
    is RuleEditorChange.AutoConfirm -> state.copy(autoConfirm = change.on)
    is RuleEditorChange.Preview -> state.copy(preview = change.preview)
    is RuleEditorChange.Errors -> state.copy(errors = change.errors)
    is RuleEditorChange.Saving -> state.copy(saving = change.saving)
    is RuleEditorChange.NoWallet -> state.copy(noWallet = change.none)
}

/** The day follows the start (or split) date until the person types one. */
private fun RuleEditorState.withFollowingDay(): RuleEditorState {
    if (dayTouched) return this
    val date = (if (fromDateMode) from else start) ?: return this
    return copy(dayText = date.day.toString())
}

/** A preset fixes what it fixes (FR-010): a gam'eya is a monthly expense; an installment is monthly and never income. */
private fun RuleEditorState.withPreset(next: Preset): RuleEditorState {
    if (next == Preset.Unknown || next == preset) return this
    val nextKind = when {
        next == Preset.Gameya -> TransactionKind.Expense
        next == Preset.Installment && kind == TransactionKind.Income -> TransactionKind.Expense
        else -> kind
    }
    val kindChanged = nextKind != kind
    return copy(
        preset = next,
        kind = nextKind,
        categoryId = if (kindChanged) null else categoryId,
        category = if (kindChanged) null else category,
        categoryChoices = if (kindChanged) null else categoryChoices,
        destinationId = if (nextKind == TransactionKind.Transfer) destinationId else null,
        destinationName = if (nextKind == TransactionKind.Transfer) destinationName else null,
        basis = if (next == Preset.Installment) basis else AmountBasis.PerOccurrence,
        frequency = if (next == Preset.Custom) frequency else Frequency.Monthly,
        intervalText = if (next == Preset.Custom) intervalText else "1",
        endMode = EndMode.Never,
        endDate = null,
        countText = if (next == Preset.Custom) "" else countText,
        payoutRoundText = if (next == Preset.Gameya) payoutRoundText else "",
        payoutCategoryId = if (next == Preset.Gameya) payoutCategoryId else null,
        payoutCategory = if (next == Preset.Gameya) payoutCategory else null,
        errors = emptyMap(),
    ).withFollowingDay()
}

private fun RuleEditorState.withView(view: RuleView): RuleEditorState = copy(
    frozen = view.frozen,
    readOnly = view.readOnlyReason(),
    unsentRule = view.rule.isPlaceholder,
    canSplit = view.canSplit(),
)

internal fun RuleView.readOnlyReason(): ReadOnlyReason? = when {
    rule.hasUnknownValues -> ReadOnlyReason.Unknown
    rule.status == RuleStatus.Ended -> ReadOnlyReason.Ended
    readOnly -> ReadOnlyReason.Viewer
    else -> null
}

/** "Change from a date" suits a custom, per-round rule that is not read-only and already has a resolved round. */
internal fun RuleView.canSplit(): Boolean =
    !readOnly && !rule.isPlaceholder && rule.preset == Preset.Custom && rule.amountBasis == AmountBasis.PerOccurrence

private fun RuleEditorState.withAmountProblem(): RuleEditorState {
    if (amountText.isBlank()) return copy(errors = errors - RuleField.Amount)
    return when (val result = TransactionAmount.parse(amountText, currency)) {
        is TransactionAmount.Result.Valid -> copy(errors = errors - RuleField.Amount)
        is TransactionAmount.Result.Invalid -> copy(errors = errors + (RuleField.Amount to result.problem.asRuleProblem()))
    }
}

private fun Map<RuleField, RuleFieldProblem>.withLength(field: RuleField, text: String, max: Int): Map<RuleField, RuleFieldProblem> =
    if (text.trim().count { !it.isLowSurrogate() } > max) this + (field to RuleFieldProblem.TooLong(max)) else this - field

/** What the form gives: a draft to preview or send, or the problems found in what was typed. */
sealed interface DraftResult {
    data class Ready(val draft: RuleDraft, val from: LocalDate?) : DraftResult
    data class Problems(val errors: Map<RuleField, RuleFieldProblem>) : DraftResult
}

/** Builds the draft from the typed text. Range checks are the service's; this only reads numbers and dates. */
fun RuleEditorState.buildDraft(): DraftResult {
    val problems = linkedMapOf<RuleField, RuleFieldProblem>()
    val amount = TransactionAmount.parse(amountText, currency)
    val amountMinor = (amount as? TransactionAmount.Result.Valid)?.minor
    if (amountMinor == null) problems[RuleField.Amount] = (amount as TransactionAmount.Result.Invalid).problem.asRuleProblem()
    val startDate = (if (fromDateMode) from else start)
    if (startDate == null) problems[if (fromDateMode) RuleField.From else RuleField.Start] = RuleFieldProblem.Required
    val interval = if (presetShaped) 1 else intervalText.toIntOrNull()
    if (interval == null) problems[RuleField.Interval] = RuleFieldProblem.Required
    val anchor = if (showsDay) dayText.toIntOrNull() else null
    if (showsDay && anchor == null) problems[RuleField.Day] = RuleFieldProblem.Required
    val count = countText.toIntOrNull()
    val usesCount = presetShaped || endMode == EndMode.After
    if (usesCount && count == null) problems[RuleField.Count] = RuleFieldProblem.Required
    val usesEndDate = !presetShaped && endMode == EndMode.OnDate
    if (usesEndDate && endDate == null) problems[RuleField.End] = RuleFieldProblem.Required
    val payoutRound = payoutRoundText.toIntOrNull()
    if (preset == Preset.Gameya && payoutRound == null) problems[RuleField.PayoutRound] = RuleFieldProblem.Required
    if (problems.isNotEmpty() || amountMinor == null || startDate == null || interval == null) return DraftResult.Problems(problems)
    val draft = RuleDraft(
        preset = preset,
        kind = kind,
        amountMinor = amountMinor,
        amountBasis = basis,
        categoryId = if (isTransfer) null else categoryId,
        counterWalletId = if (isTransfer) destinationId else null,
        description = note,
        memo = details,
        counterparty = person,
        autoConfirm = autoConfirm,
        frequency = if (presetShaped) Frequency.Monthly else frequency,
        interval = interval,
        anchorDay = if (presetShaped) startDate.day else anchor,
        startDate = startDate,
        endDate = if (usesEndDate) endDate else null,
        occurrenceCount = if (usesCount) count else null,
        payoutRound = if (preset == Preset.Gameya) payoutRound else null,
        payoutCategoryId = if (preset == Preset.Gameya) payoutCategoryId else null,
        sharedBudgetId = budgetId.takeIf { kind == TransactionKind.Expense && preset != Preset.Gameya },
    )
    return DraftResult.Ready(draft, if (fromDateMode) from else null)
}

/** Choosing a budget limits the categories; a category that no longer fits is dropped so the person picks again. */
private fun RuleEditorState.withBudget(id: String?): RuleEditorState {
    val budget = budgetChoices.firstOrNull { it.id == id }
    val kept = category?.takeIf { shown ->
        when {
            budget != null -> budget.categories.any { it.id == shown.id } || shown.systemKey == OTHER_EXPENSE_KEY
            else -> shown.source != CategorySource.Partner
        }
    }
    return copy(
        budgetId = budget?.id, budgetSheetOpen = false, budgetRefused = false,
        categoryId = kept?.id ?: if (category == null) categoryId else null, category = kept,
        errors = errors - RuleField.Budget - RuleField.Category,
    )
}

/** A budget in another currency than the wallet's is dropped when the wallet changes. */
private fun RuleEditorState.withBudgetKeptOnlyIfFits(): RuleEditorState =
    if (budgetId != null && budgetChoices.none { it.id == budgetId && it.currency == currency }) withBudget(null) else this
