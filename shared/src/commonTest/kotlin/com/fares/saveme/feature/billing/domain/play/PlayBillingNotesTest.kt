package com.fares.saveme.feature.billing.domain.play

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayBillingNotesTest {

    @Test
    fun notesAreKeptPerPersonAndResetWhenAnotherPersonIsWrittenFor() {
        val notes = PlayBillingNotes()
        notes.update("a") { it.copy(anotherAccount = true, pendingProducts = setOf("plus_monthly")) }
        assertTrue(notes.notes.value.anotherAccount)
        assertEquals("a", notes.notes.value.userId)
        notes.update("b") { it.copy(heldProducts = setOf("plus_yearly")) }
        assertEquals("b", notes.notes.value.userId)
        assertFalse(notes.notes.value.anotherAccount, "A's notes are gone before B's are written")
        assertTrue(notes.notes.value.pendingProducts.isEmpty())
        assertEquals(setOf("plus_yearly"), notes.notes.value.heldProducts)
    }

    @Test
    fun currentIsEmptyForAPersonWhoseNotesAreNotHeld() {
        val notes = PlayBillingNotes()
        notes.update("a") { it.copy(anotherAccount = true) }
        assertFalse(notes.current("b").anotherAccount)
        assertTrue(notes.current("a").anotherAccount)
    }

    @Test
    fun resetClearsEveryNote() {
        val notes = PlayBillingNotes()
        notes.update("a") { it.copy(anotherAccount = true, refused = setOf("x"), pendingSent = setOf("y"), testProducts = setOf("p"), activated = PlanActivated("Plus")) }
        notes.reset()
        assertEquals(PlayNotes(), notes.notes.value)
    }

    @Test
    fun activatedIsAOneShot() {
        val notes = PlayBillingNotes()
        notes.update("a") { it.copy(activated = PlanActivated("Plus · Monthly")) }
        assertEquals("Plus · Monthly", notes.consumeActivated()?.planName)
        assertNull(notes.consumeActivated())
    }

    @Test
    fun thePlanScreenPresenceIsCounted() {
        val notes = PlayBillingNotes()
        assertFalse(notes.planScreenShowing)
        notes.planScreenOpened()
        notes.planScreenOpened()
        notes.planScreenClosed()
        assertTrue(notes.planScreenShowing)
        notes.planScreenClosed()
        notes.planScreenClosed()
        assertFalse(notes.planScreenShowing)
    }
}
