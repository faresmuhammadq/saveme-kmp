package com.fares.saveme.feature.account.ui.changepassword

import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.account.domain.model.ChangePasswordOutcome
import com.fares.saveme.feature.auth.domain.FieldError
import com.fares.saveme.feature.auth.ui.FieldProblem
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChangePasswordReducerTest {
    @Test
    fun typingClearsThatFieldsError() {
        val state = ChangePasswordState(currentError = CurrentPasswordProblem.Incorrect, newError = FieldProblem.Client(FieldError.PasswordTooShort))
        assertNull(reduce(state, ChangePasswordChange.Current("x")).currentError)
        assertNull(reduce(state, ChangePasswordChange.New("x")).newError)
    }
}

class ChangePasswordViewModelTest {

    private fun vm(outcome: suspend () -> ChangePasswordOutcome, calls: MutableList<Pair<String, String>> = mutableListOf()) =
        ChangePasswordViewModel(email = { "sara@example.com" }, changePassword = { c, n -> calls += c to n; outcome() })

    private fun ChangePasswordViewModel.fill(current: String = "old-secret", new: String = "new-secret-1") {
        onIntent(ChangePasswordIntent.CurrentChanged(current))
        onIntent(ChangePasswordIntent.NewChanged(new))
    }

    @Test
    fun clientRulesBlockTheRequest() = runMviTest {
        val calls = mutableListOf<Pair<String, String>>()
        val vm = vm({ ChangePasswordOutcome.Success }, calls)
        vm.fill(current = "", new = "SARA@example.com")
        vm.onIntent(ChangePasswordIntent.Submit)
        assertEquals(CurrentPasswordProblem.Field(FieldProblem.Client(FieldError.Required)), vm.state.value.currentError)
        assertEquals(FieldProblem.Client(FieldError.PasswordEqualsEmail), vm.state.value.newError)
        vm.fill(current = "x", new = "short")
        vm.onIntent(ChangePasswordIntent.Submit)
        assertEquals(FieldProblem.Client(FieldError.PasswordTooShort), vm.state.value.newError)
        assertEquals(emptyList(), calls)
    }

    @Test
    fun submitsOnceWhileBusy() = runMviTest {
        val gate = CompletableDeferred<ChangePasswordOutcome>()
        val calls = mutableListOf<Pair<String, String>>()
        val vm = vm({ gate.await() }, calls)
        vm.fill()
        vm.onIntent(ChangePasswordIntent.Submit)
        vm.onIntent(ChangePasswordIntent.Submit)
        assertEquals(true, vm.state.value.submitting)
        gate.complete(ChangePasswordOutcome.Success)
        assertEquals(1, calls.size)
        assertEquals(true, vm.state.value.done)
        assertEquals("", vm.state.value.current)
    }

    @Test
    fun wrongCurrentPasswordMarksOnlyThatField() = runMviTest {
        val vm = vm({ ChangePasswordOutcome.WrongCurrentPassword })
        vm.fill()
        vm.onIntent(ChangePasswordIntent.Submit)
        assertEquals(CurrentPasswordProblem.Incorrect, vm.state.value.currentError)
        assertNull(vm.state.value.newError)
        assertEquals(false, vm.state.value.done)
    }

    @Test
    fun lockDisablesSubmitWithTheWait() = runMviTest {
        val calls = mutableListOf<Pair<String, String>>()
        val vm = vm({ ChangePasswordOutcome.Locked(900) }, calls)
        vm.fill()
        vm.onIntent(ChangePasswordIntent.Submit)
        assertEquals(15, vm.state.value.waitMinutes)
        assertEquals(WaitReason.Locked, vm.state.value.waitReason)
        vm.onIntent(ChangePasswordIntent.Submit)
        assertEquals(1, calls.size)

        testScheduler.advanceUntilIdle()
        assertNull(vm.state.value.waitMinutes, "submit comes back once the lock has passed")
    }
}
