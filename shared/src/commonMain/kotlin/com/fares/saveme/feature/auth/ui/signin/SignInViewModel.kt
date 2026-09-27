package com.fares.saveme.feature.auth.ui.signin

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.ClientValidation
import com.fares.saveme.feature.auth.domain.model.SignInOutcome
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class SignInViewModel(
    args: SignInArgs,
    private val signIn: suspend (email: String, password: String, attemptSessionId: String) -> SignInOutcome,
    private val newSessionId: () -> String = { Uuid.generateV7().toString() },
) : MviViewModel<SignInState, SignInIntent, SignInEffect>(SignInState.initial(args)) {

    override fun onIntent(intent: SignInIntent) {
        when (intent) {
            is SignInIntent.EmailChanged -> setState { reduce(it, SignInChange.EmailEdited(intent.value)) }
            is SignInIntent.PasswordChanged -> setState { reduce(it, SignInChange.PasswordEdited(intent.value)) }
            SignInIntent.TogglePasswordVisibility -> setState { reduce(it, SignInChange.VisibilityToggled) }
            SignInIntent.Submit -> submit()
            SignInIntent.ForgotPasswordClicked -> sendEffect(SignInEffect.NavigateForgotPassword(currentState.email.trim()))
            SignInIntent.CreateAccountClicked -> sendEffect(SignInEffect.NavigateRegister)
            SignInIntent.Back -> sendEffect(SignInEffect.NavigateBack)
        }
    }

    private fun submit() {
        val state = currentState
        if (state.submitting) return
        val emailError = ClientValidation.validateEmail(state.email)
        val passwordError = ClientValidation.validateExistingPassword(state.password)
        if (emailError != null || passwordError != null) {
            setState { reduce(it, SignInChange.ClientInvalid(emailError, passwordError)) }
            return
        }
        // Generated here, never in the reducer, which must stay pure (P-16.4).
        val sessionId = state.attemptSessionId ?: newSessionId()
        setState { reduce(it, SignInChange.AttemptStarted(sessionId)) }
        viewModelScope.launch {
            val outcome = signIn(state.email, state.password, sessionId)
            setState { reduce(it, SignInChange.Finished(outcome)) }
        }
    }
}
