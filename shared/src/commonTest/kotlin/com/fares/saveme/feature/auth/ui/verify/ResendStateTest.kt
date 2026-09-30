package com.fares.saveme.feature.auth.ui.verify

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.model.ResendOutcome
import com.fares.saveme.feature.auth.ui.home.HomeIntent
import com.fares.saveme.feature.auth.ui.home.HomeViewModel
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.AccountSnapshot
import com.fares.saveme.feature.auth.domain.model.AccountState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResendStateTest {

    @Test
    fun sentStartsTheCooldownAndTicksDownToZero() {
        var state = reduceResend(ResendState(), ResendChange.Started)
        assertFalse(state.canResend)
        state = reduceResend(state, ResendChange.Finished(ResendOutcome.Sent))
        assertEquals(ResendState.COOLDOWN_SECONDS, state.cooldownSeconds)
        assertEquals(ResendMessage.Sent, state.message)
        repeat(ResendState.COOLDOWN_SECONDS + 5) { state = reduceResend(state, ResendChange.Tick) }
        assertEquals(0, state.cooldownSeconds)
        assertTrue(state.canResend)
    }

    @Test
    fun failuresDoNotStartTheCooldown() {
        val limited = reduceResend(ResendState(sending = true), ResendChange.Finished(ResendOutcome.RateLimited(1800)))
        assertEquals(ResendMessage.RateLimited(30), limited.message)
        assertTrue(limited.canResend)
        val failed = reduceResend(ResendState(sending = true), ResendChange.Finished(ResendOutcome.Error(ApiErrorCode.INTERNAL_ERROR, null)))
        assertEquals(ResendMessage.Failed, failed.message)
    }

    private val unverified = AccountSnapshot("u1", "sara@example.com", false, AccountState.Active, null, "Free", emptyMap())

    @Test
    fun homeResendIsThrottledForThirtySeconds() = runMviTest {
        var calls = 0
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(unverified)), resendVerification = { calls++; ResendOutcome.Sent })
        assertFalse(vm.state.value.emailVerified)
        vm.onIntent(HomeIntent.ResendClicked)
        vm.onIntent(HomeIntent.ResendClicked) // during the cooldown: nothing sent
        assertEquals(1, calls)
        assertEquals(ResendState.COOLDOWN_SECONDS, vm.state.value.resend.cooldownSeconds)
        advanceTimeBy(ResendState.COOLDOWN_SECONDS * 1_000L + 1)
        assertTrue(vm.state.value.resend.canResend)
        vm.onIntent(HomeIntent.ResendClicked)
        assertEquals(2, calls)
        // Let the second cooldown finish so no timer outlives the test's Main dispatcher.
        advanceTimeBy(ResendState.COOLDOWN_SECONDS * 1_000L + 1)
    }

    @Test
    fun homeShowsTheRateLimitWait() = runMviTest {
        val vm = HomeViewModel(MutableStateFlow(AuthState.SignedIn(unverified)), resendVerification = { ResendOutcome.RateLimited(600) })
        vm.onIntent(HomeIntent.ResendClicked)
        assertEquals(ResendMessage.RateLimited(10), vm.state.value.resend.message)
    }
}
