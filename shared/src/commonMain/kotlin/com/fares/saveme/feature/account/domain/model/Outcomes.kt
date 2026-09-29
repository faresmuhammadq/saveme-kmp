package com.fares.saveme.feature.account.domain.model

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
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

sealed interface DeletionOutcome {
    data class Scheduled(val date: Instant) : DeletionOutcome
    data object WrongPassword : DeletionOutcome
    data class Locked(val retryAfterSeconds: Long?) : DeletionOutcome
    data class Validation(val fields: Map<PasswordField, String>) : DeletionOutcome
    data object Offline : DeletionOutcome
    data class Error(val code: ApiErrorCode, val requestId: String?) : DeletionOutcome
}
