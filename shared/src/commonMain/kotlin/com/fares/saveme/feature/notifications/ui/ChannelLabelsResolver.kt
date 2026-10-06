package com.fares.saveme.feature.notifications.ui

import com.fares.saveme.feature.notifications.domain.ChannelLabel
import com.fares.saveme.feature.notifications.domain.ChannelLabels
import com.fares.saveme.feature.notifications.domain.NotificationCategory
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.notifications_category_billing
import saveme.shared.generated.resources.notifications_category_billing_desc
import saveme.shared.generated.resources.notifications_category_budget_exceeded
import saveme.shared.generated.resources.notifications_category_budget_exceeded_desc
import saveme.shared.generated.resources.notifications_category_deletion_reminder
import saveme.shared.generated.resources.notifications_category_deletion_reminder_desc
import saveme.shared.generated.resources.notifications_category_export_ready
import saveme.shared.generated.resources.notifications_category_export_ready_desc
import saveme.shared.generated.resources.notifications_category_recurring_due
import saveme.shared.generated.resources.notifications_category_recurring_due_desc
import saveme.shared.generated.resources.notifications_category_sharing
import saveme.shared.generated.resources.notifications_category_sharing_desc
import saveme.shared.generated.resources.push_public_text

fun NotificationCategory.titleRes(): StringResource = when (this) {
    NotificationCategory.RecurringDue -> Res.string.notifications_category_recurring_due
    NotificationCategory.BudgetExceeded -> Res.string.notifications_category_budget_exceeded
    NotificationCategory.Sharing -> Res.string.notifications_category_sharing
    NotificationCategory.Billing -> Res.string.notifications_category_billing
    NotificationCategory.ExportReady -> Res.string.notifications_category_export_ready
    NotificationCategory.DeletionReminder -> Res.string.notifications_category_deletion_reminder
}

fun NotificationCategory.descriptionRes(): StringResource = when (this) {
    NotificationCategory.RecurringDue -> Res.string.notifications_category_recurring_due_desc
    NotificationCategory.BudgetExceeded -> Res.string.notifications_category_budget_exceeded_desc
    NotificationCategory.Sharing -> Res.string.notifications_category_sharing_desc
    NotificationCategory.Billing -> Res.string.notifications_category_billing_desc
    NotificationCategory.ExportReady -> Res.string.notifications_category_export_ready_desc
    NotificationCategory.DeletionReminder -> Res.string.notifications_category_deletion_reminder_desc
}

/** The six channel names and descriptions and the lock-screen text in the language the resources resolve now (R-08). */
suspend fun resolveChannelLabels(): ChannelLabels = ChannelLabels(
    channels = NotificationCategory.entries.associateWith { ChannelLabel(getString(it.titleRes()), getString(it.descriptionRes())) },
    publicText = getString(Res.string.push_public_text),
)
