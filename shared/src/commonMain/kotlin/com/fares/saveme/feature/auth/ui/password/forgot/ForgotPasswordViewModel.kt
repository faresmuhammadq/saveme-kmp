package com.fares.saveme.feature.auth.ui.password.forgot

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.ClientValidation
import com.fares.saveme.feature.auth.domain.model.ForgotOutcome
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(
    prefillEmail: String?,
    private val requestReset: suspend (email: String) -> ForgotOutcome,
) : MviViewModel<ForgotPasswordState, ForgotPasswordIntent, ForgotPasswordEffect>(
    ForgotPasswordState(email = prefillEmail.orEmpty()),
) {
    override fun onIntent(intent: ForgotPasswordIntent) {
        when (intent) {
            is ForgotPasswordIntent.EmailChanged -> setState { reduce(it, ForgotPasswordChange.Edited(intent.value)) }
            ForgotPasswordIntent.Submit -> submit()
            ForgotPasswordIntent.Back -> sendEffect(ForgotPasswordEffect.NavigateBack)
        }
    }

    private fun submit() {
        if (currentState.submitting) return
        val email = currentState.email.trim()
        ClientValidation.validateEmail(email)?.let { error ->
            setState { reduce(it, ForgotPasswordChange.ClientInvalid(error)) }
            return
        }
        setState { reduce(it, ForgotPasswordChange.Started) }
        viewModelScope.launch {
            val outcome = requestReset(email)
            setState { reduce(it, ForgotPasswordChange.Finished(outcome)) }
            if (outcome == ForgotOutcome.Sent) sendEffect(ForgotPasswordEffect.NavigateCheckInbox(email))
        }
    }
}
