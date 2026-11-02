package com.fares.saveme.feature.auth.ui.home

import com.fares.saveme.core.sync.SyncStatus
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.BudgetGlimpse
import com.fares.saveme.feature.auth.domain.DueGlimpse
import com.fares.saveme.feature.auth.domain.HomeOverview
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.auth.ui.verify.ResendChange
import com.fares.saveme.feature.auth.ui.verify.ResendState
import com.fares.saveme.feature.auth.ui.verify.reduceResend
import kotlin.time.Instant

/** Placeholder signed-in home until the Home feature exists (spec 001 Assumptions). */
data class HomeState(
    val email: String = "",
    val emailVerified: Boolean = true,
    val deletionCancelledNotice: Boolean = false,
    val deletionPendingOn: Instant? = null,
    /** The present minute (a tick), so "being deleted" is re-evaluated without any write (spec 016 FR-032). */
    val now: Instant = Instant.fromEpochMilliseconds(0),
    val cancellingDeletion: Boolean = false,
    val confirmSignOutVisible: Boolean = false,
    val signingOut: Boolean = false,
    /** Verify prompt's "Resend email" (spec 003 FR-012, FR-013). */
    val resend: ResendState = ResendState(),
    /** Changes still only on this phone; the sign-out sheet warns about them (spec 004 FR-017). */
    val sync: SyncStatus = SyncStatus(),
    /** Due and missed recurring occurrences in wallets the person can write to; the card shows when above zero (spec 010 FR-019). */
    val dueCount: Int = 0,
    val firstDue: DueGlimpse? = null,
    /** Up to three active budgets of the current period, the highest share spent first; the card is hidden when empty (spec 012 FR-017). */
    val budgets: List<BudgetGlimpse> = emptyList(),
    /** An invitation link is held in memory for this app session: the card reopens it (spec 013 FR-013). */
    val invitationHeld: Boolean = false,
    /** The profile's display name for the greeting; null when none is set. */
    val displayName: String? = null,
    /** The current period's spending, the wallets and the newest transactions. */
    val overview: HomeOverview = HomeOverview(),
    /** What the person typically spends in a period, for the "set a budget" suggestion; null when unknown. */
    val typicalSpendMinor: Long? = null,
    /** True until the stored choice is read, so the suggestion never flashes. */
    val suggestionDismissed: Boolean = true,
)

/** Whether the scheduled deletion date has passed while the account is still pending (spec 016 FR-032). */
val HomeState.beingDeleted: Boolean get() = deletionPendingOn?.let { it <= now } == true

/** What cancelling a pending deletion came to, as far as Home needs to know. */
enum class CancelDeletionResult { Done, Offline, Failed }

sealed interface HomeIntent {
    data object DismissDeletionNotice : HomeIntent
    data object AccountClicked : HomeIntent
    data object WalletsClicked : HomeIntent
    data object CategoriesClicked : HomeIntent
    data object AddTransactionClicked : HomeIntent
    data object CancelDeletion : HomeIntent
    data object SignOutClicked : HomeIntent
    data object SignOutConfirmed : HomeIntent
    data object SignOutDismissed : HomeIntent
    data object ResendClicked : HomeIntent
    data object SyncNowClicked : HomeIntent
    data object DueClicked : HomeIntent
    data object BudgetsClicked : HomeIntent
    data object ReportsClicked : HomeIntent
    data class BudgetClicked(val id: String) : HomeIntent
    data object InvitationClicked : HomeIntent
    data class WalletClicked(val id: String) : HomeIntent
    data class TransactionClicked(val id: String, val walletId: String) : HomeIntent
    data object ReportClicked : HomeIntent
    data object MoreCategoriesClicked : HomeIntent
    data object SetBudgetClicked : HomeIntent
    data object DismissSuggestion : HomeIntent
}

sealed interface HomeEffect {
    data object NavigateAccount : HomeEffect
    data object NavigateWallets : HomeEffect
    data object NavigateCategories : HomeEffect
    data object NavigateAddTransaction : HomeEffect
    data object OpenDue : HomeEffect
    data object NavigateBudgets : HomeEffect
    data object NavigateReports : HomeEffect
    data class OpenBudget(val id: String) : HomeEffect
    data object OpenInvitation : HomeEffect
    data class OpenWallet(val id: String) : HomeEffect
    data class OpenTransaction(val id: String, val walletId: String) : HomeEffect
    data object OpenReport : HomeEffect
    data class OpenReportCategories(val from: kotlinx.datetime.LocalDate, val to: kotlinx.datetime.LocalDate, val currency: String) : HomeEffect
    data object OpenBudgetEditor : HomeEffect
    data class ShowMessage(val message: HomeMessage) : HomeEffect
}

enum class HomeMessage { DeletionCancelled, Offline, Failed, InvitationDeclined }

sealed interface HomeChange {
    data class Account(val signedIn: AuthState.SignedIn) : HomeChange
    data object DeletionNoticeDismissed : HomeChange
    data class CancellingDeletion(val busy: Boolean) : HomeChange
    data class SignOutConfirmVisible(val visible: Boolean) : HomeChange
    data object SigningOut : HomeChange
    data class Resend(val change: ResendChange) : HomeChange
    data class Sync(val status: SyncStatus) : HomeChange
    data class Due(val count: Int, val first: DueGlimpse?) : HomeChange
    data class Budgets(val budgets: List<BudgetGlimpse>) : HomeChange
    data class InvitationHeld(val held: Boolean) : HomeChange
    data class Tick(val now: Instant) : HomeChange
    data class Name(val displayName: String?) : HomeChange
    data class Overview(val overview: HomeOverview) : HomeChange
    data class TypicalSpend(val minor: Long?) : HomeChange
    data class SuggestionDismissed(val dismissed: Boolean) : HomeChange
}

fun reduce(state: HomeState, change: HomeChange): HomeState = when (change) {
    is HomeChange.Account -> state.copy(
        email = change.signedIn.account.email,
        emailVerified = change.signedIn.account.emailVerified,
        deletionCancelledNotice = state.deletionCancelledNotice || change.signedIn.deletionCancelled,
        deletionPendingOn = change.signedIn.account.deletionScheduledFor
            .takeIf { change.signedIn.account.state == AccountState.PendingDeletion },
    )
    HomeChange.DeletionNoticeDismissed -> state.copy(deletionCancelledNotice = false)
    is HomeChange.CancellingDeletion -> state.copy(cancellingDeletion = change.busy)
    is HomeChange.SignOutConfirmVisible -> state.copy(confirmSignOutVisible = change.visible)
    HomeChange.SigningOut -> state.copy(signingOut = true)
    is HomeChange.Resend -> state.copy(resend = reduceResend(state.resend, change.change))
    is HomeChange.Sync -> state.copy(sync = change.status)
    is HomeChange.Due -> state.copy(dueCount = change.count, firstDue = change.first)
    is HomeChange.Budgets -> state.copy(budgets = change.budgets)
    is HomeChange.InvitationHeld -> state.copy(invitationHeld = change.held)
    is HomeChange.Tick -> state.copy(now = change.now)
    is HomeChange.Overview -> state.copy(overview = change.overview)
    is HomeChange.TypicalSpend -> state.copy(typicalSpendMinor = change.minor)
    is HomeChange.SuggestionDismissed -> state.copy(suggestionDismissed = change.dismissed)
    is HomeChange.Name -> state.copy(displayName = change.displayName?.takeIf { it.isNotBlank() })
}
