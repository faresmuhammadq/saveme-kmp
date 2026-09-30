package com.fares.saveme.feature.auth.domain.usecase

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.data.AuthRepository
import com.fares.saveme.feature.auth.domain.model.ForgotOutcome

/** Same answer for a registered and an unknown address (uniform answers, P-09.4). */
class ForgotPasswordUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): ForgotOutcome = when (val result = repository.forgotPassword(email.trim())) {
        is ApiResult.Success -> ForgotOutcome.Sent
        is ApiResult.Failure -> when (result.code) {
            ApiErrorCode.VALIDATION_FAILED -> ForgotOutcome.Validation(result.problem.fieldErrors())
            ApiErrorCode.RATE_LIMITED -> ForgotOutcome.RateLimited(result.retryAfterSeconds)
            else -> ForgotOutcome.Error(result.code, result.problem.requestId)
        }
        ApiResult.Offline, ApiResult.Timeout -> ForgotOutcome.Offline
    }
}
