package com.fares.saveme.navigation

import androidx.navigation3.runtime.NavKey
import com.fares.saveme.feature.budgets.ui.editor.BudgetEditorEffect
import com.fares.saveme.feature.notifications.domain.PushTarget
import com.fares.saveme.feature.recurring.ui.editor.RuleEditorEffect
import com.fares.saveme.feature.sharing.ui.invitation.InvitationEffect

/**
 * The screen a tapped notification opens (spec 017 FR-031). Exports map to the account screen until spec 016's export
 * screens are merged (tasks note): the tap still only navigates.
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
    is PushTarget.Export -> AccountKey
    PushTarget.Exports -> AccountKey
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
 * Whether an effect of the screen showing [key] is one of the moments after which the notification explanation is offered
 * (FR-011): a recurring rule or a budget just created, a shared wallet just joined. An edit is not one.
 */
internal fun isPromptMoment(key: NavKey?, effect: Any): Boolean = when (effect) {
    BudgetEditorEffect.Saved -> key is BudgetEditorKey && key.budgetId == null
    is InvitationEffect.OpenWallet -> true
    RuleEditorEffect.Created -> true
    else -> false
}
