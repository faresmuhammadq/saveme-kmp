package com.fares.saveme.feature.account.ui.changepassword

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.account.domain.model.ChangePasswordOutcome
import com.fares.saveme.feature.account.domain.model.PasswordField
import com.fares.saveme.feature.auth.domain.ClientValidation
import com.fares.saveme.feature.auth.domain.FieldError
import com.fares.saveme.feature.auth.ui.FieldProblem

data class ChangePasswordState(
    val current: String = "",
    val new: String = "",
    val currentVisible: Boolean = false,
    val newVisible: Boolean = false,
    val currentError: CurrentPasswordProblem? = null,
    val newError: FieldProblem? = null,
    val submitting: Boolean = false,
    /** Minutes to wait while the account is locked or rate limited; submit is disabled meanwhile. */
    val waitMinutes: Int? = null,
    val waitReason: WaitReason? = null,
    val banner: ChangePasswordBanner? = null,
    /** Changed: this device stays signed in, every other one was signed out (FR-009). */
    val done: Boolean = false,
)

enum class WaitReason { Locked, RateLimited }

sealed interface CurrentPasswordProblem {
    data object Incorrect : CurrentPasswordProblem
    data class Field(val problem: FieldProblem) : CurrentPasswordProblem
}

sealed interface ChangePasswordBanner {
    data object Offline : ChangePasswordBanner
    data object Validation : ChangePasswordBanner
    data class Error(val code: ApiErrorCode, val requestId: String?) : ChangePasswordBanner
}

sealed interface ChangePasswordIntent {
    data class CurrentChanged(val value: String) : ChangePasswordIntent
    data class NewChanged(val value: String) : ChangePasswordIntent
    data object ToggleCurrentVisible : ChangePasswordIntent
    data object ToggleNewVisible : ChangePasswordIntent
    data object Submit : ChangePasswordIntent
    data object Back : ChangePasswordIntent
}

sealed interface ChangePasswordEffect {
    data object NavigateBack : ChangePasswordEffect
}

sealed interface ChangePasswordChange {
    data class Current(val value: String) : ChangePasswordChange
    data class New(val value: String) : ChangePasswordChange
    data object ToggleCurrent : ChangePasswordChange
    data object ToggleNew : ChangePasswordChange
    data class Invalid(val current: FieldError?, val new: FieldError?) : ChangePasswordChange
    data object Submitting : ChangePasswordChange
    data class Result(val outcome: ChangePasswordOutcome, val waitMinutes: Int?) : ChangePasswordChange
    data object WaitOver : ChangePasswordChange
}

/** Client checks before any request (FR-007): current non-empty; new 8–128, not the email. */
fun validate(state: ChangePasswordState, email: String): ChangePasswordChange.Invalid? {
    val current = ClientValidation.validateExistingPassword(state.current)
    val new = ClientValidation.validateNewPassword(state.new, email)
    return if (current == null && new == null) null else ChangePasswordChange.Invalid(current, new)
}

fun reduce(state: ChangePasswordState, change: ChangePasswordChange): ChangePasswordState = when (change) {
    is ChangePasswordChange.Current -> state.copy(current = change.value, currentError = null, banner = null)
    is ChangePasswordChange.New -> state.copy(new = change.value, newError = null, banner = null)
    ChangePasswordChange.ToggleCurrent -> state.copy(currentVisible = !state.currentVisible)
    ChangePasswordChange.ToggleNew -> state.copy(newVisible = !state.newVisible)
    is ChangePasswordChange.Invalid -> state.copy(
        currentError = change.current?.let { CurrentPasswordProblem.Field(FieldProblem.Client(it)) },
        newError = change.new?.let { FieldProblem.Client(it) },
    )
    ChangePasswordChange.Submitting -> state.copy(submitting = true, banner = null)
    is ChangePasswordChange.Result -> {
        val done = state.copy(submitting = false)
        when (val outcome = change.outcome) {
            ChangePasswordOutcome.Success -> done.copy(current = "", new = "", done = true)
            ChangePasswordOutcome.WrongCurrentPassword -> done.copy(currentError = CurrentPasswordProblem.Incorrect)
            is ChangePasswordOutcome.Locked -> done.copy(waitMinutes = change.waitMinutes, waitReason = WaitReason.Locked)
            is ChangePasswordOutcome.RateLimited -> done.copy(waitMinutes = change.waitMinutes, waitReason = WaitReason.RateLimited)
            is ChangePasswordOutcome.Validation -> done.copy(
                currentError = outcome.fields[PasswordField.Current]?.let { CurrentPasswordProblem.Field(FieldProblem.Server(it)) },
                newError = outcome.fields[PasswordField.New]?.let { FieldProblem.Server(it) },
                banner = if (outcome.fields.isEmpty()) ChangePasswordBanner.Validation else null,
            )
            ChangePasswordOutcome.Offline -> done.copy(banner = ChangePasswordBanner.Offline)
            is ChangePasswordOutcome.Error -> done.copy(banner = ChangePasswordBanner.Error(outcome.code, outcome.requestId))
        }
    }
    ChangePasswordChange.WaitOver -> state.copy(waitMinutes = null, waitReason = null)
}
