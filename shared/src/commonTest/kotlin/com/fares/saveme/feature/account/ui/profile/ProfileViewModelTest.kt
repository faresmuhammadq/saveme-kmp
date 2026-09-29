package com.fares.saveme.feature.account.ui.profile

import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.model.Profile
import com.fares.saveme.feature.account.domain.model.ProfileDraft
import com.fares.saveme.feature.account.domain.model.ProfileField
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.auth.domain.FieldError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val server = Profile("Sara", "en", "Africa/Cairo", "EGP", false, 1, null, 3)
private fun view(pending: ProfileDraft? = null, conflict: Profile? = null, locked: Boolean = false) =
    ProfileView(server.copy(currencyLocked = locked), pending, conflict, emptyMap(), null)

class ProfileReducerTest {
    @Test
    fun aStoredCopyReplacesTheFormOnlyWhenNotEditing() {
        val loaded = reduce(ProfileState(), ProfileChange.Stored(view()))
        assertEquals(server.toDraft(), loaded.form)
        val editing = reduce(loaded, ProfileChange.Edit(server.toDraft().copy(displayName = "S")))
        val pulled = reduce(editing, ProfileChange.Stored(view().copy(server = server.copy(displayName = "Web", version = 4))))
        assertEquals("S", pulled.form!!.displayName)
    }

    @Test
    fun conflictListsOnlyDifferingFields() {
        val v = view(pending = server.toDraft().copy(displayName = "Mine"), conflict = server.copy(displayName = "Theirs", version = 6))
        assertEquals(listOf(Triple(ProfileField.DisplayName, "Mine", "Theirs")), v.conflictingFields())
    }
}

class ProfileViewModelTest {
    private val saved = mutableListOf<ProfileDraft>()

    private fun vm(stored: ProfileView?, calls: MutableList<String> = mutableListOf()) = ProfileViewModel(
        account = flowOf("sara@example.com" to true),
        profile = MutableStateFlow(stored),
        refresh = { calls += "refresh"; ActionOutcome.Done },
        saveLocally = { saved += it },
        keepMine = { calls += "keep" },
        useTheirs = { calls += "theirs" },
        discard = { calls += "discard" },
    )

    @Test
    fun saveIsDisabledUntilSomethingChanges() = runMviTest {
        val vm = vm(view())
        vm.onIntent(ProfileIntent.Save)
        assertEquals(emptyList(), saved)
        assertEquals(false, vm.state.value.dirty)
        vm.onIntent(ProfileIntent.DisplayNameChanged("Sara M"))
        assertEquals(true, vm.state.value.dirty)
        vm.onIntent(ProfileIntent.Save)
        assertEquals("Sara M", saved.single().displayName)
    }

    @Test
    fun displayNameRules() = runMviTest {
        val vm = vm(view())
        vm.onIntent(ProfileIntent.DisplayNameChanged("x".repeat(101)))
        vm.onIntent(ProfileIntent.Save)
        assertEquals(FieldError.DisplayNameTooLong, vm.state.value.displayNameError)
        assertEquals(emptyList(), saved)
        vm.onIntent(ProfileIntent.DisplayNameChanged("   "))
        vm.onIntent(ProfileIntent.Save)
        assertNull(saved.single().displayName, "empty clears the name")
    }

    @Test
    fun budgetDayStaysWithinOneToTwentyEight() = runMviTest {
        val vm = vm(view())
        vm.onIntent(ProfileIntent.OpenPicker(ProfilePicker.BudgetDay))
        vm.onIntent(ProfileIntent.Picked("31"))
        assertEquals(28, vm.state.value.form!!.budgetStartDay)
        assertNull(vm.state.value.picker)
    }

    @Test
    fun lockedCurrencyCannotBePickedOrSent() = runMviTest {
        val vm = vm(view(locked = true))
        vm.onIntent(ProfileIntent.OpenPicker(ProfilePicker.Currency))
        assertNull(vm.state.value.picker)
        vm.onIntent(ProfileIntent.LanguageChanged("ar"))
        vm.onIntent(ProfileIntent.Save)
        assertEquals("EGP", saved.single().defaultCurrency)
        assertEquals("ar", saved.single().locale)
    }

    @Test
    fun pendingShowsWaitingAndConflictChoicesCallOnce() = runMviTest {
        val calls = mutableListOf<String>()
        val pendingOnly = vm(view(pending = server.toDraft().copy(displayName = "Mine")), calls)
        assertEquals(true, pendingOnly.state.value.waitingToSync)

        val conflicted = vm(view(pending = server.toDraft().copy(displayName = "Mine"), conflict = server.copy(displayName = "Theirs")), calls)
        assertEquals(false, conflicted.state.value.waitingToSync)
        conflicted.onIntent(ProfileIntent.KeepMine)
        conflicted.onIntent(ProfileIntent.UseTheirs)
        assertEquals(listOf("refresh", "refresh", "keep", "theirs"), calls)
    }
}
