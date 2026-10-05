package com.fares.saveme.feature.billing.ui.redeem

import com.fares.saveme.feature.billing.domain.AttemptKey
import com.fares.saveme.feature.billing.domain.RedeemCode
import com.fares.saveme.feature.billing.domain.RedeemConfirmation
import kotlin.time.Instant

/** Where the screen stands: typing, waiting for the answer, or showing the confirmation. */
sealed interface RedeemPhase {
    data object Editing : RedeemPhase
    data object Sending : RedeemPhase
    data class Done(val confirmation: RedeemConfirmation) : RedeemPhase
}

/** The refusal shown; each has its own copy by code (spec 015 FR-017 to FR-026). */
sealed interface RedeemMessage {
    data object Offline : RedeemMessage
    data object NotValid : RedeemMessage
    data object Used : RedeemMessage
    data object Expired : RedeemMessage
    data class OtherAddress(val signedInEmail: String?) : RedeemMessage
    data object NotVerified : RedeemMessage

    /** [minutes] null: no wait time was given. */
    data class RateLimited(val minutes: Int?) : RedeemMessage
    data object InvalidFormat : RedeemMessage
    data object InProgress : RedeemMessage
    data class Failed(val requestId: String?) : RedeemMessage
}

/**
 * Memory only (spec 015 R-10): the code and the attempt identifier are redacted wherever this prints (P-09.2).
 * [code] holds the raw normalised characters, 0 to 16.
 */
data class RedeemState(
    val code: String = "",
    val attempt: AttemptKey? = null,
    val verified: Boolean = true,
    val signedInEmail: String? = null,
    val phase: RedeemPhase = RedeemPhase.Editing,
    val message: RedeemMessage? = null,
    val waitUntil: Instant? = null,
    val timeZoneId: String = "UTC",
) {
    val canRedeem: Boolean
        get() = phase == RedeemPhase.Editing && RedeemCode.isComplete(code) && waitUntil == null

    /** Only the refusals that are not already said by the verify note. */
    val showsMessage: Boolean get() = message != null && message != RedeemMessage.NotVerified

    override fun toString() = "RedeemState(code=<redacted>, attempt=<redacted>, phase=${phase::class.simpleName}, message=${message?.let { it::class.simpleName }})"
}

sealed interface RedeemIntent {
    data object Opened : RedeemIntent

    /** Typed or pasted text; the store normalises it. */
    data class CodeChanged(val text: String) : RedeemIntent {
        override fun toString() = "CodeChanged(<redacted>)"
    }

    data object Redeem : RedeemIntent
    data object TryAgain : RedeemIntent
    data object Done : RedeemIntent
    data object Back : RedeemIntent
}

sealed interface RedeemEffect {
    data object NavigateBack : RedeemEffect
    data object RequireVerification : RedeemEffect
}

sealed interface RedeemChange {
    data class Code(val normalised: String) : RedeemChange {
        override fun toString() = "Code(<redacted>)"
    }

    data class SendStarted(val attempt: AttemptKey) : RedeemChange
    data class Succeeded(val confirmation: RedeemConfirmation) : RedeemChange

    /** A refusal; [keepAttempt] false drops the attempt so the next Redeem starts a new one. */
    data class Refused(val message: RedeemMessage, val keepAttempt: Boolean = true) : RedeemChange
    data class WaitStarted(val until: Instant) : RedeemChange
    data object WaitOver : RedeemChange
    data class Account(val verified: Boolean, val signedInEmail: String?) : RedeemChange
    data class TimeZone(val id: String) : RedeemChange
    data object SessionSignedOut : RedeemChange
}

fun reduce(state: RedeemState, change: RedeemChange): RedeemState = when (change) {
    is RedeemChange.Code ->
        if (state.phase != RedeemPhase.Editing || change.normalised == state.code) {
            state
        } else {
            state.copy(code = change.normalised, attempt = null, message = null)
        }
    is RedeemChange.SendStarted -> state.copy(phase = RedeemPhase.Sending, attempt = change.attempt, message = null)
    is RedeemChange.Succeeded -> state.copy(phase = RedeemPhase.Done(change.confirmation), code = "", attempt = null, message = null, waitUntil = null)
    is RedeemChange.Refused -> state.copy(
        phase = RedeemPhase.Editing,
        message = when (val m = change.message) {
            is RedeemMessage.OtherAddress -> RedeemMessage.OtherAddress(state.signedInEmail)
            else -> m
        },
        attempt = if (change.keepAttempt) state.attempt else null,
        verified = if (change.message == RedeemMessage.NotVerified) false else state.verified,
    )
    is RedeemChange.WaitStarted -> state.copy(waitUntil = change.until)
    RedeemChange.WaitOver -> state.copy(waitUntil = null)
    is RedeemChange.Account -> state.copy(verified = change.verified, signedInEmail = change.signedInEmail)
    is RedeemChange.TimeZone -> state.copy(timeZoneId = change.id)
    RedeemChange.SessionSignedOut -> state.copy(phase = RedeemPhase.Editing, code = "", attempt = null, message = null, waitUntil = null)
}
