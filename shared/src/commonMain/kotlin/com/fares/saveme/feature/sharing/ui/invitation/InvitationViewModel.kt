package com.fares.saveme.feature.sharing.ui.invitation

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.sharing.domain.model.DeclineResult
import com.fares.saveme.feature.sharing.domain.model.JoinResult
import com.fares.saveme.feature.sharing.domain.model.PreviewResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Answers an invitation link (spec 013 US2): reads what it offers, then joins or declines. The secret is a private constructor
 * value - never in the state, never in an effect, never logged (FR-010) - and every call is made once per tap and never retried
 * on its own (P-02.6): "Try again" repeats the last thing the person asked for.
 */
class InvitationViewModel(
    private val token: String,
    private val isSignedIn: () -> Boolean,
    private val preview: suspend (token: String) -> PreviewResult,
    private val join: suspend (token: String) -> JoinResult,
    private val decline: suspend (token: String) -> DeclineResult,
    /** Keeps the secret in memory for this app session ("Not now"). */
    private val hold: (token: String) -> Unit,
    /** Drops the held secret: the link can never be answered. */
    private val clearHeld: () -> Unit,
    /** Leaves "Invitation declined" for the screen that comes back. */
    private val announceDeclined: () -> Unit = {},
    timeZoneId: Flow<String> = emptyFlow(),
) : MviViewModel<InvitationState, InvitationIntent, InvitationEffect>(InvitationState()) {

    private enum class Pending { Preview, Join, Decline }

    private var pending = Pending.Preview
    private var started = false

    init {
        timeZoneId.onEach { id -> setState { reduce(it, InvitationChange.TimeZone(id)) } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: InvitationIntent) {
        when (intent) {
            InvitationIntent.Opened -> if (!started) {
                started = true
                load()
            }
            InvitationIntent.JoinClicked -> if (readyToAnswer()) answer(Pending.Join)
            InvitationIntent.DeclineClicked -> if (readyToAnswer()) setState { reduce(it, InvitationChange.ConfirmDecline(true)) }
            InvitationIntent.DeclineConfirmed -> {
                setState { reduce(it, InvitationChange.ConfirmDecline(false)) }
                if (readyToAnswer()) answer(Pending.Decline)
            }
            InvitationIntent.DeclineDismissed -> setState { reduce(it, InvitationChange.ConfirmDecline(false)) }
            InvitationIntent.LaterClicked -> {
                if (isWellFormed(token)) hold(token)
                sendEffect(InvitationEffect.Close())
            }
            InvitationIntent.RetryClicked -> if (!currentState.busy) when (pending) {
                Pending.Preview -> load()
                Pending.Join -> answer(Pending.Join)
                Pending.Decline -> answer(Pending.Decline)
            }
            InvitationIntent.SignInClicked -> sendEffect(InvitationEffect.NavigateSignIn)
            InvitationIntent.RegisterClicked -> sendEffect(InvitationEffect.NavigateRegister)
            InvitationIntent.OpenWalletClicked -> (currentState.mode as? InvitationMode.AlreadyMember)?.let { sendEffect(InvitationEffect.OpenWallet(it.walletId)) }
            InvitationIntent.Back -> sendEffect(InvitationEffect.Close())
        }
    }

    private fun readyToAnswer() = !currentState.busy && currentState.mode is InvitationMode.Ready

    /** No call is made for a link that cannot be an invitation, or while nobody is signed in. */
    private fun load() {
        when {
            !isWellFormed(token) -> setMode(InvitationMode.InvalidLink)
            !isSignedIn() -> setMode(InvitationMode.SignedOut)
            else -> {
                pending = Pending.Preview
                setState { reduce(reduce(it, InvitationChange.Mode(InvitationMode.Loading)), InvitationChange.Busy(true)) }
                viewModelScope.launch {
                    val mode = when (val result = preview(token)) {
                        is PreviewResult.Ready -> if (result.preview.alreadyMember) {
                            clearHeld()
                            InvitationMode.AlreadyMember(result.preview.walletId, result.preview.walletName)
                        } else {
                            InvitationMode.Ready(result.preview)
                        }
                        PreviewResult.Unavailable -> { clearHeld(); InvitationMode.Unavailable }
                        PreviewResult.InvalidLink -> { clearHeld(); InvitationMode.InvalidLink }
                        is PreviewResult.TooMany -> InvitationMode.TooMany(result.minutes)
                        PreviewResult.Offline -> InvitationMode.Offline
                        is PreviewResult.Failed -> InvitationMode.Failed(result.requestId)
                    }
                    setState { reduce(reduce(it, InvitationChange.Mode(mode)), InvitationChange.Busy(false)) }
                }
            }
        }
    }

    private fun answer(action: Pending) {
        pending = action
        val previous = currentState.mode
        setState { reduce(it, InvitationChange.Busy(true)) }
        viewModelScope.launch {
            when (action) {
                Pending.Join -> when (val result = join(token)) {
                    is JoinResult.Joined -> {
                        sendEffect(InvitationEffect.OpenWallet(result.walletId))
                        return@launch finish(previous, null)
                    }
                    JoinResult.OwnerPlanFull -> finish(previous, InvitationMode.OwnerPlanFull)
                    JoinResult.Unavailable -> { clearHeld(); finish(previous, InvitationMode.Unavailable) }
                    JoinResult.InvalidLink -> { clearHeld(); finish(previous, InvitationMode.InvalidLink) }
                    is JoinResult.TooMany -> finish(previous, InvitationMode.TooMany(result.minutes))
                    JoinResult.Offline -> finish(previous, InvitationMode.Offline)
                    is JoinResult.Failed -> finish(previous, InvitationMode.Failed(result.requestId))
                }
                Pending.Decline -> when (val result = decline(token)) {
                    DeclineResult.Declined -> {
                        announceDeclined()
                        sendEffect(InvitationEffect.Close(InvitationCloseMessage.Declined))
                        return@launch finish(previous, null)
                    }
                    DeclineResult.Unavailable -> { clearHeld(); finish(previous, InvitationMode.Unavailable) }
                    DeclineResult.InvalidLink -> { clearHeld(); finish(previous, InvitationMode.InvalidLink) }
                    is DeclineResult.TooMany -> finish(previous, InvitationMode.TooMany(result.minutes))
                    DeclineResult.Offline -> finish(previous, InvitationMode.Offline)
                    is DeclineResult.Failed -> finish(previous, InvitationMode.Failed(result.requestId))
                }
                Pending.Preview -> Unit
            }
        }
    }

    private fun finish(previous: InvitationMode, mode: InvitationMode?) {
        setState { reduce(reduce(it, InvitationChange.Busy(false)), InvitationChange.Mode(mode ?: previous)) }
    }

    private fun setMode(mode: InvitationMode) = setState { reduce(it, InvitationChange.Mode(mode)) }

    private companion object {
        const val TOKEN_LENGTH = 43

        fun isWellFormed(token: String) = token.length == TOKEN_LENGTH && token.all { c ->
            c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '_' || c == '-'
        }
    }
}
