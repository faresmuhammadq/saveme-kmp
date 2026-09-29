package com.fares.saveme.feature.account.ui.changepassword

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.account.domain.model.ChangePasswordOutcome
import com.fares.saveme.feature.auth.ui.minutesToWait
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class ChangePasswordViewModel(
    private val email: () -> String,
    private val changePassword: suspend (current: String, new: String) -> ChangePasswordOutcome,
) : MviViewModel<ChangePasswordState, ChangePasswordIntent, ChangePasswordEffect>(ChangePasswordState()) {

    override fun onIntent(intent: ChangePasswordIntent) {
        when (intent) {
            is ChangePasswordIntent.CurrentChanged -> setState { reduce(it, ChangePasswordChange.Current(intent.value)) }
            is ChangePasswordIntent.NewChanged -> setState { reduce(it, ChangePasswordChange.New(intent.value)) }
            ChangePasswordIntent.ToggleCurrentVisible -> setState { reduce(it, ChangePasswordChange.ToggleCurrent) }
            ChangePasswordIntent.ToggleNewVisible -> setState { reduce(it, ChangePasswordChange.ToggleNew) }
            ChangePasswordIntent.Submit -> submit()
            ChangePasswordIntent.Back -> sendEffect(ChangePasswordEffect.NavigateBack)
        }
    }

    private fun submit() {
        val state = currentState
        if (state.submitting || state.waitMinutes != null || state.done) return
        validate(state, email())?.let { invalid -> setState { reduce(it, invalid) }; return }
        setState { reduce(it, ChangePasswordChange.Submitting) }
        viewModelScope.launch {
            val outcome = changePassword(state.current, state.new)
            val wait = when (outcome) {
                is ChangePasswordOutcome.Locked -> outcome.retryAfterSeconds
                is ChangePasswordOutcome.RateLimited -> outcome.retryAfterSeconds
                else -> null
            }
            setState { reduce(it, ChangePasswordChange.Result(outcome, minutesToWait(wait))) }
            if (wait != null) {
                delay(wait.seconds)
                setState { reduce(it, ChangePasswordChange.WaitOver) }
            }
        }
    }
}
