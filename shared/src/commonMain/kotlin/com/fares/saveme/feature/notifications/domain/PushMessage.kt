package com.fares.saveme.feature.notifications.domain

/** The data keys a tap or a posted notification may carry (contract PushPayload); anything else is dropped (FR-044). */
object PushTapKeys {
    val all: Set<String> = setOf(
        "schema", "type", "category", "notification_id", "wallet_id", "occurrence_id", "budget_id", "invitation_id", "export_id", "role",
    )

    fun filter(data: Map<String, String>): Map<String, String> = data.filterKeys { it in all }
}

/**
 * One received or tapped message, parsed from untrusted data (FR-030, FR-044). [typeName] and printing show the type
 * only: never an id, a title or a text (P-09.2).
 */
sealed class PushMessage(val typeName: String) {
    abstract val notificationId: String?

    /** The category named by the data, or the type's own; null for an unknown type that names none. */
    abstract val category: NotificationCategory?

    /** The tag a posted notification gets, so a newer message about the same subject replaces it (contract table). */
    abstract val collapseKey: String

    final override fun toString() = "PushMessage($typeName)"

    data class OccurrenceDue(
        override val notificationId: String?, override val category: NotificationCategory?,
        val walletId: String, val occurrenceId: String,
    ) : PushMessage("occurrence_due") {
        override val collapseKey get() = "occurrence:$occurrenceId"
    }

    data class OccurrenceAutoConfirmRefused(
        override val notificationId: String?, override val category: NotificationCategory?,
        val walletId: String, val occurrenceId: String,
    ) : PushMessage("occurrence_auto_confirm_refused") {
        override val collapseKey get() = "occurrence:$occurrenceId"
    }

    data class OccurrencesDueSummary(
        override val notificationId: String?, override val category: NotificationCategory?,
    ) : PushMessage("occurrences_due_summary") {
        override val collapseKey get() = "occurrences_due"
    }

    data class BudgetExceeded(
        override val notificationId: String?, override val category: NotificationCategory?, val budgetId: String,
    ) : PushMessage("budget_exceeded") {
        override val collapseKey get() = "budget:$budgetId"
    }

    data class InvitationReceived(
        override val notificationId: String?, override val category: NotificationCategory?, val invitationId: String?,
    ) : PushMessage("invitation_received") {
        override val collapseKey get() = "invitation:${invitationId ?: notificationId}"
    }

    data class MemberRoleChanged(
        override val notificationId: String?, override val category: NotificationCategory?,
        val walletId: String, val role: String?,
    ) : PushMessage("member_role_changed") {
        override val collapseKey get() = "wallet:$walletId"
    }

    data class MemberRemoved(
        override val notificationId: String?, override val category: NotificationCategory?, val walletId: String?,
    ) : PushMessage("member_removed") {
        override val collapseKey get() = "wallet:${walletId ?: notificationId}"
    }

    data class WalletOwnershipReceived(
        override val notificationId: String?, override val category: NotificationCategory?, val walletId: String,
    ) : PushMessage("wallet_ownership_received") {
        override val collapseKey get() = "wallet:$walletId"
    }

    data class SubscriptionEnding(
        override val notificationId: String?, override val category: NotificationCategory?,
    ) : PushMessage("subscription_ending") {
        override val collapseKey get() = "subscription"
    }

    data class SubscriptionLapsed(
        override val notificationId: String?, override val category: NotificationCategory?,
    ) : PushMessage("subscription_lapsed") {
        override val collapseKey get() = "subscription"
    }

    data class ExportReady(
        override val notificationId: String?, override val category: NotificationCategory?, val exportId: String,
    ) : PushMessage("export_ready") {
        override val collapseKey get() = "export:$exportId"
    }

    data class DeletionReminder(
        override val notificationId: String?, override val category: NotificationCategory?,
    ) : PushMessage("deletion_reminder") {
        override val collapseKey get() = "deletion"
    }

    /** A known type whose needed identifier is missing or not a UUID: opens the type's list screen (FR-030). */
    data class Malformed(
        val type: String, override val notificationId: String?, override val category: NotificationCategory?,
    ) : PushMessage("malformed:$type") {
        override val collapseKey get() = "other:${notificationId ?: "none"}"
    }

    /** An unknown type, `schema` other than "1", or a missing `type` or `notification_id`: shown as received, opens Home. */
    data class Unknown(
        override val notificationId: String?, override val category: NotificationCategory?,
    ) : PushMessage("unknown") {
        override val collapseKey get() = "other:${notificationId ?: "none"}"
    }
}
