package com.fares.saveme.feature.billing.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedeemCodeTest {

    @Test
    fun lowerCaseIsUpperCased() = assertEquals("7KQ2MX", RedeemCode.normalise("7kq2mx"))

    @Test
    fun oBecomesZeroAndIAndLBecomeOne() {
        assertEquals("0011", RedeemCode.normalise("Oo" + "Il"))
        assertEquals("0011", RedeemCode.normalise("oOiL"))
    }

    @Test
    fun uAndPunctuationAreDropped() = assertEquals("7KQ2", RedeemCode.normalise("7K#Q.U2!"))

    @Test
    fun spacesHyphensAndLineBreaksAreDropped() = assertEquals("7KQ2MX4D", RedeemCode.normalise(" 7KQ2-MX4D\n\t"))

    @Test
    fun arabicIndicDigitsAreDroppedNotConverted() = assertEquals("7K", RedeemCode.normalise("7٢K٣"))

    @Test
    fun nonAsciiLettersAreDropped() = assertEquals("AB", RedeemCode.normalise("AéBا"))

    @Test
    fun isCutAtSixteenCharacters() {
        val long = "7KQ2MX4D9TRBH3WE" + "ABCD"
        assertEquals("7KQ2MX4D9TRBH3WE", RedeemCode.normalise(long))
        assertEquals(16, RedeemCode.normalise(long).length)
    }

    @Test
    fun fromPasteTakesTheGroupedCodeOutOfAWholeEmailLine() {
        assertEquals("7KQ2MX4D9TRBH3WE", RedeemCode.fromPaste("Your code is 7KQ2-MX4D-9TRB-H3WE. It expires in 30 days."))
        assertEquals("7KQ2MX4D9TRBH3WE", RedeemCode.fromPaste("code: 7kq2 mx4d 9trb h3we please"))
    }

    @Test
    fun fromPasteSkipsALookAlikeThatIsNotACompleteCode() {
        // "Redeem" is not 4+4+4+4; the real code is later in the line.
        assertEquals("7KQ2MX4D9TRBH3WE", RedeemCode.fromPaste("Code 1234-5678 then 7KQ2-MX4D-9TRB-H3WE"))
    }

    @Test
    fun fromPasteFallsBackToNormaliseForPlainText() {
        assertEquals("7KQ2MX4D9TRBH3WE", RedeemCode.fromPaste("7kq2mx4d9trbh3we"))
        assertEquals("7KQ2", RedeemCode.fromPaste("7kq2"))
    }

    @Test
    fun groupedAndIsComplete() {
        assertEquals("7KQ2-MX4D-9TRB-H3WE", RedeemCode.grouped("7KQ2MX4D9TRBH3WE"))
        assertEquals("7KQ2-MX", RedeemCode.grouped("7KQ2MX"))
        assertTrue(RedeemCode.isComplete("7KQ2MX4D9TRBH3WE"))
        assertFalse(RedeemCode.isComplete("7KQ2MX4D9TRBH3W"))
    }
}
