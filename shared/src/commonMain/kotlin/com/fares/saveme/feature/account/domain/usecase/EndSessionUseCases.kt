package com.fares.saveme.feature.account.domain.usecase

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.data.DeviceSessionRepository
import com.fares.saveme.feature.account.domain.SessionRefresher
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.model.toActionOutcome

/**
 * Ends one other device's session. Never retried and never queued offline: it revokes a
 * credential, so the person must see it happen (FR-024). An already-gone session counts as done.
 */
class EndSessionUseCase(
    private val repository: DeviceSessionRepository,
    private val refresher: SessionRefresher,
) {
    suspend operator fun invoke(id: String): ActionOutcome {
        val result = repository.end(id)
        val outcome = if (result is ApiResult.Failure && result.code == ApiErrorCode.NOT_FOUND) {
            ActionOutcome.Done
        } else {
            result.toActionOutcome()
        }
        if (outcome == ActionOutcome.Done) {
            repository.forget(id)
            refresher.refresh()
        }
        return outcome
    }
}

/** Ends every session except this one (FR-004, FR-005). */
class EndOtherSessionsUseCase(
    private val repository: DeviceSessionRepository,
    private val refresher: SessionRefresher,
) {
    suspend operator fun invoke(): ActionOutcome {
        val outcome = repository.endOthers().toActionOutcome()
        if (outcome == ActionOutcome.Done) refresher.refresh()
        return outcome
    }
}
