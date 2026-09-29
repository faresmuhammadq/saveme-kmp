package com.fares.saveme.feature.account.domain.usecase

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.data.AccountApi
import com.fares.saveme.feature.account.data.dto.ChangePasswordRequest
import com.fares.saveme.feature.account.domain.SessionRefresher
import com.fares.saveme.feature.account.domain.model.ChangePasswordOutcome

/**
 * Changes the password; the server ends every other session and keeps this one (FR-009).
 * Never retried automatically (FR-024).
 */
class ChangePasswordUseCase(
    private val api: AccountApi,
    private val refresher: SessionRefresher,
) {
    suspend operator fun invoke(current: String, new: String): ChangePasswordOutcome =
        when (val result = api.changePassword(ChangePasswordRequest(current, new))) {
            is ApiResult.Success -> {
                refresher.refresh() // the list now shows only this device
                ChangePasswordOutcome.Success
            }
            is ApiResult.Failure -> when (result.code) {
                ApiErrorCode.INVALID_CREDENTIALS -> ChangePasswordOutcome.WrongCurrentPassword
                ApiErrorCode.ACCOUNT_LOCKED -> ChangePasswordOutcome.Locked(result.retryAfterSeconds)
                ApiErrorCode.VALIDATION_FAILED -> ChangePasswordOutcome.Validation(result.problem.passwordFieldErrors())
                ApiErrorCode.RATE_LIMITED -> ChangePasswordOutcome.RateLimited(result.retryAfterSeconds)
                else -> ChangePasswordOutcome.Error(result.code, result.problem.requestId)
            }
            ApiResult.Offline, ApiResult.Timeout -> ChangePasswordOutcome.Offline
        }
}
