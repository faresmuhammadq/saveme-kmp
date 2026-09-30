package com.fares.saveme.feature.auth.domain.usecase

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.data.AuthRepository
import com.fares.saveme.feature.auth.domain.model.AuthField
import com.fares.saveme.feature.auth.domain.model.ResetOutcome

/**
 * Sets a new password from a mailed secret. It never touches the local session (FR-019): the
 * server ends every session of the reset account, and if this phone held one, its next request's
 * 401 runs the existing session-ended flow.
 */
class ResetPasswordUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(token: String, password: String): ResetOutcome =
        when (val result = repository.resetPassword(token, password)) {
            is ApiResult.Success -> ResetOutcome.Done
            is ApiResult.Failure -> {
                val fields = result.problem.fieldErrors()
                when {
                    result.code == ApiErrorCode.RESET_TOKEN_INVALID -> ResetOutcome.LinkInvalid
                    result.code == ApiErrorCode.VALIDATION_FAILED && AuthField.Token in fields -> ResetOutcome.LinkInvalid
                    result.code == ApiErrorCode.VALIDATION_FAILED -> ResetOutcome.Validation(fields)
                    result.code == ApiErrorCode.RATE_LIMITED -> ResetOutcome.RateLimited(result.retryAfterSeconds)
                    else -> ResetOutcome.Error(result.code, result.problem.requestId)
                }
            }
            ApiResult.Offline, ApiResult.Timeout -> ResetOutcome.Offline
        }
}
