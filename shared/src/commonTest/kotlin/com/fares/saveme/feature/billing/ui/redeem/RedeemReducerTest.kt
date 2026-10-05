package com.fares.saveme.feature.billing.ui.redeem

import com.fares.saveme.feature.billing.RAW_CODE
import com.fares.saveme.feature.billing.domain.AttemptKey
import com.fares.saveme.feature.billing.domain.RedeemConfirmation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RedeemReducerTest {

    private val key = AttemptKey("0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70")
    private val confirmation = RedeemConfirmation("Plus", null, true, false, "Plus", null, null)
    private fun typed(code: String = RAW_CODE) = reduce(RedeemState(), RedeemChange.Code(code))

    @Test
    fun codeChangedStoresItAndClearsAttemptAndMessage() {
        val failed = reduce(reduce(typed(), RedeemChange.SendStarted(key)), RedeemChange.Refused(RedeemMessage.Offline))
        assertEquals(key, failed.attempt)
        val edited = reduce(failed, RedeemChange.Code("7KQ2"))
        assertEquals("7KQ2", edited.code)
        assertNull(edited.attempt)
        assertNull(edited.message)
    }

    @Test
    fun theSameCodeAgainChangesNothing() {
        val sending = reduce(typed(), RedeemChange.SendStarted(key))
        val back = reduce(sending, RedeemChange.Refused(RedeemMessage.Offline))
        assertEquals(back, reduce(back, RedeemChange.Code(RAW_CODE)))
    }

    @Test
    fun canRedeemOnlyAtSixteenCharactersNotSendingAndNoWait() {
        assertFalse(typed("7KQ2MX4D9TRBH3W").canRedeem)
        assertTrue(typed().canRedeem)
        assertFalse(reduce(typed(), RedeemChange.SendStarted(key)).canRedeem)
        assertFalse(reduce(typed(), RedeemChange.WaitStarted(Instant.parse("2026-10-06T10:00:00Z"))).canRedeem)
        assertTrue(reduce(reduce(typed(), RedeemChange.WaitStarted(Instant.parse("2026-10-06T10:00:00Z"))), RedeemChange.WaitOver).canRedeem)
    }

    @Test
    fun sendStartedIsSendingWithTheAttempt() {
        val s = reduce(typed(), RedeemChange.SendStarted(key))
        assertEquals(RedeemPhase.Sending, s.phase)
        assertEquals(key, s.attempt)
    }

    @Test
    fun succeededIsDoneAndForgetsTheCodeAndAttempt() {
        val s = reduce(reduce(typed(), RedeemChange.SendStarted(key)), RedeemChange.Succeeded(confirmation))
        assertEquals(RedeemPhase.Done(confirmation), s.phase)
        assertEquals("", s.code)
        assertNull(s.attempt)
    }

    @Test
    fun refusalsKeepTheAttemptExceptKeyReused() {
        val sending = reduce(typed(), RedeemChange.SendStarted(key))
        assertEquals(key, reduce(sending, RedeemChange.Refused(RedeemMessage.Used)).attempt)
        assertNull(reduce(sending, RedeemChange.Refused(RedeemMessage.Failed("r"), keepAttempt = false)).attempt)
        assertEquals(RedeemPhase.Editing, reduce(sending, RedeemChange.Refused(RedeemMessage.Offline)).phase)
    }

    @Test
    fun otherAddressCarriesOnlyTheSignedInAddress() {
        val s = reduce(reduce(typed(), RedeemChange.Account(true, "sara@example.com")), RedeemChange.Refused(RedeemMessage.OtherAddress(null)))
        assertEquals(RedeemMessage.OtherAddress("sara@example.com"), s.message)
    }

    @Test
    fun aNotVerifiedAnswerCorrectsTheFlag() {
        val s = reduce(typed(), RedeemChange.Refused(RedeemMessage.NotVerified))
        assertFalse(s.verified)
        assertFalse(s.showsMessage, "the verify note says it")
    }

    @Test
    fun signOutClearsCodeAndAttempt() {
        val s = reduce(reduce(typed(), RedeemChange.SendStarted(key)), RedeemChange.SessionSignedOut)
        assertEquals("", s.code)
        assertNull(s.attempt)
        assertEquals(RedeemPhase.Editing, s.phase)
    }

    @Test
    fun editingWhileSendingIsIgnored() {
        val s = reduce(typed(), RedeemChange.SendStarted(key))
        assertEquals(RAW_CODE, reduce(s, RedeemChange.Code("AAAA")).code)
    }

    @Test
    fun stateAndIntentAndAttemptNeverPrintTheCodeOrKey() {
        val s = reduce(typed(), RedeemChange.SendStarted(key))
        val printed = listOf(s.toString(), RedeemIntent.CodeChanged(RAW_CODE).toString(), RedeemChange.Code(RAW_CODE).toString(), key.toString())
        printed.forEach {
            assertFalse(it.contains("7KQ2"), it)
            assertFalse(it.contains(key.value), it)
        }
        assertIs<RedeemPhase.Sending>(s.phase)
    }
}
