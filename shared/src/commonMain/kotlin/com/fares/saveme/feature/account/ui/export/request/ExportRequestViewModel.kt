package com.fares.saveme.feature.account.ui.export.request

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.RequestOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Asks for an export (spec 016 FR-006, FR-007). One request per tap, none while one is in flight or a rate-limit wait is on;
 * never retried. [presetWalletId] opens the one-wallet scope with that wallet chosen. The clock and the wait timer live here,
 * never in `reduce`. Domain lambdas only (P-16.3).
 */
class ExportRequestViewModel(
    private val request: suspend (scope: ExportScope, walletId: String?) -> RequestOutcome,
    wallets: Flow<List<WalletChoice>> = emptyFlow(),
    unsentChanges: Flow<Boolean> = emptyFlow(),
    signedIn: Flow<Boolean> = emptyFlow(),
    private val presetWalletId: String? = null,
    private val now: () -> Instant = { Clock.System.now() },
) : MviViewModel<ExportRequestState, ExportRequestIntent, ExportRequestEffect>(
    ExportRequestState(
        scope = if (presetWalletId != null) ExportScope.Wallet else ExportScope.Account,
        walletId = presetWalletId,
    ),
) {

    private var waitTimer: Job? = null

    init {
        wallets.onEach { list -> setState { reduce(it, ExportRequestChange.Wallets(list)) } }.launchIn(viewModelScope)
        unsentChanges.onEach { unsent -> setState { reduce(it, ExportRequestChange.Unsent(unsent)) } }.launchIn(viewModelScope)
        signedIn.onEach { isSignedIn -> if (!isSignedIn) waitTimer?.cancel() }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: ExportRequestIntent) {
        when (intent) {
            is ExportRequestIntent.ScopeChosen -> setState { reduce(it, ExportRequestChange.Scope(intent.scope)) }
            ExportRequestIntent.WalletFieldClicked -> setState { reduce(it, ExportRequestChange.WalletPicker(true)) }
            ExportRequestIntent.WalletPickerDismissed -> setState { reduce(it, ExportRequestChange.WalletPicker(false)) }
            is ExportRequestIntent.WalletChosen -> setState { reduce(it, ExportRequestChange.Wallet(intent.id)) }
            ExportRequestIntent.Submit -> submit()
            ExportRequestIntent.SeeIt -> {
                val message = currentState.message as? ExportRequestMessage.InProgress ?: return
                sendEffect(message.currentId?.let { ExportRequestEffect.OpenDetail(it) } ?: ExportRequestEffect.OpenExports)
            }
            ExportRequestIntent.Back -> sendEffect(ExportRequestEffect.NavigateBack)
        }
    }

    private fun submit() {
        val state = currentState
        if (!state.canSubmit) return
        val walletId = if (state.scope == ExportScope.Wallet) state.chosenWallet?.id else null
        setState { reduce(it, ExportRequestChange.Submitting) }
        viewModelScope.launch {
            when (val outcome = request(state.scope, walletId)) {
                is RequestOutcome.Created -> {
                    setState { reduce(it, ExportRequestChange.Sent) }
                    sendEffect(ExportRequestEffect.OpenDetail(outcome.export.id))
                }
                is RequestOutcome.InProgress ->
                    refused(ExportRequestMessage.InProgress(outcome.current?.id))
                is RequestOutcome.RateLimited -> rateLimited(outcome.retryAfterSeconds)
                RequestOutcome.WalletGone -> refused(ExportRequestMessage.WalletGone)
                RequestOutcome.Offline -> refused(ExportRequestMessage.Offline)
                is RequestOutcome.Failed -> refused(ExportRequestMessage.Error(outcome.code, outcome.requestId))
            }
        }
    }

    private fun refused(message: ExportRequestMessage) = setState { reduce(it, ExportRequestChange.Refused(message)) }

    private fun rateLimited(seconds: Long?) {
        refused(ExportRequestMessage.RateLimited(seconds))
        val wait = seconds?.takeIf { it > 0 } ?: return
        waitTimer?.cancel()
        setState { reduce(it, ExportRequestChange.Blocked(now() + wait.seconds)) }
        waitTimer = viewModelScope.launch {
            delay(wait.seconds)
            setState { reduce(it, ExportRequestChange.WaitOver) }
        }
    }
}
