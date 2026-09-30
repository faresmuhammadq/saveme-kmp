package com.fares.saveme.feature.auth.ui.checkinbox

import com.fares.saveme.core.presentation.MviViewModel

/** Static confirmation; it only navigates, so it has no Change type or reducer (P-16). */
class CheckInboxViewModel(email: String, kind: CheckInboxKind) :
    MviViewModel<CheckInboxState, CheckInboxIntent, CheckInboxEffect>(CheckInboxState(email, kind)) {

    override fun onIntent(intent: CheckInboxIntent) {
        when (intent) {
            CheckInboxIntent.GoToSignInClicked -> sendEffect(CheckInboxEffect.NavigateSignIn(currentState.email))
        }
    }
}
