package com.fares.saveme.feature.account.ui.deleteaccount

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.account.domain.model.DeletionOutcome
import com.fares.saveme.feature.auth.ui.minutesToWait
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class DeleteAccountViewModel(
    private val requestDeletion: suspend (password: String) -> DeletionOutcome,
) : MviViewModel<DeleteAccountState, DeleteAccountIntent, DeleteAccountEffect>(DeleteAccountState()) {

    override fun onIntent(intent: DeleteAccountIntent) {
        when (intent) {
            DeleteAccountIntent.Continue -> setState { reduce(it, DeleteAccountChange.ToConfirm) }
            is DeleteAccountIntent.PasswordChanged -> setState { reduce(it, DeleteAccountChange.Password(intent.value)) }
            DeleteAccountIntent.TogglePasswordVisible -> setState { reduce(it, DeleteAccountChange.ToggleVisible) }
            DeleteAccountIntent.Confirm -> confirm()
            DeleteAccountIntent.Done -> sendEffect(DeleteAccountEffect.Finished)
            DeleteAccountIntent.Back -> sendEffect(DeleteAccountEffect.NavigateBack)
        }
    }

    private fun confirm() {
        val state = currentState
        if (state.step != DeleteStep.Confirm || state.submitting || state.waitMinutes != null) return
        if (state.password.isEmpty()) {
            setState { reduce(it, DeleteAccountChange.MissingPassword) }
            return
        }
        setState { reduce(it, DeleteAccountChange.Submitting) }
        viewModelScope.launch {
            val outcome = requestDeletion(state.password)
            val wait = (outcome as? DeletionOutcome.Locked)?.retryAfterSeconds
            setState { reduce(it, DeleteAccountChange.Result(outcome, minutesToWait(wait))) }
            if (wait != null) {
                delay(wait.seconds)
                setState { reduce(it, DeleteAccountChange.WaitOver) }
            }
        }
    }
}
