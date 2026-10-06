package com.fares.saveme.feature.account.ui.deleteaccount

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.account.domain.deletion.DeletionPlan
import com.fares.saveme.feature.account.domain.deletion.OwnedSharedWallet
import com.fares.saveme.feature.account.domain.deletion.PlanState
import com.fares.saveme.feature.account.domain.model.DeletionOutcome
import com.fares.saveme.feature.account.domain.model.PasswordField
import com.fares.saveme.feature.account.domain.model.ScheduledOwner
import kotlin.time.Instant

/**
 * The Delete account flow (spec 002, extended by spec 016): explanation, a plan read, new owners, review, password, scheduled; and
 * while pending, the pending view with its own path to change the owners. Memory only: the plan and the picks are never stored.
 */
data class DeleteAccountState(
    val step: DeleteStep = DeleteStep.Explain,
    /** The server's plan, joined with the phone's names; null until read. */
    val plan: DeletionPlan? = null,
    /** The new owner picked per wallet id; a wallet without a pick uses the server's suggestion. */
    val picks: Map<String, String> = emptyMap(),
    /** Changing the owners of an already pending deletion (the password step then keeps the date). */
    val changing: Boolean = false,
    /** The date of a deletion already pending, from the account snapshot: shown when the plan cannot be read. */
    val pendingOn: Instant? = null,
    val now: Instant = Instant.fromEpochMilliseconds(0),
    val password: String = "",
    val passwordVisible: Boolean = false,
    val passwordError: DeletePasswordProblem? = null,
    val submitting: Boolean = false,
    val cancelling: Boolean = false,
    val waitMinutes: Int? = null,
    val banner: DeleteBanner? = null,
    /** The plan could not be read (offline or refused): "Try again" is offered. */
    val planBanner: DeleteBanner? = null,
    /** The server refused the owners because the shared wallets changed (FR-028). */
    val successorsChanged: Boolean = false,
    val ownersSaved: Boolean = false,
) {
    /** The date being deleted on: the plan's, else the snapshot's. */
    val scheduledFor: Instant? get() = plan?.scheduledFor ?: pendingOn

    val beingDeleted: Boolean get() = scheduledFor?.let { it <= now } == true

    /** The new owner for [wallet]: the person's pick, else the server's suggestion. */
    fun ownerOf(wallet: OwnedSharedWallet): String =
        picks[wallet.walletId]?.takeIf { pick -> wallet.candidates.any { it.userId == pick } } ?: wallet.successorUserId

    /** The password is a secret: it prints redacted wherever the state lands (P-09.2). */
    override fun toString() =
        "DeleteAccountState(step=${step::class.simpleName}, password=${if (password.isEmpty()) "" else "***"}, wallets=${plan?.wallets?.size}, " +
            "picks=${picks.size}, changing=$changing, submitting=$submitting)"

    /** "Change new owners" is offered when a wallet expects a choice and the plan was read. */
    val canChangeOwners: Boolean get() = plan?.state == PlanState.PendingDeletion && plan.offersChoice
}

sealed interface DeleteStep {
    data object Explain : DeleteStep
    data object LoadingPlan : DeleteStep
    data object NewOwners : DeleteStep
    data object Review : DeleteStep
    data object Confirm : DeleteStep
    data class Scheduled(val date: Instant, val owners: List<ScheduledOwner>) : DeleteStep

    /** A deletion is pending (the account says so); the plan, when read, lists the owners. */
    data object Pending : DeleteStep

    /** Cancelled from the pending view; [choicesDropped] says the stored new owners went with it. */
    data class Cancelled(val choicesDropped: Boolean) : DeleteStep
}

sealed interface DeletePasswordProblem {
    data object Required : DeletePasswordProblem
    data object Incorrect : DeletePasswordProblem
    data class Server(val code: String) : DeletePasswordProblem
}

sealed interface DeleteBanner {
    data object Offline : DeleteBanner
    data class Error(val code: ApiErrorCode, val requestId: String?) : DeleteBanner
}

sealed interface DeleteAccountIntent {
    /** The screen opened: while a deletion is pending, the plan is read for the pending view. */
    data object Opened : DeleteAccountIntent
    data object Continue : DeleteAccountIntent
    data class PickOwner(val walletId: String, val userId: String) : DeleteAccountIntent
    data object OwnersNext : DeleteAccountIntent
    data object ReviewConfirm : DeleteAccountIntent
    data class PasswordChanged(val value: String) : DeleteAccountIntent {
        override fun toString() = "PasswordChanged(***)"
    }
    data object TogglePasswordVisible : DeleteAccountIntent
    data object Confirm : DeleteAccountIntent
    data object ChangeOwners : DeleteAccountIntent
    data object CancelDeletion : DeleteAccountIntent
    data object ExportClicked : DeleteAccountIntent
    data object Done : DeleteAccountIntent
    data object Back : DeleteAccountIntent
}

sealed interface DeleteAccountEffect {
    data object Finished : DeleteAccountEffect
    data object NavigateBack : DeleteAccountEffect
    data object OpenExports : DeleteAccountEffect
}

sealed interface DeleteAccountChange {
    data class Opened(val pendingOn: Instant?, val now: Instant) : DeleteAccountChange
    data object LoadingPlan : DeleteAccountChange
    data class PlanLoaded(val plan: DeletionPlan, val next: DeleteStep) : DeleteAccountChange
    data class PlanFailed(val banner: DeleteBanner, val back: DeleteStep) : DeleteAccountChange
    data class Step(val step: DeleteStep) : DeleteAccountChange
    data class Pick(val walletId: String, val userId: String) : DeleteAccountChange
    data object ChangingOwners : DeleteAccountChange
    data class Password(val value: String) : DeleteAccountChange {
        override fun toString() = "Password(***)"
    }
    data object ToggleVisible : DeleteAccountChange
    data object MissingPassword : DeleteAccountChange
    data object Submitting : DeleteAccountChange
    data class Result(val outcome: DeletionOutcome, val waitMinutes: Int?) : DeleteAccountChange
    data object WaitOver : DeleteAccountChange
    data class Cancelling(val busy: Boolean) : DeleteAccountChange
    data class CancelFailed(val banner: DeleteBanner) : DeleteAccountChange
    data object Cancelled : DeleteAccountChange
}

fun reduce(state: DeleteAccountState, change: DeleteAccountChange): DeleteAccountState = when (change) {
    is DeleteAccountChange.Opened -> state.copy(pendingOn = change.pendingOn, now = change.now, planBanner = null)
    DeleteAccountChange.LoadingPlan -> state.copy(step = DeleteStep.LoadingPlan, planBanner = null, banner = null, ownersSaved = false)
    is DeleteAccountChange.PlanLoaded -> state.copy(
        plan = change.plan, step = change.next, planBanner = null,
        // The suggestions are the defaults again whenever the plan was read anew (FR-028).
        picks = emptyMap(),
    )
    is DeleteAccountChange.PlanFailed -> state.copy(planBanner = change.banner, step = change.back)
    is DeleteAccountChange.Step -> state.copy(step = change.step, banner = null)
    is DeleteAccountChange.Pick -> state.copy(picks = state.picks + (change.walletId to change.userId))
    DeleteAccountChange.ChangingOwners -> state.copy(
        step = DeleteStep.NewOwners, changing = true, picks = emptyMap(), password = "", passwordError = null, banner = null,
        ownersSaved = false, successorsChanged = false,
    )
    is DeleteAccountChange.Password -> state.copy(password = change.value, passwordError = null, banner = null)
    DeleteAccountChange.ToggleVisible -> state.copy(passwordVisible = !state.passwordVisible)
    DeleteAccountChange.MissingPassword -> state.copy(passwordError = DeletePasswordProblem.Required)
    DeleteAccountChange.Submitting -> state.copy(submitting = true, banner = null, successorsChanged = false)
    is DeleteAccountChange.Result -> {
        val done = state.copy(submitting = false)
        when (val outcome = change.outcome) {
            is DeletionOutcome.Scheduled ->
                if (state.changing) {
                    // The owners saved: back to the pending view with the server's owners and the same date (FR-031).
                    done.copy(
                        step = DeleteStep.Pending, password = "", changing = false, ownersSaved = true, picks = emptyMap(),
                        plan = state.plan?.withOwners(outcome),
                    )
                } else {
                    done.copy(step = DeleteStep.Scheduled(outcome.date, outcome.owners), password = "", pendingOn = outcome.date)
                }
            DeletionOutcome.SuccessorsChanged -> done.copy(successorsChanged = true, password = "")
            DeletionOutcome.WrongPassword -> done.copy(passwordError = DeletePasswordProblem.Incorrect)
            is DeletionOutcome.Locked -> done.copy(waitMinutes = change.waitMinutes)
            is DeletionOutcome.Validation -> done.copy(
                passwordError = outcome.fields[PasswordField.Current]?.let { DeletePasswordProblem.Server(it) },
            )
            DeletionOutcome.Offline -> done.copy(banner = DeleteBanner.Offline)
            is DeletionOutcome.Error -> done.copy(banner = DeleteBanner.Error(outcome.code, outcome.requestId))
        }
    }
    DeleteAccountChange.WaitOver -> state.copy(waitMinutes = null)
    is DeleteAccountChange.Cancelling -> state.copy(cancelling = change.busy, banner = null)
    is DeleteAccountChange.CancelFailed -> state.copy(cancelling = false, banner = change.banner)
    DeleteAccountChange.Cancelled -> state.copy(
        cancelling = false,
        step = DeleteStep.Cancelled(choicesDropped = state.plan?.hasStoredChoice == true),
        pendingOn = null,
    )
}

/** The plan with the owners the server just answered, keeping the names and candidates the phone knows. */
private fun DeletionPlan.withOwners(outcome: DeletionOutcome.Scheduled): DeletionPlan = copy(
    scheduledFor = outcome.date,
    wallets = wallets.map { wallet ->
        outcome.owners.firstOrNull { it.walletId == wallet.walletId }
            ?.let { owner -> wallet.copy(successorUserId = owner.ownerUserId, reason = owner.reason) }
            ?: wallet
    },
)
