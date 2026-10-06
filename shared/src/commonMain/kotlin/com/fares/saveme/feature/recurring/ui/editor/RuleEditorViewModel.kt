package com.fares.saveme.feature.recurring.ui.editor

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.RuleDraft
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.domain.model.RuleResult
import com.fares.saveme.feature.recurring.domain.model.RuleView
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.SchedulePreview
import com.fares.saveme.feature.transactions.domain.TransactionAmount
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Creates a rule, edits one, or edits it "from a date", through domain lambdas only (P-16.3). The preview is the
 * phone's own calculation, recomputed after every change (FR-006). In edit mode the form starts from what the phone
 * shows (a waiting change or the server copy) and remembers that version as the base.
 */
class RuleEditorViewModel(
    private val args: RuleEditorArgs,
    private val observeRule: (String) -> Flow<RuleView?>,
    private val writableWallets: Flow<List<WalletChoice>>,
    private val defaultWalletId: suspend () -> String?,
    private val destinations: (String) -> Flow<List<WalletChoice>>,
    private val categoryChoices: (TransactionKind, String?) -> Flow<CategoryChoices>,
    private val displays: (String) -> Flow<CategoryDisplays>,
    private val walletName: (String) -> Flow<String?>,
    private val preview: (RuleDraft) -> SchedulePreview,
    private val create: suspend (String, RuleDraft) -> RuleResult,
    private val save: suspend (String, RuleDraft, Long) -> RuleResult,
    private val changeFrom: suspend (String, LocalDate, RuleDraft, Long) -> RuleResult,
    private val today: () -> LocalDate,
) : MviViewModel<RuleEditorState, RuleEditorIntent, RuleEditorEffect>(
    RuleEditorState(ruleId = args.ruleId, fromDateMode = args.fromDate && args.ruleId != null),
) {

    private val walletFlow = MutableStateFlow<String?>(null)
    private val kindFlow = MutableStateFlow(TransactionKind.Expense)
    private var displaysSnapshot: CategoryDisplays? = null
    private var leaving = false

    init {
        viewModelScope.launch { if (args.ruleId == null) loadCreate() else loadEdit(args.ruleId) }
    }

    private fun startFlows(walletId: String, includeWallets: Boolean) {
        walletFlow.value = walletId
        if (includeWallets) {
            viewModelScope.launch { writableWallets.collect { list -> change(RuleEditorChange.Wallets(list)) } }
            viewModelScope.launch {
                walletFlow.flatMapLatest { id -> if (id == null) emptyFlow() else destinations(id) }
                    .collect { list -> change(RuleEditorChange.Destinations(list)) }
            }
        }
        viewModelScope.launch {
            kindFlow.flatMapLatest { kind ->
                if (kind == TransactionKind.Income || kind == TransactionKind.Expense) categoryChoices(kind, currentState.categoryId) else emptyFlow()
            }.collect { choices -> change(RuleEditorChange.Choices(choices)) }
        }
        viewModelScope.launch { categoryChoices(TransactionKind.Income, currentState.payoutCategoryId).collect { change(RuleEditorChange.PayoutChoices(it)) } }
    }

    private suspend fun loadCreate() {
        val wallets = writableWallets.first()
        val prefill = args.prefill
        val chosen = (args.walletId ?: prefill?.walletId)?.let { id -> wallets.firstOrNull { it.id == id } }
            ?: defaultWalletId()?.let { id -> wallets.firstOrNull { it.id == id } }
            ?: wallets.firstOrNull()
        val start = prefill?.startDate ?: today()
        if (chosen == null) {
            setState { reduce(it, RuleEditorChange.Loaded(it.copy(start = start, noWallet = true))) }
            return
        }
        val snapshot = displays(chosen.id).first()
        displaysSnapshot = snapshot
        val kind = prefill?.kind?.takeIf { it != TransactionKind.Unknown } ?: TransactionKind.Expense
        val destinationName = prefill?.counterWalletId?.let { walletName(it).first() }
        setState {
            reduce(
                it,
                RuleEditorChange.Loaded(
                    it.copy(
                        walletChoices = wallets,
                        walletId = chosen.id,
                        currency = chosen.currency,
                        kind = kind,
                        amountText = prefill?.takeIf { it.amountMinor > 0 }?.let { p -> TransactionAmount.format(p.amountMinor, chosen.currency) }.orEmpty(),
                        categoryId = prefill?.categoryId,
                        category = prefill?.categoryId?.let { id -> snapshot.resolve(id) },
                        destinationId = prefill?.counterWalletId,
                        destinationName = destinationName,
                        note = prefill?.description.orEmpty(),
                        details = prefill?.memo.orEmpty(),
                        person = prefill?.counterparty.orEmpty(),
                        start = start,
                        dayText = start.day.toString(),
                    ),
                ),
            )
        }
        kindFlow.value = kind
        startFlows(chosen.id, includeWallets = true)
        refreshPreview()
    }

    private suspend fun loadEdit(id: String) {
        val first = observeRule(id).first()
        if (first == null) {
            sendEffect(RuleEditorEffect.ShowMessage(EditorMessage.Gone))
            sendEffect(RuleEditorEffect.Close)
            return
        }
        val rule = first.rule
        val shown = first.shown
        val snapshot = displays(rule.walletId).first()
        displaysSnapshot = snapshot
        val destinationName = rule.counterWalletId?.let { walletName(it).first() }
        val fromDate = args.fromDate
        val base = RuleEditorState(
            ruleId = id,
            fromDateMode = fromDate,
            walletId = rule.walletId,
            preset = shown.preset,
            kind = shown.kind,
            currency = rule.currency,
            amountText = TransactionAmount.format(shown.amountMinor, rule.currency),
            basis = shown.amountBasis,
            categoryId = shown.categoryId,
            category = shown.categoryId?.let { c -> snapshot.resolve(c) },
            payoutCategoryId = shown.payoutCategoryId,
            payoutCategory = shown.payoutCategoryId?.let { c -> snapshot.resolve(c) },
            destinationId = shown.counterWalletId,
            destinationName = destinationName,
            countText = shown.occurrenceCount?.toString().orEmpty(),
            payoutRoundText = shown.payoutRound?.toString().orEmpty(),
            frequency = shown.frequency,
            intervalText = shown.interval.toString(),
            dayText = (shown.anchorDay ?: shown.startDate.day).toString(),
            dayTouched = true,
            start = shown.startDate,
            endMode = when {
                shown.preset != Preset.Custom -> EndMode.Never
                shown.endDate != null -> EndMode.OnDate
                shown.occurrenceCount != null -> EndMode.After
                else -> EndMode.Never
            },
            endDate = shown.endDate,
            from = if (fromDate) (first.nextDate ?: first.today) else null,
            note = shown.description.orEmpty(),
            details = shown.memo.orEmpty(),
            person = shown.counterparty.orEmpty(),
            autoConfirm = shown.autoConfirm,
            baseVersion = rule.version,
            errors = (first.syncState as? RecurringSyncState.Refused)?.fields.orEmpty().asProblems(),
        )
        // A split starts the new rule on its own date, so the day follows it.
        val loaded = if (fromDate) base.copy(dayText = (base.from?.day ?: shown.startDate.day).toString(), dayTouched = false) else base
        setState { reduce(it, RuleEditorChange.Loaded(reduce(loaded, RuleEditorChange.Stored(first)))) }
        kindFlow.value = shown.kind
        startFlows(rule.walletId, includeWallets = false)
        refreshPreview()
        observeRule(id).collect { view -> onView(view) }
    }

    private fun onView(view: RuleView?) {
        if (view == null) {
            if (!leaving) {
                leaving = true
                sendEffect(RuleEditorEffect.ShowMessage(EditorMessage.Gone))
                sendEffect(RuleEditorEffect.Close)
            }
            return
        }
        change(RuleEditorChange.Stored(view))
    }

    override fun onIntent(intent: RuleEditorIntent) {
        when (intent) {
            is RuleEditorIntent.PresetChosen -> {
                change(RuleEditorChange.PresetSet(intent.preset))
                kindFlow.value = currentState.kind
            }
            RuleEditorIntent.WalletClicked -> if (currentState.walletEditable && currentState.walletChoices.size > 1) change(RuleEditorChange.Sheet(EditorSheet.Wallet))
            is RuleEditorIntent.WalletChosen -> currentState.walletChoices.firstOrNull { it.id == intent.id }?.let {
                change(RuleEditorChange.Wallet(it))
                change(RuleEditorChange.Sheet(null))
                walletFlow.value = it.id
            }
            RuleEditorIntent.WalletSheetDismissed, RuleEditorIntent.SheetDismissed -> change(RuleEditorChange.Sheet(null))
            is RuleEditorIntent.KindChosen -> {
                change(RuleEditorChange.Kind(intent.kind))
                kindFlow.value = currentState.kind
            }
            is RuleEditorIntent.AmountChanged -> change(RuleEditorChange.Amount(intent.text))
            is RuleEditorIntent.BasisChosen -> change(RuleEditorChange.Basis(intent.basis))
            RuleEditorIntent.CategoryClicked -> if (currentState.canEdit) change(RuleEditorChange.Sheet(EditorSheet.Category))
            is RuleEditorIntent.CategoryChosen -> currentState.categoryChoices.display(intent.id)?.let { change(RuleEditorChange.Category(it)) }
            RuleEditorIntent.PayoutCategoryClicked -> if (currentState.canEdit) change(RuleEditorChange.Sheet(EditorSheet.PayoutCategory))
            is RuleEditorIntent.PayoutCategoryChosen -> currentState.payoutChoices.display(intent.id)?.let { change(RuleEditorChange.PayoutCategory(it)) }
            RuleEditorIntent.DestinationClicked -> if (currentState.destinationEditable) change(RuleEditorChange.Sheet(EditorSheet.Destination))
            is RuleEditorIntent.DestinationChosen -> currentState.destinationChoices.firstOrNull { it.id == intent.id }?.let { change(RuleEditorChange.Destination(it)) }
            is RuleEditorIntent.CountChanged -> change(RuleEditorChange.Count(intent.text))
            is RuleEditorIntent.PayoutRoundChanged -> change(RuleEditorChange.PayoutRound(intent.text))
            RuleEditorIntent.FrequencyClicked -> if (currentState.scheduleEditable) change(RuleEditorChange.Sheet(EditorSheet.Frequency))
            is RuleEditorIntent.FrequencyChosen -> change(RuleEditorChange.FrequencySet(intent.frequency))
            is RuleEditorIntent.IntervalChanged -> change(RuleEditorChange.Interval(intent.text))
            is RuleEditorIntent.DayChanged -> change(RuleEditorChange.Day(intent.text))
            is RuleEditorIntent.DateClicked -> change(RuleEditorChange.DatePickerOpen(intent.target))
            RuleEditorIntent.DatePickerDismissed -> change(RuleEditorChange.DatePickerOpen(null))
            is RuleEditorIntent.DateChosen -> change(RuleEditorChange.Date(intent.date))
            is RuleEditorIntent.EndModeChosen -> change(RuleEditorChange.EndModeSet(intent.mode))
            is RuleEditorIntent.NoteChanged -> change(RuleEditorChange.Note(intent.text))
            is RuleEditorIntent.DetailsChanged -> change(RuleEditorChange.Details(intent.text))
            is RuleEditorIntent.PersonChanged -> change(RuleEditorChange.Person(intent.text))
            is RuleEditorIntent.AutoConfirmToggled -> if (currentState.canEdit) change(RuleEditorChange.AutoConfirm(intent.on))
            RuleEditorIntent.ChangeFromDateClicked ->
                if (currentState.canSplit && currentState.anyFrozen) {
                    sendEffect(RuleEditorEffect.OpenFromDate(currentState.ruleId.orEmpty(), currentState.walletId))
                }
            RuleEditorIntent.CreateWalletClicked -> sendEffect(RuleEditorEffect.OpenWalletEditor)
            RuleEditorIntent.SaveClicked -> submit()
            RuleEditorIntent.Back -> sendEffect(RuleEditorEffect.Close)
        }
    }

    private fun CategoryChoices?.display(id: String) =
        this?.let { (listOfNotNull(it.current) + it.own + it.defaults).firstOrNull { d -> d.id == id } }

    private fun change(change: RuleEditorChange) {
        setState { reduce(it, change) }
        if (change !is RuleEditorChange.Preview && change !is RuleEditorChange.Errors && change !is RuleEditorChange.Saving && change !is RuleEditorChange.Stored) {
            refreshPreview()
        }
    }

    private fun refreshPreview() {
        val state = currentState
        val next = (state.buildDraft() as? DraftResult.Ready)?.let { preview(it.draft) } ?: SchedulePreview.Empty
        if (next != state.preview) setState { reduce(it, RuleEditorChange.Preview(next)) }
    }

    private fun submit() {
        val state = currentState
        if (!state.canSave) return
        when (val built = state.buildDraft()) {
            is DraftResult.Problems -> change(RuleEditorChange.Errors(state.errors + built.errors))
            is DraftResult.Ready -> {
                change(RuleEditorChange.Saving(true))
                viewModelScope.launch {
                    val id = state.ruleId
                    val result = when {
                        id == null -> create(state.walletId, built.draft)
                        built.from != null -> changeFrom(id, built.from, built.draft, state.baseVersion)
                        else -> save(id, built.draft, state.baseVersion)
                    }
                    when (result) {
                        is RuleResult.Created, RuleResult.Saved -> {
                            leaving = true
                            if (result is RuleResult.Created) sendEffect(RuleEditorEffect.Created)
                            sendEffect(RuleEditorEffect.Close)
                        }
                        RuleResult.NotVerified -> sendEffect(RuleEditorEffect.RequireVerification)
                        RuleResult.NotAllowed -> sendEffect(RuleEditorEffect.ShowMessage(EditorMessage.NotAllowed))
                        RuleResult.ReadOnly -> sendEffect(RuleEditorEffect.ShowMessage(EditorMessage.ReadOnly))
                        RuleResult.NoLongerAvailable -> {
                            leaving = true
                            sendEffect(RuleEditorEffect.ShowMessage(EditorMessage.Gone))
                            sendEffect(RuleEditorEffect.Close)
                        }
                        is RuleResult.Invalid -> change(RuleEditorChange.Errors(result.errors))
                    }
                    change(RuleEditorChange.Saving(false))
                }
            }
        }
    }
}

/** Refused fields from the server, worded by code: required, out of range, or "check this field". */
private fun Map<String, String>.asProblems(): Map<RuleField, RuleFieldProblem> = buildMap {
    this@asProblems.forEach { (field, code) ->
        val ruleField = RuleField.fromName(field) ?: return@forEach
        put(
            ruleField,
            when (code) {
                "REQUIRED" -> RuleFieldProblem.Required
                "OUT_OF_RANGE" -> RuleFieldProblem.OutOfRange(1, Int.MAX_VALUE.toLong())
                else -> RuleFieldProblem.Invalid
            },
        )
    }
}
