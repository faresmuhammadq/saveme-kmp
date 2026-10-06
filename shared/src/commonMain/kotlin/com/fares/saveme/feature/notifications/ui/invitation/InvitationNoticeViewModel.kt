package com.fares.saveme.feature.notifications.ui.invitation

import com.fares.saveme.core.presentation.MviViewModel

/** No call is made: the invitation's token is only in the mailed link (contract PushPayload). */
class InvitationNoticeViewModel(address: () -> String) :
    MviViewModel<InvitationNoticeState, InvitationNoticeIntent, InvitationNoticeEffect>(InvitationNoticeState(address())) {

    override fun onIntent(intent: InvitationNoticeIntent) {
        when (intent) {
            InvitationNoticeIntent.Ok -> sendEffect(InvitationNoticeEffect.Close)
        }
    }
}
