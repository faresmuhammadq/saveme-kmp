package com.fares.saveme.feature.auth.ui.password.reset

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.ClientValidation
import com.fares.saveme.feature.auth.domain.model.ResetOutcome
import kotlinx.coroutines.launch

/** The link secret lives only here and is never printed (FR-009). */
class ResetPasswordViewModel(
    private val token: String,
    private val reset: suspend (token: String, password: String) -> ResetOutcome,
) : MviViewModel<ResetPasswordState, ResetPasswordIntent, ResetPasswordEffect>(ResetPasswordState()) {

    override fun onIntent(intent: ResetPasswordIntent) {
        when (intent) {
            is ResetPasswordIntent.PasswordChanged -> setState { reduce(it, ResetPasswordChange.Edited(intent.value)) }
            ResetPasswordIntent.TogglePasswordVisibility -> setState { reduce(it, ResetPasswordChange.VisibilityToggled) }
            ResetPasswordIntent.Submit -> submit()
            ResetPasswordIntent.RequestNewLinkClicked -> sendEffect(ResetPasswordEffect.NavigateForgotPassword)
            ResetPasswordIntent.ContinueClicked -> sendEffect(ResetPasswordEffect.NavigateSignIn)
            ResetPasswordIntent.Back -> sendEffect(ResetPasswordEffect.NavigateBack)
        }
    }

    private fun submit() {
        val state = currentState
        if (state.submitting || state.status != ResetStatus.Editing) return
        // The email isn't known here, so only length is checked locally; the server checks the rest.
        ClientValidation.validateNewPassword(state.password, email = null)?.let { error ->
            setState { reduce(it, ResetPasswordChange.ClientInvalid(error)) }
            return
        }
        setState { reduce(it, ResetPasswordChange.Started) }
        viewModelScope.launch {
            val outcome = reset(token, state.password)
            setState { reduce(it, ResetPasswordChange.Finished(outcome)) }
        }
    }

    override fun toString() = "ResetPasswordViewModel(state=$currentState)"
}
