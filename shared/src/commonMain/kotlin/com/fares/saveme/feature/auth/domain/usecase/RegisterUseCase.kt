package com.fares.saveme.feature.auth.domain.usecase

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.feature.auth.data.AuthRepository
import com.fares.saveme.feature.auth.domain.model.RegisterOutcome

/** Creates an account; the person signs in afterwards (the contract returns no credential). */
class RegisterUseCase(
    private val clientInfo: ClientInfo,
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String, displayName: String?): RegisterOutcome =
        when (val result = repository.register(email.trim(), password, displayName, clientInfo.timezoneId())) {
            is ApiResult.Success -> RegisterOutcome.Accepted
            is ApiResult.Failure -> when (result.code) {
                ApiErrorCode.EMAIL_ALREADY_REGISTERED -> RegisterOutcome.AlreadyRegistered
                ApiErrorCode.VALIDATION_FAILED -> RegisterOutcome.Validation(result.problem.fieldErrors())
                ApiErrorCode.RATE_LIMITED -> RegisterOutcome.RateLimited(result.retryAfterSeconds)
                else -> RegisterOutcome.Error(result.code, result.problem.requestId)
            }
            ApiResult.Offline, ApiResult.Timeout -> RegisterOutcome.Offline
        }
}
