package com.fares.saveme.feature.auth.ui.checkinbox

import kotlinx.serialization.Serializable

/** Which mail was requested: a verification link (register) or a reset link (forgot password). */
@Serializable
enum class CheckInboxKind { Verification, Reset }

data class CheckInboxState(val email: String, val kind: CheckInboxKind)

sealed interface CheckInboxIntent {
    data object GoToSignInClicked : CheckInboxIntent
}

sealed interface CheckInboxEffect {
    data class NavigateSignIn(val email: String) : CheckInboxEffect
}
