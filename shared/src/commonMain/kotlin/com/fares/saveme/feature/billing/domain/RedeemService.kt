package com.fares.saveme.feature.billing.domain

import co.touchlab.kermit.Logger
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.billing.data.BillingApi
import com.fares.saveme.feature.billing.data.SubscriptionRepository
import com.fares.saveme.feature.billing.data.dto.RedeemResultDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.jvm.JvmInline
import kotlin.time.Clock

/** One redeem attempt's identifier, sent as `Idempotency-Key`; memory only, never printed (P-09.2). */
@JvmInline
value class AttemptKey(val value: String) {
    override fun toString() = "AttemptKey(***)"
}

/** What one redemption came to (spec 015 R-08). The copy for each is chosen by the screen, never from server text. */
sealed interface RedeemOutcome {
    data class Redeemed(val confirmation: RedeemConfirmation) : RedeemOutcome
    data object Offline : RedeemOutcome
    data object NotValid : RedeemOutcome
    data object Used : RedeemOutcome
    data object Expired : RedeemOutcome
    data object OtherAddress : RedeemOutcome
    data object NotVerified : RedeemOutcome
    data class RateLimited(val retryAfterSeconds: Long?) : RedeemOutcome
    data object InvalidFormat : RedeemOutcome
    data object InProgress : RedeemOutcome
    data class KeyReused(val requestId: String?) : RedeemOutcome
    data class Failed(val requestId: String?) : RedeemOutcome
    data object SignedOut : RedeemOutcome
}

/**
 * Redeems a mailed code (spec 015). One request, never retried, never queued; offline is an outcome. A success is
 * applied only for the person signed in when the call started (FR-012).
 */
class RedeemService(
    private val api: BillingApi,
    private val sessions: SessionManager,
    private val subscriptions: SubscriptionRepository,
    private val entitlements: Entitlements,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {

    suspend fun redeem(rawCode: String, attempt: AttemptKey): RedeemOutcome {
        require(RedeemCode.isComplete(rawCode)) { "an incomplete code is never sent" }
        val userId = (sessions.state.value as? AuthState.SignedIn)?.account?.userId ?: return RedeemOutcome.SignedOut
        // Request and apply are one unit, so leaving the screen never cancels applying a success (FR-012).
        val outcome = scope.async {
            try {
                when (val result = api.redeem(RedeemCode.grouped(rawCode), attempt.value)) {
                    is ApiResult.Success -> applied(userId, result.value)
                    ApiResult.Offline, ApiResult.Timeout -> RedeemOutcome.Offline
                    is ApiResult.Failure -> refusal(result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // An answer that cannot be read is an unexpected failure, not a crash.
                RedeemOutcome.Failed(null)
            }
        }.await()
        Logger.i(tag = "redeem") { "outcome=${outcome::class.simpleName}" + ((outcome as? RedeemOutcome.Failed)?.requestId?.let { " request_id=$it" } ?: "") }
        return outcome
    }

    private suspend fun applied(userId: String, result: RedeemResultDto): RedeemOutcome {
        val stillThem = (sessions.state.value as? AuthState.SignedIn)?.account?.userId == userId
        val applied = stillThem && sessions.applyEntitlements(
            forUserId = userId,
            planId = result.entitlements.plan.id,
            planName = result.entitlements.plan.name,
            features = result.entitlements.features,
        )
        if (!applied) return RedeemOutcome.SignedOut
        subscriptions.save(result.governing)
        // The re-read only drives the Plan screen's stale note; the applied answer already shows.
        scope.launch { entitlements.refreshAfterBillingAction() }
        return RedeemOutcome.Redeemed(RedeemConfirmation.from(result, clock.now()))
    }

    private fun refusal(failure: ApiResult.Failure): RedeemOutcome {
        val requestId = failure.problem.requestId
        return when (failure.code) {
            ApiErrorCode.REDEEM_CODE_EMAIL_MISMATCH -> RedeemOutcome.OtherAddress
            ApiErrorCode.EMAIL_NOT_VERIFIED -> RedeemOutcome.NotVerified
            ApiErrorCode.NOT_FOUND -> RedeemOutcome.NotValid
            ApiErrorCode.REDEEM_CODE_USED -> RedeemOutcome.Used
            ApiErrorCode.REDEEM_CODE_EXPIRED -> RedeemOutcome.Expired
            ApiErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS -> RedeemOutcome.InProgress
            ApiErrorCode.IDEMPOTENCY_KEY_REUSED -> RedeemOutcome.KeyReused(requestId)
            ApiErrorCode.VALIDATION_FAILED -> RedeemOutcome.InvalidFormat
            ApiErrorCode.RATE_LIMITED -> RedeemOutcome.RateLimited(failure.retryAfterSeconds)
            // 401 ends the session and 426 asks for an update through the app-wide events: nothing extra here.
            ApiErrorCode.UNAUTHENTICATED -> RedeemOutcome.Failed(null)
            else -> if (failure.problem.status == 401 || failure.problem.status == 426) RedeemOutcome.Failed(null) else RedeemOutcome.Failed(requestId)
        }
    }
}
