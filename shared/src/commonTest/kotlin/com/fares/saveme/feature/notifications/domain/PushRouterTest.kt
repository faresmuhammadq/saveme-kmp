package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.feature.notifications.BUDGET
import com.fares.saveme.feature.notifications.EXPORT
import com.fares.saveme.feature.notifications.INVITATION
import com.fares.saveme.feature.notifications.NOTIFICATION
import com.fares.saveme.feature.notifications.OCCURRENCE
import com.fares.saveme.feature.notifications.WALLET
import kotlin.test.Test
import kotlin.test.assertEquals

/** contracts/push-domain.md §2 (collapse keys) and §3 (read and target), including every fallback. */
class PushRouterTest {

    private fun parse(type: String, vararg extra: Pair<String, String>) =
        PushPayloadParser.parse(mapOf("schema" to "1", "type" to type, "notification_id" to NOTIFICATION) + extra)

    @Test
    fun collapseKeysFollowTheContractTable() {
        assertEquals("occurrence:$OCCURRENCE", parse("occurrence_due", "wallet_id" to WALLET, "occurrence_id" to OCCURRENCE).collapseKey)
        assertEquals("occurrence:$OCCURRENCE", parse("occurrence_auto_confirm_refused", "wallet_id" to WALLET, "occurrence_id" to OCCURRENCE).collapseKey)
        assertEquals("occurrences_due", parse("occurrences_due_summary").collapseKey)
        assertEquals("budget:$BUDGET", parse("budget_exceeded", "budget_id" to BUDGET).collapseKey)
        assertEquals("invitation:$INVITATION", parse("invitation_received", "invitation_id" to INVITATION).collapseKey)
        assertEquals("wallet:$WALLET", parse("member_role_changed", "wallet_id" to WALLET).collapseKey)
        assertEquals("wallet:$WALLET", parse("member_removed", "wallet_id" to WALLET).collapseKey)
        assertEquals("wallet:$WALLET", parse("wallet_ownership_received", "wallet_id" to WALLET).collapseKey)
        assertEquals("subscription", parse("subscription_ending").collapseKey)
        assertEquals("subscription", parse("subscription_lapsed").collapseKey)
        assertEquals("export:$EXPORT", parse("export_ready", "export_id" to EXPORT).collapseKey)
        assertEquals("deletion", parse("deletion_reminder").collapseKey)
        assertEquals("other:$NOTIFICATION", parse("future_type").collapseKey)
        assertEquals("other:$NOTIFICATION", parse("budget_exceeded").collapseKey)
    }

    private fun route(type: String, vararg extra: Pair<String, String>) = PushRouter.route(parse(type, *extra))

    @Test
    fun everyValidRowOpensItsScreenAfterItsRead() {
        assertEquals(PushRoute(PushRead.Wallet(WALLET), PushTarget.Occurrence(OCCURRENCE)), route("occurrence_due", "wallet_id" to WALLET, "occurrence_id" to OCCURRENCE))
        assertEquals(PushRoute(PushRead.Wallet(WALLET), PushTarget.Occurrence(OCCURRENCE)), route("occurrence_auto_confirm_refused", "wallet_id" to WALLET, "occurrence_id" to OCCURRENCE))
        assertEquals(PushRoute(PushRead.Everything, PushTarget.RecurringAll), route("occurrences_due_summary"))
        assertEquals(PushRoute(PushRead.User, PushTarget.Budget(BUDGET)), route("budget_exceeded", "budget_id" to BUDGET))
        assertEquals(PushRoute(PushRead.None, PushTarget.InvitationNotice), route("invitation_received", "invitation_id" to INVITATION))
        assertEquals(PushRoute(PushRead.None, PushTarget.InvitationNotice), route("invitation_received"))
        assertEquals(PushRoute(PushRead.Wallet(WALLET), PushTarget.Wallet(WALLET)), route("member_role_changed", "wallet_id" to WALLET))
        assertEquals(PushRoute(PushRead.Wallet(WALLET), PushTarget.Wallet(WALLET)), route("wallet_ownership_received", "wallet_id" to WALLET))
        assertEquals(PushRoute(PushRead.Everything, PushTarget.Wallets), route("member_removed", "wallet_id" to WALLET))
        assertEquals(PushRoute(PushRead.Everything, PushTarget.Wallets), route("member_removed"))
        assertEquals(PushRoute(PushRead.Entitlements, PushTarget.Plan), route("subscription_ending"))
        assertEquals(PushRoute(PushRead.Entitlements, PushTarget.Plan), route("subscription_lapsed"))
        assertEquals(PushRoute(PushRead.None, PushTarget.Export(EXPORT)), route("export_ready", "export_id" to EXPORT))
        assertEquals(PushRoute(PushRead.Account, PushTarget.AccountDeletion), route("deletion_reminder"))
    }

    @Test
    fun malformedKnownTypesFallBackToTheirListScreens() {
        assertEquals(PushRoute(PushRead.None, PushTarget.RecurringAll), route("occurrence_due", "wallet_id" to WALLET))
        assertEquals(PushRoute(PushRead.None, PushTarget.RecurringAll), route("occurrence_auto_confirm_refused"))
        assertEquals(PushRoute(PushRead.None, PushTarget.Budgets), route("budget_exceeded"))
        assertEquals(PushRoute(PushRead.None, PushTarget.Wallets), route("member_role_changed"))
        assertEquals(PushRoute(PushRead.None, PushTarget.Wallets), route("wallet_ownership_received", "wallet_id" to "x"))
        assertEquals(PushRoute(PushRead.None, PushTarget.Exports), route("export_ready"))
    }

    @Test
    fun unknownOpensHomeWithNoRead() {
        assertEquals(PushRoute(PushRead.None, PushTarget.Home), route("future_type"))
        assertEquals(PushRoute(PushRead.None, PushTarget.Home), PushRouter.route(PushPayloadParser.parse(emptyMap())))
    }

    @Test
    fun aRouteAndATargetPrintTheTypeOnly() {
        val r = route("budget_exceeded", "budget_id" to BUDGET)
        assertEquals("PushRoute(budget)", r.toString())
    }
}
