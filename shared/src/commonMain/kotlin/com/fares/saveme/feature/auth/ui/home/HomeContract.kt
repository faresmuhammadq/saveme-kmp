package com.fares.saveme.feature.auth.ui.home

import com.fares.saveme.feature.auth.domain.AuthState

/** Placeholder signed-in home until the Home feature exists (spec Assumptions). */
data class HomeState(
    val email: String = "",
    val emailVerified: Boolean = true,
    val deletionCancelledNotice: Boolean = false,
)

sealed interface HomeIntent {
    data object DismissDeletionNotice : HomeIntent
}

sealed interface HomeEffect

sealed interface HomeChange {
    data class Account(val signedIn: AuthState.SignedIn) : HomeChange
    data object DeletionNoticeDismissed : HomeChange
}

fun reduce(state: HomeState, change: HomeChange): HomeState = when (change) {
    is HomeChange.Account -> state.copy(
        email = change.signedIn.account.email,
        emailVerified = change.signedIn.account.emailVerified,
        deletionCancelledNotice = state.deletionCancelledNotice || change.signedIn.deletionCancelled,
    )
    HomeChange.DeletionNoticeDismissed -> state.copy(deletionCancelledNotice = false)
}
