package com.fares.saveme.feature.auth.ui.register

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.FieldError
import com.fares.saveme.feature.auth.domain.model.AuthField
import com.fares.saveme.feature.auth.domain.model.RegisterOutcome
import com.fares.saveme.feature.auth.ui.FieldProblem
import com.fares.saveme.feature.auth.ui.FormBanner
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegisterViewModelTest {

    private val calls = mutableListOf<Triple<String, String, String?>>()

    private fun vm(respond: suspend () -> RegisterOutcome) =
        RegisterViewModel(prefillEmail = null, register = { e, p, n -> calls += Triple(e, p, n); respond() })

    private fun RegisterViewModel.fill(email: String = "sara@example.com", password: String = "long-password", name: String = "") {
        onIntent(RegisterIntent.EmailChanged(email))
        onIntent(RegisterIntent.PasswordChanged(password))
        onIntent(RegisterIntent.DisplayNameChanged(name))
    }

    @Test
    fun clientRulesBlockTheRequest() = runMviTest {
        val cases = listOf(
            Triple("not-an-email", "long-password", "") to FieldError.EmailInvalid,
            Triple("a".repeat(250) + "@x.io", "long-password", "") to FieldError.EmailTooLong,
            Triple("sara@example.com", "short", "") to FieldError.PasswordTooShort,
            Triple("sara@example.com", "p".repeat(129), "") to FieldError.PasswordTooLong,
            Triple("sara@example.com", "SARA@example.com", "") to FieldError.PasswordEqualsEmail,
            Triple("sara@example.com", "long-password", "n".repeat(101)) to FieldError.DisplayNameTooLong,
        )
        cases.forEach { (input, expected) ->
            val vm = vm { RegisterOutcome.Accepted }
            vm.fill(input.first, input.second, input.third)
            vm.onIntent(RegisterIntent.Submit)
            val state = vm.state.value
            assertTrue(expected in listOfNotNull(state.emailError, state.passwordError, state.displayNameError).map { (it as FieldProblem.Client).error }, "$expected")
        }
        assertTrue(calls.isEmpty())
    }

    @Test
    fun doubleSubmitSendsOnce() = runMviTest {
        val gate = CompletableDeferred<RegisterOutcome>()
        val vm = vm { gate.await() }
        vm.fill()
        vm.onIntent(RegisterIntent.Submit)
        vm.onIntent(RegisterIntent.Submit)
        assertEquals(1, calls.size)
        gate.complete(RegisterOutcome.Offline)
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun blankDisplayNameIsSentAsNone() = runMviTest {
        val vm = vm { RegisterOutcome.Accepted }
        vm.fill(name = "   ")
        vm.onIntent(RegisterIntent.Submit)
        assertEquals(null, calls.single().third)
    }

    @Test
    fun acceptedGoesToCheckInbox() = runMviTest {
        val vm = vm { RegisterOutcome.Accepted }
        vm.effects.test {
            vm.fill()
            vm.onIntent(RegisterIntent.Submit)
            assertEquals(RegisterEffect.NavigateCheckInbox("sara@example.com"), awaitItem())
        }
    }

    @Test
    fun alreadyRegisteredOffersSignInAndResetWithTheEmail() = runMviTest {
        val vm = vm { RegisterOutcome.AlreadyRegistered }
        vm.fill()
        vm.onIntent(RegisterIntent.Submit)
        assertTrue(vm.state.value.alreadyRegistered)
        assertEquals("", vm.state.value.password)
        vm.effects.test {
            vm.onIntent(RegisterIntent.SignInClicked)
            assertEquals(RegisterEffect.NavigateSignIn("sara@example.com"), awaitItem())
            vm.onIntent(RegisterIntent.ResetPasswordClicked)
            assertEquals(RegisterEffect.NavigateForgotPassword("sara@example.com"), awaitItem())
        }
    }

    @Test
    fun serverFieldErrorsLandOnFields() = runMviTest {
        val vm = vm { RegisterOutcome.Validation(mapOf(AuthField.Email to "TAKEN_FORMAT")) }
        vm.fill()
        vm.onIntent(RegisterIntent.Submit)
        assertEquals(FieldProblem.Server("TAKEN_FORMAT"), vm.state.value.emailError)
        assertEquals(FormBanner.Validation, vm.state.value.banner)
    }

    @Test
    fun stateNeverPrintsThePassword() = runMviTest {
        val vm = vm { RegisterOutcome.Accepted }
        vm.fill(password = "super-secret-1")
        assertFalse("super-secret-1" in vm.state.value.toString())
    }
}
