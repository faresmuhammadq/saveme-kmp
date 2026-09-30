package com.fares.saveme.feature.auth.ui.password.reset

import com.fares.saveme.feature.auth.domain.FieldError
import com.fares.saveme.feature.auth.domain.model.AuthField
import com.fares.saveme.feature.auth.domain.model.ResetOutcome
import com.fares.saveme.feature.auth.ui.FieldProblem
import com.fares.saveme.feature.auth.ui.FormBanner
import com.fares.saveme.feature.auth.ui.minutesToWait

enum class ResetStatus { Editing, Done, LinkInvalid }

data class ResetPasswordState(
    val password: String = "",
    val passwordVisible: Boolean = false,
    val passwordError: FieldProblem? = null,
    val submitting: Boolean = false,
    val status: ResetStatus = ResetStatus.Editing,
    val banner: FormBanner? = null,
) {
    override fun toString() = "ResetPasswordState(password=<redacted>, submitting=$submitting, status=$status, banner=$banner)"
}

sealed interface ResetPasswordIntent {
    data class PasswordChanged(val value: String) : ResetPasswordIntent
    data object TogglePasswordVisibility : ResetPasswordIntent
    data object Submit : ResetPasswordIntent
    data object RequestNewLinkClicked : ResetPasswordIntent
    data object ContinueClicked : ResetPasswordIntent
    data object Back : ResetPasswordIntent
}

sealed interface ResetPasswordEffect {
    data object NavigateSignIn : ResetPasswordEffect
    data object NavigateForgotPassword : ResetPasswordEffect
    data object NavigateBack : ResetPasswordEffect
}

sealed interface ResetPasswordChange {
    data class Edited(val value: String) : ResetPasswordChange
    data object VisibilityToggled : ResetPasswordChange
    data class ClientInvalid(val error: FieldError) : ResetPasswordChange
    data object Started : ResetPasswordChange
    data class Finished(val outcome: ResetOutcome) : ResetPasswordChange
}

fun reduce(state: ResetPasswordState, change: ResetPasswordChange): ResetPasswordState = when (change) {
    is ResetPasswordChange.Edited -> state.copy(password = change.value, passwordError = null)
    ResetPasswordChange.VisibilityToggled -> state.copy(passwordVisible = !state.passwordVisible)
    is ResetPasswordChange.ClientInvalid -> state.copy(passwordError = FieldProblem.Client(change.error))
    ResetPasswordChange.Started -> state.copy(submitting = true, banner = null)
    is ResetPasswordChange.Finished -> state.copy(submitting = false).let { s ->
        when (val outcome = change.outcome) {
            ResetOutcome.Done -> s.copy(password = "", status = ResetStatus.Done)
            ResetOutcome.LinkInvalid -> s.copy(password = "", status = ResetStatus.LinkInvalid)
            is ResetOutcome.Validation -> s.copy(
                passwordError = outcome.fieldErrors[AuthField.Password]?.let(FieldProblem::Server),
                banner = FormBanner.Validation,
            )
            is ResetOutcome.RateLimited -> s.copy(banner = FormBanner.RateLimited(minutesToWait(outcome.retryAfterSeconds)))
            ResetOutcome.Offline -> s.copy(banner = FormBanner.Offline)
            is ResetOutcome.Error -> s.copy(banner = FormBanner.Error(outcome.code, outcome.requestId))
        }
    }
}
