package com.fares.saveme.feature.account.domain.usecase

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.data.AccountApi
import com.fares.saveme.feature.account.data.dto.DeletionRequestDto
import com.fares.saveme.feature.account.data.dto.DeletionScheduleDto
import com.fares.saveme.feature.account.data.dto.SuccessorChoiceDto
import com.fares.saveme.feature.account.domain.deletion.SuccessorChoice
import com.fares.saveme.feature.account.domain.deletion.SuccessorReason
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.model.ScheduledOwner
import com.fares.saveme.feature.account.domain.model.DeletionOutcome
import com.fares.saveme.feature.account.domain.model.toActionOutcome
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.model.AccountState
import kotlin.time.Instant

/** Asks for deletion after the 30-day grace period (security-baseline: Deletion). Never retried. */
class RequestDeletionUseCase(
    private val api: AccountApi,
    private val sessions: SessionManager,
) {
    /** [successors] null leaves `successors` out of the body (nothing was offered, or the server's rule applies). */
    suspend operator fun invoke(password: String, successors: List<SuccessorChoice>? = null): DeletionOutcome {
        val body = DeletionRequestDto(password, successors?.map { SuccessorChoiceDto(it.walletId, it.memberUserId) })
        return outcome(api.requestDeletion(body))
    }

    private suspend fun outcome(result: ApiResult<DeletionScheduleDto>): DeletionOutcome = when (result) {
        is ApiResult.Success -> {
            val date = Instant.parse(result.value.deletionScheduledFor)
            sessions.updateDeletion(AccountState.PendingDeletion, date)
            DeletionOutcome.Scheduled(
                date,
                result.value.successors.map { ScheduledOwner(it.walletId, it.successorUserId, SuccessorReason.fromWire(it.reason)) },
            )
        }
        is ApiResult.Failure -> when (result.code) {
            ApiErrorCode.INVALID_CREDENTIALS -> DeletionOutcome.WrongPassword
            ApiErrorCode.ACCOUNT_LOCKED -> DeletionOutcome.Locked(result.retryAfterSeconds)
            ApiErrorCode.VALIDATION_FAILED ->
                if (result.problem.errors.orEmpty().any { it.pointer?.startsWith("/successors") == true }) {
                    DeletionOutcome.SuccessorsChanged
                } else {
                    DeletionOutcome.Validation(result.problem.passwordFieldErrors())
                }
            else -> DeletionOutcome.Error(result.code, result.problem.requestId)
        }
        ApiResult.Offline, ApiResult.Timeout -> DeletionOutcome.Offline
    }
}

/** Cancels a pending deletion; nothing is lost (FR-020). Never retried. */
class CancelDeletionUseCase(
    private val api: AccountApi,
    private val sessions: SessionManager,
) {
    suspend operator fun invoke(): ActionOutcome {
        val outcome = api.cancelDeletion().toActionOutcome()
        if (outcome == ActionOutcome.Done) sessions.updateDeletion(AccountState.Active, null)
        return outcome
    }
}
