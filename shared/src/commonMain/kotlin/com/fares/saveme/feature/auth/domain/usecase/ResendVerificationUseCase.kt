package com.fares.saveme.feature.auth.domain.usecase

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.data.AuthRepository
import com.fares.saveme.feature.auth.domain.model.ResendOutcome

class ResendVerificationUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): ResendOutcome = when (val result = repository.resendVerification()) {
        is ApiResult.Success -> ResendOutcome.Sent
        is ApiResult.Failure -> when (result.code) {
            ApiErrorCode.RATE_LIMITED -> ResendOutcome.RateLimited(result.retryAfterSeconds)
            else -> ResendOutcome.Error(result.code, result.problem.requestId)
        }
        ApiResult.Offline, ApiResult.Timeout -> ResendOutcome.Offline
    }
}
