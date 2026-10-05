package com.fares.saveme.feature.wallets.ui.list

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.wallets.domain.model.WalletList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** The wallet list: always the phone's copy, refreshed from the server on open and on pull (P-04.1, FR-007). */
class WalletsViewModel(
    list: Flow<WalletList>,
    private val refresh: suspend (Boolean) -> RefreshOutcome,
    private val canCreateAnother: suspend () -> Boolean,
    private val isEmailVerified: () -> Boolean,
    timeZoneId: Flow<String> = emptyFlow(),
    /** The wallet the person just left, until the line is dismissed (spec 013 FR-019). */
    leftNotice: Flow<String?> = emptyFlow(),
    private val dismissLeftNotice: () -> Unit = {},
) : MviViewModel<WalletsState, WalletsIntent, WalletsEffect>(WalletsState()) {

    init {
        leftNotice.onEach { name -> setState { reduce(it, WalletsChange.LeftNotice(name)) } }.launchIn(viewModelScope)
        list.onEach { stored -> setState { reduce(it, WalletsChange.Stored(stored)) } }.launchIn(viewModelScope)
        timeZoneId.onEach { id -> setState { reduce(it, WalletsChange.TimeZone(id)) } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: WalletsIntent) {
        when (intent) {
            WalletsIntent.Opened, WalletsIntent.Refresh -> pull()
            WalletsIntent.CreateClicked -> create()
            is WalletsIntent.WalletClicked -> sendEffect(WalletsEffect.OpenDetail(intent.id))
            WalletsIntent.DismissNotice -> setState { reduce(it, WalletsChange.NotEntitled(false)) }
            WalletsIntent.DismissLeftNotice -> dismissLeftNotice()
            WalletsIntent.Back -> sendEffect(WalletsEffect.NavigateBack)
        }
    }

    private fun create() {
        if (!isEmailVerified()) {
            sendEffect(WalletsEffect.RequireVerification)
            return
        }
        viewModelScope.launch {
            if (canCreateAnother()) {
                setState { reduce(it, WalletsChange.NotEntitled(false)) }
                sendEffect(WalletsEffect.OpenEditor)
            } else {
                setState { reduce(it, WalletsChange.NotEntitled(true)) }
            }
        }
    }

    /** A pull or an open always reads again, whatever the 60 second floor says (FR-007). */
    private fun pull() {
        if (currentState.refreshing) return
        setState { reduce(it, WalletsChange.Refreshing(true)) }
        viewModelScope.launch {
            val outcome = refresh(true)
            setState { reduce(it, WalletsChange.RefreshDone(offline = outcome == RefreshOutcome.Offline, failed = outcome == RefreshOutcome.Failed)) }
        }
    }
}
