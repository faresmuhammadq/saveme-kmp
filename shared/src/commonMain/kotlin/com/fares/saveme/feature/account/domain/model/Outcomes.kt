package com.fares.saveme.feature.account.domain.model

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.domain.deletion.SuccessorReason
import kotlin.time.Instant

/** A credential action or a refresh: done, no connection, or refused with a code (for copy + reference). */
sealed interface ActionOutcome {
    data object Done : ActionOutcome
    data object Offline : ActionOutcome
    data class Error(val code: ApiErrorCode, val requestId: String?) : ActionOutcome
}

internal fun ApiResult<*>.toActionOutcome(): ActionOutcome = when (this) {
    is ApiResult.Success -> ActionOutcome.Done
    is ApiResult.Failure -> ActionOutcome.Error(code, problem.requestId)
    ApiResult.Offline, ApiResult.Timeout -> ActionOutcome.Offline
}

enum class PasswordField { Current, New }

sealed interface ChangePasswordOutcome {
    data object Success : ChangePasswordOutcome
    data object WrongCurrentPassword : ChangePasswordOutcome
    data class Locked(val retryAfterSeconds: Long?) : ChangePasswordOutcome
    data class Validation(val fields: Map<PasswordField, String>) : ChangePasswordOutcome
    data class RateLimited(val retryAfterSeconds: Long?) : ChangePasswordOutcome
    data object Offline : ChangePasswordOutcome
    data class Error(val code: ApiErrorCode, val requestId: String?) : ChangePasswordOutcome
}

/** Who the server says will own a shared wallet once the account is gone (the answer's `successors`). */
data class ScheduledOwner(val walletId: String, val ownerUserId: String, val reason: SuccessorReason)

sealed interface DeletionOutcome {
    /** [owners] are the answer's successors: the server's date and the server's owners, never computed here (FR-026, FR-027). */
    data class Scheduled(val date: Instant, val owners: List<ScheduledOwner> = emptyList()) : DeletionOutcome

    /** A 422 at `/successors…`: the person's shared wallets changed, so the plan is read again and nothing is scheduled (FR-028). */
    data object SuccessorsChanged : DeletionOutcome
    data object WrongPassword : DeletionOutcome
    data class Locked(val retryAfterSeconds: Long?) : DeletionOutcome
    data class Validation(val fields: Map<PasswordField, String>) : DeletionOutcome
    data object Offline : DeletionOutcome
    data class Error(val code: ApiErrorCode, val requestId: String?) : DeletionOutcome
}
