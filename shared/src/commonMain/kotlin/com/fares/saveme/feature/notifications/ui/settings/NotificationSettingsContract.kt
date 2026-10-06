package com.fares.saveme.feature.notifications.ui.settings

import com.fares.saveme.feature.notifications.domain.NotificationCategory
import com.fares.saveme.feature.notifications.domain.NotificationPreferences
import com.fares.saveme.feature.notifications.domain.QuarterHour
import com.fares.saveme.feature.notifications.ui.PermissionStatus

/** Why the preferences are not on screen (FR-014). */
sealed interface LoadMessage {
    data object Offline : LoadMessage
    data class Failed(val requestId: String?) : LoadMessage
}

/** The one message under Save (FR-018); chosen by outcome. */
sealed interface SettingsMessage {
    data object Conflict : SettingsMessage
    data class RateLimited(val seconds: Long?) : SettingsMessage
    data object Offline : SettingsMessage
    data class Failed(val requestId: String?) : SettingsMessage
}

data class NotificationSettingsState(
    val loading: Boolean = true,
    val notLoaded: LoadMessage? = null,
    /** Last read or saved. */
    val read: NotificationPreferences? = null,
    val edited: NotificationPreferences? = null,
    val phone: PermissionStatus = PermissionStatus.Allowed,
    val timeZoneId: String? = null,
    val saving: Boolean = false,
    val message: SettingsMessage? = null,
    val discardConfirmVisible: Boolean = false,
) {
    val dirty: Boolean get() = read != null && edited != null && edited != read
    val valid: Boolean get() = edited?.quietHours?.isValid ?: true
    val canSave: Boolean get() = dirty && valid && !saving
}

sealed interface NotificationSettingsIntent {
    data object Opened : NotificationSettingsIntent
    data class Resumed(val status: PermissionStatus) : NotificationSettingsIntent
    data object TryAgain : NotificationSettingsIntent
    data class CategoryToggled(val category: NotificationCategory, val on: Boolean) : NotificationSettingsIntent
    data class QuietHoursToggled(val on: Boolean) : NotificationSettingsIntent
    data class StartChosen(val minutes: Int) : NotificationSettingsIntent
    data class EndChosen(val minutes: Int) : NotificationSettingsIntent
    data object Save : NotificationSettingsIntent
    data object Back : NotificationSettingsIntent
    data object DiscardConfirmed : NotificationSettingsIntent
    data object DiscardDismissed : NotificationSettingsIntent
    data object TurnOnClicked : NotificationSettingsIntent
    data class PermissionResult(val granted: Boolean) : NotificationSettingsIntent
    data object OpenPhoneSettingsClicked : NotificationSettingsIntent
    data object MessageShown : NotificationSettingsIntent
}

sealed interface NotificationSettingsEffect {
    data object RequestPermission : NotificationSettingsEffect
    data object OpenPhoneSettings : NotificationSettingsEffect
    data object NavigateBack : NotificationSettingsEffect
    data object ShowSaved : NotificationSettingsEffect
}

sealed interface NotificationSettingsChange {
    data object Loading : NotificationSettingsChange
    data class Loaded(val preferences: NotificationPreferences, val timeZoneId: String?) : NotificationSettingsChange
    data class NotLoaded(val message: LoadMessage) : NotificationSettingsChange
    data class Edited(val preferences: NotificationPreferences) : NotificationSettingsChange
    data object Saving : NotificationSettingsChange
    data class Saved(val preferences: NotificationPreferences) : NotificationSettingsChange
    data class Conflict(val current: NotificationPreferences) : NotificationSettingsChange
    data class SaveFailed(val message: SettingsMessage) : NotificationSettingsChange
    data class Phone(val status: PermissionStatus) : NotificationSettingsChange
    data class DiscardVisible(val visible: Boolean) : NotificationSettingsChange
    data object MessageCleared : NotificationSettingsChange
}

fun reduce(state: NotificationSettingsState, change: NotificationSettingsChange): NotificationSettingsState = when (change) {
    NotificationSettingsChange.Loading -> state.copy(loading = true, notLoaded = null)
    is NotificationSettingsChange.Loaded -> state.copy(
        loading = false, notLoaded = null, read = change.preferences, edited = change.preferences,
        timeZoneId = change.timeZoneId, message = null,
    )
    is NotificationSettingsChange.NotLoaded -> state.copy(loading = false, notLoaded = change.message)
    is NotificationSettingsChange.Edited -> state.copy(edited = change.preferences, message = null)
    NotificationSettingsChange.Saving -> state.copy(saving = true, message = null)
    is NotificationSettingsChange.Saved -> state.copy(saving = false, read = change.preferences, edited = change.preferences, message = null)
    is NotificationSettingsChange.Conflict -> state.copy(
        saving = false, read = change.current, edited = change.current, message = SettingsMessage.Conflict,
    )
    is NotificationSettingsChange.SaveFailed -> state.copy(saving = false, message = change.message)
    is NotificationSettingsChange.Phone -> state.copy(phone = change.status)
    is NotificationSettingsChange.DiscardVisible -> state.copy(discardConfirmVisible = change.visible)
    NotificationSettingsChange.MessageCleared -> state.copy(message = null)
}

/** [QuarterHour] from a picked value; quarter hours only (FR-016). */
internal fun quarterOf(minutes: Int): QuarterHour = QuarterHour.snapped(minutes)
