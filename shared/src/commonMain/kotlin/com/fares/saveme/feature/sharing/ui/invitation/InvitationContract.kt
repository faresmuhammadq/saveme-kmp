package com.fares.saveme.feature.sharing.ui.invitation

import com.fares.saveme.feature.sharing.domain.model.InvitationPreview

/** What the invitation screen shows. Each unavailable cause reads the same on purpose (P-09.4, FR-014). */
sealed interface InvitationMode {
    /** Nobody is signed in: the entry screens come first and the invitation is held in memory meanwhile (FR-011). */
    data object SignedOut : InvitationMode
    data object Loading : InvitationMode
    data class Ready(val preview: InvitationPreview) : InvitationMode
    data class AlreadyMember(val walletId: String, val walletName: String) : InvitationMode
    data object Unavailable : InvitationMode
    data object InvalidLink : InvitationMode
    data object Offline : InvitationMode
    data class TooMany(val minutes: Int) : InvitationMode
    data object OwnerPlanFull : InvitationMode
    data class Failed(val requestId: String?) : InvitationMode
}

/**
 * The state holds no secret: the link's secret lives in the store only (spec 013 FR-010, P-09.2), so nothing here can print it.
 */
data class InvitationState(
    val mode: InvitationMode = InvitationMode.Loading,
    /** A call is in flight: the buttons wait. */
    val busy: Boolean = false,
    val confirmDecline: Boolean = false,
    val timeZoneId: String = "UTC",
)

enum class InvitationCloseMessage { Declined }

sealed interface InvitationIntent {
    /** The screen opened: reads the invitation once. */
    data object Opened : InvitationIntent
    data object JoinClicked : InvitationIntent
    data object DeclineClicked : InvitationIntent
    data object DeclineConfirmed : InvitationIntent
    data object DeclineDismissed : InvitationIntent

    /** Keeps the secret in memory for this app session (Home shows a card). */
    data object LaterClicked : InvitationIntent
    data object RetryClicked : InvitationIntent
    data object SignInClicked : InvitationIntent
    data object RegisterClicked : InvitationIntent
    data object OpenWalletClicked : InvitationIntent
    data object Back : InvitationIntent
}

sealed interface InvitationEffect {
    data class OpenWallet(val walletId: String) : InvitationEffect
    data object NavigateSignIn : InvitationEffect
    data object NavigateRegister : InvitationEffect
    data class Close(val message: InvitationCloseMessage? = null) : InvitationEffect
}

sealed interface InvitationChange {
    data class Mode(val mode: InvitationMode) : InvitationChange
    data class Busy(val busy: Boolean) : InvitationChange
    data class ConfirmDecline(val open: Boolean) : InvitationChange
    data class TimeZone(val id: String) : InvitationChange
}

fun reduce(state: InvitationState, change: InvitationChange): InvitationState = when (change) {
    is InvitationChange.Mode -> state.copy(mode = change.mode)
    is InvitationChange.Busy -> state.copy(busy = change.busy)
    is InvitationChange.ConfirmDecline -> state.copy(confirmDecline = change.open)
    is InvitationChange.TimeZone -> state.copy(timeZoneId = change.id)
}
