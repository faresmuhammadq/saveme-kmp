package com.fares.saveme.feature.auth.ui.home

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.AccountSnapshot
import com.fares.saveme.feature.auth.domain.model.AccountState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val account = AccountSnapshot("u1", "sara@example.com", true, AccountState.Active, null, "free", emptyMap())

/** Home's "You have an invitation to answer" card (spec 013 FR-011). */
class HomeInvitationTest {

    @Test
    fun theCardShowsWhileAnInvitationIsHeldAndHidesWhenItIsNot() = runMviTest {
        val held = MutableStateFlow(false)
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)), invitationHeld = held)
        assertFalse(vm.state.value.invitationHeld)
        held.value = true
        assertTrue(vm.state.value.invitationHeld)
        held.value = false
        assertFalse(vm.state.value.invitationHeld)
    }

    @Test
    fun theCardOpensTheInvitation() = runMviTest {
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)))
        vm.effects.test {
            vm.onIntent(HomeIntent.InvitationClicked)
            assertEquals(HomeEffect.OpenInvitation, awaitItem())
        }
    }

    @Test
    fun aDeclinedInvitationIsSaidOnceOnHome() = runMviTest {
        val declined = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(account)), invitationDeclined = declined)
        vm.effects.test {
            declined.tryEmit(Unit)
            assertEquals(HomeEffect.ShowMessage(HomeMessage.InvitationDeclined), awaitItem())
        }
    }
}
