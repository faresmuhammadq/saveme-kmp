package com.fares.saveme.feature.notifications.ui.settings

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.notifications.domain.NotificationPreferences
import com.fares.saveme.feature.notifications.domain.PreferencesLoad
import com.fares.saveme.feature.notifications.domain.PreferencesSave
import com.fares.saveme.feature.notifications.ui.PermissionStatus
import kotlinx.coroutines.launch

/**
 * Online-only: reads when it opens, saves the whole object once per tap, never retries or queues (FR-014, FR-017).
 * A stale version replaces the edits with the stored values; nothing is merged (FR-018).
 */
class NotificationSettingsViewModel(
    private val load: suspend () -> PreferencesLoad,
    private val save: suspend (NotificationPreferences, Long) -> PreferencesSave,
    private val timeZone: suspend () -> String? = { null },
    /** Tells the registration job the phone's permission changed (R-06). */
    private val permissionChanged: () -> Unit = {},
) : MviViewModel<NotificationSettingsState, NotificationSettingsIntent, NotificationSettingsEffect>(NotificationSettingsState()) {

    private var started = false

    override fun onIntent(intent: NotificationSettingsIntent) {
        when (intent) {
            NotificationSettingsIntent.Opened -> if (!started) {
                started = true
                read()
            }
            NotificationSettingsIntent.TryAgain -> read()
            is NotificationSettingsIntent.Resumed -> updatePhone(intent.status)
            is NotificationSettingsIntent.CategoryToggled -> edit { prefs ->
                prefs.copy(categories = prefs.categories + (intent.category to intent.on))
            }
            is NotificationSettingsIntent.QuietHoursToggled -> edit { it.copy(quietHours = it.quietHours.copy(enabled = intent.on)) }
            is NotificationSettingsIntent.StartChosen -> edit { it.copy(quietHours = it.quietHours.copy(start = quarterOf(intent.minutes))) }
            is NotificationSettingsIntent.EndChosen -> edit { it.copy(quietHours = it.quietHours.copy(end = quarterOf(intent.minutes))) }
            NotificationSettingsIntent.Save -> save()
            NotificationSettingsIntent.Back ->
                if (currentState.dirty) setState { reduce(it, NotificationSettingsChange.DiscardVisible(true)) }
                else sendEffect(NotificationSettingsEffect.NavigateBack)
            NotificationSettingsIntent.DiscardConfirmed -> {
                setState { reduce(it, NotificationSettingsChange.DiscardVisible(false)) }
                sendEffect(NotificationSettingsEffect.NavigateBack)
            }
            NotificationSettingsIntent.DiscardDismissed -> setState { reduce(it, NotificationSettingsChange.DiscardVisible(false)) }
            NotificationSettingsIntent.TurnOnClicked -> when (currentState.phone) {
                PermissionStatus.Askable -> sendEffect(NotificationSettingsEffect.RequestPermission)
                PermissionStatus.Blocked -> sendEffect(NotificationSettingsEffect.OpenPhoneSettings)
                PermissionStatus.Allowed -> Unit
            }
            is NotificationSettingsIntent.PermissionResult -> {
                if (intent.granted) {
                    phoneReported = true
                    setState { reduce(it, NotificationSettingsChange.Phone(PermissionStatus.Allowed)) }
                    permissionChanged()
                }
            }
            NotificationSettingsIntent.OpenPhoneSettingsClicked -> sendEffect(NotificationSettingsEffect.OpenPhoneSettings)
            NotificationSettingsIntent.MessageShown -> setState { reduce(it, NotificationSettingsChange.MessageCleared) }
        }
    }

    /** The first report is the screen's starting state; a later different one means the person changed it on the phone. */
    private fun updatePhone(status: PermissionStatus) {
        val changed = phoneReported && status != currentState.phone
        phoneReported = true
        setState { reduce(it, NotificationSettingsChange.Phone(status)) }
        if (changed) permissionChanged()
    }

    private var phoneReported = false

    private fun edit(transform: (NotificationPreferences) -> NotificationPreferences) {
        val edited = currentState.edited ?: return
        if (currentState.saving) return
        setState { reduce(it, NotificationSettingsChange.Edited(transform(edited))) }
    }

    private fun read() {
        setState { reduce(it, NotificationSettingsChange.Loading) }
        viewModelScope.launch {
            when (val result = load()) {
                is PreferencesLoad.Loaded -> {
                    val zone = timeZone()
                    setState { reduce(it, NotificationSettingsChange.Loaded(result.preferences, zone)) }
                }
                PreferencesLoad.Offline -> setState { reduce(it, NotificationSettingsChange.NotLoaded(LoadMessage.Offline)) }
                is PreferencesLoad.Failed -> setState { reduce(it, NotificationSettingsChange.NotLoaded(LoadMessage.Failed(result.requestId))) }
            }
        }
    }

    private fun save() {
        val state = currentState
        val edited = state.edited ?: return
        val version = state.read?.version ?: return
        if (!state.canSave) return
        setState { reduce(it, NotificationSettingsChange.Saving) }
        viewModelScope.launch {
            when (val result = save(edited, version)) {
                is PreferencesSave.Saved -> {
                    setState { reduce(it, NotificationSettingsChange.Saved(result.preferences)) }
                    sendEffect(NotificationSettingsEffect.ShowSaved)
                }
                is PreferencesSave.Conflict -> setState { reduce(it, NotificationSettingsChange.Conflict(result.current)) }
                is PreferencesSave.RateLimited ->
                    setState { reduce(it, NotificationSettingsChange.SaveFailed(SettingsMessage.RateLimited(result.seconds))) }
                PreferencesSave.Offline -> setState { reduce(it, NotificationSettingsChange.SaveFailed(SettingsMessage.Offline)) }
                is PreferencesSave.Invalid -> setState { reduce(it, NotificationSettingsChange.SaveFailed(SettingsMessage.Failed(result.requestId))) }
                is PreferencesSave.Failed -> setState { reduce(it, NotificationSettingsChange.SaveFailed(SettingsMessage.Failed(result.requestId))) }
            }
        }
    }
}
