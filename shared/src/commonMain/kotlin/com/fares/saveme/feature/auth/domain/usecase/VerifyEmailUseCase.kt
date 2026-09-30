package com.fares.saveme.feature.auth.domain.usecase

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.data.AuthRepository
import com.fares.saveme.feature.auth.domain.model.AuthField
import com.fares.saveme.feature.auth.domain.model.VerifyOutcome

/**
 * Confirms an address with a mailed secret. It never touches the local account: the answer
 * doesn't say whose address it was, so the caller re-reads the account instead (FR-011).
 */
class VerifyEmailUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(token: String): VerifyOutcome = when (val result = repository.verifyEmail(token)) {
        is ApiResult.Success -> VerifyOutcome.Verified
        is ApiResult.Failure -> when {
            result.code == ApiErrorCode.VERIFICATION_TOKEN_INVALID -> VerifyOutcome.LinkInvalid
            result.code == ApiErrorCode.VALIDATION_FAILED && AuthField.Token in result.problem.fieldErrors() -> VerifyOutcome.LinkInvalid
            else -> VerifyOutcome.Error(result.code, result.problem.requestId)
        }
        ApiResult.Offline, ApiResult.Timeout -> VerifyOutcome.Offline
    }
}
