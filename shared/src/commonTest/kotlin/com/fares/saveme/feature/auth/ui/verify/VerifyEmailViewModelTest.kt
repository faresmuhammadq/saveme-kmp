package com.fares.saveme.feature.auth.ui.verify

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.model.ResendOutcome
import com.fares.saveme.feature.auth.domain.model.VerifyOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VerifyEmailViewModelTest {

    private var verifyCalls = 0
    private var refreshCalls = 0

    private fun vm(signedIn: Boolean, outcome: () -> VerifyOutcome) = VerifyEmailViewModel(
        token = "secret-token",
        isSignedIn = { signedIn },
        verify = { verifyCalls++; outcome() },
        resend = { ResendOutcome.Sent },
        refreshAccount = { refreshCalls++ },
    )

    @Test
    fun verifiesOnceAndReReadsTheAccountWhenSignedIn() = runMviTest {
        val vm = vm(signedIn = true) { VerifyOutcome.Verified }
        vm.onIntent(VerifyEmailIntent.Started)
        vm.onIntent(VerifyEmailIntent.Started)
        assertEquals(1, verifyCalls)
        assertEquals(VerifyStatus.Verified, vm.state.value.status)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun signedOutVerificationDoesNotReadAnyAccount() = runMviTest {
        val vm = vm(signedIn = false) { VerifyOutcome.Verified }
        vm.onIntent(VerifyEmailIntent.Started)
        assertEquals(0, refreshCalls)
        vm.effects.test {
            vm.onIntent(VerifyEmailIntent.ContinueClicked)
            assertEquals(VerifyEmailEffect.NavigateSignIn, awaitItem())
        }
    }

    @Test
    fun invalidLinkOffersResendOnlyWhenSignedIn() = runMviTest {
        val signedIn = vm(signedIn = true) { VerifyOutcome.LinkInvalid }.apply { onIntent(VerifyEmailIntent.Started) }
        assertEquals(VerifyStatus.LinkInvalid, signedIn.state.value.status)
        assertEquals(true, signedIn.state.value.signedIn)
        assertEquals(0, refreshCalls)

        val signedOut = vm(signedIn = false) { VerifyOutcome.LinkInvalid }.apply { onIntent(VerifyEmailIntent.Started) }
        assertEquals(false, signedOut.state.value.signedIn)
    }

    @Test
    fun offlineRetrySendsAgainOnce() = runMviTest {
        var answer: VerifyOutcome = VerifyOutcome.Offline
        val vm = vm(signedIn = false) { answer }
        vm.onIntent(VerifyEmailIntent.Started)
        assertEquals(VerifyStatus.Offline, vm.state.value.status)
        answer = VerifyOutcome.Verified
        vm.onIntent(VerifyEmailIntent.Retry)
        assertEquals(2, verifyCalls)
        assertEquals(VerifyStatus.Verified, vm.state.value.status)
        vm.onIntent(VerifyEmailIntent.Retry) // not offline any more: nothing sent
        assertEquals(2, verifyCalls)
    }

    @Test
    fun neverPrintsTheToken() = runMviTest {
        val vm = vm(signedIn = true) { VerifyOutcome.Verified }
        assertFalse("secret-token" in vm.toString())
        assertFalse("secret-token" in vm.state.value.toString())
    }
}
