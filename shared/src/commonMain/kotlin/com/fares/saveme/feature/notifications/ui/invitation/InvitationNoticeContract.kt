package com.fares.saveme.feature.notifications.ui.invitation

/** The "check your email" notice an invitation push opens (FR-034). It shows the signed-in address and nothing about the invitation. */
data class InvitationNoticeState(val address: String = "")

sealed interface InvitationNoticeIntent {
    data object Ok : InvitationNoticeIntent
}

sealed interface InvitationNoticeEffect {
    data object Close : InvitationNoticeEffect
}

sealed interface InvitationNoticeChange {
    data class Address(val address: String) : InvitationNoticeChange
}

fun reduce(state: InvitationNoticeState, change: InvitationNoticeChange): InvitationNoticeState = when (change) {
    is InvitationNoticeChange.Address -> state.copy(address = change.address)
}
