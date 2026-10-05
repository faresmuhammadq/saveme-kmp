package com.fares.saveme.feature.billing.ui.redeem

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.billing.domain.AttemptKey
import com.fares.saveme.feature.billing.domain.RedeemCode
import com.fares.saveme.feature.billing.domain.RedeemOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Redeems a mailed code (spec 015). The store owns the attempt identifier: made on the first Redeem for a code, kept
 * on every retry of the same code, dropped when the code is edited or the server says it was reused (R-05). The clock
 * and the wait timer live here, never in `reduce`. Domain lambdas only (P-16.3).
 */
class RedeemViewModel(
    private val redeem: suspend (rawCode: String, attempt: AttemptKey) -> RedeemOutcome,
    private val isEmailVerified: () -> Boolean,
    private val signedInEmail: () -> String?,
    signedIn: Flow<Boolean>,
    timeZoneId: Flow<String>,
    private val now: () -> Instant = { Clock.System.now() },
    private val newAttemptKey: () -> AttemptKey = { AttemptKey(Uuid.generateV7().toString()) },
) : MviViewModel<RedeemState, RedeemIntent, RedeemEffect>(RedeemState()) {

    private var waitTimer: Job? = null

    init {
        refreshAccount()
        timeZoneId.onEach { id -> setState { reduce(it, RedeemChange.TimeZone(id)) } }.launchIn(viewModelScope)
        signedIn.onEach { isSignedIn ->
            if (!isSignedIn) {
                waitTimer?.cancel()
                setState { reduce(it, RedeemChange.SessionSignedOut) }
            }
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: RedeemIntent) {
        when (intent) {
            RedeemIntent.Opened -> refreshAccount()
            // Typed and pasted text take the same road: a whole email line yields the code in it (R-02).
            is RedeemIntent.CodeChanged -> setState { reduce(it, RedeemChange.Code(RedeemCode.fromPaste(intent.text))) }
            RedeemIntent.Redeem, RedeemIntent.TryAgain -> send()
            RedeemIntent.Done, RedeemIntent.Back -> sendEffect(RedeemEffect.NavigateBack)
        }
    }

    private fun refreshAccount() {
        setState { reduce(it, RedeemChange.Account(isEmailVerified(), signedInEmail())) }
    }

    private fun send() {
        if (!currentState.canRedeem) return
        refreshAccount()
        if (!currentState.verified) {
            sendEffect(RedeemEffect.RequireVerification)
            return
        }
        val code = currentState.code
        val attempt = currentState.attempt ?: newAttemptKey()
        setState { reduce(it, RedeemChange.SendStarted(attempt)) }
        viewModelScope.launch { handle(redeem(code, attempt)) }
    }

    private fun handle(outcome: RedeemOutcome) {
        fun refused(message: RedeemMessage, keepAttempt: Boolean = true) =
            setState { reduce(it, RedeemChange.Refused(message, keepAttempt)) }
        when (outcome) {
            is RedeemOutcome.Redeemed -> setState { reduce(it, RedeemChange.Succeeded(outcome.confirmation)) }
            RedeemOutcome.Offline -> refused(RedeemMessage.Offline)
            RedeemOutcome.NotValid -> refused(RedeemMessage.NotValid)
            RedeemOutcome.Used -> refused(RedeemMessage.Used)
            RedeemOutcome.Expired -> refused(RedeemMessage.Expired)
            RedeemOutcome.OtherAddress -> refused(RedeemMessage.OtherAddress(signedInEmail()))
            RedeemOutcome.NotVerified -> refused(RedeemMessage.NotVerified)
            RedeemOutcome.InvalidFormat -> refused(RedeemMessage.InvalidFormat)
            RedeemOutcome.InProgress -> refused(RedeemMessage.InProgress)
            is RedeemOutcome.KeyReused -> refused(RedeemMessage.Failed(outcome.requestId), keepAttempt = false)
            is RedeemOutcome.Failed -> refused(RedeemMessage.Failed(outcome.requestId))
            RedeemOutcome.SignedOut -> setState { reduce(it, RedeemChange.SessionSignedOut) }
            is RedeemOutcome.RateLimited -> rateLimited(outcome.retryAfterSeconds)
        }
    }

    private fun rateLimited(seconds: Long?) {
        val wait = seconds?.takeIf { it > 0 }
        val minutes = wait?.let { ((it + 59) / 60).toInt() }
        setState { reduce(it, RedeemChange.Refused(RedeemMessage.RateLimited(minutes))) }
        waitTimer?.cancel()
        if (wait != null) {
            setState { reduce(it, RedeemChange.WaitStarted(now() + wait.seconds)) }
            waitTimer = viewModelScope.launch {
                delay(wait.seconds)
                setState { reduce(it, RedeemChange.WaitOver) }
            }
        }
    }
}
