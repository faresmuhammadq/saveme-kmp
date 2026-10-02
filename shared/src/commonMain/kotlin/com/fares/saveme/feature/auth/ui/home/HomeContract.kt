package com.fares.saveme.feature.auth.ui.home

import com.fares.saveme.core.sync.SyncStatus
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.auth.ui.verify.ResendChange
import com.fares.saveme.feature.auth.ui.verify.ResendState
import com.fares.saveme.feature.auth.ui.verify.reduceResend
import kotlin.time.Instant

/** Placeholder signed-in home until the Home feature exists (spec 001 Assumptions). */
data class HomeState(
    val email: String = "",
    val emailVerified: Boolean = true,
    val deletionCancelledNotice: Boolean = false,
    val deletionPendingOn: Instant? = null,
    val cancellingDeletion: Boolean = false,
    val confirmSignOutVisible: Boolean = false,
    val signingOut: Boolean = false,
    /** Verify prompt's "Resend email" (spec 003 FR-012, FR-013). */
    val resend: ResendState = ResendState(),
    /** Changes still only on this phone; the sign-out sheet warns about them (spec 004 FR-017). */
    val sync: SyncStatus = SyncStatus(),
)

/** What cancelling a pending deletion came to, as far as Home needs to know. */
enum class CancelDeletionResult { Done, Offline, Failed }

sealed interface HomeIntent {
    data object DismissDeletionNotice : HomeIntent
    data object AccountClicked : HomeIntent
    data object WalletsClicked : HomeIntent
    data object CategoriesClicked : HomeIntent
    data object AddTransactionClicked : HomeIntent
    data object CancelDeletion : HomeIntent
    data object SignOutClicked : HomeIntent
    data object SignOutConfirmed : HomeIntent
    data object SignOutDismissed : HomeIntent
    data object ResendClicked : HomeIntent
    data object SyncNowClicked : HomeIntent
}

sealed interface HomeEffect {
    data object NavigateAccount : HomeEffect
    data object NavigateWallets : HomeEffect
    data object NavigateCategories : HomeEffect
    data object NavigateAddTransaction : HomeEffect
    data class ShowMessage(val message: HomeMessage) : HomeEffect
}

enum class HomeMessage { DeletionCancelled, Offline, Failed }

sealed interface HomeChange {
    data class Account(val signedIn: AuthState.SignedIn) : HomeChange
    data object DeletionNoticeDismissed : HomeChange
    data class CancellingDeletion(val busy: Boolean) : HomeChange
    data class SignOutConfirmVisible(val visible: Boolean) : HomeChange
    data object SigningOut : HomeChange
    data class Resend(val change: ResendChange) : HomeChange
    data class Sync(val status: SyncStatus) : HomeChange
}

fun reduce(state: HomeState, change: HomeChange): HomeState = when (change) {
    is HomeChange.Account -> state.copy(
        email = change.signedIn.account.email,
        emailVerified = change.signedIn.account.emailVerified,
        deletionCancelledNotice = state.deletionCancelledNotice || change.signedIn.deletionCancelled,
        deletionPendingOn = change.signedIn.account.deletionScheduledFor
            .takeIf { change.signedIn.account.state == AccountState.PendingDeletion },
    )
    HomeChange.DeletionNoticeDismissed -> state.copy(deletionCancelledNotice = false)
    is HomeChange.CancellingDeletion -> state.copy(cancellingDeletion = change.busy)
    is HomeChange.SignOutConfirmVisible -> state.copy(confirmSignOutVisible = change.visible)
    HomeChange.SigningOut -> state.copy(signingOut = true)
    is HomeChange.Resend -> state.copy(resend = reduceResend(state.resend, change.change))
    is HomeChange.Sync -> state.copy(sync = change.status)
}
