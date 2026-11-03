package com.fares.saveme.feature.transactions.ui.adjust

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.transactions.domain.AdjustOutcome
import com.fares.saveme.feature.transactions.domain.model.AdjustTarget
import com.fares.saveme.feature.transactions.domain.model.CreateResult
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem
import com.fares.saveme.feature.wallets.domain.model.WalletFieldProblem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Corrects a wallet's current balance by recording a dated adjustment transaction (backend ADR-0028) instead of editing the
 * opening balance. It saves through the transactions service, so it queues and works offline like any other transaction.
 */
class BalanceAdjustViewModel(
    private val walletId: String,
    private val observeTarget: (String) -> Flow<AdjustTarget?>,
    private val observeFeedReadAt: (String) -> Flow<Instant?>,
    private val today: () -> LocalDate,
    private val create: suspend (TransactionDraft) -> CreateResult,
) : MviViewModel<BalanceAdjustState, BalanceAdjustIntent, BalanceAdjustEffect>(BalanceAdjustState(walletId = walletId)) {

    init {
        setState { it.copy(date = today()) }
        viewModelScope.launch {
            observeTarget(walletId).collect { target ->
                if (target == null) {
                    sendEffect(BalanceAdjustEffect.ShowMessage(AdjustMessage.NoLongerAvailable))
                    sendEffect(BalanceAdjustEffect.Close)
                } else {
                    setState { it.copy(target = target, loaded = true) }
                }
            }
        }
        viewModelScope.launch { observeFeedReadAt(walletId).collect { at -> setState { it.copy(feedReadAt = at) } } }
    }

    override fun onIntent(intent: BalanceAdjustIntent) {
        when (intent) {
            is BalanceAdjustIntent.ModeChosen -> setState { it.copy(mode = intent.mode, amountText = "", problem = null) }
            is BalanceAdjustIntent.DirectionChosen -> setState { it.copy(direction = intent.direction, problem = null) }
            is BalanceAdjustIntent.AmountChanged -> setState { it.copy(amountText = intent.text, problem = null) }
            BalanceAdjustIntent.DateClicked -> if (currentState.canEdit) setState { it.copy(datePickerOpen = true) }
            is BalanceAdjustIntent.DateChosen -> setState { it.copy(date = intent.date, datePickerOpen = false) }
            BalanceAdjustIntent.DatePickerDismissed -> setState { it.copy(datePickerOpen = false) }
            is BalanceAdjustIntent.NoteChanged -> setState { it.copy(note = intent.text) }
            BalanceAdjustIntent.SaveClicked -> save()
            BalanceAdjustIntent.Back -> sendEffect(BalanceAdjustEffect.Close)
        }
    }

    private fun save() {
        val state = currentState
        val target = state.target ?: return
        val date = state.date ?: return
        if (state.saving || !state.canEdit) return
        val outcome = state.outcome
        if (outcome !is AdjustOutcome.Ready) {
            setState { it.copy(problem = problemOf(outcome)) }
            return
        }
        setState { it.copy(saving = true) }
        viewModelScope.launch {
            val draft = TransactionDraft(
                walletId = target.walletId,
                kindWire = outcome.adjustment.kind.wire,
                date = date,
                amountMinor = outcome.adjustment.amountMinor,
                currency = target.currency,
                categoryId = null,
                counterWalletId = null,
                description = state.note.trim().ifEmpty { null },
                memo = null,
                counterparty = null,
                cleared = false,
            )
            when (val result = create(draft)) {
                is CreateResult.Created -> sendEffect(BalanceAdjustEffect.Close)
                CreateResult.NotVerified -> sendEffect(BalanceAdjustEffect.RequireVerification)
                CreateResult.NotAllowed -> sendEffect(BalanceAdjustEffect.ShowMessage(AdjustMessage.NotAllowed))
                CreateResult.NoWallet -> {
                    sendEffect(BalanceAdjustEffect.ShowMessage(AdjustMessage.NoLongerAvailable))
                    sendEffect(BalanceAdjustEffect.Close)
                }
                is CreateResult.Invalid -> setState { it.copy(problem = result.errors.values.firstOrNull()) }
            }
            setState { it.copy(saving = false) }
        }
    }

    /** What the person is told about an amount that cannot be saved; null when there is nothing to say. */
    private fun problemOf(outcome: AdjustOutcome?): TransactionFieldProblem? = when (outcome) {
        null -> TransactionFieldProblem.AmountEmpty
        AdjustOutcome.NoChange -> TransactionFieldProblem.AmountNotPositive
        is AdjustOutcome.Invalid -> when (val problem = outcome.problem) {
            WalletFieldProblem.AmountEmpty -> TransactionFieldProblem.AmountEmpty
            WalletFieldProblem.AmountOutOfRange -> TransactionFieldProblem.AmountOutOfRange
            is WalletFieldProblem.AmountDigits -> TransactionFieldProblem.AmountDigits(problem.digits)
            else -> TransactionFieldProblem.AmountInvalid
        }
        is AdjustOutcome.Ready -> null
    }
}
