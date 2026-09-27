package com.fares.saveme.feature.auth.ui.home

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.AuthState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class HomeViewModel(authState: StateFlow<AuthState>) :
    MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        authState.filterIsInstance<AuthState.SignedIn>()
            .onEach { signedIn -> setState { reduce(it, HomeChange.Account(signedIn)) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.DismissDeletionNotice -> setState { reduce(it, HomeChange.DeletionNoticeDismissed) }
        }
    }
}
