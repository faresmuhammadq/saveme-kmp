package com.fares.saveme.feature.wallets.ui.list

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.wallets.domain.model.CurrencyTotal
import com.fares.saveme.feature.wallets.domain.model.SyncState
import com.fares.saveme.feature.wallets.domain.model.Wallet
import com.fares.saveme.feature.wallets.domain.model.WalletList
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun view(
    id: String, balance: Long = 10_000, state: SyncState = SyncState.None, role: WalletRole = WalletRole.Owner,
    archived: Boolean = false, type: String = "cash",
) = WalletView(
    Wallet(id, type, "W $id", "EGP", balance, balance, "cash", "#2C7A5B", if (archived) Instant.parse("2026-09-02T00:00:00Z") else null, false, role, 1, Instant.parse("2026-09-01T00:00:00Z")),
    null, false, state, balance,
)

class WalletsReducerTest {
    @Test
    fun cardsStatusRoleAndArchivedSection() {
        val list = WalletList(
            active = listOf(
                view("a", state = SyncState.Waiting), view("b", state = SyncState.Clash(null, true)),
                view("c", state = SyncState.Refused(com.fares.saveme.feature.wallets.domain.model.RefusalReason(com.fares.saveme.feature.wallets.domain.model.RefusalKind.Other), emptyMap())),
                view("d", role = WalletRole.Viewer),
            ),
            archived = listOf(view("e", archived = true)),
            totals = listOf(CurrencyTotal("EGP", 40_000, 4)), updatedAt = null,
        )
        val state = reduce(WalletsState(), WalletsChange.Stored(list))
        assertEquals(listOf(WalletStatusUi.Waiting, WalletStatusUi.Changed, WalletStatusUi.NotSaved, null), state.active.map { it.status })
        assertEquals(listOf(null, null, null, WalletRole.Viewer), state.active.map { it.role })
        assertTrue(state.archived.single().archived)
        assertEquals(1, state.waitingCount)
        assertTrue(state.loaded)
        assertEquals(40_000L, state.totals.single().totalMinor)
    }

    @Test
    fun anEmptyStoredListIsNotLoadedUntilARefreshFinishes() {
        val empty = reduce(WalletsState(), WalletsChange.Stored(WalletList(emptyList(), emptyList(), emptyList(), null)))
        assertFalse(empty.loaded)
        val done = reduce(empty, WalletsChange.RefreshDone(offline = false, failed = false))
        assertTrue(done.loaded && done.isEmpty)
    }

    @Test
    fun refreshOutcomesSetOfflineAndFailed() {
        val offline = reduce(WalletsState(refreshing = true), WalletsChange.RefreshDone(offline = true, failed = false))
        assertTrue(offline.offline && !offline.refreshing && !offline.refreshFailed)
        assertTrue(reduce(offline, WalletsChange.RefreshDone(offline = false, failed = true)).refreshFailed)
    }
}

class WalletsViewModelTest {
    private val list = MutableStateFlow(WalletList(emptyList(), emptyList(), emptyList(), null))

    private fun vm(
        refresh: suspend (Boolean) -> RefreshOutcome = { RefreshOutcome.Done },
        canCreate: suspend () -> Boolean = { true },
        verified: Boolean = true,
    ) = WalletsViewModel(list, refresh, canCreate, { verified })

    @Test
    fun cardsComeFromTheStoredList() = runMviTest {
        val vm = vm()
        list.value = WalletList(listOf(view("a", state = SyncState.Waiting)), emptyList(), listOf(CurrencyTotal("EGP", 10_000, 1)), null)
        assertEquals("W a", vm.state.value.active.single().name)
        assertEquals(WalletStatusUi.Waiting, vm.state.value.active.single().status)
    }

    @Test
    fun openingForcesARefreshAndSetsLoadedEvenWhenEmpty() = runMviTest {
        var forced: Boolean? = null
        val vm = vm(refresh = { forced = it; RefreshOutcome.Done })
        assertFalse(vm.state.value.loaded)
        vm.onIntent(WalletsIntent.Opened)
        assertEquals(true, forced)
        assertTrue(vm.state.value.loaded && vm.state.value.isEmpty)
    }

    @Test
    fun offlineAndFailedRefreshesKeepTheCards() = runMviTest {
        val offline = vm(refresh = { RefreshOutcome.Offline })
        list.value = WalletList(listOf(view("a")), emptyList(), emptyList(), Instant.parse("2026-10-02T10:00:00Z"))
        offline.onIntent(WalletsIntent.Refresh)
        assertTrue(offline.state.value.offline)
        assertEquals(1, offline.state.value.active.size)
        val failed = vm(refresh = { RefreshOutcome.Failed })
        failed.onIntent(WalletsIntent.Refresh)
        assertTrue(failed.state.value.refreshFailed)
        assertEquals(1, failed.state.value.active.size)
    }

    @Test
    fun createOpensTheEditorWhenAllowed() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(WalletsIntent.CreateClicked)
            assertEquals(WalletsEffect.OpenEditor, awaitItem())
        }
    }

    @Test
    fun theGateShowsTheNoticeInsteadOfTheEditor() = runMviTest {
        val vm = vm(canCreate = { false })
        vm.onIntent(WalletsIntent.CreateClicked)
        assertTrue(vm.state.value.notEntitledNotice)
        vm.onIntent(WalletsIntent.DismissNotice)
        assertFalse(vm.state.value.notEntitledNotice)
    }

    @Test
    fun anUnverifiedPersonGetsTheVerifyPrompt() = runMviTest {
        val vm = vm(verified = false)
        vm.effects.test {
            vm.onIntent(WalletsIntent.CreateClicked)
            assertEquals(WalletsEffect.RequireVerification, awaitItem())
        }
        assertNull(null)
    }

    @Test
    fun clickingACardOpensItsDetail() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(WalletsIntent.WalletClicked("w9"))
            assertEquals(WalletsEffect.OpenDetail("w9"), awaitItem())
            vm.onIntent(WalletsIntent.Back)
            assertEquals(WalletsEffect.NavigateBack, awaitItem())
        }
    }
}

/** "You left <wallet>" on the wallet list (spec 013 FR-019). */
class WalletsLeftNoticeTest {
    @Test
    fun theNoticeShowsOnceAndCanBeDismissed() = runMviTest {
        val notice = MutableStateFlow<String?>(null)
        var dismissed = 0
        val vm = WalletsViewModel(
            MutableStateFlow(WalletList(emptyList(), emptyList(), emptyList(), null)), { RefreshOutcome.Done }, { true }, { true },
            leftNotice = notice, dismissLeftNotice = { dismissed++; notice.value = null },
        )
        assertNull(vm.state.value.leftNotice)
        notice.value = "Home"
        assertEquals("Home", vm.state.value.leftNotice)
        vm.onIntent(WalletsIntent.DismissLeftNotice)
        assertEquals(1, dismissed)
        assertNull(vm.state.value.leftNotice)
    }
}
