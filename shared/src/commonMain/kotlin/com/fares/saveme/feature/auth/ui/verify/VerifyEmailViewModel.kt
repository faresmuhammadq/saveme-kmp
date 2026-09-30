package com.fares.saveme.feature.auth.ui.verify

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.model.ResendOutcome
import com.fares.saveme.feature.auth.domain.model.VerifyOutcome
import kotlinx.coroutines.launch

/**
 * The link secret lives only here (FR-009). After a verification while signed in, the account is
 * re-read from the server ([refreshAccount]) rather than marked verified locally, because the
 * answer doesn't say whose address it was (FR-011).
 */
class VerifyEmailViewModel(
    private val token: String,
    private val isSignedIn: () -> Boolean,
    private val verify: suspend (token: String) -> VerifyOutcome,
    private val resend: suspend () -> ResendOutcome,
    private val refreshAccount: suspend () -> Unit,
) : MviViewModel<VerifyEmailState, VerifyEmailIntent, VerifyEmailEffect>(VerifyEmailState()) {

    private var started = false

    override fun onIntent(intent: VerifyEmailIntent) {
        when (intent) {
            VerifyEmailIntent.Started -> if (!started) {
                started = true
                runVerify()
            }
            VerifyEmailIntent.Retry -> if (currentState.status in listOf(VerifyStatus.Offline, VerifyStatus.Error)) runVerify()
            VerifyEmailIntent.ResendClicked -> viewModelScope.launchResend(
                current = { currentState.resend },
                apply = { change -> setState { reduce(it, VerifyEmailChange.Resend(change)) } },
                resend = resend,
            )
            VerifyEmailIntent.ContinueClicked ->
                sendEffect(if (currentState.signedIn) VerifyEmailEffect.NavigateHome else VerifyEmailEffect.NavigateSignIn)
            VerifyEmailIntent.SignInClicked -> sendEffect(VerifyEmailEffect.NavigateSignIn)
        }
    }

    private fun runVerify() {
        val signedIn = isSignedIn()
        setState { reduce(reduce(it, VerifyEmailChange.SignedIn(signedIn)), VerifyEmailChange.Verifying) }
        viewModelScope.launch {
            val outcome = verify(token)
            if (outcome == VerifyOutcome.Verified && signedIn) refreshAccount()
            setState { reduce(it, VerifyEmailChange.Finished(outcome)) }
        }
    }

    override fun toString() = "VerifyEmailViewModel(state=$currentState)"
}
