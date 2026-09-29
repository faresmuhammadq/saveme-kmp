package com.fares.saveme.feature.account.ui.deleteaccount

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.account.domain.model.DeletionOutcome
import com.fares.saveme.feature.account.domain.model.PasswordField
import kotlin.time.Instant

data class DeleteAccountState(
    val step: DeleteStep = DeleteStep.Explain,
    val password: String = "",
    val passwordVisible: Boolean = false,
    val passwordError: DeletePasswordProblem? = null,
    val submitting: Boolean = false,
    val waitMinutes: Int? = null,
    val banner: DeleteBanner? = null,
)

sealed interface DeleteStep {
    data object Explain : DeleteStep
    data object Confirm : DeleteStep
    data class Scheduled(val date: Instant) : DeleteStep
}

sealed interface DeletePasswordProblem {
    data object Required : DeletePasswordProblem
    data object Incorrect : DeletePasswordProblem
    data class Server(val code: String) : DeletePasswordProblem
}

sealed interface DeleteBanner {
    data object Offline : DeleteBanner
    data class Error(val code: ApiErrorCode, val requestId: String?) : DeleteBanner
}

sealed interface DeleteAccountIntent {
    data object Continue : DeleteAccountIntent
    data class PasswordChanged(val value: String) : DeleteAccountIntent
    data object TogglePasswordVisible : DeleteAccountIntent
    data object Confirm : DeleteAccountIntent
    data object Done : DeleteAccountIntent
    data object Back : DeleteAccountIntent
}

sealed interface DeleteAccountEffect {
    data object Finished : DeleteAccountEffect
    data object NavigateBack : DeleteAccountEffect
}

sealed interface DeleteAccountChange {
    data object ToConfirm : DeleteAccountChange
    data class Password(val value: String) : DeleteAccountChange
    data object ToggleVisible : DeleteAccountChange
    data object MissingPassword : DeleteAccountChange
    data object Submitting : DeleteAccountChange
    data class Result(val outcome: DeletionOutcome, val waitMinutes: Int?) : DeleteAccountChange
    data object WaitOver : DeleteAccountChange
}

fun reduce(state: DeleteAccountState, change: DeleteAccountChange): DeleteAccountState = when (change) {
    DeleteAccountChange.ToConfirm -> state.copy(step = DeleteStep.Confirm)
    is DeleteAccountChange.Password -> state.copy(password = change.value, passwordError = null, banner = null)
    DeleteAccountChange.ToggleVisible -> state.copy(passwordVisible = !state.passwordVisible)
    DeleteAccountChange.MissingPassword -> state.copy(passwordError = DeletePasswordProblem.Required)
    DeleteAccountChange.Submitting -> state.copy(submitting = true, banner = null)
    is DeleteAccountChange.Result -> {
        val done = state.copy(submitting = false)
        when (val outcome = change.outcome) {
            is DeletionOutcome.Scheduled -> done.copy(step = DeleteStep.Scheduled(outcome.date), password = "")
            DeletionOutcome.WrongPassword -> done.copy(passwordError = DeletePasswordProblem.Incorrect)
            is DeletionOutcome.Locked -> done.copy(waitMinutes = change.waitMinutes)
            is DeletionOutcome.Validation -> done.copy(
                passwordError = outcome.fields[PasswordField.Current]?.let { DeletePasswordProblem.Server(it) },
            )
            DeletionOutcome.Offline -> done.copy(banner = DeleteBanner.Offline)
            is DeletionOutcome.Error -> done.copy(banner = DeleteBanner.Error(outcome.code, outcome.requestId))
        }
    }
    DeleteAccountChange.WaitOver -> state.copy(waitMinutes = null)
}
