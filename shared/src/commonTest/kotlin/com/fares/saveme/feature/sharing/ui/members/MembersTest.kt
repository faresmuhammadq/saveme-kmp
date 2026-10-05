package com.fares.saveme.feature.sharing.ui.members

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.sharing.domain.model.InvitationRefusal
import com.fares.saveme.feature.sharing.domain.model.InvitationRefusalKind
import com.fares.saveme.feature.sharing.domain.model.InvitationView
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.domain.model.InviteNotice
import com.fares.saveme.feature.sharing.domain.model.LeaveCheck
import com.fares.saveme.feature.sharing.domain.model.LeaveResult
import com.fares.saveme.feature.sharing.domain.model.MemberActionResult
import com.fares.saveme.feature.sharing.domain.model.MemberView
import com.fares.saveme.feature.sharing.domain.model.WithdrawResult
import com.fares.saveme.feature.sharing.ui.uiInvitation
import com.fares.saveme.feature.sharing.ui.uiMember
import com.fares.saveme.feature.sharing.ui.uiWalletView
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MembersReducerTest {

    private val owner = uiMember("u1", "Sara", "owner", isYou = true)
    private val editor = uiMember("u2")

    @Test
    fun theWalletSetsNameRoleAndArchivedAndLoadsTheScreen() {
        val state = reduce(MembersState(), MembersChange.Wallet(uiWalletView(WalletRole.Editor, "Home")))
        assertTrue(state.loaded)
        assertEquals("Home", state.walletName)
        assertEquals(WalletRole.Editor, state.myRole)
        assertEquals(state, reduce(state, MembersChange.Wallet(null)), "a missing wallet changes nothing")
    }

    @Test
    fun onlyAnOwnerOfAnActiveWalletCanInviteAndTheGateDecidesIfItIsEnabled() {
        val owned = reduce(MembersState(), MembersChange.Wallet(uiWalletView()))
        assertTrue(owned.canInvite && owned.inviteEnabled)
        assertFalse(reduce(owned, MembersChange.Check(InviteCheck.Full(2))).inviteEnabled)
        assertTrue(reduce(owned, MembersChange.Check(InviteCheck.Full(2))).canInvite)
        assertFalse(reduce(MembersState(), MembersChange.Wallet(uiWalletView(WalletRole.Editor))).canInvite)
        assertFalse(reduce(MembersState(), MembersChange.Wallet(uiWalletView(archived = true))).canInvite)
    }

    @Test
    fun ownerControlsNeverApplyToTheOwnersOwnRowOrToYourself() {
        val state = reduce(reduce(MembersState(), MembersChange.Wallet(uiWalletView())), MembersChange.Members(listOf(owner, editor)))
        assertFalse(state.canManage(owner))
        assertTrue(state.canManage(editor))
        assertTrue(state.hasOtherMembers)
        val asEditor = reduce(MembersState(), MembersChange.Wallet(uiWalletView(WalletRole.Editor)))
        assertFalse(asEditor.canManage(editor))
        assertTrue(asEditor.canLeave)
        assertFalse(state.canLeave)
    }

    @Test
    fun sheetConfirmBusyPlanNoteAndInvitationsAreCarried() {
        var state = MembersState()
        state = reduce(state, MembersChange.Sheet("u2"))
        assertEquals("u2", state.sheetFor)
        state = reduce(state, MembersChange.Confirm(MembersConfirm.Leave(2)))
        assertEquals(MembersConfirm.Leave(2), state.confirm)
        state = reduce(state, MembersChange.Busy(true))
        state = reduce(state, MembersChange.PlanNote(3))
        state = reduce(state, MembersChange.Invitations(listOf(uiInvitation())))
        state = reduce(state, MembersChange.TimeZone("Africa/Cairo"))
        assertTrue(state.busy)
        assertEquals(3, state.planNote)
        assertEquals(1, state.invitations.size)
        assertEquals("Africa/Cairo", state.timeZoneId)
    }
}

class MembersViewModelTest {
    private val wallet = MutableStateFlow<WalletView?>(uiWalletView())
    private val members = MutableStateFlow(listOf(uiMember("u1", "Sara", "owner", isYou = true), uiMember("u2")))
    private val invitations = MutableStateFlow<List<InvitationView>>(emptyList())
    private val check = MutableStateFlow<InviteCheck>(InviteCheck.Allowed)
    private val planNote = MutableStateFlow<Int?>(null)
    private val notices = MutableSharedFlow<InviteNotice>(extraBufferCapacity = 4)
    private val calls = mutableListOf<String>()
    private var roleAnswer: MemberActionResult = MemberActionResult.Done
    private var removeAnswer: MemberActionResult = MemberActionResult.Done
    private var leaveCheckAnswer: LeaveCheck = LeaveCheck.Ready(0)
    private var leaveAnswer: LeaveResult = LeaveResult.Left("Home")
    private var shareAnswer: String? = "TEXT-WITH-LINK"

    private fun vm() = MembersViewModel(
        walletId = "w1", wallet = wallet, members = members, invitations = invitations, inviteCheck = check, planNote = planNote,
        changeRole = { user, role -> calls += "role:$user:${role.wire}"; roleAnswer },
        remove = { user -> calls += "remove:$user"; removeAnswer },
        withdraw = { id -> calls += "withdraw:$id"; WithdrawResult.Withdrawn },
        shareText = { shareAnswer },
        discard = { calls += "discard:$it" },
        tryAgain = { calls += "again:$it" },
        leaveCheck = { calls += "leaveCheck"; leaveCheckAnswer },
        leave = { calls += "leave"; leaveAnswer },
        inviteNotices = notices,
    )

    @Test
    fun theOwnerViewShowsRowsInvitationsAndAnEnabledInvite() = runMviTest {
        invitations.value = listOf(uiInvitation(waiting = true), uiInvitation("i2", refusal = InvitationRefusal(InvitationRefusalKind.PlanFull, null)), uiInvitation("i3", expired = true))
        val vm = vm()
        val state = vm.state.value
        assertEquals(listOf("u1", "u2"), state.members.map { it.userId })
        assertEquals(3, state.invitations.size)
        assertTrue(state.isOwner && state.inviteEnabled)
        assertTrue(state.invitations[0].waiting)
        assertEquals(InvitationRefusalKind.PlanFull, state.invitations[1].refusal?.kind)
        assertTrue(state.invitations[2].expired)
    }

    @Test
    fun anEditorOrViewerSeesNoOwnerControls() = runMviTest {
        wallet.value = uiWalletView(WalletRole.Viewer)
        val vm = vm()
        assertFalse(vm.state.value.canInvite)
        vm.effects.test {
            vm.onIntent(MembersIntent.InviteClicked)
            vm.onIntent(MembersIntent.MemberClicked("u2"))
            vm.onIntent(MembersIntent.ReplaceLinkClicked("i1"))
            expectNoEvents()
        }
        assertNull(vm.state.value.sheetFor)
        assertTrue(vm.state.value.canLeave)
    }

    @Test
    fun inviteOpensTheInviteScreenWhenAllowedAndExplainsWhenNot() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(MembersIntent.InviteClicked)
            assertEquals(MembersEffect.OpenInvite("w1"), awaitItem())
            check.value = InviteCheck.Full(2)
            vm.onIntent(MembersIntent.InviteClicked)
            check.value = InviteCheck.NotVerified
            vm.onIntent(MembersIntent.InviteClicked)
            assertEquals(MembersEffect.ShowVerifyPrompt, awaitItem())
            check.value = InviteCheck.SharingNotIncluded
            vm.onIntent(MembersIntent.InviteClicked)
            expectNoEvents()
        }
    }

    @Test
    fun seeYourPlanOnlyOpensThePlanScreen() = runMviTest {
        check.value = InviteCheck.SharingNotIncluded
        val vm = vm()
        vm.effects.test {
            vm.onIntent(MembersIntent.SeePlanClicked)
            assertEquals(MembersEffect.OpenPlan, awaitItem())
            expectNoEvents()
        }
        assertTrue(calls.isEmpty(), "no call is made on the way to the plan screen")
    }

    @Test
    fun theMemberSheetChangesTheRoleAndWordsEveryAnswer() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(MembersIntent.MemberClicked("u2"))
            assertEquals("u2", vm.state.value.sheetFor)
            vm.onIntent(MembersIntent.ChangeRoleClicked("u2", WalletRole.Viewer))
            assertNull(vm.state.value.sheetFor)
            assertFalse(vm.state.value.busy)
            assertEquals(listOf("role:u2:viewer"), calls)
            val cases = listOf(
                MemberActionResult.Offline to MembersMessage.Offline,
                MemberActionResult.Changed to MembersMessage.Changed,
                MemberActionResult.OnlyOwner to MembersMessage.OnlyOwner,
                MemberActionResult.Gone to MembersMessage.Gone,
                MemberActionResult.OwnerRole to MembersMessage.OwnerRole,
                MemberActionResult.NotEntitled("sharing.enabled") to MembersMessage.PlanSharing,
                MemberActionResult.NotEntitled("sharing.max_members_per_wallet") to MembersMessage.PlanFull,
                MemberActionResult.NotEntitled(null) to MembersMessage.PlanOther,
                MemberActionResult.Failed("r1") to MembersMessage.Failed("r1"),
            )
            for ((result, message) in cases) {
                roleAnswer = result
                vm.onIntent(MembersIntent.ChangeRoleClicked("u2", WalletRole.Editor))
                assertEquals(MembersEffect.ShowMessage(message), awaitItem())
            }
            roleAnswer = MemberActionResult.NotVerified
            vm.onIntent(MembersIntent.ChangeRoleClicked("u2", WalletRole.Editor))
            assertEquals(MembersEffect.ShowVerifyPrompt, awaitItem())
        }
    }

    @Test
    fun removingAsksFirstThenCallsOnce() = runMviTest {
        val vm = vm()
        vm.onIntent(MembersIntent.RemoveClicked("u2"))
        assertEquals(MembersConfirm.Remove("u2", "Karim"), vm.state.value.confirm)
        assertTrue(calls.isEmpty())
        vm.onIntent(MembersIntent.ConfirmDismissed)
        assertNull(vm.state.value.confirm)
        vm.onIntent(MembersIntent.RemoveClicked("u2"))
        vm.onIntent(MembersIntent.ConfirmClicked)
        assertEquals(listOf("remove:u2"), calls)
        assertNull(vm.state.value.confirm)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun withdrawingAsksFirst() = runMviTest {
        invitations.value = listOf(uiInvitation())
        val vm = vm()
        vm.onIntent(MembersIntent.WithdrawClicked("i1"))
        assertEquals(MembersConfirm.Withdraw("i1"), vm.state.value.confirm)
        vm.onIntent(MembersIntent.ConfirmClicked)
        assertEquals(listOf("withdraw:i1"), calls)
        assertNull(vm.state.value.confirm)
    }

    @Test
    fun shareAndCopyCarryTheTextInEffectsThatNeverPrintIt() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(MembersIntent.ShareClicked("i1"))
            val share = awaitItem()
            assertEquals(MembersEffect.Share("TEXT-WITH-LINK"), share)
            assertFalse(share.toString().contains("TEXT-WITH-LINK"))
            vm.onIntent(MembersIntent.CopyClicked("i1"))
            val copy = awaitItem()
            assertEquals(MembersEffect.Copy("TEXT-WITH-LINK"), copy)
            assertFalse(copy.toString().contains("TEXT-WITH-LINK"))
            assertEquals(MembersEffect.ShowMessage(MembersMessage.Copied), awaitItem())
            shareAnswer = null
            vm.onIntent(MembersIntent.ShareClicked("i1"))
            expectNoEvents()
        }
    }

    @Test
    fun aLostLinkIsReplacedThroughTheInviteScreenAndRefusedOnesCanBeDiscardedOrRetried() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(MembersIntent.ReplaceLinkClicked("i1"))
            assertEquals(MembersEffect.OpenInvite("w1", replacing = "i1"), awaitItem())
        }
        vm.onIntent(MembersIntent.DiscardClicked("i2"))
        vm.onIntent(MembersIntent.TryAgainClicked("i3"))
        assertEquals(listOf("discard:i2", "again:i3"), calls)
    }

    @Test
    fun leavingChecksThenAsksThenLeavesAndTheWalletVanishingDoesNotCloseTwice() = runMviTest {
        wallet.value = uiWalletView(WalletRole.Editor)
        leaveCheckAnswer = LeaveCheck.Ready(2)
        val vm = vm()
        vm.effects.test {
            vm.onIntent(MembersIntent.LeaveClicked)
            assertEquals(MembersConfirm.Leave(2), vm.state.value.confirm)
            vm.onIntent(MembersIntent.ConfirmClicked)
            assertEquals(MembersEffect.Left("Home"), awaitItem())
            wallet.value = null
            expectNoEvents()
        }
        assertEquals(listOf("leaveCheck", "leave"), calls)
    }

    @Test
    fun leavingOfflineOrAsOwnerNeverAsksAndAFailedLeaveKeepsTheScreen() = runMviTest {
        wallet.value = uiWalletView(WalletRole.Editor)
        val vm = vm()
        vm.effects.test {
            leaveCheckAnswer = LeaveCheck.Offline
            vm.onIntent(MembersIntent.LeaveClicked)
            assertEquals(MembersEffect.ShowMessage(MembersMessage.Offline), awaitItem())
            assertNull(vm.state.value.confirm)
            leaveCheckAnswer = LeaveCheck.Ready(0)
            leaveAnswer = LeaveResult.Changed
            vm.onIntent(MembersIntent.LeaveClicked)
            vm.onIntent(MembersIntent.ConfirmClicked)
            assertEquals(MembersEffect.ShowMessage(MembersMessage.Changed), awaitItem())
            wallet.value = null
            assertEquals(MembersEffect.NavigateBack, awaitItem())
        }
    }

    @Test
    fun aWalletThatVanishesClosesTheScreenQuietly() = runMviTest {
        val vm = vm()
        vm.effects.test {
            wallet.value = null
            assertEquals(MembersEffect.NavigateBack, awaitItem())
        }
    }

    @Test
    fun theInviteScreensNoticeBecomesAMessage() = runMviTest {
        val vm = vm()
        vm.effects.test {
            notices.tryEmit(InviteNotice.Sent)
            assertEquals(MembersEffect.ShowMessage(MembersMessage.InviteSent), awaitItem())
            notices.tryEmit(InviteNotice.SavedOffline)
            assertEquals(MembersEffect.ShowMessage(MembersMessage.InviteSaved), awaitItem())
        }
    }

    @Test
    fun noEffectOrIntentOfferingAPurchaseExists() {
        val names = listOf(
            MembersEffect.OpenPlan, MembersEffect.ShowVerifyPrompt, MembersEffect.NavigateBack, MembersEffect.OpenInvite("w"),
            MembersEffect.Left("x"), MembersEffect.Share("s"), MembersEffect.Copy("c"),
        ).map { it::class.simpleName.orEmpty().lowercase() }
        assertTrue(names.none { "purchase" in it || "buy" in it || "checkout" in it || "subscribe" in it || "upgrade" in it })
    }

    @Suppress("unused")
    private val unusedMember: MemberView = uiMember()
}
