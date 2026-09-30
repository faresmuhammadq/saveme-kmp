package com.fares.saveme.feature.auth.ui.verify

import androidx.compose.runtime.Composable
import com.fares.saveme.feature.auth.domain.model.ResendOutcome
import com.fares.saveme.feature.auth.ui.minutesToWait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

/**
 * "Resend email", shared by the Home prompt and the email-not-verified sheet. After a send the
 * action stays off for [COOLDOWN_SECONDS] (FR-013); the server's own limit is three per hour.
 */
data class ResendState(
    val sending: Boolean = false,
    val cooldownSeconds: Int = 0,
    val message: ResendMessage? = null,
) {
    val canResend: Boolean get() = !sending && cooldownSeconds == 0

    companion object {
        const val COOLDOWN_SECONDS = 30
    }
}

sealed interface ResendMessage {
    data object Sent : ResendMessage
    data class RateLimited(val minutes: Int?) : ResendMessage
    data object Offline : ResendMessage
    data object Failed : ResendMessage
}

sealed interface ResendChange {
    data object Started : ResendChange
    data class Finished(val outcome: ResendOutcome) : ResendChange
    data object Tick : ResendChange
}

fun reduceResend(state: ResendState, change: ResendChange): ResendState = when (change) {
    ResendChange.Started -> state.copy(sending = true, message = null)
    is ResendChange.Finished -> when (val outcome = change.outcome) {
        ResendOutcome.Sent -> state.copy(sending = false, cooldownSeconds = ResendState.COOLDOWN_SECONDS, message = ResendMessage.Sent)
        is ResendOutcome.RateLimited -> state.copy(sending = false, message = ResendMessage.RateLimited(minutesToWait(outcome.retryAfterSeconds)))
        ResendOutcome.Offline -> state.copy(sending = false, message = ResendMessage.Offline)
        is ResendOutcome.Error -> state.copy(sending = false, message = ResendMessage.Failed)
    }
    ResendChange.Tick -> state.copy(cooldownSeconds = (state.cooldownSeconds - 1).coerceAtLeast(0))
}

/**
 * Runs one resend in [scope]: sends, then counts the cooldown down once a second. [current] reads
 * the latest state and [apply] reduces a change into the caller's state.
 */
fun CoroutineScope.launchResend(
    current: () -> ResendState,
    apply: (ResendChange) -> Unit,
    resend: suspend () -> ResendOutcome,
): Job? {
    if (!current().canResend) return null
    apply(ResendChange.Started)
    return launch {
        val outcome = resend()
        apply(ResendChange.Finished(outcome))
        if (outcome == ResendOutcome.Sent) {
            repeat(ResendState.COOLDOWN_SECONDS) {
                delay(1_000)
                apply(ResendChange.Tick)
            }
        }
    }
}

@Composable
fun ResendMessage.text(): String = when (this) {
    ResendMessage.Sent -> stringResource(Res.string.verification_sent)
    is ResendMessage.RateLimited -> minutes?.let { pluralStringResource(Res.plurals.error_rate_limited, it, it) }
        ?: stringResource(Res.string.error_rate_limited_soon)
    ResendMessage.Offline -> stringResource(Res.string.error_offline)
    ResendMessage.Failed -> stringResource(Res.string.error_generic)
}
