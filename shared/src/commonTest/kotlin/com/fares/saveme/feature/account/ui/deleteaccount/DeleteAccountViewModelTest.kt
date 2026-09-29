package com.fares.saveme.feature.account.ui.deleteaccount

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.account.domain.model.DeletionOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DeleteAccountReducerTest {
    @Test
    fun scheduledClearsThePassword() {
        val date = Instant.parse("2026-10-28T10:00:00Z")
        val state = reduce(DeleteAccountState(step = DeleteStep.Confirm, password = "pw"), DeleteAccountChange.Result(DeletionOutcome.Scheduled(date), null))
        assertEquals(DeleteStep.Scheduled(date), state.step)
        assertEquals("", state.password)
    }
}

class DeleteAccountViewModelTest {

    @Test
    fun explainsBeforeAskingForThePassword() = runMviTest {
        val calls = mutableListOf<String>()
        val vm = DeleteAccountViewModel { calls += it; DeletionOutcome.WrongPassword }
        assertEquals(DeleteStep.Explain, vm.state.value.step)
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertEquals(emptyList(), calls, "no request from the explanation step")

        vm.onIntent(DeleteAccountIntent.Continue)
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertEquals(DeletePasswordProblem.Required, vm.state.value.passwordError)
        assertEquals(emptyList(), calls)

        vm.onIntent(DeleteAccountIntent.PasswordChanged("nope"))
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertEquals(listOf("nope"), calls)
        assertEquals(DeletePasswordProblem.Incorrect, vm.state.value.passwordError)
        assertEquals(DeleteStep.Confirm, vm.state.value.step)
    }

    @Test
    fun scheduledShowsTheDateThenFinishes() = runMviTest {
        val date = Instant.parse("2026-10-28T10:00:00Z")
        val vm = DeleteAccountViewModel { DeletionOutcome.Scheduled(date) }
        vm.onIntent(DeleteAccountIntent.Continue)
        vm.onIntent(DeleteAccountIntent.PasswordChanged("pw"))
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertEquals(DeleteStep.Scheduled(date), vm.state.value.step)
        vm.effects.test {
            vm.onIntent(DeleteAccountIntent.Done)
            assertEquals(DeleteAccountEffect.Finished, awaitItem())
        }
    }
}
