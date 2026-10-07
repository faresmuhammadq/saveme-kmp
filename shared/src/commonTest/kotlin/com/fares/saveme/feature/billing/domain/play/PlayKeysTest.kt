package com.fares.saveme.feature.billing.domain.play

import com.fares.saveme.feature.billing.data.play.PlayPurchaseState
import com.fares.saveme.feature.billing.data.play.PurchaseToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlayKeysTest {

    private val token = PurchaseToken("opaque-token-from-play-billing.AO-J1OxPLAyTokEnExAmPlE0123456789")

    @Test
    fun theSameInputsGiveTheSameSixtyEightCharacterKey() {
        val a = PlayKeys.verify("user-1", token, PlayPurchaseState.Purchased)
        assertEquals(a, PlayKeys.verify("user-1", token, PlayPurchaseState.Purchased))
        assertEquals(68, a.length)
        assertTrue(a.startsWith("gp1:"))
        assertTrue(Regex("^gp1:[0-9a-f]{64}$").matches(a), a)
    }

    @Test
    fun theKeyUsesOnlyTheConventionsCharset() {
        val key = PlayKeys.verify("user-1", token, PlayPurchaseState.Pending, recheckHour = 490_000)
        assertTrue(Regex("^[A-Za-z0-9._:-]{16,255}$").matches(key))
    }

    @Test
    fun theKeyDiffersByUserByStateAndByRecheckHour() {
        val base = PlayKeys.verify("user-1", token, PlayPurchaseState.Purchased)
        assertNotEquals(base, PlayKeys.verify("user-2", token, PlayPurchaseState.Purchased))
        assertNotEquals(base, PlayKeys.verify("user-1", token, PlayPurchaseState.Pending))
        val hour = PlayKeys.verify("user-1", token, PlayPurchaseState.Purchased, recheckHour = 1000)
        assertNotEquals(base, hour)
        assertNotEquals(hour, PlayKeys.verify("user-1", token, PlayPurchaseState.Purchased, recheckHour = 1001))
        assertNotEquals(base, PlayKeys.verify("user-1", PurchaseToken(token.value + "x"), PlayPurchaseState.Purchased))
    }

    @Test
    fun theKeyNeverContainsTheToken() {
        val key = PlayKeys.verify("user-1", token, PlayPurchaseState.Purchased)
        assertFalse(key.contains(token.value))
        assertFalse(key.contains("AO-J1Ox"))
    }

    @Test
    fun attemptKeysAreRandomAndRedacted() {
        val a = PlayKeys.attempt()
        val b = PlayKeys.attempt()
        assertNotEquals(a.value, b.value)
        assertEquals("AttemptKey(***)", a.toString())
        assertTrue(Regex("^[A-Za-z0-9._:-]{16,255}$").matches(a.value))
    }

    @Test
    fun theDigestIsShortStableAndHidesTheToken() {
        val d = PlayKeys.digest(token)
        assertEquals(d, PlayKeys.digest(token))
        assertEquals(32, d.length)
        assertFalse(d.contains("opaque"))
    }

    @Test
    fun tokenAndReferenceHoldersPrintRedacted() {
        assertEquals("PurchaseToken(***)", token.toString())
        assertEquals("AccountReference(***)", com.fares.saveme.feature.billing.data.play.AccountReference("abc").toString())
    }
}
