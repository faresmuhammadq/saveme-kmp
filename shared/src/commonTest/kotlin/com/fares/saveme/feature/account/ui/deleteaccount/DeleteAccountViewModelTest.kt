package com.fares.saveme.feature.account.ui.deleteaccount

import app.cash.turbine.test
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.account.domain.deletion.Candidate
import com.fares.saveme.feature.account.domain.deletion.DeletionPlan
import com.fares.saveme.feature.account.domain.deletion.OwnedSharedWallet
import com.fares.saveme.feature.account.domain.deletion.PlanOutcome
import com.fares.saveme.feature.account.domain.deletion.PlanState
import com.fares.saveme.feature.account.domain.deletion.SuccessorChoice
import com.fares.saveme.feature.account.domain.deletion.SuccessorReason
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.model.DeletionOutcome
import com.fares.saveme.feature.account.domain.model.ScheduledOwner
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val date = Instant.parse("2026-10-28T10:00:00Z")
private val now = Instant.parse("2026-10-06T10:00:00Z")

internal fun fixedWallet(id: String = "w1", owner: String = "u2", reason: SuccessorReason = SuccessorReason.OnlyMember) = OwnedSharedWallet(
    id, "Home", owner, reason, choiceExpected = false, choiceOffered = false,
    candidates = listOf(Candidate(owner, "Omar", WalletRole.Viewer, Instant.parse("2026-09-01T00:00:00Z"))),
)

internal fun choiceWallet(id: String = "w3", suggested: String = "u4", reason: SuccessorReason = SuccessorReason.OldestEditor) = OwnedSharedWallet(
    id, "Trip", suggested, reason, choiceExpected = true, choiceOffered = true,
    candidates = listOf(
        Candidate("u4", "Mona", WalletRole.Editor, Instant.parse("2026-09-10T00:00:00Z")),
        Candidate("u5", "Early", WalletRole.Viewer, Instant.parse("2026-09-12T00:00:00Z")),
    ),
)

internal fun aPlan(vararg wallets: OwnedSharedWallet, state: PlanState = PlanState.Active, scheduledFor: Instant? = null) =
    DeletionPlan(state, null, scheduledFor, wallets.toList())

class DeleteAccountReducerTest {
    @Test
    fun scheduledClearsThePassword() {
        val state = reduce(DeleteAccountState(step = DeleteStep.Confirm, password = "pw"), DeleteAccountChange.Result(DeletionOutcome.Scheduled(date), null))
        assertEquals(DeleteStep.Scheduled(date, emptyList()), state.step)
        assertEquals("", state.password)
    }

    @Test
    fun scheduledWhileChangingReturnsToPendingWithTheServersOwnersAndDate() {
        val plan = aPlan(choiceWallet(reason = SuccessorReason.OldestEditor), state = PlanState.PendingDeletion, scheduledFor = date)
        val before = DeleteAccountState(step = DeleteStep.Confirm, plan = plan, changing = true, password = "pw")
        val after = reduce(before, DeleteAccountChange.Result(DeletionOutcome.Scheduled(date, listOf(ScheduledOwner("w3", "u5", SuccessorReason.Chosen))), null))
        assertEquals(DeleteStep.Pending, after.step)
        assertTrue(after.ownersSaved)
        assertFalse(after.changing)
        assertEquals("u5", after.plan!!.wallets.single().successorUserId)
        assertEquals(SuccessorReason.Chosen, after.plan!!.wallets.single().reason)
        assertEquals(date, after.plan!!.scheduledFor)
    }

}

class DeleteAccountSecrecyTest {
    @Test
    fun thePasswordNeverPrints() {
        val state = DeleteAccountState(password = "hunter2-secret")
        listOf(
            state.toString(), DeleteAccountIntent.PasswordChanged("hunter2-secret").toString(), DeleteAccountChange.Password("hunter2-secret").toString(),
        ).forEach { assertFalse(it.contains("hunter2"), it) }
    }
}

class DeleteAccountViewModelTest {

    private fun vm(
        plan: suspend () -> PlanOutcome = { PlanOutcome.Loaded(aPlan()) },
        request: suspend (String, List<SuccessorChoice>?) -> DeletionOutcome = { _, _ -> DeletionOutcome.WrongPassword },
        cancel: suspend () -> ActionOutcome = { ActionOutcome.Done },
        pendingOn: () -> Instant? = { null },
    ) = DeleteAccountViewModel(readPlan = plan, requestDeletion = request, cancelDeletion = cancel, pendingOn = pendingOn, now = { now })

    @Test
    fun explainsBeforeAskingForThePasswordAndNeverWithoutAPlan() = runMviTest {
        val calls = mutableListOf<String>()
        val vm = vm(request = { password, _ -> calls += password; DeletionOutcome.WrongPassword })
        assertEquals(DeleteStep.Explain, vm.state.value.step)
        vm.onIntent(DeleteAccountIntent.Confirm)
        vm.onIntent(DeleteAccountIntent.ReviewConfirm)
        assertEquals(DeleteStep.Explain, vm.state.value.step, "the password step is unreachable without a loaded plan")
        assertEquals(emptyList(), calls)

        vm.onIntent(DeleteAccountIntent.Continue)
        assertEquals(DeleteStep.Review, vm.state.value.step, "no shared wallets: straight to the review")
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertEquals(emptyList(), calls, "the review is not the password step")
        vm.onIntent(DeleteAccountIntent.ReviewConfirm)
        assertEquals(DeleteStep.Confirm, vm.state.value.step)
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
    fun aPlanFailureReturnsToTheExplanationWithTheNoteAndTryAgainWorks() = runMviTest {
        var outcome: PlanOutcome = PlanOutcome.Offline
        val vm = vm(plan = { outcome })
        vm.onIntent(DeleteAccountIntent.Continue)
        assertEquals(DeleteStep.Explain, vm.state.value.step)
        assertEquals(DeleteBanner.Offline, vm.state.value.planBanner)
        outcome = PlanOutcome.Failed(ApiErrorCode.INTERNAL_ERROR, "req-1")
        vm.onIntent(DeleteAccountIntent.Continue)
        assertEquals(DeleteBanner.Error(ApiErrorCode.INTERNAL_ERROR, "req-1"), vm.state.value.planBanner)
        outcome = PlanOutcome.Loaded(aPlan(fixedWallet()))
        vm.onIntent(DeleteAccountIntent.Continue)
        assertEquals(DeleteStep.NewOwners, vm.state.value.step)
        assertNull(vm.state.value.planBanner)
    }

    @Test
    fun picksDefaultToSuggestionsAndChangeByIntentAndConfirmSendsThem() = runMviTest {
        var sent: List<SuccessorChoice>? = null
        val plan = aPlan(fixedWallet(), choiceWallet())
        val vm = vm(plan = { PlanOutcome.Loaded(plan) }, request = { _, s -> sent = s; DeletionOutcome.Scheduled(date, listOf(ScheduledOwner("w3", "u5", SuccessorReason.Chosen))) })
        vm.onIntent(DeleteAccountIntent.Continue)
        assertEquals(DeleteStep.NewOwners, vm.state.value.step)
        assertEquals("u4", vm.state.value.ownerOf(plan.wallets[1]))
        vm.onIntent(DeleteAccountIntent.PickOwner("w3", "u5"))
        assertEquals("u5", vm.state.value.ownerOf(plan.wallets[1]))
        vm.onIntent(DeleteAccountIntent.OwnersNext)
        assertEquals(DeleteStep.Review, vm.state.value.step)
        vm.onIntent(DeleteAccountIntent.ReviewConfirm)
        vm.onIntent(DeleteAccountIntent.PasswordChanged("pw"))
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertEquals(listOf(SuccessorChoice("w3", "u5")), sent, "only the wallet that offered a choice is sent")
        assertEquals(DeleteStep.Scheduled(date, listOf(ScheduledOwner("w3", "u5", SuccessorReason.Chosen))), vm.state.value.step)
        assertEquals("", vm.state.value.password)
    }

    @Test
    fun withNoOfferedChoiceNoSuccessorsAreSent() = runMviTest {
        var sent: List<SuccessorChoice>? = listOf(SuccessorChoice("x", "y"))
        val vm = vm(plan = { PlanOutcome.Loaded(aPlan(fixedWallet())) }, request = { _, s -> sent = s; DeletionOutcome.Scheduled(date) })
        vm.onIntent(DeleteAccountIntent.Continue)
        vm.onIntent(DeleteAccountIntent.OwnersNext)
        vm.onIntent(DeleteAccountIntent.ReviewConfirm)
        vm.onIntent(DeleteAccountIntent.PasswordChanged("pw"))
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertNull(sent)
    }

    @Test
    fun successorsChangedRereadsThePlanResetsThePicksAndSchedulesNothing() = runMviTest {
        var reads = 0
        var attempts = 0
        val vm = vm(
            plan = { reads++; PlanOutcome.Loaded(aPlan(choiceWallet(suggested = if (reads > 1) "u5" else "u4"))) },
            request = { _, _ -> attempts++; DeletionOutcome.SuccessorsChanged },
        )
        vm.onIntent(DeleteAccountIntent.Continue)
        vm.onIntent(DeleteAccountIntent.PickOwner("w3", "u4"))
        vm.onIntent(DeleteAccountIntent.OwnersNext)
        vm.onIntent(DeleteAccountIntent.ReviewConfirm)
        vm.onIntent(DeleteAccountIntent.PasswordChanged("pw"))
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertEquals(2, reads)
        assertEquals(1, attempts)
        assertEquals(DeleteStep.NewOwners, vm.state.value.step)
        assertTrue(vm.state.value.successorsChanged)
        assertEquals(emptyMap(), vm.state.value.picks)
        assertEquals("u5", vm.state.value.ownerOf(vm.state.value.plan!!.wallets.single()))
        assertEquals("", vm.state.value.password)
    }

    @Test
    fun backWalksTheStepsAndNeverThroughTheLoading() = runMviTest {
        val vm = vm(plan = { PlanOutcome.Loaded(aPlan(choiceWallet())) })
        vm.onIntent(DeleteAccountIntent.Continue)
        vm.onIntent(DeleteAccountIntent.OwnersNext)
        vm.onIntent(DeleteAccountIntent.ReviewConfirm)
        assertEquals(DeleteStep.Confirm, vm.state.value.step)
        vm.onIntent(DeleteAccountIntent.Back)
        assertEquals(DeleteStep.Review, vm.state.value.step)
        vm.onIntent(DeleteAccountIntent.Back)
        assertEquals(DeleteStep.NewOwners, vm.state.value.step)
        vm.onIntent(DeleteAccountIntent.Back)
        assertEquals(DeleteStep.Explain, vm.state.value.step)
        vm.effects.test {
            vm.onIntent(DeleteAccountIntent.Back)
            assertEquals(DeleteAccountEffect.NavigateBack, awaitItem())
        }
    }

    @Test
    fun scheduledShowsTheDateThenFinishesAndExportsOpens() = runMviTest {
        val vm = vm(request = { _, _ -> DeletionOutcome.Scheduled(date) })
        vm.onIntent(DeleteAccountIntent.Continue)
        vm.onIntent(DeleteAccountIntent.ReviewConfirm)
        vm.onIntent(DeleteAccountIntent.PasswordChanged("pw"))
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertEquals(DeleteStep.Scheduled(date, emptyList()), vm.state.value.step)
        vm.effects.test {
            vm.onIntent(DeleteAccountIntent.ExportClicked)
            assertEquals(DeleteAccountEffect.OpenExports, awaitItem())
            vm.onIntent(DeleteAccountIntent.Done)
            assertEquals(DeleteAccountEffect.Finished, awaitItem())
        }
    }

    // --- The pending view (US5) ---

    private val pendingPlan = aPlan(choiceWallet(suggested = "u5", reason = SuccessorReason.Chosen), state = PlanState.PendingDeletion, scheduledFor = date)

    @Test
    fun openingWhilePendingReadsThePlanAndShowsThePendingView() = runMviTest {
        val vm = vm(plan = { PlanOutcome.Loaded(pendingPlan) }, pendingOn = { date })
        vm.onIntent(DeleteAccountIntent.Opened)
        assertEquals(DeleteStep.Pending, vm.state.value.step)
        assertEquals(date, vm.state.value.scheduledFor)
        assertTrue(vm.state.value.canChangeOwners)
        assertFalse(vm.state.value.beingDeleted)
    }

    @Test
    fun openingWhileNotPendingReadsNothing() = runMviTest {
        var reads = 0
        val vm = vm(plan = { reads++; PlanOutcome.Loaded(aPlan()) })
        vm.onIntent(DeleteAccountIntent.Opened)
        assertEquals(0, reads)
        assertEquals(DeleteStep.Explain, vm.state.value.step)
    }

    @Test
    fun aPlanFailureWhilePendingKeepsTheSnapshotDateWithoutChange() = runMviTest {
        val vm = vm(plan = { PlanOutcome.Offline }, pendingOn = { date })
        vm.onIntent(DeleteAccountIntent.Opened)
        assertEquals(DeleteStep.Pending, vm.state.value.step)
        assertEquals(date, vm.state.value.scheduledFor)
        assertEquals(DeleteBanner.Offline, vm.state.value.planBanner)
        assertFalse(vm.state.value.canChangeOwners)
    }

    @Test
    fun changingOwnersGoesThroughReviewAndPasswordAndReturnsWithTheSameDate() = runMviTest {
        var sent: List<SuccessorChoice>? = null
        val vm = vm(
            plan = { PlanOutcome.Loaded(pendingPlan) },
            request = { _, s -> sent = s; DeletionOutcome.Scheduled(date, listOf(ScheduledOwner("w3", "u4", SuccessorReason.Chosen))) },
            pendingOn = { date },
        )
        vm.onIntent(DeleteAccountIntent.Opened)
        vm.onIntent(DeleteAccountIntent.ChangeOwners)
        assertEquals(DeleteStep.NewOwners, vm.state.value.step)
        assertTrue(vm.state.value.changing)
        assertEquals("u5", vm.state.value.ownerOf(vm.state.value.plan!!.wallets.single()), "the stored choice is the starting point")
        vm.onIntent(DeleteAccountIntent.PickOwner("w3", "u4"))
        vm.onIntent(DeleteAccountIntent.OwnersNext)
        vm.onIntent(DeleteAccountIntent.ReviewConfirm)
        vm.onIntent(DeleteAccountIntent.PasswordChanged("pw"))
        vm.onIntent(DeleteAccountIntent.Confirm)
        assertEquals(listOf(SuccessorChoice("w3", "u4")), sent)
        assertEquals(DeleteStep.Pending, vm.state.value.step)
        assertTrue(vm.state.value.ownersSaved)
        assertEquals(date, vm.state.value.scheduledFor)
        assertEquals("u4", vm.state.value.plan!!.wallets.single().successorUserId)
    }

    @Test
    fun backOutOfChangingReturnsToPendingWithoutSaving() = runMviTest {
        var requests = 0
        val vm = vm(plan = { PlanOutcome.Loaded(pendingPlan) }, request = { _, _ -> requests++; DeletionOutcome.Offline }, pendingOn = { date })
        vm.onIntent(DeleteAccountIntent.Opened)
        vm.onIntent(DeleteAccountIntent.ChangeOwners)
        vm.onIntent(DeleteAccountIntent.Back)
        assertEquals(DeleteStep.Pending, vm.state.value.step)
        assertFalse(vm.state.value.changing)
        assertEquals(0, requests)
    }

    @Test
    fun changeOwnersIsOnlyOfferedWhenAWalletExpectsAChoice() = runMviTest {
        val vm = vm(plan = { PlanOutcome.Loaded(aPlan(fixedWallet(), state = PlanState.PendingDeletion, scheduledFor = date)) }, pendingOn = { date })
        vm.onIntent(DeleteAccountIntent.Opened)
        assertFalse(vm.state.value.canChangeOwners)
        vm.onIntent(DeleteAccountIntent.ChangeOwners)
        assertEquals(DeleteStep.Pending, vm.state.value.step)
    }

    @Test
    fun cancelDropsTheChoicesAndSaysSoOnlyWhenAnyWereStored() = runMviTest {
        val withChoice = vm(plan = { PlanOutcome.Loaded(pendingPlan) }, pendingOn = { date })
        withChoice.onIntent(DeleteAccountIntent.Opened)
        withChoice.onIntent(DeleteAccountIntent.CancelDeletion)
        assertEquals(DeleteStep.Cancelled(choicesDropped = true), withChoice.state.value.step)

        val plain = vm(plan = { PlanOutcome.Loaded(aPlan(choiceWallet(), state = PlanState.PendingDeletion, scheduledFor = date)) }, pendingOn = { date })
        plain.onIntent(DeleteAccountIntent.Opened)
        plain.onIntent(DeleteAccountIntent.CancelDeletion)
        assertEquals(DeleteStep.Cancelled(choicesDropped = false), plain.state.value.step)
    }

    @Test
    fun cancelOfflineKeepsThePendingViewWithABanner() = runMviTest {
        val vm = vm(plan = { PlanOutcome.Loaded(pendingPlan) }, cancel = { ActionOutcome.Offline }, pendingOn = { date })
        vm.onIntent(DeleteAccountIntent.Opened)
        vm.onIntent(DeleteAccountIntent.CancelDeletion)
        assertEquals(DeleteStep.Pending, vm.state.value.step)
        assertEquals(DeleteBanner.Offline, vm.state.value.banner)
        assertFalse(vm.state.value.cancelling)
    }

    @Test
    fun anUnknownPlanStateIsHandledAsPendingWithoutChange() = runMviTest {
        val vm = vm(plan = { PlanOutcome.Loaded(aPlan(choiceWallet(), state = PlanState.Unknown("on_hold"))) }, pendingOn = { date })
        vm.onIntent(DeleteAccountIntent.Opened)
        assertEquals(DeleteStep.Pending, vm.state.value.step)
        assertFalse(vm.state.value.canChangeOwners)
        assertEquals(date, vm.state.value.scheduledFor, "the snapshot date stays")
    }

    @Test
    fun afterTheDateThePendingViewIsBeingDeleted() = runMviTest {
        val past = Instant.parse("2026-10-01T10:00:00Z")
        val vm = vm(plan = { PlanOutcome.Loaded(aPlan(state = PlanState.PendingDeletion, scheduledFor = past)) }, pendingOn = { past })
        vm.onIntent(DeleteAccountIntent.Opened)
        assertTrue(vm.state.value.beingDeleted)
        vm.onIntent(DeleteAccountIntent.CancelDeletion)
        assertTrue(vm.state.value.step is DeleteStep.Cancelled, "Cancel deletion stays available")
    }

    @Test
    fun anAlreadyActivePlanWhileOpenedPendingFallsBackToTheExplanation() = runMviTest {
        val vm = vm(plan = { PlanOutcome.Loaded(aPlan()) }, pendingOn = { date })
        vm.onIntent(DeleteAccountIntent.Opened)
        assertEquals(DeleteStep.Explain, vm.state.value.step)
    }

    @Test
    fun startingWhileAnotherDeviceAlreadyAskedShowsThePendingView() = runMviTest {
        val vm = vm(plan = { PlanOutcome.Loaded(pendingPlan) })
        vm.onIntent(DeleteAccountIntent.Continue)
        assertEquals(DeleteStep.Pending, vm.state.value.step)
    }
}
