package com.fares.saveme.feature.account.ui.account

import com.fares.saveme.core.sync.SyncStatus
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.AccountState as AccountStatus
import kotlin.time.Instant

data class AccountState(
    val email: String = "",
    val emailVerified: Boolean = true,
    val deletionPendingOn: Instant? = null,
    val confirmSignOutVisible: Boolean = false,
    val signingOut: Boolean = false,
    /** Changes still only on this phone (spec 004 FR-016). */
    val sync: SyncStatus = SyncStatus(),
)

enum class AccountDestination { Profile, Devices, ChangePassword, DeleteAccount }

sealed interface AccountIntent {
    data class Open(val destination: AccountDestination) : AccountIntent
    data object Back : AccountIntent
    data object SignOutClicked : AccountIntent
    data object SignOutConfirmed : AccountIntent
    data object SignOutDismissed : AccountIntent
    data object SyncNowClicked : AccountIntent
}

sealed interface AccountEffect {
    data class Navigate(val destination: AccountDestination) : AccountEffect
    data object NavigateBack : AccountEffect
}

sealed interface AccountChange {
    data class Account(val signedIn: AuthState.SignedIn) : AccountChange
    data class SignOutConfirmVisible(val visible: Boolean) : AccountChange
    data object SigningOut : AccountChange
    data class Sync(val status: SyncStatus) : AccountChange
}

fun reduce(state: AccountState, change: AccountChange): AccountState = when (change) {
    is AccountChange.Account -> state.copy(
        email = change.signedIn.account.email,
        emailVerified = change.signedIn.account.emailVerified,
        deletionPendingOn = change.signedIn.account.deletionScheduledFor
            .takeIf { change.signedIn.account.state == AccountStatus.PendingDeletion },
    )
    is AccountChange.SignOutConfirmVisible -> state.copy(confirmSignOutVisible = change.visible)
    AccountChange.SigningOut -> state.copy(signingOut = true)
    is AccountChange.Sync -> state.copy(sync = change.status)
}
