package com.fares.saveme.feature.transactions.ui.editor

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.transactions.domain.TransactionAmount
import com.fares.saveme.feature.transactions.domain.model.Counterpart
import com.fares.saveme.feature.transactions.domain.model.CreateResult
import com.fares.saveme.feature.transactions.domain.model.RefusalKind
import com.fares.saveme.feature.transactions.domain.model.SaveResult
import com.fares.saveme.feature.transactions.domain.model.Transaction
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.TransactionSyncState
import com.fares.saveme.feature.transactions.domain.model.TransactionView
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.fares.saveme.feature.transactions.ui.asProblems
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Records, edits or shows one transaction through domain lambdas only (P-16.3). In edit mode it is
 * also the transaction's detail: status, clash and refusal choices, delete. Edit mode starts from
 * what the phone shows (a waiting change or the server copy) and remembers that version as the base.
 *
 * [walletId] is the wallet to preselect when creating, and the side the transaction is viewed from
 * when editing.
 */
class TransactionEditorViewModel(
    private val transactionId: String?,
    private val walletId: String?,
    private val observeTransaction: (String, String) -> Flow<TransactionView?>,
    private val writableWallets: Flow<List<WalletChoice>>,
    private val defaultWalletId: suspend () -> String?,
    private val destinations: (String) -> Flow<List<WalletChoice>>,
    private val categoryChoices: (TransactionKind, String?) -> Flow<CategoryChoices>,
    private val displays: (String?) -> Flow<CategoryDisplays>,
    private val create: suspend (TransactionDraft) -> CreateResult,
    /** The last argument is the server copy the editor opened on, given only once the transaction left the phone. */
    private val save: suspend (String, TransactionDraft, Long, Transaction?) -> SaveResult,
    private val delete: suspend (String) -> SaveResult,
    private val keepMine: suspend (String) -> Unit,
    private val useTheirs: suspend (String) -> Unit,
    private val tryAgain: suspend (String) -> Unit,
    private val discard: suspend (String) -> Unit,
    private val today: () -> LocalDate,
) : MviViewModel<TransactionEditorState, TransactionEditorIntent, TransactionEditorEffect>(
    TransactionEditorState(transactionId = transactionId),
) {

    private val walletFlow = MutableStateFlow<String?>(null)
    private val kindFlow = MutableStateFlow(TransactionKind.Expense)
    private var currentCategoryId: String? = null
    private var displaysSnapshot: CategoryDisplays? = null

    /** True from a confirmed delete on, so the transaction vanishing is not reported as "gone". */
    private var leaving = false

    /**
     * True once the person chose a clash or refusal action. If the transaction then leaves the phone
     * ("Accept the deletion", "Discard" of a never-sent one), that is their own choice: the editor
     * closes without the "no longer available" message.
     */
    private var resolvedByPerson = false

    /** The form as it was loaded (edit mode), to tell whether the person has typed anything since. */
    private var loadedForm: EditorForm? = null

    /** The server copy last shown; kept when the transaction leaves the phone while the person types. */
    private var lastServer: Transaction? = null

    /**
     * True when a refresh removed the transaction (deleted elsewhere) while the person had typed a
     * change: the editor keeps the typing and the save waits on [lastServer], so the server answers
     * it with a clash or a refusal (spec Edge Cases).
     */
    private var removedWhileTyping = false

    init {
        viewModelScope.launch { load() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun load() {
        if (transactionId == null) loadCreate() else loadEdit(transactionId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun loadCreate() {
        val wallets = writableWallets.first()
        val chosen = walletId?.let { id -> wallets.firstOrNull { it.id == id } }
            ?: defaultWalletId()?.let { id -> wallets.firstOrNull { it.id == id } }
            ?: wallets.firstOrNull()
        setState {
            reduce(
                it,
                TransactionEditorChange.Loaded(
                    it.copy(
                        walletChoices = wallets,
                        walletId = chosen?.id.orEmpty(),
                        currency = chosen?.currency.orEmpty(),
                        date = today(),
                        noWallet = chosen == null,
                    ),
                ),
            )
        }
        if (chosen == null) return
        walletFlow.value = chosen.id
        viewModelScope.launch { writableWallets.collect { list -> setState { reduce(it, TransactionEditorChange.Wallets(list)) } } }
        viewModelScope.launch {
            walletFlow.flatMapLatest { id -> if (id == null) emptyFlow() else destinations(id) }
                .collect { list -> setState { reduce(it, TransactionEditorChange.Destinations(list)) } }
        }
        viewModelScope.launch {
            kindFlow.flatMapLatest { kind ->
                if (kind == TransactionKind.Income || kind == TransactionKind.Expense) categoryChoices(kind, null) else emptyFlow()
            }.collect { choices -> setState { reduce(it, TransactionEditorChange.Choices(choices)) } }
        }
    }

    private suspend fun loadEdit(id: String) {
        val from = walletId.orEmpty()
        val first = observeTransaction(id, from).first()
        if (first == null) {
            sendEffect(TransactionEditorEffect.ShowMessage(EditorMessage.Gone))
            sendEffect(TransactionEditorEffect.Close)
            return
        }
        currentCategoryId = first.shown.categoryId
        val snapshot = displays(first.shown.walletId).first()
        displaysSnapshot = snapshot
        val categoryDisplay = first.shown.categoryId?.let { snapshot.resolve(it) }
        setState { reduce(it, TransactionEditorChange.Loaded(stateFrom(first, categoryDisplay, it))) }
        loadedForm = EditorForm.of(currentState)
        lastServer = first.server
        kindFlow.value = first.shown.kind
        viewModelScope.launch {
            kindFlow.flatMapLatest { kind ->
                if (kind == TransactionKind.Income || kind == TransactionKind.Expense) categoryChoices(kind, currentCategoryId) else emptyFlow()
            }.collect { choices -> setState { reduce(it, TransactionEditorChange.Choices(choices)) } }
        }
        viewModelScope.launch { displays(first.shown.walletId).collect { displaysSnapshot = it } }
        // Keeps the status and the offered actions current while the person types (typed fields are never overwritten).
        observeTransaction(id, from).collect { view -> onView(view) }
    }

    private fun stateFrom(view: TransactionView, category: CategoryDisplay?, base: TransactionEditorState): TransactionEditorState {
        val shown = view.shown
        val refusal = (view.syncState as? TransactionSyncState.Refused)?.reason
        // The wallet's currency changed under a never-sent create: type the amount again in the present currency (FR-026).
        val reenter = refusal?.kind == RefusalKind.CurrencyChanged
        val currency = if (reenter) view.walletCurrency else shown.currency
        return base.copy(
            walletId = shown.walletId,
            kindWire = shown.kindWire,
            currency = currency,
            amountText = if (reenter) "" else TransactionAmount.format(shown.amountMinor, shown.currency),
            currencyChanged = reenter,
            shownAmountMinor = shown.amountMinor,
            shownDate = shown.date,
            date = shown.date,
            categoryId = shown.categoryId,
            category = category,
            destinationId = shown.counterWalletId,
            destinationName = (view.counterpart as? Counterpart.Named)?.name,
            note = shown.description.orEmpty(),
            details = shown.memo.orEmpty(),
            person = shown.counterparty.orEmpty(),
            cleared = shown.cleared,
            moreOpen = base.moreOpen || !shown.memo.isNullOrBlank() || !shown.counterparty.isNullOrBlank() || shown.cleared,
            baseVersion = view.baseVersion,
            canEdit = view.canEdit,
            canDelete = view.canDelete && !view.pendingDelete,
            addedBy = view.addedBy,
            fromRecurringRule = view.fromRecurringRule,
            errors = (view.syncState as? TransactionSyncState.Refused)?.fields.orEmpty().asProblems(),
        )
    }

    private fun onView(view: TransactionView?) {
        if (view == null) {
            val typed = loadedForm?.let { it != EditorForm.of(currentState) } == true
            if (!leaving && !resolvedByPerson && typed && lastServer != null && currentState.canEdit) {
                // Gone from the phone with the person's typing on screen: keep it; Save sends it for the server to answer.
                removedWhileTyping = true
                setState { it.copy(canDelete = false, confirmDelete = false) }
                return
            }
            if (!leaving) {
                leaving = true
                if (!resolvedByPerson) sendEffect(TransactionEditorEffect.ShowMessage(EditorMessage.Gone))
                sendEffect(TransactionEditorEffect.Close)
            }
            return
        }
        removedWhileTyping = false
        if (view.server != null) lastServer = view.server
        val clash = view.syncState as? TransactionSyncState.Clash
        val lines = clash?.theirs?.let { theirs ->
            clashLines(view.shown, theirs) { id -> id?.let { displaysSnapshot?.resolve(it) } }
        }.orEmpty()
        setState { reduce(it, TransactionEditorChange.Stored(view, lines)) }
    }

    override fun onIntent(intent: TransactionEditorIntent) {
        when (intent) {
            TransactionEditorIntent.WalletClicked -> if (currentState.walletEditable && currentState.walletChoices.size > 1) change(TransactionEditorChange.WalletSheet(true))
            TransactionEditorIntent.WalletSheetDismissed -> change(TransactionEditorChange.WalletSheet(false))
            is TransactionEditorIntent.WalletChosen -> currentState.walletChoices.firstOrNull { it.id == intent.id }?.let {
                change(TransactionEditorChange.Wallet(it))
                walletFlow.value = it.id
            }
            is TransactionEditorIntent.KindChosen -> if (currentState.kindEditable) {
                change(TransactionEditorChange.Kind(intent.kind))
                if (intent.kind != TransactionKind.Unknown) kindFlow.value = intent.kind
            }
            is TransactionEditorIntent.AmountChanged -> change(TransactionEditorChange.Amount(intent.text))
            TransactionEditorIntent.DateClicked -> change(TransactionEditorChange.DatePicker(true))
            TransactionEditorIntent.DatePickerDismissed -> change(TransactionEditorChange.DatePicker(false))
            is TransactionEditorIntent.DateChosen -> change(TransactionEditorChange.Date(intent.date))
            TransactionEditorIntent.CategoryClicked -> change(TransactionEditorChange.CategorySheet(true))
            is TransactionEditorIntent.CategoryChosen -> chooseCategory(intent.id)
            TransactionEditorIntent.SheetDismissed -> {
                change(TransactionEditorChange.CategorySheet(false))
                change(TransactionEditorChange.DestinationSheet(false))
            }
            TransactionEditorIntent.DestinationClicked -> if (currentState.isCreate) change(TransactionEditorChange.DestinationSheet(true))
            is TransactionEditorIntent.DestinationChosen -> currentState.destinationChoices.firstOrNull { it.id == intent.id }
                ?.let { change(TransactionEditorChange.Destination(it)) }
            is TransactionEditorIntent.NoteChanged -> change(TransactionEditorChange.Note(intent.text))
            is TransactionEditorIntent.DetailsChanged -> change(TransactionEditorChange.Details(intent.text))
            is TransactionEditorIntent.PersonChanged -> change(TransactionEditorChange.Person(intent.text))
            is TransactionEditorIntent.ClearedToggled -> change(TransactionEditorChange.Cleared(intent.cleared))
            TransactionEditorIntent.MoreToggled -> change(TransactionEditorChange.More)
            TransactionEditorIntent.SaveClicked -> submit()
            TransactionEditorIntent.DeleteClicked -> if (currentState.canDelete) change(TransactionEditorChange.ConfirmDelete(true))
            TransactionEditorIntent.DeleteCancelled -> if (!currentState.busy) change(TransactionEditorChange.ConfirmDelete(false))
            TransactionEditorIntent.DeleteConfirmed -> confirmDelete()
            TransactionEditorIntent.KeepMine, TransactionEditorIntent.RestoreWithMine, TransactionEditorIntent.DeleteAnyway -> resolve(keepMine)
            TransactionEditorIntent.UseTheirs, TransactionEditorIntent.AcceptDeletion, TransactionEditorIntent.KeepTransaction ->
                resolve(useTheirs, reload = true)
            TransactionEditorIntent.TryAgain -> resolve(tryAgain)
            TransactionEditorIntent.Discard -> resolve(discard, reload = true)
            TransactionEditorIntent.CreateWalletClicked -> sendEffect(TransactionEditorEffect.OpenWalletEditor)
            TransactionEditorIntent.Back -> sendEffect(TransactionEditorEffect.Close)
        }
    }

    private fun change(change: TransactionEditorChange) = setState { reduce(it, change) }

    private fun chooseCategory(id: String) {
        val choices = currentState.categoryChoices ?: return
        val display = (listOfNotNull(choices.current) + choices.own + choices.defaults).firstOrNull { it.id == id } ?: return
        change(TransactionEditorChange.Category(display))
    }

    private fun submit() {
        val state = currentState
        if (!state.canSave) return
        val amount = TransactionAmount.parse(state.amountText, state.currency)
        if (amount !is TransactionAmount.Result.Valid) {
            val problem = (amount as TransactionAmount.Result.Invalid).problem
            change(TransactionEditorChange.Errors(state.errors + (TransactionField.Amount to problem)))
            return
        }
        val draft = TransactionDraft(
            walletId = state.walletId,
            kindWire = state.kindWire,
            date = state.date ?: today(),
            amountMinor = amount.minor,
            currency = state.currency,
            categoryId = state.categoryId,
            counterWalletId = state.destinationId,
            description = state.note,
            memo = state.details,
            counterparty = state.person,
            cleared = state.cleared,
        )
        change(TransactionEditorChange.Saving(true))
        viewModelScope.launch {
            val id = state.transactionId
            if (id == null) {
                when (val result = create(draft)) {
                    is CreateResult.Created -> sendEffect(TransactionEditorEffect.Close)
                    CreateResult.NotVerified -> sendEffect(TransactionEditorEffect.RequireVerification)
                    CreateResult.NoWallet -> change(TransactionEditorChange.NoWallet(true))
                    CreateResult.NotAllowed -> sendEffect(TransactionEditorEffect.ShowMessage(EditorMessage.NotAllowed))
                    is CreateResult.Invalid -> change(TransactionEditorChange.Errors(result.errors))
                }
            } else {
                when (val result = save(id, draft, state.baseVersion, lastServer.takeIf { removedWhileTyping })) {
                    SaveResult.Saved -> sendEffect(TransactionEditorEffect.Close)
                    SaveResult.NotVerified -> sendEffect(TransactionEditorEffect.RequireVerification)
                    SaveResult.NoLongerAvailable -> {
                        sendEffect(TransactionEditorEffect.ShowMessage(EditorMessage.Gone))
                        sendEffect(TransactionEditorEffect.Close)
                    }
                    SaveResult.NotAllowed -> sendEffect(TransactionEditorEffect.ShowMessage(EditorMessage.NotAllowed))
                    is SaveResult.Invalid -> change(TransactionEditorChange.Errors(result.errors))
                }
            }
            change(TransactionEditorChange.Saving(false))
        }
    }

    private fun confirmDelete() {
        val id = transactionId ?: return
        if (currentState.busy) return
        change(TransactionEditorChange.Busy(true))
        viewModelScope.launch {
            leaving = true
            val result = delete(id)
            change(TransactionEditorChange.Busy(false))
            change(TransactionEditorChange.ConfirmDelete(false))
            when (result) {
                SaveResult.Saved -> sendEffect(TransactionEditorEffect.Close)
                SaveResult.NotVerified -> {
                    leaving = false
                    sendEffect(TransactionEditorEffect.RequireVerification)
                }
                SaveResult.NotAllowed -> {
                    leaving = false
                    sendEffect(TransactionEditorEffect.ShowMessage(EditorMessage.NotAllowed))
                }
                else -> sendEffect(TransactionEditorEffect.Close)
            }
        }
    }

    /** [reload]: the choice drops this phone's change, so the fields show what the phone holds again. */
    private fun resolve(action: suspend (String) -> Unit, reload: Boolean = false) {
        val id = transactionId ?: return
        if (currentState.busy) return
        resolvedByPerson = true
        change(TransactionEditorChange.Busy(true))
        viewModelScope.launch {
            action(id)
            if (reload) {
                val view = observeTransaction(id, walletId.orEmpty()).first()
                if (view != null) {
                    val category = view.shown.categoryId?.let { displaysSnapshot?.resolve(it) }
                    change(TransactionEditorChange.Loaded(stateFrom(view, category, currentState)))
                }
            }
            change(TransactionEditorChange.Busy(false))
        }
    }
}


/** What the person can type in edit mode; two equal forms mean nothing was changed. */
private data class EditorForm(
    val amountText: String,
    val date: LocalDate?,
    val categoryId: String?,
    val note: String,
    val details: String,
    val person: String,
    val cleared: Boolean,
) {
    companion object {
        fun of(state: TransactionEditorState) =
            EditorForm(state.amountText, state.date, state.categoryId, state.note, state.details, state.person, state.cleared)
    }
}
