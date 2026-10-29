package com.fares.saveme.feature.notifications.ui.open

import com.fares.saveme.feature.notifications.domain.NotificationCategory
import com.fares.saveme.feature.notifications.domain.PushTarget

/** The "Opening…" screen while a tap's read runs (FR-032). */
data class NotificationOpenState(val waiting: Boolean = false, val category: NotificationCategory? = null)

sealed interface NotificationOpenIntent {
    data object Started : NotificationOpenIntent
    data object Cancel : NotificationOpenIntent
}

sealed interface NotificationOpenEffect {
    /** The nav host swaps this screen for the target's. */
    data class Replace(val target: PushTarget) : NotificationOpenEffect
    data object Close : NotificationOpenEffect
}

sealed interface NotificationOpenChange {
    data object Waiting : NotificationOpenChange
    data object Finished : NotificationOpenChange
}

fun reduce(state: NotificationOpenState, change: NotificationOpenChange): NotificationOpenState = when (change) {
    NotificationOpenChange.Waiting -> state.copy(waiting = true)
    NotificationOpenChange.Finished -> state.copy(waiting = false)
}

/** The category of notification a tap on [this] screen belongs to; null for screens no category sends to. */
fun PushTarget.category(): NotificationCategory? = when (this) {
    is PushTarget.Occurrence, PushTarget.RecurringAll -> NotificationCategory.RecurringDue
    is PushTarget.Budget, PushTarget.Budgets -> NotificationCategory.BudgetExceeded
    PushTarget.InvitationNotice, is PushTarget.Wallet, PushTarget.Wallets -> NotificationCategory.Sharing
    PushTarget.Plan -> NotificationCategory.Billing
    is PushTarget.Export, PushTarget.Exports -> NotificationCategory.ExportReady
    PushTarget.AccountDeletion -> NotificationCategory.DeletionReminder
    PushTarget.Home -> null
}
