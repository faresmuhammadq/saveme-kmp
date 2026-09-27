package com.fares.saveme.feature.auth.ui.signin

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.FieldError
import com.fares.saveme.feature.auth.domain.model.SignInOutcome
import com.fares.saveme.feature.auth.ui.FieldProblem
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignInViewModelTest {

    private val calls = mutableListOf<Triple<String, String, String>>()
    private var nextId = 0

    private fun vm(respond: suspend () -> SignInOutcome) = SignInViewModel(
        args = SignInArgs(),
        signIn = { e, p, id -> calls += Triple(e, p, id); respond() },
        newSessionId = { "id-${++nextId}" },
    )

    private fun SignInViewModel.fill(email: String = "sara@example.com", password: String = "pw") {
        onIntent(SignInIntent.EmailChanged(email))
        onIntent(SignInIntent.PasswordChanged(password))
    }

    @Test
    fun invalidInputNeverCallsTheServer() = runMviTest {
        val vm = vm { SignInOutcome.Success(false) }
        vm.fill(email = "not-an-email", password = "")
        vm.onIntent(SignInIntent.Submit)
        assertTrue(calls.isEmpty())
        assertEquals(FieldProblem.Client(FieldError.EmailInvalid), vm.state.value.emailError)
        assertEquals(FieldProblem.Client(FieldError.Required), vm.state.value.passwordError)
    }

    @Test
    fun doubleSubmitSendsOneRequest() = runMviTest {
        val gate = CompletableDeferred<SignInOutcome>()
        val vm = vm { gate.await() }
        vm.fill()
        vm.onIntent(SignInIntent.Submit)
        vm.onIntent(SignInIntent.Submit)
        assertEquals(1, calls.size)
        assertTrue(vm.state.value.submitting)
        gate.complete(SignInOutcome.Success(false))
        assertEquals(false, vm.state.value.submitting)
    }

    @Test
    fun retryReusesTheAttemptIdUntilTheEmailChanges() = runMviTest {
        val vm = vm { SignInOutcome.Offline }
        vm.fill()
        vm.onIntent(SignInIntent.Submit)
        vm.onIntent(SignInIntent.Submit)
        vm.onIntent(SignInIntent.EmailChanged("other@example.com"))
        vm.onIntent(SignInIntent.Submit)
        assertEquals(listOf("id-1", "id-1", "id-2"), calls.map { it.third })
    }

    @Test
    fun wrongPasswordAndUnknownAddressLookTheSame() = runMviTest {
        val wrongPassword = vm { SignInOutcome.InvalidCredentials }.apply { fill("known@example.com"); onIntent(SignInIntent.Submit) }
        val unknownAddress = vm { SignInOutcome.InvalidCredentials }.apply { fill("known@example.com"); onIntent(SignInIntent.Submit) }
        assertEquals(wrongPassword.state.value.copy(attemptSessionId = null), unknownAddress.state.value.copy(attemptSessionId = null))
    }

    @Test
    fun linksBecomeEffects() = runMviTest {
        val vm = vm { SignInOutcome.Success(false) }
        vm.fill(email = " sara@example.com ")
        vm.effects.test {
            vm.onIntent(SignInIntent.ForgotPasswordClicked)
            assertEquals(SignInEffect.NavigateForgotPassword("sara@example.com"), awaitItem())
            vm.onIntent(SignInIntent.CreateAccountClicked)
            assertEquals(SignInEffect.NavigateRegister, awaitItem())
        }
    }
}
