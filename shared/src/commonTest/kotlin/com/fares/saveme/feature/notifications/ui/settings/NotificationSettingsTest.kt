package com.fares.saveme.feature.notifications.ui.settings

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.notifications.domain.NotificationCategory
import com.fares.saveme.feature.notifications.domain.NotificationPreferences
import com.fares.saveme.feature.notifications.domain.PreferencesLoad
import com.fares.saveme.feature.notifications.domain.PreferencesSave
import com.fares.saveme.feature.notifications.domain.QuarterHour
import com.fares.saveme.feature.notifications.domain.QuietHours
import com.fares.saveme.feature.notifications.ui.PermissionStatus
import com.fares.saveme.feature.notifications.ui.minutesFor
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val defaults = NotificationPreferences(
    version = 1,
    categories = NotificationCategory.entries.associateWith { true },
    quietHours = QuietHours(true, QuarterHour(22 * 60), QuarterHour(8 * 60)),
)

class NotificationSettingsReducerTest {

    private val loaded = reduce(NotificationSettingsState(), NotificationSettingsChange.Loaded(defaults, "Africa/Cairo"))

    @Test
    fun loadingThenReady() {
        assertTrue(NotificationSettingsState().loading)
        assertFalse(loaded.loading)
        assertEquals(defaults, loaded.read)
        assertEquals(defaults, loaded.edited)
        assertEquals("Africa/Cairo", loaded.timeZoneId)
        assertFalse(loaded.dirty)
        assertFalse(loaded.canSave)
    }

    @Test
    fun anEditMakesItDirtyAndSaveable() {
        val edited = reduce(loaded, NotificationSettingsChange.Edited(defaults.copy(categories = defaults.categories + (NotificationCategory.Billing to false))))
        assertTrue(edited.dirty && edited.valid && edited.canSave)
        assertFalse(reduce(edited, NotificationSettingsChange.Saving).canSave)
    }

    @Test
    fun startEqualToEndBlocksSave() {
        val bad = reduce(loaded, NotificationSettingsChange.Edited(defaults.copy(quietHours = QuietHours(true, QuarterHour(480), QuarterHour(480)))))
        assertTrue(bad.dirty)
        assertFalse(bad.valid)
        assertFalse(bad.canSave)
    }

    @Test
    fun savedReplacesBothCopiesAndConflictReplacesTheEdits() {
        val edited = reduce(loaded, NotificationSettingsChange.Edited(defaults.copy(categories = defaults.categories + (NotificationCategory.Billing to false))))
        val stored = defaults.copy(version = 2)
        val saved = reduce(reduce(edited, NotificationSettingsChange.Saving), NotificationSettingsChange.Saved(stored))
        assertEquals(stored, saved.read)
        assertEquals(stored, saved.edited)
        assertFalse(saved.saving)
        val theirs = defaults.copy(version = 5, categories = defaults.categories + (NotificationCategory.Sharing to false))
        val conflict = reduce(reduce(edited, NotificationSettingsChange.Saving), NotificationSettingsChange.Conflict(theirs))
        assertEquals(theirs, conflict.edited)
        assertEquals(theirs, conflict.read)
        assertEquals(SettingsMessage.Conflict, conflict.message)
        assertFalse(conflict.dirty)
    }

    @Test
    fun aFailedSaveKeepsTheEditsAndShowsTheMessage() {
        val edited = reduce(loaded, NotificationSettingsChange.Edited(defaults.copy(categories = defaults.categories + (NotificationCategory.Billing to false))))
        val failed = reduce(reduce(edited, NotificationSettingsChange.Saving), NotificationSettingsChange.SaveFailed(SettingsMessage.Offline))
        assertTrue(failed.dirty)
        assertEquals(SettingsMessage.Offline, failed.message)
        assertNull(reduce(failed, NotificationSettingsChange.Edited(failed.edited!!)).message)
    }

    @Test
    fun rateLimitMinutesRoundUp() {
        assertEquals(2, minutesFor(61))
        assertEquals(2, minutesFor(120))
        assertEquals(3, minutesFor(121))
        assertEquals(30, minutesFor(1800))
        assertEquals(31, minutesFor(1801))
    }
}

class NotificationSettingsViewModelTest {

    private var saved = mutableListOf<Pair<NotificationPreferences, Long>>()
    private var permissionChanges = 0

    private fun vm(
        load: suspend () -> PreferencesLoad = { PreferencesLoad.Loaded(defaults) },
        save: suspend (NotificationPreferences, Long) -> PreferencesSave = { prefs, version -> saved += prefs to version; PreferencesSave.Saved(prefs.copy(version = version + 1)) },
        timeZone: suspend () -> String? = { "Africa/Cairo" },
    ) = NotificationSettingsViewModel(load, save, timeZone) { permissionChanges++ }

    @Test
    fun openingReadsOnceAndShowsTheTimeZone() = runMviTest {
        var reads = 0
        val store = vm(load = { reads++; PreferencesLoad.Loaded(defaults) })
        assertTrue(store.state.value.loading)
        store.onIntent(NotificationSettingsIntent.Opened)
        store.onIntent(NotificationSettingsIntent.Opened)
        assertEquals(1, reads)
        assertFalse(store.state.value.loading)
        assertEquals("Africa/Cairo", store.state.value.timeZoneId)
    }

    @Test
    fun theTimeZoneNoteIsAbsentWhileTheProfileIsNotOnThePhone() = runMviTest {
        val store = vm(timeZone = { null })
        store.onIntent(NotificationSettingsIntent.Opened)
        assertNull(store.state.value.timeZoneId)
    }

    @Test
    fun offlineAndFailureShowTheNotLoadedMessageAndTryAgainReloads() = runMviTest {
        var answer: PreferencesLoad = PreferencesLoad.Offline
        val store = vm(load = { answer })
        store.onIntent(NotificationSettingsIntent.Opened)
        assertEquals(LoadMessage.Offline, store.state.value.notLoaded)
        answer = PreferencesLoad.Failed("req-1")
        store.onIntent(NotificationSettingsIntent.TryAgain)
        assertEquals(LoadMessage.Failed("req-1"), store.state.value.notLoaded)
        answer = PreferencesLoad.Loaded(defaults)
        store.onIntent(NotificationSettingsIntent.TryAgain)
        assertNull(store.state.value.notLoaded)
        assertEquals(defaults, store.state.value.read)
    }

    @Test
    fun twoEditsThenOneSaveSendsEverythingWithTheReadVersion() = runMviTest {
        val store = vm()
        store.onIntent(NotificationSettingsIntent.Opened)
        store.onIntent(NotificationSettingsIntent.CategoryToggled(NotificationCategory.BudgetExceeded, false))
        store.onIntent(NotificationSettingsIntent.StartChosen(23 * 60))
        store.effects.test {
            store.onIntent(NotificationSettingsIntent.Save)
            assertEquals(NotificationSettingsEffect.ShowSaved, awaitItem())
        }
        val (sent, version) = saved.single()
        assertEquals(1, version)
        assertFalse(sent.isOn(NotificationCategory.BudgetExceeded))
        assertEquals(QuarterHour(23 * 60), sent.quietHours.start)
        assertEquals(2, store.state.value.read?.version)
        assertFalse(store.state.value.dirty)
    }

    @Test
    fun saveDoesNothingWhenNotDirtyInvalidOrAlreadySaving() = runMviTest {
        val gate = CompletableDeferred<PreferencesSave>()
        val store = vm(save = { _, _ -> gate.await() })
        store.onIntent(NotificationSettingsIntent.Opened)
        store.onIntent(NotificationSettingsIntent.Save)
        assertFalse(store.state.value.saving, "not dirty")
        store.onIntent(NotificationSettingsIntent.StartChosen(8 * 60))
        store.onIntent(NotificationSettingsIntent.Save)
        assertFalse(store.state.value.saving, "start equals end")
        store.onIntent(NotificationSettingsIntent.EndChosen(9 * 60))
        store.onIntent(NotificationSettingsIntent.Save)
        assertTrue(store.state.value.saving)
        store.onIntent(NotificationSettingsIntent.Save)
        gate.complete(PreferencesSave.Offline)
        assertEquals(SettingsMessage.Offline, store.state.value.message)
        assertTrue(store.state.value.dirty, "offline keeps the edits")
    }

    @Test
    fun aConflictReplacesTheEditsWithTheStoredValues() = runMviTest {
        val theirs = defaults.copy(version = 4, categories = defaults.categories + (NotificationCategory.Sharing to false))
        val store = vm(save = { _, _ -> PreferencesSave.Conflict(theirs) })
        store.onIntent(NotificationSettingsIntent.Opened)
        store.onIntent(NotificationSettingsIntent.QuietHoursToggled(false))
        store.onIntent(NotificationSettingsIntent.Save)
        assertEquals(theirs, store.state.value.edited)
        assertEquals(4, store.state.value.read?.version)
        assertEquals(SettingsMessage.Conflict, store.state.value.message)
    }

    @Test
    fun otherRefusalsMapToTheirMessages() = runMviTest {
        var answer: PreferencesSave = PreferencesSave.RateLimited(125)
        val store = vm(save = { _, _ -> answer })
        store.onIntent(NotificationSettingsIntent.Opened)
        store.onIntent(NotificationSettingsIntent.QuietHoursToggled(false))
        store.onIntent(NotificationSettingsIntent.Save)
        assertEquals(SettingsMessage.RateLimited(125), store.state.value.message)
        answer = PreferencesSave.Invalid("req-9")
        store.onIntent(NotificationSettingsIntent.Save)
        assertEquals(SettingsMessage.Failed("req-9"), store.state.value.message)
        answer = PreferencesSave.Failed(null)
        store.onIntent(NotificationSettingsIntent.Save)
        assertEquals(SettingsMessage.Failed(null), store.state.value.message)
    }

    @Test
    fun backWithEditsAsksFirstAndBackWithoutLeavesAtOnce() = runMviTest {
        val store = vm()
        store.onIntent(NotificationSettingsIntent.Opened)
        store.effects.test {
            store.onIntent(NotificationSettingsIntent.Back)
            assertEquals(NotificationSettingsEffect.NavigateBack, awaitItem())
            store.onIntent(NotificationSettingsIntent.QuietHoursToggled(false))
            store.onIntent(NotificationSettingsIntent.Back)
            assertTrue(store.state.value.discardConfirmVisible)
            expectNoEvents()
            store.onIntent(NotificationSettingsIntent.DiscardDismissed)
            assertFalse(store.state.value.discardConfirmVisible)
            store.onIntent(NotificationSettingsIntent.Back)
            store.onIntent(NotificationSettingsIntent.DiscardConfirmed)
            assertEquals(NotificationSettingsEffect.NavigateBack, awaitItem())
        }
    }

    @Test
    fun turnOnAsksWhenAskableAndOpensSettingsWhenBlocked() = runMviTest {
        val store = vm()
        store.onIntent(NotificationSettingsIntent.Opened)
        store.effects.test {
            store.onIntent(NotificationSettingsIntent.Resumed(PermissionStatus.Askable))
            store.onIntent(NotificationSettingsIntent.TurnOnClicked)
            assertEquals(NotificationSettingsEffect.RequestPermission, awaitItem())
            store.onIntent(NotificationSettingsIntent.Resumed(PermissionStatus.Blocked))
            store.onIntent(NotificationSettingsIntent.TurnOnClicked)
            assertEquals(NotificationSettingsEffect.OpenPhoneSettings, awaitItem())
            store.onIntent(NotificationSettingsIntent.OpenPhoneSettingsClicked)
            assertEquals(NotificationSettingsEffect.OpenPhoneSettings, awaitItem())
        }
    }

    @Test
    fun aChangeOfThePhonesStatusTellsTheRegistrationJobButTheFirstReportDoesNot() = runMviTest {
        val store = vm()
        store.onIntent(NotificationSettingsIntent.Resumed(PermissionStatus.Askable))
        assertEquals(0, permissionChanges)
        store.onIntent(NotificationSettingsIntent.Resumed(PermissionStatus.Askable))
        assertEquals(0, permissionChanges)
        store.onIntent(NotificationSettingsIntent.Resumed(PermissionStatus.Allowed))
        assertEquals(1, permissionChanges)
        assertEquals(PermissionStatus.Allowed, store.state.value.phone)
    }

    @Test
    fun aGrantedDialogAnswerUpdatesTheCardAndTellsTheJob() = runMviTest {
        val store = vm()
        store.onIntent(NotificationSettingsIntent.Resumed(PermissionStatus.Askable))
        store.onIntent(NotificationSettingsIntent.PermissionResult(granted = false))
        assertEquals(0, permissionChanges)
        store.onIntent(NotificationSettingsIntent.PermissionResult(granted = true))
        assertEquals(1, permissionChanges)
        assertEquals(PermissionStatus.Allowed, store.state.value.phone)
    }

    @Test
    fun theMessageClearsWhenShown() = runMviTest {
        val store = vm(save = { _, _ -> PreferencesSave.Offline })
        store.onIntent(NotificationSettingsIntent.Opened)
        store.onIntent(NotificationSettingsIntent.QuietHoursToggled(false))
        store.onIntent(NotificationSettingsIntent.Save)
        assertTrue(store.state.value.message != null)
        store.onIntent(NotificationSettingsIntent.MessageShown)
        assertNull(store.state.value.message)
    }
}
