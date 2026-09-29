package com.fares.saveme.feature.auth.ui.home

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.AuthState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HomeViewModel(
    authState: StateFlow<AuthState>,
    private val signOut: suspend () -> Unit = {},
    private val cancelDeletion: suspend () -> CancelDeletionResult = { CancelDeletionResult.Failed },
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        authState.filterIsInstance<AuthState.SignedIn>()
            .onEach { signedIn -> setState { reduce(it, HomeChange.Account(signedIn)) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.DismissDeletionNotice -> setState { reduce(it, HomeChange.DeletionNoticeDismissed) }
            HomeIntent.AccountClicked -> sendEffect(HomeEffect.NavigateAccount)
            HomeIntent.CancelDeletion -> cancel()
            HomeIntent.SignOutClicked -> setState { reduce(it, HomeChange.SignOutConfirmVisible(true)) }
            HomeIntent.SignOutDismissed -> setState { reduce(it, HomeChange.SignOutConfirmVisible(false)) }
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
