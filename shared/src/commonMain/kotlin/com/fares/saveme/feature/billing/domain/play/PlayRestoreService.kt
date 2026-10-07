package com.fares.saveme.feature.billing.domain.play

import co.touchlab.kermit.Logger
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.billing.data.BillingApi
import com.fares.saveme.feature.billing.data.dto.PlayRestoreResultDto
import com.fares.saveme.feature.billing.data.play.PlayBillingClient
import com.fares.saveme.feature.billing.data.play.PlayConnection
import com.fares.saveme.feature.billing.data.play.PlayPurchase
import com.fares.saveme.feature.billing.data.play.PlayResult
import com.fares.saveme.feature.billing.domain.AttemptKey
import com.fares.saveme.feature.billing.domain.BillingAnswerApplier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

enum class RestoreTrigger { SignIn, Tap }

/** One item's outcome (`google-play.yaml` v1.15); a value added later is [Unknown] and adds no message. */
enum class RestoreOutcome {
    Linked, AlreadyYours, LinkedElsewhere, NotVerified, Pending, Unavailable, Unknown;

    companion object {
        fun from(wire: String): RestoreOutcome = when (wire) {
            "linked" -> Linked
            "already_yours" -> AlreadyYours
            "linked_elsewhere" -> LinkedElsewhere
            "not_verified" -> NotVerified
            "pending" -> Pending
            "unavailable" -> Unavailable
            else -> Unknown
        }
    }
}

/** The sentences a tapped restore may combine (FR-023). The copy for each is chosen by the screen. */
enum class RestoreMessagePart { Restored, Nothing, AnotherAccount, Pending, Unavailable }

data class RestoreSummary(val sent: Int = 0, val counts: Map<RestoreOutcome, Int> = emptyMap()) {
    fun count(outcome: RestoreOutcome): Int = counts[outcome] ?: 0

    operator fun plus(other: RestoreSummary) = RestoreSummary(
        sent + other.sent,
        (counts.keys + other.counts.keys).associateWith { count(it) + other.count(it) },
    )

    /**
     * One combined message from the outcomes (FR-023): restored for any linked or already yours, another account, pending, "couldn't
     * be reached"; "nothing to restore" when nothing was sent or every item was `not_verified`; unknown outcomes add nothing.
     */
    fun messageParts(): List<RestoreMessagePart> {
        val parts = buildList {
            if (count(RestoreOutcome.Linked) + count(RestoreOutcome.AlreadyYours) > 0) add(RestoreMessagePart.Restored)
            if (count(RestoreOutcome.LinkedElsewhere) > 0) add(RestoreMessagePart.AnotherAccount)
            if (count(RestoreOutcome.Pending) > 0) add(RestoreMessagePart.Pending)
            if (count(RestoreOutcome.Unavailable) > 0) add(RestoreMessagePart.Unavailable)
        }
        if (parts.isNotEmpty()) return parts
        return if (sent == 0 || count(RestoreOutcome.NotVerified) == sent) listOf(RestoreMessagePart.Nothing) else emptyList()
    }
}

/**
 * Where a refused restore continues from: the chunks, their keys and what was already counted. In memory only; "Try again"
 * resends the failed chunk with its own key, then the rest (FR-024). Holds purchases, which print without their tokens.
 */
class RestoreRetry internal constructor(
    internal val userId: String,
    internal val chunks: List<RestoreChunk>,
    internal val next: Int,
    internal val summary: RestoreSummary,
) {
    override fun toString() = "RestoreRetry(next=$next of ${chunks.size})"
}

internal class RestoreChunk(val purchases: List<PlayPurchase>, val key: AttemptKey)

sealed interface RestoreResult {
    data class Done(val summary: RestoreSummary) : RestoreResult
    data object GooglePlayUnavailable : RestoreResult
    data object SignedOut : RestoreResult

    /** A refusal the tapped restore shows with "Try again" (FR-024). */
    sealed interface Refused : RestoreResult {
        val retry: RestoreRetry
    }

    data class Offline(override val retry: RestoreRetry) : Refused
    data class RateLimited(val retryAfterSeconds: Long?, override val retry: RestoreRetry) : Refused
    data class ServiceUnavailable(override val retry: RestoreRetry) : Refused
    data class InProgress(override val retry: RestoreRetry) : Refused
    data class Failed(val requestId: String?, override val retry: RestoreRetry) : Refused
}

/**
 * "Restore purchases" (spec 018 US2): sends everything Google Play says this phone owns, 25 at a time, most recent first, each
 * request with its own key. Silent after a real sign-in; a tap reports one combined result. Shares the pass lock with the verifier
 * so a purchase is never sent twice at once.
 */
class PlayRestoreService(
    private val port: PlayBillingClient,
    private val api: BillingApi,
    private val sessions: SessionManager,
    private val applier: BillingAnswerApplier,
    private val notes: PlayBillingNotes,
    private val lock: PlayPassLock,
    private val scope: CoroutineScope,
) {
    suspend fun restore(trigger: RestoreTrigger, retry: RestoreRetry? = null): RestoreResult {
        // Request and apply are one unit, so leaving the screen never cancels applying a success.
        val result = scope.async {
            try {
                lock.exclusive { run(trigger, retry) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RestoreResult.SignedOut.takeIf { signedInUser() == null } ?: failedUnreadable(retry)
            }
        }.await()
        Logger.i(tag = "play") { "play_restore trigger=$trigger outcome=${result::class.simpleName}" }
        return result
    }

    private fun signedInUser(): String? = (sessions.state.value as? AuthState.SignedIn)?.account?.userId

    private fun failedUnreadable(retry: RestoreRetry?): RestoreResult =
        retry?.let { RestoreResult.Failed(null, it) } ?: RestoreResult.Failed(null, RestoreRetry(signedInUser().orEmpty(), emptyList(), 0, RestoreSummary()))

    private suspend fun run(trigger: RestoreTrigger, retry: RestoreRetry?): RestoreResult {
        val userId = signedInUser() ?: return RestoreResult.SignedOut
        var start = retry?.takeIf { it.userId == userId }
        if (start == null) {
            if (port.connect() !is PlayConnection.Ready) return RestoreResult.GooglePlayUnavailable
            val listed = port.purchases()
            if (listed !is PlayResult.Ok) return RestoreResult.GooglePlayUnavailable
            if (signedInUser() != userId) return RestoreResult.SignedOut
            val owned = listed.value.distinctBy { it.token }.sortedByDescending { it.purchaseTime }
            // A sign-in restore with nothing to send is skipped: it saves the 10-an-hour budget (FR-020).
            if (owned.isEmpty() && trigger == RestoreTrigger.SignIn) return RestoreResult.Done(RestoreSummary())
            val chunks = if (owned.isEmpty()) {
                listOf(RestoreChunk(emptyList(), PlayKeys.attempt()))
            } else {
                owned.chunked(MAX_ITEMS).map { RestoreChunk(it, PlayKeys.attempt()) }
            }
            start = RestoreRetry(userId, chunks, 0, RestoreSummary())
        }
        var summary = start.summary
        for (index in start.next until start.chunks.size) {
            val chunk = start.chunks[index]
            val here = RestoreRetry(userId, start.chunks, index, summary)
            val result = api.restorePlayPurchases(chunk.purchases.map { it.token.value to it.productId }, chunk.key.value)
            if (signedInUser() != userId) return RestoreResult.SignedOut
            when (result) {
                is ApiResult.Success -> {
                    if (!applier.apply(userId, result.value.entitlements, result.value.subscription)) return RestoreResult.SignedOut
                    summary += fold(userId, chunk, result.value)
                }
                ApiResult.Offline, ApiResult.Timeout -> return RestoreResult.Offline(here)
                is ApiResult.Failure -> return refusal(result, here)
            }
        }
        return RestoreResult.Done(summary)
    }

    /** Counts the outcomes and remembers, in memory, which purchases belong elsewhere or cannot be verified (FR-032). */
    private fun fold(userId: String, chunk: RestoreChunk, answer: PlayRestoreResultDto): RestoreSummary {
        val counts = mutableMapOf<RestoreOutcome, Int>()
        answer.results.forEach { item ->
            val outcome = RestoreOutcome.from(item.outcome)
            counts[outcome] = (counts[outcome] ?: 0) + 1
            val purchase = chunk.purchases.getOrNull(item.index) ?: return@forEach
            val digest = PlayKeys.digest(purchase.token)
            when (outcome) {
                RestoreOutcome.LinkedElsewhere -> notes.update(userId) { it.copy(refused = it.refused + digest, anotherAccount = true) }
                RestoreOutcome.NotVerified -> notes.update(userId) { it.copy(refused = it.refused + digest) }
                RestoreOutcome.Pending -> notes.update(userId) { it.copy(pendingSent = it.pendingSent + digest) }
                RestoreOutcome.Linked, RestoreOutcome.AlreadyYours -> notes.update(userId) { it.copy(refused = it.refused - digest) }
                RestoreOutcome.Unavailable, RestoreOutcome.Unknown -> Unit
            }
        }
        return RestoreSummary(chunk.purchases.size, counts)
    }

    private fun refusal(failure: ApiResult.Failure, retry: RestoreRetry): RestoreResult = when (failure.code) {
        ApiErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS -> RestoreResult.InProgress(retry)
        ApiErrorCode.RATE_LIMITED -> RestoreResult.RateLimited(failure.retryAfterSeconds, retry)
        ApiErrorCode.SERVICE_UNAVAILABLE -> RestoreResult.ServiceUnavailable(retry)
        ApiErrorCode.IDEMPOTENCY_KEY_REUSED -> {
            Logger.e(tag = "play") { "play_client_defect code=IDEMPOTENCY_KEY_REUSED" }
            RestoreResult.Failed(failure.problem.requestId, retry)
        }
        else -> if (failure.problem.status == 401 || failure.problem.status == 426 || failure.code == ApiErrorCode.UNAUTHENTICATED) {
            RestoreResult.Failed(null, retry)
        } else {
            RestoreResult.Failed(failure.problem.requestId, retry)
        }
    }

    private companion object {
        const val MAX_ITEMS = 25
    }
}
