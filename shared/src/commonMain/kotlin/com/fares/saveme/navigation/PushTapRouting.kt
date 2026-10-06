package com.fares.saveme.navigation

import androidx.navigation3.runtime.NavKey
import com.fares.saveme.feature.budgets.ui.editor.BudgetEditorEffect
import com.fares.saveme.feature.notifications.domain.PushRead
import com.fares.saveme.feature.notifications.domain.PushRoute
import com.fares.saveme.feature.notifications.domain.PushTarget
import com.fares.saveme.feature.recurring.ui.editor.RuleEditorEffect
import com.fares.saveme.feature.sharing.ui.invitation.InvitationEffect

/**
 * The screen a tapped notification opens (spec 017 FR-031): a ready export opens its detail (spec 016), a malformed export
 * payload the exports list, a deletion reminder the account-deletion screen, which shows the pending deletion and its
 * cancellation. The tap only navigates.
 */
internal fun PushTarget.toNavKey(): NavKey = when (this) {
    is PushTarget.Occurrence -> OccurrenceKey(id, confirm = false)
    PushTarget.RecurringAll -> RecurringKey(null)
    is PushTarget.Budget -> BudgetEditorKey(id)
    PushTarget.Budgets -> BudgetsKey
    PushTarget.InvitationNotice -> InvitationNoticeKey
    is PushTarget.Wallet -> WalletDetailKey(id)
    PushTarget.Wallets -> WalletsKey
    PushTarget.Plan -> PlanKey
    is PushTarget.Export -> ExportDetailKey(id)
    PushTarget.Exports -> ExportsKey
    PushTarget.AccountDeletion -> DeleteAccountKey
    PushTarget.Home -> HomeKey
}

/**
 * Opens [target] on this stack: Home trims back to the existing Home entry (a second one would repeat a key), anything else
 * goes on top so an edited screen stays underneath with its edits (spec edge case).
 */
internal fun MutableList<NavKey>.openPushTarget(target: PushTarget) {
    val key = target.toNavKey()
    if (key == HomeKey) {
        while (size > 1 && last() != HomeKey) removeAt(lastIndex)
        if (isEmpty()) add(HomeKey)
    } else {
        add(key)
    }
}

/**
 * What a tap does to this stack (FR-029, FR-033): forgotten when nobody is signed in, dropped while the blocking update
 * screen shows, otherwise a newer tap replaces an "Opening…" screen still showing; a route with no read opens its target at
 * once, any other waits on a new "Opening…" screen numbered by [nextNonce]. Returns whether the tap was routed.
 */
internal fun MutableList<NavKey>.openPushTap(route: PushRoute, signedIn: Boolean, nextNonce: () -> Long): Boolean {
    if (!signedIn) return false
    if (lastOrNull() is UpdateRequiredKey) return false
    if (lastOrNull() is NotificationOpenKey) removeAt(lastIndex)
    if (route.read == PushRead.None) {
        openPushTarget(route.target)
    } else {
        add(NotificationOpenKey(route, nextNonce()))
    }
    return true
}

/**
 * Whether an effect of the screen showing [key] is one of the moments after which the notification explanation is offered
 * (FR-011): a recurring rule or a budget just created, a shared wallet just joined. An edit is not one.
 */
internal fun isPromptMoment(key: NavKey?, effect: Any): Boolean = when (effect) {
    BudgetEditorEffect.Saved -> key is BudgetEditorKey && key.budgetId == null
    is InvitationEffect.OpenWallet -> true
    RuleEditorEffect.Created -> true
    else -> false
}
