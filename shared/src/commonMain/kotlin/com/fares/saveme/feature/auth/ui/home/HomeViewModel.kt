package com.fares.saveme.feature.auth.ui.home

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.core.sync.SyncStatus
import com.fares.saveme.feature.auth.domain.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.fares.saveme.feature.auth.domain.model.ResendOutcome
import com.fares.saveme.feature.auth.ui.verify.launchResend
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HomeViewModel(
    authState: StateFlow<AuthState>,
    private val signOut: suspend () -> Unit = {},
    private val cancelDeletion: suspend () -> CancelDeletionResult = { CancelDeletionResult.Failed },
    private val resendVerification: suspend () -> ResendOutcome = { ResendOutcome.Offline },
    syncStatus: Flow<SyncStatus> = emptyFlow(),
    private val syncNow: suspend () -> Unit = {},
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        authState.filterIsInstance<AuthState.SignedIn>()
            .onEach { signedIn -> setState { reduce(it, HomeChange.Account(signedIn)) } }
            .launchIn(viewModelScope)
        syncStatus.onEach { status -> setState { reduce(it, HomeChange.Sync(status)) } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.DismissDeletionNotice -> setState { reduce(it, HomeChange.DeletionNoticeDismissed) }
            HomeIntent.AccountClicked -> sendEffect(HomeEffect.NavigateAccount)
            HomeIntent.WalletsClicked -> sendEffect(HomeEffect.NavigateWallets)
            HomeIntent.CancelDeletion -> cancel()
            HomeIntent.SignOutClicked -> setState { reduce(it, HomeChange.SignOutConfirmVisible(true)) }
            HomeIntent.SignOutDismissed -> setState { reduce(it, HomeChange.SignOutConfirmVisible(false)) }
            HomeIntent.SyncNowClicked -> viewModelScope.launch { syncNow() }
            HomeIntent.ResendClicked -> viewModelScope.launchResend(
                current = { currentState.resend },
                apply = { change -> setState { reduce(it, HomeChange.Resend(change)) } },
                resend = resendVerification,
            )
            HomeIntent.SignOutConfirmed -> if (!currentState.signingOut) {
                setState { reduce(it, HomeChange.SigningOut) }
                viewModelScope.launch { signOut() }
            }
        }
    }

    private fun cancel() {
        if (currentState.cancellingDeletion) return
        setState { reduce(it, HomeChange.CancellingDeletion(true)) }
        viewModelScope.launch {
            val message = when (cancelDeletion()) {
                CancelDeletionResult.Done -> HomeMessage.DeletionCancelled
                CancelDeletionResult.Offline -> HomeMessage.Offline
                CancelDeletionResult.Failed -> HomeMessage.Failed
            }
            setState { reduce(it, HomeChange.CancellingDeletion(false)) }
            sendEffect(HomeEffect.ShowMessage(message))
        }
    }
}
