package com.fares.saveme.feature.notifications.ui.invitation

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InvitationNoticeTest {
    @Test
    fun reducerKeepsTheAddress() {
        assertEquals("a@b.c", reduce(InvitationNoticeState(), InvitationNoticeChange.Address("a@b.c")).address)
    }

    @Test
    fun showsTheSignedInAddressAndOkCloses() = runMviTest {
        var reads = 0
        val store = InvitationNoticeViewModel { reads++; "sara@example.com" }
        assertEquals("sara@example.com", store.state.value.address)
        store.effects.test {
            store.onIntent(InvitationNoticeIntent.Ok)
            assertEquals(InvitationNoticeEffect.Close, awaitItem())
        }
        assertEquals(1, reads, "no call is made: the address is read once from the session")
    }
}
