package com.fares.saveme.feature.billing.domain.play

import co.touchlab.kermit.Logger
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.billing.data.BillingApi
import com.fares.saveme.feature.billing.data.dto.PlayPurchaseResultDto
import com.fares.saveme.feature.billing.data.play.PlayBillingClient
import com.fares.saveme.feature.billing.data.play.PlayConnection
import com.fares.saveme.feature.billing.data.play.PlayPurchase
import com.fares.saveme.feature.billing.data.play.PlayPurchaseState
import com.fares.saveme.feature.billing.data.play.PlayResult
import com.fares.saveme.feature.billing.domain.BillingAnswerApplier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** What started a pass (FR-010). Only [Retry] keeps the in-run backoff of purchases waiting to be resent. */
enum class PassTrigger { ColdStart, Foreground, PlanScreen, Update, Retry }

/** What one purchase send came to (data-model §3). The copy is chosen by the screen from codes, never from server text. */
sealed interface VerifyOutcome {
    data class Granted(val state: PlayServerState, val test: Boolean, val planChanged: Boolean) : VerifyOutcome
    data object Pending : VerifyOutcome
    data object AnotherAccount : VerifyOutcome
    data object NotVerified : VerifyOutcome
    data class RetryLater(val afterSeconds: Long?) : VerifyOutcome
    data object SignedOut : VerifyOutcome
    data class Failed(val requestId: String?) : VerifyOutcome

    /** 401 or 426: the app-wide handling takes over and the pass stops. */
    data object StopPass : VerifyOutcome
}

/** The server's view of a purchase (`purchase.state`); an unknown value keeps the plan from `entitlements` and adds nothing. */
enum class PlayServerState {
    Pending, Active, Grace, Canceled, OnHold, Paused, Expired, Revoked, Unknown;

    companion object {
        fun from(wire: String): PlayServerState = when (wire) {
            "pending" -> Pending
            "active" -> Active
            "grace" -> Grace
            "canceled" -> Canceled
            "on_hold" -> OnHold
            "paused" -> Paused
            "expired" -> Expired
            "revoked" -> Revoked
            else -> Unknown
        }
    }
}

/**
 * The one pipe from Google Play to the person's plan (spec 018 US1): lists Google Play's purchases through the port and sends each
 * one Google Play reports as not acknowledged to `POST /billing/google/purchases` with a derived, stable idempotency key.
 * Answers are applied through [BillingAnswerApplier]. The app never acknowledges, never consumes and never grants a plan itself
 * (FR-002): the port has no such operation and a pending purchase is only noted.
 */
class PlayPurchaseVerifier(
    private val port: PlayBillingClient,
    private val api: BillingApi,
    private val sessions: SessionManager,
    private val applier: BillingAnswerApplier,
    private val notes: PlayBillingNotes,
    /** Reloads the catalogue after `VALIDATION_FAILED` on a product id, once per run (FR-016). */
    private val reloadCatalogue: suspend () -> Unit,
    private val lock: PlayPassLock,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private var retryJob: Job? = null

    /** Runs one pass (or joins the one running, FR-011). Never throws; nothing shows. */
    suspend fun pass(trigger: PassTrigger) {
        lock.runPass { runOnce(trigger) }
    }

    /** Cancels backoff timers (sign-out). The notes themselves are cleared by the cleaner. */
    fun cancelTimers() {
        retryJob?.cancel()
        retryJob = null
    }

    private fun signedInUser(): String? = (sessions.state.value as? AuthState.SignedIn)?.account?.userId

    private suspend fun runOnce(trigger: PassTrigger) {
        val userId = signedInUser() ?: return
        // A retry pass runs inside the timer's own coroutine: it must not cancel itself.
        if (trigger != PassTrigger.Retry) retryJob?.cancel()
        retryJob = null
        if (trigger != PassTrigger.Retry) notes.update(userId) { it.copy(retry = emptyMap()) }
        if (port.connect() !is PlayConnection.Ready) {
            Logger.i(tag = "play") { "play_pass trigger=$trigger outcome=unavailable" }
            return
        }
        val listed = port.purchases()
        if (listed !is PlayResult.Ok) {
            Logger.i(tag = "play") { "play_pass trigger=$trigger outcome=list_failed" }
            return
        }
        if (signedInUser() != userId) return
        val purchases = listed.value.sortedByDescending { it.purchaseTime }
        recomputePending(userId, purchases)

        var sent = 0
        var granted = 0
        var pending = 0
        var refused = 0
        var retrying = 0
        var failed = 0
        for (purchase in purchases) {
            val plan = eligible(userId, purchase) ?: continue
            val outcome = send(userId, purchase, plan.recheckHour)
            sent++
            when (outcome) {
                is VerifyOutcome.Granted -> granted++
                VerifyOutcome.Pending -> pending++
                VerifyOutcome.AnotherAccount, VerifyOutcome.NotVerified -> refused++
                is VerifyOutcome.RetryLater -> retrying++
                is VerifyOutcome.Failed -> failed++
                VerifyOutcome.SignedOut, VerifyOutcome.StopPass -> break
            }
        }
        Logger.i(tag = "play") {
            "play_pass trigger=$trigger listed=${purchases.size} sent=$sent granted=$granted pending=$pending refused=$refused retrying=$retrying failed=$failed"
        }
        scheduleRetry(userId)
    }

    /** Pending products come from Google Play's list plus purchases the server answered `202` for that Google Play still lists. */
    private fun recomputePending(userId: String, purchases: List<PlayPurchase>) {
        val listed = purchases.associateBy { PlayKeys.digest(it.token) }
        notes.update(userId) { held ->
            val answered = held.answeredPending.filterKeys { it in listed }
            val google = purchases.filter { it.state == PlayPurchaseState.Pending }.map { it.productId }
            held.copy(
                answeredPending = answered,
                pendingProducts = (google + answered.values.map { it.productId }).toSet(),
                pendingSent = held.pendingSent.filter { it in listed }.toSet(),
                retry = held.retry.filterKeys { it in listed },
            )
        }
    }

    /** A purchase to send now; [recheckHour] is set only for the hourly recheck of a purchased purchase the server answered `202`. */
    private class SendPlan(val recheckHour: Long?)

    private fun eligible(userId: String, purchase: PlayPurchase): SendPlan? {
        if (purchase.acknowledged) return null
        val digest = PlayKeys.digest(purchase.token)
        val held = notes.current(userId)
        if (digest in held.refused) return null
        if (purchase.state == PlayPurchaseState.Pending && digest in held.pendingSent) return null
        held.retry[digest]?.let { if (it.sends >= MAX_SENDS || clock.now() < it.nextAt) return null }
        val answered = held.answeredPending[digest]
        if (purchase.state == PlayPurchaseState.Purchased && answered != null) {
            if (clock.now() < answered.recheckAfter) return null
            return SendPlan(clock.now().epochSeconds / SECONDS_PER_HOUR)
        }
        return SendPlan(null)
    }

    private suspend fun send(userId: String, purchase: PlayPurchase, recheckHour: Long?): VerifyOutcome {
        val key = PlayKeys.verify(userId, purchase.token, purchase.state, recheckHour)
        val result = try {
            api.verifyPlayPurchase(purchase.token.value, purchase.productId, key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An answer that cannot be read is an unexpected failure, not a crash.
            return VerifyOutcome.Failed(null)
        }
        if (signedInUser() != userId) return VerifyOutcome.SignedOut
        val digest = PlayKeys.digest(purchase.token)
        return when (result) {
            is ApiResult.Success -> answered(userId, purchase, digest, result.value)
            ApiResult.Offline, ApiResult.Timeout -> retryLater(userId, digest, null)
            is ApiResult.Failure -> refusal(userId, digest, result)
        }
    }

    private suspend fun answered(userId: String, purchase: PlayPurchase, digest: String, answer: PlayPurchaseResultDto): VerifyOutcome {
        val before = applier.storedPlanId()
        if (!applier.apply(userId, answer.entitlements, answer.subscription)) return VerifyOutcome.SignedOut
        val state = PlayServerState.from(answer.purchase.state)
        val product = purchase.productId
        if (state == PlayServerState.Pending) {
            notes.update(userId) { held ->
                if (purchase.state == PlayPurchaseState.Pending) {
                    held.copy(pendingSent = held.pendingSent + digest, retry = held.retry - digest)
                } else {
                    val recheck = AnsweredPending(product, clock.now() + 1.hours)
                    held.copy(
                        answeredPending = held.answeredPending + (digest to recheck),
                        pendingProducts = held.pendingProducts + product,
                        retry = held.retry - digest,
                    )
                }
            }
            return VerifyOutcome.Pending
        }
        val planChanged = before != answer.subscription.plan.id
        notes.update(userId) { held ->
            held.copy(
                answeredPending = held.answeredPending - digest,
                pendingProducts = if (held.answeredPending.containsKey(digest)) held.pendingProducts - product else held.pendingProducts,
                heldProducts = if (state == PlayServerState.OnHold) held.heldProducts + product else held.heldProducts - product,
                pausedProducts = if (state == PlayServerState.Paused) held.pausedProducts + product else held.pausedProducts - product,
                testProducts = if (answer.purchase.test) held.testProducts + product else held.testProducts - product,
                retry = held.retry - digest,
                activated = if (planChanged && notes.planScreenShowing) PlanActivated(answer.subscription.plan.name) else held.activated,
            )
        }
        return VerifyOutcome.Granted(state, answer.purchase.test, planChanged)
    }

    private fun refusal(userId: String, digest: String, failure: ApiResult.Failure): VerifyOutcome {
        val requestId = failure.problem.requestId
        return when (failure.code) {
            ApiErrorCode.PURCHASE_ALREADY_LINKED -> {
                notes.update(userId) { it.copy(refused = it.refused + digest, anotherAccount = true, retry = it.retry - digest) }
                VerifyOutcome.AnotherAccount
            }
            ApiErrorCode.PURCHASE_NOT_VERIFIED -> {
                notes.update(userId) { it.copy(refused = it.refused + digest, retry = it.retry - digest) }
                VerifyOutcome.NotVerified
            }
            ApiErrorCode.VALIDATION_FAILED -> {
                notes.update(userId) { it.copy(refused = it.refused + digest, retry = it.retry - digest) }
                scope.launch { reloadCatalogueOnce(userId) }
                VerifyOutcome.NotVerified
            }
            ApiErrorCode.IDEMPOTENCY_KEY_REUSED -> {
                // A client defect: the code only, never the key (FR-050).
                Logger.e(tag = "play") { "play_client_defect code=IDEMPOTENCY_KEY_REUSED" }
                notes.update(userId) { it.copy(refused = it.refused + digest, retry = it.retry - digest) }
                VerifyOutcome.Failed(requestId)
            }
            ApiErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS, ApiErrorCode.RATE_LIMITED, ApiErrorCode.SERVICE_UNAVAILABLE ->
                retryLater(userId, digest, failure.retryAfterSeconds)
            else -> if (failure.problem.status == 401 || failure.problem.status == 426 || failure.code == ApiErrorCode.UNAUTHENTICATED) {
                VerifyOutcome.StopPass
            } else {
                VerifyOutcome.Failed(requestId)
            }
        }
    }

    private suspend fun reloadCatalogueOnce(userId: String) {
        var first = false
        notes.update(userId) { held ->
            first = !held.catalogueReloaded
            held.copy(catalogueReloaded = true)
        }
        if (first) {
            try {
                reloadCatalogue()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(tag = "play") { "play_catalogue_reload outcome=failed" }
            }
        }
    }

    private fun retryLater(userId: String, digest: String, retryAfterSeconds: Long?): VerifyOutcome {
        notes.update(userId) { held ->
            val sends = (held.retry[digest]?.sends ?: 0) + 1
            val wait = (retryAfterSeconds ?: backoffSeconds(sends)).coerceIn(1, MAX_WAIT_SECONDS)
            held.copy(retry = held.retry + (digest to RetryState(sends, clock.now() + wait.seconds)))
        }
        return VerifyOutcome.RetryLater(retryAfterSeconds)
    }

    private fun backoffSeconds(sends: Int): Long {
        var wait = FIRST_WAIT_SECONDS
        repeat((sends - 1).coerceAtLeast(0)) { wait *= 2 }
        return wait.coerceAtMost(MAX_WAIT_SECONDS)
    }

    /** One timer for the earliest purchase still waiting to be resent; a new pass replaces it. Cancelled at sign-out. */
    private fun scheduleRetry(userId: String) {
        val waiting = notes.current(userId).retry.values.filter { it.sends < MAX_SENDS }
        val next = waiting.minOfOrNull { it.nextAt } ?: return
        val wait = (next - clock.now()).coerceAtLeast(1.seconds)
        retryJob = scope.launch {
            delay(wait)
            if (signedInUser() == userId) pass(PassTrigger.Retry)
        }
    }

    private companion object {
        const val MAX_SENDS = 6
        const val FIRST_WAIT_SECONDS = 30L
        const val MAX_WAIT_SECONDS = 15 * 60L
        const val SECONDS_PER_HOUR = 3600L
    }
}
