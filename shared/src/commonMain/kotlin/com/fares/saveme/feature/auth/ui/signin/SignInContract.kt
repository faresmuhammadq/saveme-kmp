package com.fares.saveme.feature.auth.ui.signin

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.auth.domain.FieldError
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.auth.domain.model.AuthField
import com.fares.saveme.feature.auth.domain.model.SignInOutcome
import com.fares.saveme.feature.auth.ui.FieldProblem
import com.fares.saveme.feature.auth.ui.minutesToWait

/** Route arguments, passed to the store through Koin parameters. */
data class SignInArgs(val prefillEmail: String? = null, val reason: SignOutReason? = null)

sealed interface SignInBanner {
    data object InvalidCredentials : SignInBanner
    data class Locked(val minutes: Int?) : SignInBanner
    data class RateLimited(val minutes: Int?) : SignInBanner
    data object Validation : SignInBanner
    data object Offline : SignInBanner
    data object SessionEnded : SignInBanner
    data object PasswordReset : SignInBanner
    data object PlatformUnsupported : SignInBanner
    data class Error(val code: ApiErrorCode, val requestId: String?) : SignInBanner
}

data class SignInState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val emailError: FieldProblem? = null,
    val passwordError: FieldProblem? = null,
    val submitting: Boolean = false,
    val banner: SignInBanner? = null,
    /** One per attempt; reused on retry so the server sees the same session (identity.yaml signIn). */
    val attemptSessionId: String? = null,
) {
    companion object {
        fun initial(args: SignInArgs) = SignInState(
            email = args.prefillEmail.orEmpty(),
            banner = when (args.reason) {
                SignOutReason.SessionEnded -> SignInBanner.SessionEnded
                SignOutReason.PasswordReset -> SignInBanner.PasswordReset
                else -> null
            },
        )
    }
}

sealed interface SignInIntent {
    data class EmailChanged(val value: String) : SignInIntent
    data class PasswordChanged(val value: String) : SignInIntent
    data object TogglePasswordVisibility : SignInIntent
    data object Submit : SignInIntent
    data object ForgotPasswordClicked : SignInIntent
    data object CreateAccountClicked : SignInIntent
    data object Back : SignInIntent
}

sealed interface SignInEffect {
    data class NavigateForgotPassword(val email: String) : SignInEffect
    data object NavigateRegister : SignInEffect
    data object NavigateBack : SignInEffect
}

sealed interface SignInChange {
    data class EmailEdited(val value: String) : SignInChange
    data class PasswordEdited(val value: String) : SignInChange
    data object VisibilityToggled : SignInChange
    data class ClientInvalid(val email: FieldError?, val password: FieldError?) : SignInChange
    data class AttemptStarted(val sessionId: String) : SignInChange
    data class Finished(val outcome: SignInOutcome) : SignInChange
}

fun reduce(state: SignInState, change: SignInChange): SignInState = when (change) {
    // A different address is a different attempt: the next submit gets a fresh session id.
    is SignInChange.EmailEdited -> state.copy(email = change.value, emailError = null, attemptSessionId = null)
    is SignInChange.PasswordEdited -> state.copy(password = change.value, passwordError = null)
    SignInChange.VisibilityToggled -> state.copy(passwordVisible = !state.passwordVisible)
    is SignInChange.ClientInvalid -> state.copy(
        emailError = change.email?.let(FieldProblem::Client),
        passwordError = change.password?.let(FieldProblem::Client),
    )
    is SignInChange.AttemptStarted -> state.copy(submitting = true, attemptSessionId = change.sessionId, banner = null)
    is SignInChange.Finished -> finish(state.copy(submitting = false), change.outcome)
}

private fun finish(state: SignInState, outcome: SignInOutcome): SignInState = when (outcome) {
    is SignInOutcome.Success -> state.copy(password = "")
    // Same message for a wrong password and an unknown address (uniform answers, P-09.4).
    SignInOutcome.InvalidCredentials -> state.copy(password = "", banner = SignInBanner.InvalidCredentials)
    is SignInOutcome.Locked -> state.copy(password = "", banner = SignInBanner.Locked(minutesToWait(outcome.retryAfterSeconds)))
    is SignInOutcome.RateLimited -> state.copy(banner = SignInBanner.RateLimited(minutesToWait(outcome.retryAfterSeconds)))
    is SignInOutcome.Validation -> state.copy(
        emailError = outcome.fieldErrors[AuthField.Email]?.let(FieldProblem::Server),
        passwordError = outcome.fieldErrors[AuthField.Password]?.let(FieldProblem::Server),
        banner = SignInBanner.Validation,
    )
    SignInOutcome.UpdateRequired -> state
    SignInOutcome.Offline -> state.copy(banner = SignInBanner.Offline)
    SignInOutcome.PlatformUnsupported -> state.copy(banner = SignInBanner.PlatformUnsupported)
    is SignInOutcome.Error -> state.copy(banner = SignInBanner.Error(outcome.code, outcome.requestId))
}
