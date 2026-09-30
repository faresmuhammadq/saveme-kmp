package com.fares.saveme.feature.auth.ui.register

import com.fares.saveme.feature.auth.domain.FieldError
import com.fares.saveme.feature.auth.domain.model.AuthField
import com.fares.saveme.feature.auth.domain.model.RegisterOutcome
import com.fares.saveme.feature.auth.ui.FieldProblem
import com.fares.saveme.feature.auth.ui.FormBanner
import com.fares.saveme.feature.auth.ui.minutesToWait

data class RegisterState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val displayName: String = "",
    val emailError: FieldProblem? = null,
    val passwordError: FieldProblem? = null,
    val displayNameError: FieldProblem? = null,
    val submitting: Boolean = false,
    /** 409: the address already has an account; the screen offers sign-in or reset (FR-005). */
    val alreadyRegistered: Boolean = false,
    val banner: FormBanner? = null,
) {
    override fun toString() = "RegisterState(email=$email, password=<redacted>, displayName=$displayName, " +
        "submitting=$submitting, alreadyRegistered=$alreadyRegistered, banner=$banner)"

    companion object {
        fun initial(prefillEmail: String?) = RegisterState(email = prefillEmail.orEmpty())
    }
}

sealed interface RegisterIntent {
    data class EmailChanged(val value: String) : RegisterIntent
    data class PasswordChanged(val value: String) : RegisterIntent
    data class DisplayNameChanged(val value: String) : RegisterIntent
    data object TogglePasswordVisibility : RegisterIntent
    data object Submit : RegisterIntent
    data object SignInClicked : RegisterIntent
    data object ResetPasswordClicked : RegisterIntent
    data object Back : RegisterIntent
}

sealed interface RegisterEffect {
    data class NavigateCheckInbox(val email: String) : RegisterEffect
    data class NavigateSignIn(val email: String?) : RegisterEffect
    data class NavigateForgotPassword(val email: String) : RegisterEffect
    data object NavigateBack : RegisterEffect
}

sealed interface RegisterChange {
    data class EmailEdited(val value: String) : RegisterChange
    data class PasswordEdited(val value: String) : RegisterChange
    data class DisplayNameEdited(val value: String) : RegisterChange
    data object VisibilityToggled : RegisterChange
    data class ClientInvalid(val email: FieldError?, val password: FieldError?, val displayName: FieldError?) : RegisterChange
    data object Started : RegisterChange
    data class Finished(val outcome: RegisterOutcome) : RegisterChange
}

fun reduce(state: RegisterState, change: RegisterChange): RegisterState = when (change) {
    // A different address can't be "already registered" any more.
    is RegisterChange.EmailEdited -> state.copy(email = change.value, emailError = null, alreadyRegistered = false)
    is RegisterChange.PasswordEdited -> state.copy(password = change.value, passwordError = null)
    is RegisterChange.DisplayNameEdited -> state.copy(displayName = change.value, displayNameError = null)
    RegisterChange.VisibilityToggled -> state.copy(passwordVisible = !state.passwordVisible)
    is RegisterChange.ClientInvalid -> state.copy(
        emailError = change.email?.let(FieldProblem::Client),
        passwordError = change.password?.let(FieldProblem::Client),
        displayNameError = change.displayName?.let(FieldProblem::Client),
    )
    RegisterChange.Started -> state.copy(submitting = true, banner = null, alreadyRegistered = false)
    is RegisterChange.Finished -> finish(state.copy(submitting = false), change.outcome)
}

private fun finish(state: RegisterState, outcome: RegisterOutcome): RegisterState = when (outcome) {
    RegisterOutcome.Accepted -> state.copy(password = "")
    RegisterOutcome.AlreadyRegistered -> state.copy(password = "", alreadyRegistered = true)
    is RegisterOutcome.Validation -> state.copy(
        emailError = outcome.fieldErrors[AuthField.Email]?.let(FieldProblem::Server),
        passwordError = outcome.fieldErrors[AuthField.Password]?.let(FieldProblem::Server),
        displayNameError = outcome.fieldErrors[AuthField.DisplayName]?.let(FieldProblem::Server),
        banner = FormBanner.Validation,
    )
    is RegisterOutcome.RateLimited -> state.copy(banner = FormBanner.RateLimited(minutesToWait(outcome.retryAfterSeconds)))
    RegisterOutcome.Offline -> state.copy(banner = FormBanner.Offline)
    is RegisterOutcome.Error -> state.copy(banner = FormBanner.Error(outcome.code, outcome.requestId))
}
