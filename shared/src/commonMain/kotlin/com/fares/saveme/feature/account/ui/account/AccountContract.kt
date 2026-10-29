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
    /** This build has a push provider (spec 017 FR-013); false in staging and production until the Firebase phase. */
    val notificationsAvailable: Boolean = false,
    /** Development builds only (spec 017 FR-036). */
    val devToolsAvailable: Boolean = false,
    /** The server address can be edited and is shown here (Android builds). */
    val serverAddress: String? = null,
    /** The profile's display name; null until the profile is known or when it has none. */
    val displayName: String? = null,
    val plan: AccountPlan? = null,
    /** Devices signed in, from the stored session list; null until it has been read. */
    val deviceCount: Int? = null,
)

/** The plan row of the account screen: the plan's name and, for a paid one, the status line. */
data class AccountPlan(val name: String, val status: AccountPlanStatus? = null)

sealed interface AccountPlanStatus {
    data class Renews(val on: Instant) : AccountPlanStatus
    data class Ends(val on: Instant) : AccountPlanStatus
    data object NoEnd : AccountPlanStatus
    data class Grace(val until: Instant) : AccountPlanStatus
}

enum class AccountDestination { Profile, Devices, ChangePassword, DeleteAccount, Plan, Exports, Notifications, Categories, DevTools, Server }

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
    data class DisplayName(val name: String?) : AccountChange
    data class Plan(val plan: AccountPlan?) : AccountChange
    data class DeviceCount(val count: Int) : AccountChange
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
    is AccountChange.DisplayName -> state.copy(displayName = change.name?.takeIf { it.isNotBlank() })
    is AccountChange.Plan -> state.copy(plan = change.plan)
    is AccountChange.DeviceCount -> state.copy(deviceCount = change.count)
}
