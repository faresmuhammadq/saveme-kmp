package com.fares.saveme.feature.account.ui.deleteaccount

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.account.domain.deletion.DeletionPlan
import com.fares.saveme.feature.account.domain.deletion.PlanOutcome
import com.fares.saveme.feature.account.domain.deletion.PlanState
import com.fares.saveme.feature.account.domain.deletion.SuccessorChoice
import com.fares.saveme.feature.account.domain.deletion.SuccessorChoices
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.model.DeletionOutcome
import com.fares.saveme.feature.auth.ui.minutesToWait
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The Delete account flow (spec 002, spec 016). The plan is read before the password step and the password step cannot be
 * reached without one (FR-024). Nothing here is retried or queued. The password lives in the state only while the step is open
 * and never prints (P-09.2). Domain lambdas only (P-16.3).
 */
class DeleteAccountViewModel(
    private val readPlan: suspend () -> PlanOutcome = { PlanOutcome.Offline },
    private val requestDeletion: suspend (password: String, successors: List<SuccessorChoice>?) -> DeletionOutcome,
    private val cancelDeletion: suspend () -> ActionOutcome = { ActionOutcome.Offline },
    /** The date of a deletion already pending according to the account, else null. */
    private val pendingOn: () -> Instant? = { null },
    private val now: () -> Instant = { Clock.System.now() },
) : MviViewModel<DeleteAccountState, DeleteAccountIntent, DeleteAccountEffect>(DeleteAccountState()) {

    override fun onIntent(intent: DeleteAccountIntent) {
        when (intent) {
            DeleteAccountIntent.Opened -> opened()
            DeleteAccountIntent.Continue -> loadPlan(onLoaded = ::afterPlanForNewDeletion)
            is DeleteAccountIntent.PickOwner -> setState { reduce(it, DeleteAccountChange.Pick(intent.walletId, intent.userId)) }
            DeleteAccountIntent.OwnersNext -> if (currentState.step == DeleteStep.NewOwners) step(DeleteStep.Review)
            DeleteAccountIntent.ReviewConfirm -> if (currentState.step == DeleteStep.Review && currentState.plan != null) step(DeleteStep.Confirm)
            is DeleteAccountIntent.PasswordChanged -> setState { reduce(it, DeleteAccountChange.Password(intent.value)) }
            DeleteAccountIntent.TogglePasswordVisible -> setState { reduce(it, DeleteAccountChange.ToggleVisible) }
            DeleteAccountIntent.Confirm -> confirm()
            DeleteAccountIntent.ChangeOwners -> if (currentState.canChangeOwners) setState { reduce(it, DeleteAccountChange.ChangingOwners) }
            DeleteAccountIntent.CancelDeletion -> cancel()
            DeleteAccountIntent.ExportClicked -> sendEffect(DeleteAccountEffect.OpenExports)
            DeleteAccountIntent.Done -> sendEffect(DeleteAccountEffect.Finished)
            DeleteAccountIntent.Back -> back()
        }
    }

    private fun step(next: DeleteStep) = setState { reduce(it, DeleteAccountChange.Step(next)) }

    private fun opened() {
        val date = pendingOn()
        setState { reduce(it, DeleteAccountChange.Opened(date, now())) }
        if (date != null) loadPlan(onLoaded = { plan -> if (plan.state == PlanState.Active) DeleteStep.Explain else DeleteStep.Pending })
    }

    /** What a freshly read plan leads to when the person is starting a deletion. */
    private fun afterPlanForNewDeletion(plan: DeletionPlan): DeleteStep = when {
        // Another device already asked: the pending view is the truth now.
        plan.state != PlanState.Active -> DeleteStep.Pending
        plan.wallets.isNotEmpty() -> DeleteStep.NewOwners
        else -> DeleteStep.Review
    }

    /** Reads the plan; on failure the flow returns to where [failureStep] says with a note and "Try again". */
    private fun loadPlan(failureStep: () -> DeleteStep = { if (currentState.pendingOn != null) DeleteStep.Pending else DeleteStep.Explain }, onLoaded: (DeletionPlan) -> DeleteStep) {
        if (currentState.step == DeleteStep.LoadingPlan) return
        val back = failureStep()
        setState { reduce(it, DeleteAccountChange.LoadingPlan) }
        viewModelScope.launch {
            when (val outcome = readPlan()) {
                is PlanOutcome.Loaded -> {
                    setState { reduce(it, DeleteAccountChange.Opened(outcome.plan.scheduledFor ?: it.pendingOn.takeIf { outcome.plan.state is PlanState.Unknown }, now())) }
                    setState { reduce(it, DeleteAccountChange.PlanLoaded(outcome.plan, onLoaded(outcome.plan))) }
                }
                PlanOutcome.Offline -> setState { reduce(it, DeleteAccountChange.PlanFailed(DeleteBanner.Offline, back)) }
                is PlanOutcome.Failed ->
                    setState { reduce(it, DeleteAccountChange.PlanFailed(DeleteBanner.Error(outcome.code, outcome.requestId), back)) }
            }
        }
    }

    private fun confirm() {
        val state = currentState
        val plan = state.plan
        if (state.step != DeleteStep.Confirm || plan == null || state.submitting || state.waitMinutes != null) return
        if (state.password.isEmpty()) {
            setState { reduce(it, DeleteAccountChange.MissingPassword) }
            return
        }
        setState { reduce(it, DeleteAccountChange.Submitting) }
        viewModelScope.launch {
            val outcome = requestDeletion(state.password, SuccessorChoices.build(plan, state.picks))
            val wait = (outcome as? DeletionOutcome.Locked)?.retryAfterSeconds
            setState { reduce(it, DeleteAccountChange.Result(outcome, minutesToWait(wait))) }
            if (outcome == DeletionOutcome.SuccessorsChanged) {
                // The shared wallets changed: nothing was scheduled; read again and ask again (FR-028).
                loadPlan(failureStep = { if (state.changing) DeleteStep.Pending else DeleteStep.Explain }, onLoaded = { DeleteStep.NewOwners })
            }
            if (wait != null) {
                delay(wait.seconds)
                setState { reduce(it, DeleteAccountChange.WaitOver) }
            }
        }
    }

    private fun cancel() {
        val state = currentState
        if (state.step != DeleteStep.Pending || state.cancelling) return
        setState { reduce(it, DeleteAccountChange.Cancelling(true)) }
        viewModelScope.launch {
            when (val outcome = cancelDeletion()) {
                ActionOutcome.Done -> setState { reduce(it, DeleteAccountChange.Cancelled) }
                ActionOutcome.Offline -> setState { reduce(it, DeleteAccountChange.CancelFailed(DeleteBanner.Offline)) }
                is ActionOutcome.Error -> setState { reduce(it, DeleteAccountChange.CancelFailed(DeleteBanner.Error(outcome.code, outcome.requestId))) }
            }
        }
    }

    private fun back() {
        val state = currentState
        when (state.step) {
            DeleteStep.NewOwners -> if (state.changing) leaveChange() else step(DeleteStep.Explain)
            DeleteStep.Review -> step(if (state.plan?.wallets?.isNotEmpty() == true) DeleteStep.NewOwners else DeleteStep.Explain)
            DeleteStep.Confirm -> step(DeleteStep.Review)
            DeleteStep.LoadingPlan -> Unit
            else -> sendEffect(DeleteAccountEffect.NavigateBack)
        }
    }

    /** Backing out of changing owners returns to the pending view without saving. */
    private fun leaveChange() {
        setState { reduce(it, DeleteAccountChange.Step(DeleteStep.Pending)).copy(changing = false, picks = emptyMap()) }
    }
}
