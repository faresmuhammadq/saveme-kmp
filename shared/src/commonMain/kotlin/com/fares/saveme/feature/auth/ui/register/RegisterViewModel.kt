package com.fares.saveme.feature.auth.ui.register

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.ClientValidation
import com.fares.saveme.feature.auth.domain.model.RegisterOutcome
import kotlinx.coroutines.launch

class RegisterViewModel(
    prefillEmail: String?,
    private val register: suspend (email: String, password: String, displayName: String?) -> RegisterOutcome,
) : MviViewModel<RegisterState, RegisterIntent, RegisterEffect>(RegisterState.initial(prefillEmail)) {

    override fun onIntent(intent: RegisterIntent) {
        when (intent) {
            is RegisterIntent.EmailChanged -> setState { reduce(it, RegisterChange.EmailEdited(intent.value)) }
            is RegisterIntent.PasswordChanged -> setState { reduce(it, RegisterChange.PasswordEdited(intent.value)) }
            is RegisterIntent.DisplayNameChanged -> setState { reduce(it, RegisterChange.DisplayNameEdited(intent.value)) }
            RegisterIntent.TogglePasswordVisibility -> setState { reduce(it, RegisterChange.VisibilityToggled) }
            RegisterIntent.Submit -> submit()
            RegisterIntent.SignInClicked -> sendEffect(RegisterEffect.NavigateSignIn(currentState.email.trim().ifEmpty { null }))
            RegisterIntent.ResetPasswordClicked -> sendEffect(RegisterEffect.NavigateForgotPassword(currentState.email.trim()))
            RegisterIntent.Back -> sendEffect(RegisterEffect.NavigateBack)
        }
    }

    private fun submit() {
        val state = currentState
        if (state.submitting) return
        val email = state.email.trim()
        val emailError = ClientValidation.validateEmail(email)
        val passwordError = ClientValidation.validateNewPassword(state.password, email)
        val displayNameError = ClientValidation.validateDisplayName(state.displayName)
        if (emailError != null || passwordError != null || displayNameError != null) {
            setState { reduce(it, RegisterChange.ClientInvalid(emailError, passwordError, displayNameError)) }
            return
        }
        setState { reduce(it, RegisterChange.Started) }
        viewModelScope.launch {
            val outcome = register(email, state.password, state.displayName.trim().ifEmpty { null })
            setState { reduce(it, RegisterChange.Finished(outcome)) }
            if (outcome == RegisterOutcome.Accepted) sendEffect(RegisterEffect.NavigateCheckInbox(email))
        }
    }
}
