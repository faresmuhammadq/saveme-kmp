package com.fares.saveme.feature.wallets.ui.editor

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.wallets.domain.AmountInput
import com.fares.saveme.feature.wallets.domain.model.CreateResult
import com.fares.saveme.feature.wallets.domain.model.SaveResult
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletField
import com.fares.saveme.feature.wallets.domain.model.WalletView
import com.fares.saveme.feature.wallets.ui.asProblems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Creates or edits one wallet through domain lambdas only (P-16.3). Edit mode starts from what
 * the phone shows (a waiting change or the server copy) and remembers that version as the base.
 */
class WalletEditorViewModel(
    private val walletId: String?,
    private val observeWallet: (String) -> Flow<WalletView?>,
    private val defaultCurrency: suspend () -> String?,
    private val create: suspend (WalletDraft) -> CreateResult,
    private val save: suspend (String, WalletDraft, Long) -> SaveResult,
) : MviViewModel<WalletEditorState, WalletEditorIntent, WalletEditorEffect>(WalletEditorState(walletId = walletId)) {

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (walletId == null) {
            val currency = defaultCurrency() ?: FALLBACK_CURRENCY
            setState { reduce(it, WalletEditorChange.Loaded(it.copy(currency = currency))) }
            return
        }
        val view = observeWallet(walletId).first()
        if (view == null) {
            sendEffect(WalletEditorEffect.NavigateBack)
            return
        }
        val shown = view.shown
        val refused = (view.syncState as? SyncState.Refused)?.fields.orEmpty()
        setState {
            reduce(
                it,
                WalletEditorChange.Loaded(
                    it.copy(
                        type = shown.walletType,
                        typeWire = shown.type,
                        name = shown.name,
                        currency = shown.currency,
                        // A wallet with an unsent transaction is locked too: the server locks it as soon as one arrives (FR-029).
                        currencyLocked = view.server.currencyLocked || view.hasPendingPostings,
                        amountText = AmountInput.format(shown.openingBalanceMinor, shown.currency, shown.walletType.isLiability),
                        iconKey = shown.icon,
                        accentKey = shown.color,
                        errors = refused.asProblems(),
                        baseVersion = view.server.version,
                        archive = shown.archive,
                    ),
                ),
            )
        }
    }

    override fun onIntent(intent: WalletEditorIntent) {
        when (intent) {
            is WalletEditorIntent.TypeChosen -> setState { reduce(it, WalletEditorChange.TypeChosen(intent.type)) }
            is WalletEditorIntent.NameChanged -> setState { reduce(it, WalletEditorChange.Name(intent.value)) }
            WalletEditorIntent.CurrencyClicked -> if (currentState.currencyEditable) {
                setState { reduce(it, WalletEditorChange.CurrencyPicker(true)) }
            }
            WalletEditorIntent.DismissCurrencyPicker -> setState { reduce(it, WalletEditorChange.CurrencyPicker(false)) }
            is WalletEditorIntent.CurrencyChosen -> if (currentState.currencyEditable) {
                setState { reduce(it, WalletEditorChange.Currency(intent.currency)) }
            }
            is WalletEditorIntent.AmountChanged -> setState { reduce(it, WalletEditorChange.Amount(intent.value)) }
            is WalletEditorIntent.IconChosen -> setState { reduce(it, WalletEditorChange.Icon(intent.key)) }
            is WalletEditorIntent.AccentChosen -> setState { reduce(it, WalletEditorChange.Accent(intent.key)) }
            WalletEditorIntent.SaveClicked -> submit()
            WalletEditorIntent.Back -> sendEffect(WalletEditorEffect.NavigateBack)
        }
    }

    private fun submit() {
        val state = currentState
        if (!state.canSave) return
        val amount = AmountInput.parse(state.amountText, state.currency, state.isLiability)
        if (amount !is AmountInput.Result.Valid) {
            val problem = (amount as AmountInput.Result.Invalid).problem
            setState { reduce(it, WalletEditorChange.Errors(it.errors + (WalletField.OpeningBalance to problem))) }
            return
        }
        val draft = WalletDraft(
            type = state.typeWire,
            name = state.name.trim(),
            currency = state.currency,
            openingBalanceMinor = amount.minor,
            icon = state.iconKey,
            color = state.accentKey,
            archive = state.archive,
        )
        setState { reduce(it, WalletEditorChange.Saving(true)) }
        viewModelScope.launch {
            val id = state.walletId
            if (id == null) {
                when (val result = create(draft)) {
                    is CreateResult.Created -> sendEffect(WalletEditorEffect.Saved(result.id))
                    CreateResult.NotVerified -> sendEffect(WalletEditorEffect.RequireVerification)
                    CreateResult.NotEntitled -> sendEffect(WalletEditorEffect.NotEntitled)
                    is CreateResult.Invalid -> setState { reduce(it, WalletEditorChange.Errors(result.errors)) }
                }
            } else {
                when (val result = save(id, draft, state.baseVersion)) {
                    SaveResult.Saved -> sendEffect(WalletEditorEffect.Saved(id))
                    SaveResult.NotVerified -> sendEffect(WalletEditorEffect.RequireVerification)
                    SaveResult.NotOwner, SaveResult.Gone -> sendEffect(WalletEditorEffect.NavigateBack)
                    is SaveResult.Invalid -> setState { reduce(it, WalletEditorChange.Errors(result.errors)) }
                }
            }
            setState { reduce(it, WalletEditorChange.Saving(false)) }
        }
    }

    private companion object {
        /** The profile always has a currency once read; this only covers a phone that never read it. */
        const val FALLBACK_CURRENCY = "EGP"
    }
}
