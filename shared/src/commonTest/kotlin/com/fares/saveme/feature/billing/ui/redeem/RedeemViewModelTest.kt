package com.fares.saveme.feature.billing.ui.redeem

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.billing.GROUPED_CODE
import com.fares.saveme.feature.billing.RAW_CODE
import com.fares.saveme.feature.billing.domain.AttemptKey
import com.fares.saveme.feature.billing.domain.RedeemConfirmation
import com.fares.saveme.feature.billing.domain.RedeemOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RedeemViewModelTest {

    private val confirmation = RedeemConfirmation("Plus", null, true, false, "Plus", null, null)
    private val calls = mutableListOf<Pair<String, AttemptKey>>()
    private var outcome: RedeemOutcome = RedeemOutcome.Redeemed(confirmation)
    private var gate: CompletableDeferred<Unit>? = null
    private var verified = true
    private val signedIn = MutableStateFlow(true)
    private var keyCounter = 0
    private var clock = Instant.parse("2026-10-06T10:00:00Z")

    private fun vm() = RedeemViewModel(
        redeem = { code, attempt -> calls += code to attempt; gate?.await(); outcome },
        isEmailVerified = { verified },
        signedInEmail = { "sara@example.com" },
        signedIn = signedIn,
        timeZoneId = MutableStateFlow("Africa/Cairo"),
        now = { clock },
        newAttemptKey = { AttemptKey("key-${++keyCounter}") },
    )

    private fun RedeemViewModel.type(text: String = RAW_CODE) = onIntent(RedeemIntent.CodeChanged(text))

    @Test
    fun redeemCreatesOneAttemptKeyAndPassesIt() = runMviTest {
        val vm = vm()
        vm.type()
        vm.onIntent(RedeemIntent.Redeem)
        assertEquals(listOf(RAW_CODE to AttemptKey("key-1")), calls)
        assertIs<RedeemPhase.Done>(vm.state.value.phase)
    }

    @Test
    fun aSecondRedeemWhileSendingIsIgnored() = runMviTest {
        gate = CompletableDeferred()
        val vm = vm()
        vm.type()
        vm.onIntent(RedeemIntent.Redeem)
        vm.onIntent(RedeemIntent.Redeem)
        vm.onIntent(RedeemIntent.TryAgain)
        assertEquals(1, calls.size)
        gate!!.complete(Unit)
    }

    @Test
    fun anIncompleteCodeSendsNothing() = runMviTest {
        val vm = vm()
        vm.type("7KQ2")
        vm.onIntent(RedeemIntent.Redeem)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun unverifiedAsksToVerifyAndSendsNothing() = runMviTest {
        verified = false
        val vm = vm()
        vm.type()
        vm.effects.test {
            vm.onIntent(RedeemIntent.Redeem)
            assertEquals(RedeemEffect.RequireVerification, awaitItem())
        }
        assertTrue(calls.isEmpty())
        assertFalse(vm.state.value.verified)
    }

    @Test
    fun doneAndBackNavigateBack() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(RedeemIntent.Done)
            vm.onIntent(RedeemIntent.Back)
            assertEquals(RedeemEffect.NavigateBack, awaitItem())
            assertEquals(RedeemEffect.NavigateBack, awaitItem())
        }
    }

    @Test
    fun pastingAWholeEmailLineYieldsTheCode() = runMviTest {
        val vm = vm()
        vm.type("Your code is $GROUPED_CODE - it works for 30 days")
        assertEquals(RAW_CODE, vm.state.value.code)
    }

    @Test
    fun offlineKeepsTheAttemptAndTryAgainReusesIt() = runMviTest {
        outcome = RedeemOutcome.Offline
        val vm = vm()
        vm.type()
        vm.onIntent(RedeemIntent.Redeem)
        assertEquals(RedeemMessage.Offline, vm.state.value.message)
        vm.onIntent(RedeemIntent.TryAgain)
        assertEquals(2, calls.size)
        assertEquals(calls[0].second, calls[1].second)
    }

    @Test
    fun inProgressKeepsTheAttemptToo() = runMviTest {
        outcome = RedeemOutcome.InProgress
        val vm = vm()
        vm.type()
        vm.onIntent(RedeemIntent.Redeem)
        assertEquals(RedeemMessage.InProgress, vm.state.value.message)
        vm.onIntent(RedeemIntent.TryAgain)
        assertEquals(calls[0].second, calls[1].second)
    }

    @Test
    fun editingTheCodeClearsTheMessageAndTheNextRedeemUsesANewKey() = runMviTest {
        outcome = RedeemOutcome.NotValid
        val vm = vm()
        vm.type()
        vm.onIntent(RedeemIntent.Redeem)
        assertEquals(RedeemMessage.NotValid, vm.state.value.message)
        vm.type("7KQ2MX4D9TRBH3WF")
        assertNull(vm.state.value.message)
        vm.onIntent(RedeemIntent.Redeem)
        assertNotEquals(calls[0].second, calls[1].second)
    }

    @Test
    fun keyReusedDropsTheKeyAndShowsTheRequestId() = runMviTest {
        outcome = RedeemOutcome.KeyReused("req-9")
        val vm = vm()
        vm.type()
        vm.onIntent(RedeemIntent.Redeem)
        assertEquals(RedeemMessage.Failed("req-9"), vm.state.value.message)
        assertNull(vm.state.value.attempt)
        vm.onIntent(RedeemIntent.Redeem)
        assertNotEquals(calls[0].second, calls[1].second)
    }

    @Test
    fun eachRefusalSetsItsMessage() = runMviTest {
        val table = mapOf(
            RedeemOutcome.Used to RedeemMessage.Used,
            RedeemOutcome.Expired to RedeemMessage.Expired,
            RedeemOutcome.OtherAddress to RedeemMessage.OtherAddress("sara@example.com"),
            RedeemOutcome.NotVerified to RedeemMessage.NotVerified,
            RedeemOutcome.InvalidFormat to RedeemMessage.InvalidFormat,
            RedeemOutcome.Failed("r1") to RedeemMessage.Failed("r1"),
        )
        table.forEach { (result, message) ->
            outcome = result
            val vm = vm()
            vm.type()
            vm.onIntent(RedeemIntent.Redeem)
            assertEquals(message, vm.state.value.message, result.toString())
        }
    }

    @Test
    fun rateLimitedDisablesRedeemUntilTheWaitPassesWithMinutesRoundedUp() = runMviTest {
        outcome = RedeemOutcome.RateLimited(61)
        val vm = vm()
        vm.type()
        vm.onIntent(RedeemIntent.Redeem)
        assertEquals(RedeemMessage.RateLimited(2), vm.state.value.message)
        assertFalse(vm.state.value.canRedeem)
        assertEquals(Instant.parse("2026-10-06T10:01:01Z"), vm.state.value.waitUntil)

        advanceTimeBy(60_000)
        assertFalse(vm.state.value.canRedeem)
        advanceTimeBy(1_001)
        assertTrue(vm.state.value.canRedeem)
        assertEquals(1, calls.size)
    }

    @Test
    fun underAMinuteSaysAMinuteAndNoWaitTimeStaysAvailable() = runMviTest {
        outcome = RedeemOutcome.RateLimited(30)
        val a = vm()
        a.type()
        a.onIntent(RedeemIntent.Redeem)
        assertEquals(RedeemMessage.RateLimited(1), a.state.value.message)

        outcome = RedeemOutcome.RateLimited(null)
        val b = vm()
        b.type()
        b.onIntent(RedeemIntent.Redeem)
        assertEquals(RedeemMessage.RateLimited(null), b.state.value.message)
        assertTrue(b.state.value.canRedeem)
        advanceTimeBy(31_000) // lets a's wait timer finish before the test ends
    }

    @Test
    fun theTimeZoneIsTheProfilesAndSignOutClearsTheCode() = runMviTest {
        val vm = vm()
        vm.type()
        assertEquals("Africa/Cairo", vm.state.value.timeZoneId)
        signedIn.value = false
        assertEquals("", vm.state.value.code)
        assertNull(vm.state.value.attempt)
    }

    @Test
    fun signedOutOutcomeClearsTheCode() = runMviTest {
        outcome = RedeemOutcome.SignedOut
        val vm = vm()
        vm.type()
        vm.onIntent(RedeemIntent.Redeem)
        assertEquals("", vm.state.value.code)
    }

    @Test
    fun theSuccessConfirmationComesFromTheService() = runMviTest {
        val vm = vm()
        vm.type()
        vm.onIntent(RedeemIntent.Redeem)
        assertEquals(RedeemPhase.Done(confirmation), vm.state.value.phase)
        assertTrue(currentTime >= 0)
    }
}
