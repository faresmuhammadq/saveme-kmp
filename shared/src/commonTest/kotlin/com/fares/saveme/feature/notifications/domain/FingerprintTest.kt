package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.feature.notifications.TOKEN_A
import com.fares.saveme.feature.notifications.TOKEN_B
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class FingerprintTest {

    /** The published FNV-1a 64-bit test vectors. */
    @Test
    fun knownVectors() {
        assertEquals("cbf29ce484222325", fingerprint(""))
        assertEquals("af63dc4c8601ec8c", fingerprint("a"))
        assertEquals("85944171f73967e8", fingerprint("foobar"))
    }

    @Test
    fun sixteenHexDigitsDifferentInputsDifferAndTheInputIsNotInTheOutput() {
        val a = fingerprint(TOKEN_A)
        assertEquals(16, a.length)
        assertEquals(a, fingerprint(TOKEN_A))
        assertNotEquals(a, fingerprint(TOKEN_B))
        assertFalse(a.contains("fake") || a.contains(TOKEN_A))
        assertEquals(fingerprint("é"), fingerprint("é"))
    }
}
