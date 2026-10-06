package com.fares.saveme.feature.notifications.domain

/** The routing table of FR-031 / contracts/push-domain.md §3. Pure; a tap only navigates (FR-029). */
object PushRouter {
    fun route(message: PushMessage): PushRoute = when (message) {
        is PushMessage.OccurrenceDue -> PushRoute(PushRead.Wallet(message.walletId), PushTarget.Occurrence(message.occurrenceId))
        is PushMessage.OccurrenceAutoConfirmRefused -> PushRoute(PushRead.Wallet(message.walletId), PushTarget.Occurrence(message.occurrenceId))
        is PushMessage.OccurrencesDueSummary -> PushRoute(PushRead.Everything, PushTarget.RecurringAll)
        is PushMessage.BudgetExceeded -> PushRoute(PushRead.User, PushTarget.Budget(message.budgetId))
        is PushMessage.InvitationReceived -> PushRoute(PushRead.None, PushTarget.InvitationNotice)
        is PushMessage.MemberRoleChanged -> PushRoute(PushRead.Wallet(message.walletId), PushTarget.Wallet(message.walletId))
        is PushMessage.WalletOwnershipReceived -> PushRoute(PushRead.Wallet(message.walletId), PushTarget.Wallet(message.walletId))
        is PushMessage.MemberRemoved -> PushRoute(PushRead.Everything, PushTarget.Wallets)
        is PushMessage.SubscriptionEnding, is PushMessage.SubscriptionLapsed -> PushRoute(PushRead.Entitlements, PushTarget.Plan)
        is PushMessage.ExportReady -> PushRoute(PushRead.None, PushTarget.Export(message.exportId))
        is PushMessage.DeletionReminder -> PushRoute(PushRead.Account, PushTarget.AccountDeletion)
        is PushMessage.Malformed -> PushRoute(PushRead.None, malformedTarget(message.type))
        is PushMessage.Unknown -> PushRoute(PushRead.None, PushTarget.Home)
    }

    private fun malformedTarget(type: String): PushTarget = when (type) {
        "occurrence_due", "occurrence_auto_confirm_refused" -> PushTarget.RecurringAll
        "budget_exceeded" -> PushTarget.Budgets
        "member_role_changed", "wallet_ownership_received" -> PushTarget.Wallets
        "export_ready" -> PushTarget.Exports
        else -> PushTarget.Home
    }
}
