package com.fares.saveme.feature.sharing.domain

import app.cash.turbine.test
import com.fares.saveme.feature.sharing.FAKE_LINK
import com.fares.saveme.feature.sharing.FAKE_TOKEN
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The memory holders (spec 013 FR-005, FR-010, FR-031): replaced, cleared at sign-out, never printing a secret. */
class SharingHoldersTest {

    @Test
    fun aHeldInvitationIsReplacedByANewLinkAndClearedAtSignOut() = runTest {
        val held = HeldInvitation()
        assertNull(held.held.value)
        held.hold(InvitationLink.Valid(FAKE_TOKEN))
        assertEquals(FAKE_TOKEN, held.held.value?.token)
        held.hold(InvitationLink.Valid("another"))
        assertEquals("another", held.held.value?.token)
        held.isHeld().test {
            assertTrue(awaitItem())
            held.clear()
            assertFalse(awaitItem())
        }
        assertNull(held.held.value)
    }

    @Test
    fun dropForgetsWithoutSuspending() {
        val held = HeldInvitation()
        held.hold(InvitationLink.Valid(FAKE_TOKEN))
        held.drop()
        assertNull(held.held.value)
    }

    @Test
    fun shareLinksOfferGetForgetAndClear() = runTest {
        val links = ShareLinks()
        links.offer("i1", FAKE_LINK)
        links.offer("i2", "$FAKE_LINK-2")
        assertEquals(FAKE_LINK, links.get("i1"))
        links.forget("i1")
        assertNull(links.get("i1"))
        links.observeHeldIds().test {
            assertEquals(setOf("i2"), awaitItem())
            links.clear()
            assertEquals(emptySet(), awaitItem())
        }
        assertNull(links.get("i2"))
    }

    @Test
    fun planDemotionNotesRecordObserveAndClear() = runTest {
        val notes = PlanDemotionNotes()
        notes.observe("w1").test {
            assertFalse(awaitItem())
            notes.record("w1", true)
            assertTrue(awaitItem())
            notes.record("w1", false)
            assertFalse(awaitItem())
            notes.record("w1", true)
            assertTrue(awaitItem())
            notes.clear()
            assertFalse(awaitItem())
        }
    }

    @Test
    fun noHolderPrintsASecretOrALink() {
        val held = HeldInvitation().apply { hold(InvitationLink.Valid(FAKE_TOKEN)) }
        val links = ShareLinks().apply { offer("i1", FAKE_LINK) }
        val printed = listOf(held.toString(), links.toString(), ShareLink(FAKE_LINK).toString(), InvitationLink.Valid(FAKE_TOKEN).toString())
        printed.forEach {
            assertFalse(it.contains(FAKE_TOKEN), it)
            assertFalse(it.contains("invite?token"), it)
        }
    }
}
