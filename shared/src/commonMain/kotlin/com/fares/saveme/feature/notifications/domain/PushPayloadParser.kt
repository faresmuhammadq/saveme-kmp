package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.feature.notifications.domain.PushMessage.Malformed
import com.fares.saveme.feature.notifications.domain.PushMessage.Unknown

/**
 * Pure parsing of the `PushPayload` data block (contracts/push-domain.md §2). Input is untrusted (FR-044): unknown keys are
 * ignored (P-02.3), ids must be UUID text, and nothing here throws.
 */
object PushPayloadParser {
    private val uuid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun parse(data: Map<String, String>): PushMessage {
        val type = data["type"]?.takeIf { it.isNotBlank() }
        val notificationId = data["notification_id"]?.takeIf { it.isNotBlank() }
        val named = NotificationCategory.fromWire(data["category"])
        if (data["schema"] != "1" || type == null || notificationId == null) return Unknown(notificationId, named)

        fun id(key: String): String? = data[key]?.takeIf { uuid.matches(it) }
        fun category(default: NotificationCategory) = named ?: default
        fun malformed() = Malformed(type, notificationId, named ?: defaultCategory(type))

        return when (type) {
            "occurrence_due" -> {
                val wallet = id("wallet_id")
                val occurrence = id("occurrence_id")
                if (wallet == null || occurrence == null) malformed()
                else PushMessage.OccurrenceDue(notificationId, category(NotificationCategory.RecurringDue), wallet, occurrence)
            }
            "occurrence_auto_confirm_refused" -> {
                val wallet = id("wallet_id")
                val occurrence = id("occurrence_id")
                if (wallet == null || occurrence == null) malformed()
                else PushMessage.OccurrenceAutoConfirmRefused(notificationId, category(NotificationCategory.RecurringDue), wallet, occurrence)
            }
            "occurrences_due_summary" -> PushMessage.OccurrencesDueSummary(notificationId, category(NotificationCategory.RecurringDue))
            "budget_exceeded" -> id("budget_id")?.let { PushMessage.BudgetExceeded(notificationId, category(NotificationCategory.BudgetExceeded), it) }
                ?: malformed()
            "invitation_received" -> PushMessage.InvitationReceived(notificationId, category(NotificationCategory.Sharing), id("invitation_id"))
            "member_role_changed" -> id("wallet_id")?.let {
                PushMessage.MemberRoleChanged(notificationId, category(NotificationCategory.Sharing), it, data["role"])
            } ?: malformed()
            "member_removed" -> PushMessage.MemberRemoved(notificationId, category(NotificationCategory.Sharing), id("wallet_id"))
            "wallet_ownership_received" -> id("wallet_id")?.let {
                PushMessage.WalletOwnershipReceived(notificationId, category(NotificationCategory.Sharing), it)
            } ?: malformed()
            "subscription_ending" -> PushMessage.SubscriptionEnding(notificationId, category(NotificationCategory.Billing))
            "subscription_lapsed" -> PushMessage.SubscriptionLapsed(notificationId, category(NotificationCategory.Billing))
            "export_ready" -> id("export_id")?.let { PushMessage.ExportReady(notificationId, category(NotificationCategory.ExportReady), it) }
                ?: malformed()
            "deletion_reminder" -> PushMessage.DeletionReminder(notificationId, category(NotificationCategory.DeletionReminder))
            else -> Unknown(notificationId, named)
        }
    }

    private fun defaultCategory(type: String): NotificationCategory? = when {
        type.startsWith("occurrence") -> NotificationCategory.RecurringDue
        type == "budget_exceeded" -> NotificationCategory.BudgetExceeded
        type == "export_ready" -> NotificationCategory.ExportReady
        type.startsWith("member_") || type == "wallet_ownership_received" || type == "invitation_received" -> NotificationCategory.Sharing
        else -> null
    }
}
