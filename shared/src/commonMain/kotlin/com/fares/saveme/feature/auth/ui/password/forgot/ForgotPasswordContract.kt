package com.fares.saveme.feature.auth.ui.password.forgot

import com.fares.saveme.feature.auth.domain.FieldError
import com.fares.saveme.feature.auth.domain.model.AuthField
import com.fares.saveme.feature.auth.domain.model.ForgotOutcome
import com.fares.saveme.feature.auth.ui.FieldProblem
import com.fares.saveme.feature.auth.ui.FormBanner
import com.fares.saveme.feature.auth.ui.minutesToWait

data class ForgotPasswordState(
    val email: String = "",
    val emailError: FieldProblem? = null,
    val submitting: Boolean = false,
    val banner: FormBanner? = null,
)

sealed interface ForgotPasswordIntent {
    data class EmailChanged(val value: String) : ForgotPasswordIntent
    data object Submit : ForgotPasswordIntent
    data object Back : ForgotPasswordIntent
}

sealed interface ForgotPasswordEffect {
    data class NavigateCheckInbox(val email: String) : ForgotPasswordEffect
    data object NavigateBack : ForgotPasswordEffect
}

sealed interface ForgotPasswordChange {
    data class Edited(val value: String) : ForgotPasswordChange
    data class ClientInvalid(val error: FieldError) : ForgotPasswordChange
    data object Started : ForgotPasswordChange
    data class Finished(val outcome: ForgotOutcome) : ForgotPasswordChange
}

fun reduce(state: ForgotPasswordState, change: ForgotPasswordChange): ForgotPasswordState = when (change) {
    is ForgotPasswordChange.Edited -> state.copy(email = change.value, emailError = null)
    is ForgotPasswordChange.ClientInvalid -> state.copy(emailError = FieldProblem.Client(change.error))
    ForgotPasswordChange.Started -> state.copy(submitting = true, banner = null)
    is ForgotPasswordChange.Finished -> state.copy(submitting = false).let { s ->
        when (val outcome = change.outcome) {
            ForgotOutcome.Sent -> s
            is ForgotOutcome.Validation -> s.copy(
                emailError = outcome.fieldErrors[AuthField.Email]?.let(FieldProblem::Server),
                banner = FormBanner.Validation,
            )
            is ForgotOutcome.RateLimited -> s.copy(banner = FormBanner.RateLimited(minutesToWait(outcome.retryAfterSeconds)))
            ForgotOutcome.Offline -> s.copy(banner = FormBanner.Offline)
            is ForgotOutcome.Error -> s.copy(banner = FormBanner.Error(outcome.code, outcome.requestId))
        }
    }
}
