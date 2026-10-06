package com.fares.saveme.navigation

import androidx.navigation3.runtime.NavKey
import com.fares.saveme.feature.budgets.ui.editor.BudgetEditorEffect
import com.fares.saveme.feature.notifications.domain.PushTarget
import com.fares.saveme.feature.recurring.ui.editor.RuleEditorEffect
import com.fares.saveme.feature.sharing.ui.invitation.InvitationEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ID = "0192f7a0-0000-7000-8000-0000000000a1"

/** The tap's last mile (spec 017 FR-031, FR-033): which key each target opens, how it goes on the stack, the tap bus, the prompt moments. */
class PushTapRoutingTest {

    @Test
    fun everyTargetMapsToItsKey() {
        assertEquals(OccurrenceKey(ID, confirm = false), PushTarget.Occurrence(ID).toNavKey())
        assertEquals(RecurringKey(null), PushTarget.RecurringAll.toNavKey())
        assertEquals(BudgetEditorKey(ID), PushTarget.Budget(ID).toNavKey())
        assertEquals(BudgetsKey, PushTarget.Budgets.toNavKey())
        assertEquals(InvitationNoticeKey, PushTarget.InvitationNotice.toNavKey())
        assertEquals(WalletDetailKey(ID), PushTarget.Wallet(ID).toNavKey())
        assertEquals(WalletsKey, PushTarget.Wallets.toNavKey())
        assertEquals(PlanKey, PushTarget.Plan.toNavKey())
        assertEquals(ExportDetailKey(ID), PushTarget.Export(ID).toNavKey())
        assertEquals(ExportsKey, PushTarget.Exports.toNavKey())
        assertEquals(DeleteAccountKey, PushTarget.AccountDeletion.toNavKey())
        assertEquals(HomeKey, PushTarget.Home.toNavKey())
    }

    @Test
    fun aTargetGoesOnTopSoAnEditedScreenStaysUnderneath() {
        val stack = mutableListOf<NavKey>(HomeKey, BudgetEditorKey(null))
        stack.openPushTarget(PushTarget.Wallet(ID))
        assertEquals(listOf(HomeKey, BudgetEditorKey(null), WalletDetailKey(ID)), stack)
    }

    @Test
    fun homeTrimsBackToTheExistingHomeInsteadOfRepeatingIt() {
        val stack = mutableListOf<NavKey>(HomeKey, WalletsKey, WalletDetailKey(ID))
        stack.openPushTarget(PushTarget.Home)
        assertEquals(listOf<NavKey>(HomeKey), stack)
    }

    @Test
    fun theBusReplaysOneTapUntilItIsConsumed() {
        val bus = PushTapBus()
        bus.submit(mapOf("type" to "budget_exceeded"))
        assertEquals(1, bus.taps.replayCache.size)
        bus.consume()
        assertTrue(bus.taps.replayCache.isEmpty())
    }

    @Test
    fun anOpeningKeyPrintsTheTargetTypeOnly() {
        val key = NotificationOpenKey(com.fares.saveme.feature.notifications.domain.PushRoute(
            com.fares.saveme.feature.notifications.domain.PushRead.User, PushTarget.Budget(ID),
        ))
        assertFalse(key.toString().contains(ID))
        assertTrue(key.toString().contains("budget"))
    }

    @Test
    fun theMomentsAreACreatedBudgetARuleCreateAndAJoinedWalletNeverAnEdit() {
        assertTrue(isPromptMoment(BudgetEditorKey(null), BudgetEditorEffect.Saved))
        assertFalse(isPromptMoment(BudgetEditorKey(ID), BudgetEditorEffect.Saved), "an edit save is not a moment")
        assertFalse(isPromptMoment(BudgetEditorKey(null), BudgetEditorEffect.NavigateBack))
        assertTrue(isPromptMoment(RuleEditorKey(), RuleEditorEffect.Created))
        assertFalse(isPromptMoment(RuleEditorKey(), RuleEditorEffect.Close))
        assertTrue(isPromptMoment(InvitationKey("t"), InvitationEffect.OpenWallet(ID)))
        assertFalse(isPromptMoment(InvitationKey("t"), InvitationEffect.NavigateSignIn))
        assertFalse(isPromptMoment(HomeKey, BudgetEditorEffect.Saved))
    }
}
