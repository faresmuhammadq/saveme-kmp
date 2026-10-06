package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.feature.notifications.BUDGET
import com.fares.saveme.feature.notifications.EXPORT
import com.fares.saveme.feature.notifications.INVITATION
import com.fares.saveme.feature.notifications.NOTIFICATION
import com.fares.saveme.feature.notifications.OCCURRENCE
import com.fares.saveme.feature.notifications.WALLET
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** contracts/push-domain.md §2: every type, every refusal, nothing thrown, nothing printed. */
class PushPayloadParserTest {

    private fun data(type: String, vararg extra: Pair<String, String>, category: String? = null) =
        buildMap {
            put("schema", "1")
            put("type", type)
            put("notification_id", NOTIFICATION)
            category?.let { put("category", it) }
            putAll(extra)
        }

    @Test
    fun everyKnownTypeParsesToItsCaseWithTypedIds() {
        assertEquals(
            PushMessage.OccurrenceDue(NOTIFICATION, NotificationCategory.RecurringDue, WALLET, OCCURRENCE),
            PushPayloadParser.parse(data("occurrence_due", "wallet_id" to WALLET, "occurrence_id" to OCCURRENCE, category = "recurring_due")),
        )
        assertIs<PushMessage.OccurrenceAutoConfirmRefused>(
            PushPayloadParser.parse(data("occurrence_auto_confirm_refused", "wallet_id" to WALLET, "occurrence_id" to OCCURRENCE)),
        )
        assertIs<PushMessage.OccurrencesDueSummary>(PushPayloadParser.parse(data("occurrences_due_summary")))
        assertEquals(BUDGET, assertIs<PushMessage.BudgetExceeded>(PushPayloadParser.parse(data("budget_exceeded", "budget_id" to BUDGET))).budgetId)
        assertEquals(INVITATION, assertIs<PushMessage.InvitationReceived>(PushPayloadParser.parse(data("invitation_received", "invitation_id" to INVITATION))).invitationId)
        val role = assertIs<PushMessage.MemberRoleChanged>(PushPayloadParser.parse(data("member_role_changed", "wallet_id" to WALLET, "role" to "editor")))
        assertEquals("editor", role.role)
        assertEquals(WALLET, assertIs<PushMessage.MemberRemoved>(PushPayloadParser.parse(data("member_removed", "wallet_id" to WALLET))).walletId)
        assertIs<PushMessage.WalletOwnershipReceived>(PushPayloadParser.parse(data("wallet_ownership_received", "wallet_id" to WALLET)))
        assertIs<PushMessage.SubscriptionEnding>(PushPayloadParser.parse(data("subscription_ending")))
        assertIs<PushMessage.SubscriptionLapsed>(PushPayloadParser.parse(data("subscription_lapsed")))
        assertEquals(EXPORT, assertIs<PushMessage.ExportReady>(PushPayloadParser.parse(data("export_ready", "export_id" to EXPORT))).exportId)
        assertIs<PushMessage.DeletionReminder>(PushPayloadParser.parse(data("deletion_reminder")))
    }

    @Test
    fun unknownTypeOtherSchemaAndMissingTypeOrIdAreUnknown() {
        assertIs<PushMessage.Unknown>(PushPayloadParser.parse(data("future_type")))
        assertIs<PushMessage.Unknown>(PushPayloadParser.parse(data("budget_exceeded", "budget_id" to BUDGET) + ("schema" to "2")))
        assertIs<PushMessage.Unknown>(PushPayloadParser.parse(data("budget_exceeded", "budget_id" to BUDGET) - "type"))
        assertIs<PushMessage.Unknown>(PushPayloadParser.parse(data("budget_exceeded", "budget_id" to BUDGET) - "notification_id"))
        assertIs<PushMessage.Unknown>(PushPayloadParser.parse(emptyMap()))
    }

    @Test
    fun aKnownTypeWithAMissingOrNonUuidNeededIdIsMalformedNotUnknown() {
        assertEquals("occurrence_due", assertIs<PushMessage.Malformed>(PushPayloadParser.parse(data("occurrence_due", "wallet_id" to WALLET))).type)
        assertIs<PushMessage.Malformed>(PushPayloadParser.parse(data("occurrence_due", "wallet_id" to "not-a-uuid", "occurrence_id" to OCCURRENCE)))
        assertIs<PushMessage.Malformed>(PushPayloadParser.parse(data("budget_exceeded", "budget_id" to "1; DROP TABLE")))
        assertIs<PushMessage.Malformed>(PushPayloadParser.parse(data("member_role_changed")))
        assertIs<PushMessage.Malformed>(PushPayloadParser.parse(data("wallet_ownership_received", "wallet_id" to "")))
        assertIs<PushMessage.Malformed>(PushPayloadParser.parse(data("export_ready")))
    }

    @Test
    fun anOptionalIdThatIsNotAUuidIsDroppedNotFatal() {
        assertNull(assertIs<PushMessage.InvitationReceived>(PushPayloadParser.parse(data("invitation_received", "invitation_id" to "x"))).invitationId)
        assertNull(assertIs<PushMessage.MemberRemoved>(PushPayloadParser.parse(data("member_removed"))).walletId)
    }

    @Test
    fun extraKeysAreIgnoredAndUuidsMayBeUpperCase() {
        val message = PushPayloadParser.parse(data("budget_exceeded", "budget_id" to BUDGET.uppercase(), "future_key" to "x", "google.message_id" to "m"))
        assertEquals(BUDGET.uppercase(), assertIs<PushMessage.BudgetExceeded>(message).budgetId)
    }

    @Test
    fun categoryComesFromTheDataElseTheTypesOwn() {
        assertEquals(NotificationCategory.Sharing, PushPayloadParser.parse(data("member_removed", category = "sharing")).category)
        assertEquals(NotificationCategory.Billing, PushPayloadParser.parse(data("subscription_ending")).category)
        assertEquals(NotificationCategory.RecurringDue, PushPayloadParser.parse(data("occurrences_due_summary")).category)
        assertEquals(NotificationCategory.DeletionReminder, PushPayloadParser.parse(data("deletion_reminder", category = "deletion_reminder")).category)
        // A category that is not one of the six is ignored.
        assertEquals(NotificationCategory.Billing, PushPayloadParser.parse(data("subscription_lapsed", category = "marketing")).category)
        assertNull(PushPayloadParser.parse(data("future_type", category = "marketing")).category)
    }

    @Test
    fun printingShowsTheTypeOnly() {
        val message = PushPayloadParser.parse(data("occurrence_due", "wallet_id" to WALLET, "occurrence_id" to OCCURRENCE))
        assertEquals("PushMessage(occurrence_due)", message.toString())
        assertFalse(message.toString().contains(WALLET) || message.toString().contains(NOTIFICATION))
        assertTrue(PushPayloadParser.parse(data("future_type")).toString().contains("unknown"))
    }

    @Test
    fun tapKeysKeepOnlyTheContractsKeys() {
        val filtered = PushTapKeys.filter(mapOf("type" to "x", "google.message_id" to "m", "evil" to "1", "wallet_id" to WALLET))
        assertEquals(mapOf("type" to "x", "wallet_id" to WALLET), filtered)
    }
}
