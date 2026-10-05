package com.fares.saveme.feature.wallets.ui.detail

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.wallets.domain.model.Member
import com.fares.saveme.feature.wallets.domain.model.RefusalKind
import com.fares.saveme.feature.wallets.domain.model.RefusalReason
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val owner = Member("u1", "Me", WalletRole.Owner, true)
private val editor = Member("u2", "Omar", WalletRole.Editor, false)

private fun wallet(role: WalletRole = WalletRole.Owner, archivedAt: Instant? = null, name: String = "Pocket") = Wallet(
    "w1", "cash", name, "EGP", 25_000, 25_000, "cash", "#2C7A5B", archivedAt, false, role, 3, Instant.parse("2026-09-01T00:00:00Z"),
)

private fun view(
    role: WalletRole = WalletRole.Owner, sync: SyncState = SyncState.None, pending: WalletDraft? = null,
    pendingDelete: Boolean = false, archivedAt: Instant? = null,
) = WalletView(wallet(role, archivedAt), pending, pendingDelete, sync, 25_000)

class WalletDetailReducerTest {
    @Test
    fun ownerSeesTheActionsEditorAndViewerDoNot() {
        val owned = reduce(WalletDetailState(), WalletDetailChange.Stored(view()))
        assertTrue(owned.canEdit)
        assertFalse(reduce(owned, WalletDetailChange.Stored(view(WalletRole.Editor))).canEdit)
        assertFalse(reduce(owned, WalletDetailChange.Stored(view(WalletRole.Viewer))).canEdit)
    }

    @Test
    fun transferNeedsAnotherMemberAndNothingWaiting() {
        val base = reduce(WalletDetailState(), WalletDetailChange.Stored(view()))
        assertFalse(reduce(base, WalletDetailChange.Members(listOf(owner))).canTransfer)
        val withEditor = reduce(base, WalletDetailChange.Members(listOf(owner, editor)))
        assertTrue(withEditor.canTransfer)
        assertEquals(listOf(editor), withEditor.candidates)
        assertFalse(reduce(withEditor, WalletDetailChange.Stored(view(sync = SyncState.Waiting))).canTransfer)
        assertFalse(reduce(withEditor, WalletDetailChange.Stored(view(WalletRole.Editor))).canTransfer)
    }

    @Test
    fun clashLinesListOnlyDifferingFields() {
        val mine = WalletDraft("cash", "Mine", "EGP", 25_000, "cash", "#2C7A5B")
        val theirs = wallet(name = "Theirs")
        assertEquals(
            listOf(ClashLine(ClashField.Name, ClashValue.Text("Mine"), ClashValue.Text("Theirs"))),
            clashLines(mine, theirs),
        )
        assertEquals(emptyList(), clashLines(mine.copy(name = "Pocket"), wallet()))
        assertEquals(
            setOf(ClashField.Archived, ClashField.OpeningBalance),
            clashLines(mine.copy(name = "Pocket", archive = true, openingBalanceMinor = 1), wallet()).map { it.field }.toSet(),
        )
    }
}

class WalletDetailViewModelTest {
    private val wallet = MutableStateFlow<WalletView?>(view())
    private val members = MutableStateFlow(listOf(owner))
    private val calls = mutableListOf<String>()

    private fun vm(
        archive: suspend () -> SaveResult = { calls += "archive"; SaveResult.Saved },
        delete: suspend () -> SaveResult = { calls += "delete"; SaveResult.Saved },
        transfer: suspend (String) -> TransferResult = { calls += "transfer:$it"; TransferResult.Done },
        refreshMembers: suspend () -> RefreshOutcome = { calls += "members"; RefreshOutcome.Done },
    ) = WalletDetailViewModel(
        "w1", wallet, members, refreshMembers, archive, delete,
        keepMine = { calls += "keepMine" }, useTheirs = { calls += "useTheirs" }, tryAgain = { calls += "tryAgain" },
        discard = { calls += "discard" }, transfer = transfer,
    )

    @Test
    fun membersAreRefreshedOnOpen() = runMviTest {
        val vm = vm()
        assertEquals(listOf("members"), calls)
        assertTrue(vm.state.value.membersUpdatedAt != null)
        assertEquals(listOf(owner), vm.state.value.members)
    }

    @Test
    fun nonOwnersCannotEditArchiveOrDelete() = runMviTest {
        wallet.value = view(WalletRole.Viewer)
        val vm = vm()
        vm.onIntent(WalletDetailIntent.ArchiveClicked)
        vm.onIntent(WalletDetailIntent.DeleteClicked)
        assertEquals(DetailConfirm.None, vm.state.value.confirm)
        vm.effects.test {
            vm.onIntent(WalletDetailIntent.EditClicked)
            expectNoEvents()
        }
    }

    @Test
    fun aGoneWalletClosesTheScreenWithAMessage() = runMviTest {
        val vm = vm()
        vm.effects.test {
            wallet.value = null
            assertEquals(WalletDetailEffect.ShowMessage(DetailMessage.Gone), awaitItem())
            assertEquals(WalletDetailEffect.NavigateBack, awaitItem())
        }
    }

    @Test
    fun archiveAsksFirstThenCallsTheDomain() = runMviTest {
        val vm = vm()
        vm.onIntent(WalletDetailIntent.ArchiveClicked)
        assertEquals(DetailConfirm.Archive, vm.state.value.confirm)
        assertFalse("archive" in calls)
        vm.onIntent(WalletDetailIntent.ConfirmArchive)
        assertTrue("archive" in calls)
        assertEquals(DetailConfirm.None, vm.state.value.confirm)
    }

    @Test
    fun deleteConfirmsThenLeavesWithoutAGoneMessage() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(WalletDetailIntent.DeleteClicked)
            assertEquals(DetailConfirm.Delete, vm.state.value.confirm)
            vm.onIntent(WalletDetailIntent.ConfirmDelete)
            wallet.value = null
            assertEquals(WalletDetailEffect.NavigateBack, awaitItem())
            expectNoEvents()
        }
        assertTrue("delete" in calls)
    }

    @Test
    fun anUnverifiedPersonGetsThePromptOnArchive() = runMviTest {
        val vm = vm(archive = { SaveResult.NotVerified })
        vm.effects.test {
            vm.onIntent(WalletDetailIntent.ArchiveClicked)
            vm.onIntent(WalletDetailIntent.ConfirmArchive)
            assertEquals(WalletDetailEffect.RequireVerification, awaitItem())
        }
    }

    @Test
    fun clashAndRefusalChoicesCallTheService() = runMviTest {
        val vm = vm()
        listOf(
            WalletDetailIntent.KeepMine to "keepMine", WalletDetailIntent.UseTheirs to "useTheirs",
            WalletDetailIntent.AcceptDeletion to "useTheirs", WalletDetailIntent.TryAgain to "tryAgain",
            WalletDetailIntent.Discard to "discard",
        ).forEach { (intent, call) ->
            calls.clear()
            vm.onIntent(intent)
            assertEquals(listOf(call), calls)
        }
    }

    @Test
    fun transferGoesThroughTheSheetThenAConfirmation() = runMviTest {
        members.value = listOf(owner, editor)
        val vm = vm()
        vm.onIntent(WalletDetailIntent.TransferClicked)
        assertEquals(DetailConfirm.Transfer(listOf(editor)), vm.state.value.confirm)
        vm.onIntent(WalletDetailIntent.TransferTargetPicked("u2"))
        assertEquals(DetailConfirm.TransferTo(editor), vm.state.value.confirm)
        vm.onIntent(WalletDetailIntent.ConfirmTransfer("u2"))
        assertTrue("transfer:u2" in calls)
        assertEquals(DetailConfirm.None, vm.state.value.confirm)
    }

    @Test
    fun transferOutcomesBecomeMessages() = runMviTest {
        members.value = listOf(owner, editor)
        mapOf(
            TransferResult.Offline to DetailMessage.TransferOffline,
            TransferResult.NoLongerMember to DetailMessage.NoLongerMember,
            TransferResult.Changed to DetailMessage.Changed,
            TransferResult.Failed("r") to DetailMessage.TransferFailed,
        ).forEach { (result, message) ->
            val vm = vm(transfer = { result })
            vm.effects.test {
                vm.onIntent(WalletDetailIntent.ConfirmTransfer("u2"))
                assertEquals(WalletDetailEffect.ShowMessage(message), awaitItem())
            }
        }
    }

    @Test
    fun noTransferWhileAChangeWaitsAndNeverUnarchive() = runMviTest {
        members.value = listOf(owner, editor)
        wallet.value = view(pending = WalletDraft("cash", "X", "EGP", 1, null, null), sync = SyncState.Waiting)
        val vm = vm()
        vm.onIntent(WalletDetailIntent.TransferClicked)
        assertEquals(DetailConfirm.None, vm.state.value.confirm)
        assertNull((vm.state.value.confirm as? DetailConfirm.Transfer))
    }

    @Test
    fun aDeleteClashKeepsTheWalletVisibleWithItsChoices() = runMviTest {
        wallet.value = view(sync = SyncState.Clash(wallet(name = "Web"), false), pendingDelete = true)
        val vm = vm()
        assertTrue(vm.state.value.wallet!!.pendingDelete)
        assertTrue(vm.state.value.wallet!!.syncState is SyncState.Clash)
        assertFalse(vm.state.value.wallet!!.hidden)
        wallet.value = view(sync = SyncState.Refused(RefusalReason(RefusalKind.NotVerified), emptyMap()))
        assertTrue((vm.state.value.wallet!!.syncState as SyncState.Refused).reason.canRetryUnchanged)
    }
}

class WalletDetailTransactionsTest {
    private val wallet = MutableStateFlow<WalletView?>(view())

    private fun vm() = WalletDetailViewModel(
        "w1", wallet, MutableStateFlow(emptyList()), { RefreshOutcome.Done }, { SaveResult.Saved }, { SaveResult.Saved },
        keepMine = {}, useTheirs = {}, tryAgain = {}, discard = {}, transfer = { TransferResult.Done },
    )

    @Test
    fun theTransactionsRowOpensTheWalletsTransactions() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(WalletDetailIntent.TransactionsClicked)
            assertEquals(WalletDetailEffect.OpenTransactions("w1"), awaitItem())
        }
    }

    @Test
    fun theRecurringRowOpensTheWalletsRecurringScreen() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(WalletDetailIntent.RecurringClicked)
            assertEquals(WalletDetailEffect.OpenRecurring("w1"), awaitItem())
        }
    }

    @Test
    fun theReportActionOpensTheWalletsReportForOwnerEditorAndViewer() = runMviTest {
        // Spec 014 FR-021: navigation only, whatever the role.
        listOf(WalletRole.Owner, WalletRole.Editor, WalletRole.Viewer).forEach { role ->
            wallet.value = view(role)
            val vm = vm()
            vm.effects.test {
                vm.onIntent(WalletDetailIntent.ReportClicked)
                assertEquals(WalletDetailEffect.OpenReport("w1"), awaitItem())
            }
        }
    }

    @Test
    fun addTransactionIsOfferedToOwnersAndEditorsOfAnActiveWalletOnly() = runMviTest {
        val vm = vm()
        assertTrue(vm.state.value.canAddTransaction)
        vm.effects.test {
            vm.onIntent(WalletDetailIntent.AddTransactionClicked)
            assertEquals(WalletDetailEffect.OpenAddTransaction("w1"), awaitItem())
        }
        wallet.value = view(WalletRole.Editor)
        assertTrue(vm.state.value.canAddTransaction)
        wallet.value = view(WalletRole.Viewer)
        assertFalse(vm.state.value.canAddTransaction)
        wallet.value = view(archivedAt = Instant.parse("2026-09-30T00:00:00Z"))
        assertFalse(vm.state.value.canAddTransaction)
        vm.effects.test {
            vm.onIntent(WalletDetailIntent.AddTransactionClicked)
            expectNoEvents()
        }
    }
}
