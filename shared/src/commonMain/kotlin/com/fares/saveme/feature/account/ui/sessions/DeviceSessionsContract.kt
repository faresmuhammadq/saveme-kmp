package com.fares.saveme.feature.account.ui.sessions

import com.fares.saveme.feature.account.domain.model.DeviceSession
import com.fares.saveme.feature.account.domain.model.DeviceSessionList
import com.fares.saveme.feature.account.ui.AccountBanner
import kotlin.time.Instant

data class DeviceSessionsState(
    val sessions: List<DeviceSession> = emptyList(),
    val refreshedAt: Instant? = null,
    val timeZoneId: String = "UTC",
    val refreshing: Boolean = false,
    val confirm: ConfirmEnd? = null,
    val ending: Boolean = false,
    val banner: AccountBanner? = null,
) {
    /** FR-006: "sign out all others" only when there is another active session. */
    val canEndOthers: Boolean get() = sessions.any { !it.isCurrent }
}

sealed interface ConfirmEnd {
    data class One(val id: String, val deviceName: String) : ConfirmEnd
    data object Others : ConfirmEnd
}

sealed interface DeviceSessionsIntent {
    data object Refresh : DeviceSessionsIntent
    data class EndClicked(val id: String) : DeviceSessionsIntent
    data object EndOthersClicked : DeviceSessionsIntent
    data object ConfirmEnd : DeviceSessionsIntent
    data object DismissConfirm : DeviceSessionsIntent
    data object Back : DeviceSessionsIntent
}

sealed interface DeviceSessionsEffect {
    data class Ended(val others: Boolean) : DeviceSessionsEffect
    data object NavigateBack : DeviceSessionsEffect
}

sealed interface DeviceSessionsChange {
    data class Stored(val list: DeviceSessionList) : DeviceSessionsChange
    data class TimeZone(val id: String) : DeviceSessionsChange
    data class Refreshing(val busy: Boolean) : DeviceSessionsChange
    data class Confirm(val confirm: ConfirmEnd?) : DeviceSessionsChange
    data class Ending(val busy: Boolean) : DeviceSessionsChange
    data class Banner(val banner: AccountBanner?) : DeviceSessionsChange
}

fun reduce(state: DeviceSessionsState, change: DeviceSessionsChange): DeviceSessionsState = when (change) {
    is DeviceSessionsChange.Stored -> state.copy(sessions = change.list.items, refreshedAt = change.list.refreshedAt)
    is DeviceSessionsChange.TimeZone -> state.copy(timeZoneId = change.id)
    is DeviceSessionsChange.Refreshing -> state.copy(refreshing = change.busy)
    is DeviceSessionsChange.Confirm -> state.copy(confirm = change.confirm)
    is DeviceSessionsChange.Ending -> state.copy(ending = change.busy)
    is DeviceSessionsChange.Banner -> state.copy(banner = change.banner)
}
