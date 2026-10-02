package com.fares.saveme.feature.account.ui.account

import com.fares.saveme.core.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.AuthState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AccountViewModel(
    authState: StateFlow<AuthState>,
    private val signOut: suspend () -> Unit,
    private val refresh: suspend () -> Unit = {},
    syncStatus: Flow<SyncStatus> = emptyFlow(),
    private val syncNow: suspend () -> Unit = {},
) : MviViewModel<AccountState, AccountIntent, AccountEffect>(AccountState()) {

    init {
        authState.filterIsInstance<AuthState.SignedIn>()
            .onEach { signedIn -> setState { reduce(it, AccountChange.Account(signedIn)) } }
            .launchIn(viewModelScope)
        syncStatus.onEach { status -> setState { reduce(it, AccountChange.Sync(status)) } }.launchIn(viewModelScope)
        // The account area is a refresh trigger for profile and account state (spec FR-011, FR-021).
        viewModelScope.launch { refresh() }
    }

    override fun onIntent(intent: AccountIntent) {
        when (intent) {
            is AccountIntent.Open -> sendEffect(AccountEffect.Navigate(intent.destination))
            AccountIntent.Back -> sendEffect(AccountEffect.NavigateBack)
            AccountIntent.SignOutClicked -> setState { reduce(it, AccountChange.SignOutConfirmVisible(true)) }
            AccountIntent.SignOutDismissed -> setState { reduce(it, AccountChange.SignOutConfirmVisible(false)) }
            AccountIntent.SyncNowClicked -> viewModelScope.launch { syncNow() }
            AccountIntent.SignOutConfirmed -> if (!currentState.signingOut) {
                setState { reduce(it, AccountChange.SigningOut) }
                viewModelScope.launch { signOut() }
            }
        }
    }
}
