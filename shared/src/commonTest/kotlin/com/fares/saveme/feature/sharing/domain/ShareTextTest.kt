package com.fares.saveme.feature.sharing.domain

import com.fares.saveme.feature.sharing.FAKE_LINK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The text shared with a link names the app, says one sentence and carries the link; no wallet name, no amount (FR-006). */
class ShareTextTest {

    private val template = "%1\$s: I'd like to share a wallet with you. Open this link to join: %2\$s"
    private val arabic = "%1\$s: أود مشاركة محفظة معك. افتح هذا الرابط للانضمام: %2\$s"

    @Test
    fun itHoldsTheAppNameTheSentenceAndTheLink() {
        val text = ShareText.build("Save Me", FAKE_LINK, template)
        assertEquals("Save Me: I'd like to share a wallet with you. Open this link to join: $FAKE_LINK", text)
        assertTrue(text.contains("Save Me") && text.contains(FAKE_LINK))
    }

    @Test
    fun theFunctionTakesNoWalletNameOrAmountSoNoneCanAppear() {
        // The wallet "Home" with a balance of 1234567 exists on the phone; nothing here can read either.
        val text = ShareText.build("Save Me", FAKE_LINK, template)
        assertFalse(text.contains("Home"))
        assertFalse("1234567".any { it in text.substringBefore(FAKE_LINK) }, "no digit of a balance in the sentence")
    }

    @Test
    fun theArabicTemplateIsFilledInTheSameWay() {
        assertEquals("Save Me: أود مشاركة محفظة معك. افتح هذا الرابط للانضمام: $FAKE_LINK", ShareText.build("Save Me", FAKE_LINK, arabic))
    }
}
