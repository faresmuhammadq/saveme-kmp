package com.fares.saveme.feature.account.ui.sessions

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.model.DeviceSessionList
import com.fares.saveme.feature.account.ui.AccountBanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DeviceSessionsViewModel(
    sessions: Flow<DeviceSessionList>,
    timeZoneId: Flow<String>,
    private val refresh: suspend () -> ActionOutcome,
    private val endSession: suspend (String) -> ActionOutcome,
    private val endOthers: suspend () -> ActionOutcome,
) : MviViewModel<DeviceSessionsState, DeviceSessionsIntent, DeviceSessionsEffect>(DeviceSessionsState()) {

    init {
        sessions.onEach { list -> setState { reduce(it, DeviceSessionsChange.Stored(list)) } }.launchIn(viewModelScope)
        timeZoneId.onEach { id -> setState { reduce(it, DeviceSessionsChange.TimeZone(id)) } }.launchIn(viewModelScope)
        pull()
    }

    override fun onIntent(intent: DeviceSessionsIntent) {
        when (intent) {
            DeviceSessionsIntent.Refresh -> pull()
            is DeviceSessionsIntent.EndClicked -> {
                val session = currentState.sessions.firstOrNull { it.id == intent.id && !it.isCurrent } ?: return
                setState { reduce(it, DeviceSessionsChange.Confirm(ConfirmEnd.One(session.id, session.deviceName))) }
            }
            DeviceSessionsIntent.EndOthersClicked -> if (currentState.canEndOthers) {
                setState { reduce(it, DeviceSessionsChange.Confirm(ConfirmEnd.Others)) }
            }
            DeviceSessionsIntent.DismissConfirm -> if (!currentState.ending) {
                setState { reduce(it, DeviceSessionsChange.Confirm(null)) }
            }
            DeviceSessionsIntent.ConfirmEnd -> end()
            DeviceSessionsIntent.Back -> sendEffect(DeviceSessionsEffect.NavigateBack)
        }
    }

    private fun pull() {
        if (currentState.refreshing) return
        setState { reduce(it, DeviceSessionsChange.Refreshing(true)) }
        viewModelScope.launch {
            val outcome = refresh()
            setState { reduce(reduce(it, DeviceSessionsChange.Refreshing(false)), DeviceSessionsChange.Banner(outcome.banner())) }
        }
    }

    private fun end() {
        val confirm = currentState.confirm ?: return
        if (currentState.ending) return
        setState { reduce(it, DeviceSessionsChange.Ending(true)) }
        viewModelScope.launch {
            val outcome = when (confirm) {
                is ConfirmEnd.One -> endSession(confirm.id)
                ConfirmEnd.Others -> endOthers()
            }
            setState {
                val done = reduce(it, DeviceSessionsChange.Ending(false))
                reduce(reduce(done, DeviceSessionsChange.Confirm(null)), DeviceSessionsChange.Banner(outcome.banner()))
            }
            if (outcome == ActionOutcome.Done) sendEffect(DeviceSessionsEffect.Ended(others = confirm == ConfirmEnd.Others))
        }
    }
}

private fun ActionOutcome.banner(): AccountBanner? = when (this) {
    ActionOutcome.Done -> null
    ActionOutcome.Offline -> AccountBanner.Offline
    is ActionOutcome.Error -> AccountBanner.Error(code, requestId)
}
