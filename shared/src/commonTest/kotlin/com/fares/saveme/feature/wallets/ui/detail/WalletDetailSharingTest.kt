package com.fares.saveme.feature.wallets.ui.detail

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.wallets.domain.model.Member
import com.fares.saveme.feature.wallets.domain.model.SaveResult
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.TransferResult
import com.fares.saveme.feature.wallets.domain.model.Wallet
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

private val me = Member("u1", "Me", WalletRole.Owner, true)
private val other = Member("u2", "Omar", WalletRole.Editor, false)

private fun detailView(role: WalletRole = WalletRole.Owner, pending: WalletDraft? = null, sync: SyncState = SyncState.None) = WalletView(
    Wallet("w1", "cash", "Pocket", "EGP", 0, 0, null, null, null, false, role, 3, Instant.parse("2026-09-01T00:00:00Z")),
    pending, false, sync, 0,
)

/** The wallet detail's sharing additions (spec 013 FR-013, FR-016, FR-021, FR-023, FR-027). */
class WalletDetailSharingTest {
    private val wallet = MutableStateFlow<WalletView?>(detailView())
    private val members = MutableStateFlow(listOf(me))
    private val pending = MutableStateFlow(0)
    private val planLimited = MutableStateFlow(false)
    private val joining = MutableStateFlow(false)
    private val left = MutableStateFlow(false)
    private var refresh: RefreshOutcome = RefreshOutcome.Done
    private var reads = 0

    private fun vm() = WalletDetailViewModel(
        "w1", wallet, members, { reads++; refresh }, { SaveResult.Saved }, { SaveResult.Saved },
        keepMine = {}, useTheirs = {}, tryAgain = {}, discard = {}, transfer = { TransferResult.Done },
        pendingInvitations = pending, planLimited = planLimited, joining = joining, leftByPerson = left,
    )

    @Test
    fun membersAndActivityRowsOpenTheirScreens() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(WalletDetailIntent.MembersClicked)
            assertEquals(WalletDetailEffect.NavigateMembers("w1"), awaitItem())
            vm.onIntent(WalletDetailIntent.ActivityClicked)
            assertEquals(WalletDetailEffect.NavigateActivity("w1"), awaitItem())
        }
    }

    @Test
    fun thePendingCountFollowsTheGlance() = runMviTest {
        val vm = vm()
        assertEquals(0, vm.state.value.pendingInvitations)
        pending.value = 2
        assertEquals(2, vm.state.value.pendingInvitations)
    }

    @Test
    fun transferIsOfferedOnceASecondMemberExistsAndNothingWaitsAndHiddenWithOnlyTheOwner() = runMviTest {
        val vm = vm()
        assertFalse(vm.state.value.canTransfer)
        members.value = listOf(me, other)
        assertTrue(vm.state.value.canTransfer)
        wallet.value = detailView(pending = WalletDraft("cash", "X", "EGP", 0, null, null), sync = SyncState.Waiting)
        assertFalse(vm.state.value.canTransfer)
    }

    @Test
    fun theViewerNoteAppliesToViewersOnlyAndNamesThePlanWhenItWasThePlan() = runMviTest {
        wallet.value = detailView(WalletRole.Viewer)
        val vm = vm()
        assertTrue(vm.state.value.isViewer)
        assertFalse(vm.state.value.planLimited)
        planLimited.value = true
        assertTrue(vm.state.value.planLimited)
        for (role in listOf(WalletRole.Owner, WalletRole.Editor)) {
            wallet.value = detailView(role)
            assertFalse(vm.state.value.isViewer, role.name)
        }
    }

    @Test
    fun aViewerCannotAddATransaction() = runMviTest {
        wallet.value = detailView(WalletRole.Viewer)
        val vm = vm()
        assertFalse(vm.state.value.canAddTransaction)
        vm.effects.test {
            vm.onIntent(WalletDetailIntent.AddTransactionClicked)
            expectNoEvents()
        }
    }

    @Test
    fun whileJoiningAMissingWalletIsNeverGoneAndTheNormalDetailAppearsWhenItLands() = runMviTest {
        wallet.value = null
        joining.value = true
        val vm = vm()
        vm.effects.test {
            assertTrue(vm.state.value.joining)
            expectNoEvents()
            wallet.value = detailView()
            joining.value = false
            assertFalse(vm.state.value.joining)
            assertTrue(vm.state.value.loaded)
            expectNoEvents()
        }
    }

    @Test
    fun aJoinReadThatDidNotCompleteOffersTryAgainWhichReadsAgain() = runMviTest {
        wallet.value = null
        joining.value = true
        refresh = RefreshOutcome.Offline
        val vm = vm()
        assertTrue(vm.state.value.joiningFailed)
        refresh = RefreshOutcome.Done
        vm.onIntent(WalletDetailIntent.JoiningRetryClicked)
        assertFalse(vm.state.value.joiningFailed)
        assertEquals(2, reads)
    }

    @Test
    fun aWalletThatVanishesWithoutALeaveSaysAccessLostAndAfterALeaveSaysNothing() = runMviTest {
        val vm = vm()
        vm.effects.test {
            wallet.value = null
            assertEquals(WalletDetailEffect.ShowMessage(DetailMessage.Gone), awaitItem())
            assertEquals(WalletDetailEffect.NavigateBack, awaitItem())
        }
        wallet.value = detailView()
        left.value = true
        val afterLeave = vm()
        afterLeave.effects.test {
            wallet.value = null
            expectNoEvents()
        }
    }
}
