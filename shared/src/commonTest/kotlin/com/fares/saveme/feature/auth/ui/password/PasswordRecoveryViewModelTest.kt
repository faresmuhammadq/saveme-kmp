package com.fares.saveme.feature.auth.ui.password

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.FieldError
import com.fares.saveme.feature.auth.domain.model.ForgotOutcome
import com.fares.saveme.feature.auth.domain.model.ResetOutcome
import com.fares.saveme.feature.auth.ui.FieldProblem
import com.fares.saveme.feature.auth.ui.password.forgot.ForgotPasswordEffect
import com.fares.saveme.feature.auth.ui.password.forgot.ForgotPasswordIntent
import com.fares.saveme.feature.auth.ui.password.forgot.ForgotPasswordViewModel
import com.fares.saveme.feature.auth.ui.password.reset.ResetPasswordEffect
import com.fares.saveme.feature.auth.ui.password.reset.ResetPasswordIntent
import com.fares.saveme.feature.auth.ui.password.reset.ResetPasswordViewModel
import com.fares.saveme.feature.auth.ui.password.reset.ResetStatus
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForgotPasswordViewModelTest {

    @Test
    fun prefillsAndGoesToCheckInboxForAnyAddress() = runMviTest {
        val sent = mutableListOf<String>()
        val vm = ForgotPasswordViewModel("sara@example.com") { sent += it; ForgotOutcome.Sent }
        assertEquals("sara@example.com", vm.state.value.email)
        vm.effects.test {
            vm.onIntent(ForgotPasswordIntent.Submit)
            assertEquals(ForgotPasswordEffect.NavigateCheckInbox("sara@example.com"), awaitItem())
        }
        assertEquals(listOf("sara@example.com"), sent)
    }

    @Test
    fun malformedEmailIsNotSentAndDoubleSubmitSendsOnce() = runMviTest {
        val gate = CompletableDeferred<ForgotOutcome>()
        var calls = 0
        val vm = ForgotPasswordViewModel(null) { calls++; gate.await() }
        vm.onIntent(ForgotPasswordIntent.EmailChanged("nope"))
        vm.onIntent(ForgotPasswordIntent.Submit)
        assertEquals(FieldProblem.Client(FieldError.EmailInvalid), vm.state.value.emailError)
        vm.onIntent(ForgotPasswordIntent.EmailChanged("a@example.com"))
        vm.onIntent(ForgotPasswordIntent.Submit)
        vm.onIntent(ForgotPasswordIntent.Submit)
        assertEquals(1, calls)
        gate.complete(ForgotOutcome.Offline)
    }
}

class ResetPasswordViewModelTest {

    private val calls = mutableListOf<Pair<String, String>>()
    private fun vm(outcome: ResetOutcome) = ResetPasswordViewModel("secret-token") { t, p -> calls += t to p; outcome }

    @Test
    fun enforcesLengthBeforeSending() = runMviTest {
        val vm = vm(ResetOutcome.Done)
        vm.onIntent(ResetPasswordIntent.PasswordChanged("short"))
        vm.onIntent(ResetPasswordIntent.Submit)
        assertEquals(FieldProblem.Client(FieldError.PasswordTooShort), vm.state.value.passwordError)
        vm.onIntent(ResetPasswordIntent.PasswordChanged("p".repeat(129)))
        vm.onIntent(ResetPasswordIntent.Submit)
        assertEquals(FieldProblem.Client(FieldError.PasswordTooLong), vm.state.value.passwordError)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun doneThenContinueGoesToSignIn() = runMviTest {
        val vm = vm(ResetOutcome.Done)
        vm.onIntent(ResetPasswordIntent.PasswordChanged("new-password"))
        vm.onIntent(ResetPasswordIntent.Submit)
        assertEquals(listOf("secret-token" to "new-password"), calls)
        assertEquals(ResetStatus.Done, vm.state.value.status)
        vm.onIntent(ResetPasswordIntent.Submit) // done: nothing more is sent
        assertEquals(1, calls.size)
        vm.effects.test {
            vm.onIntent(ResetPasswordIntent.ContinueClicked)
            assertEquals(ResetPasswordEffect.NavigateSignIn, awaitItem())
        }
    }

    @Test
    fun invalidLinkOffersANewOne() = runMviTest {
        val vm = vm(ResetOutcome.LinkInvalid)
        vm.onIntent(ResetPasswordIntent.PasswordChanged("new-password"))
        vm.onIntent(ResetPasswordIntent.Submit)
        assertEquals(ResetStatus.LinkInvalid, vm.state.value.status)
        vm.effects.test {
            vm.onIntent(ResetPasswordIntent.RequestNewLinkClicked)
            assertEquals(ResetPasswordEffect.NavigateForgotPassword, awaitItem())
        }
    }

    @Test
    fun neverPrintsTheTokenOrPassword() = runMviTest {
        val vm = vm(ResetOutcome.Done)
        vm.onIntent(ResetPasswordIntent.PasswordChanged("typed-secret"))
        assertFalse("secret-token" in vm.toString())
        assertFalse("typed-secret" in vm.state.value.toString())
    }
}
