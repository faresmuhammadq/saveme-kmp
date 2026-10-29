package com.fares.saveme.feature.auth.ui.verify

import com.fares.saveme.feature.auth.domain.model.VerifyOutcome

enum class VerifyStatus { Verifying, Verified, LinkInvalid, Offline, Error }

data class VerifyEmailState(
    val status: VerifyStatus = VerifyStatus.Verifying,
    val signedIn: Boolean = false,
    val requestId: String? = null,
    val resend: ResendState = ResendState(),
    /** The signed-in address the fresh link would go to; null when signed out. */
    val email: String? = null,
)

sealed interface VerifyEmailIntent {
    /** Sent once by the route when the screen first appears. */
    data object Started : VerifyEmailIntent
    data object Retry : VerifyEmailIntent
    data object ResendClicked : VerifyEmailIntent
    data object ContinueClicked : VerifyEmailIntent
    data object SignInClicked : VerifyEmailIntent
}

sealed interface VerifyEmailEffect {
    data object NavigateHome : VerifyEmailEffect
    data object NavigateSignIn : VerifyEmailEffect
}

sealed interface VerifyEmailChange {
    data class SignedIn(val signedIn: Boolean) : VerifyEmailChange
    data object Verifying : VerifyEmailChange
    data class Finished(val outcome: VerifyOutcome) : VerifyEmailChange
    data class Resend(val change: ResendChange) : VerifyEmailChange
}

fun reduce(state: VerifyEmailState, change: VerifyEmailChange): VerifyEmailState = when (change) {
    is VerifyEmailChange.SignedIn -> state.copy(signedIn = change.signedIn)
    VerifyEmailChange.Verifying -> state.copy(status = VerifyStatus.Verifying, requestId = null)
    is VerifyEmailChange.Finished -> when (val outcome = change.outcome) {
        VerifyOutcome.Verified -> state.copy(status = VerifyStatus.Verified)
        VerifyOutcome.LinkInvalid -> state.copy(status = VerifyStatus.LinkInvalid)
        VerifyOutcome.Offline -> state.copy(status = VerifyStatus.Offline)
        is VerifyOutcome.Error -> state.copy(status = VerifyStatus.Error, requestId = outcome.requestId)
    }
    is VerifyEmailChange.Resend -> state.copy(resend = reduceResend(state.resend, change.change))
}
