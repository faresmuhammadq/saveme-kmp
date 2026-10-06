package com.fares.saveme.feature.account.ui.export

import app.cash.turbine.test
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.account.domain.export.Export
import com.fares.saveme.feature.account.domain.export.ExportScope
import com.fares.saveme.feature.account.domain.export.ExportStatus
import com.fares.saveme.feature.account.domain.export.PageOutcome
import com.fares.saveme.feature.account.ui.export.list.ExportsBanner
import com.fares.saveme.feature.account.ui.export.list.ExportsEffect
import com.fares.saveme.feature.account.ui.export.list.ExportsIntent
import com.fares.saveme.feature.account.ui.export.list.ExportsState
import com.fares.saveme.feature.account.ui.export.list.ExportsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

fun anExport(id: String, status: ExportStatus = ExportStatus.Ready, scope: ExportScope = ExportScope.Account, walletId: String? = null) =
    Export(id, scope, walletId, status, null, Instant.parse("2026-10-06T10:00:00Z"), null, null, null)

class ExportsViewModelTest {

    private val first = PageOutcome.Page(listOf(anExport("a"), anExport("b", ExportStatus.Expired)), "c2", true)
    private val second = PageOutcome.Page(listOf(anExport("c")), null, false)

    @Test
    fun openLoadsTheFirstPageAndShowMoreAppendsWithTheCursor() = runMviTest {
        val cursors = mutableListOf<String?>()
        val vm = ExportsViewModel(page = { cursors += it; if (it == null) first else second })
        vm.onIntent(ExportsIntent.Opened)
        assertEquals(listOf("a", "b"), vm.state.value.items.map { it.id })
        assertTrue(vm.state.value.hasMore)
        vm.onIntent(ExportsIntent.ShowMore)
        assertEquals(listOf("a", "b", "c"), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.hasMore)
        assertEquals(listOf(null, "c2"), cursors)
        vm.onIntent(ExportsIntent.ShowMore)
        assertEquals(2, cursors.size, "no more pages, no read")
    }

    @Test
    fun refreshRestartsFromTheFirstPage() = runMviTest {
        val cursors = mutableListOf<String?>()
        val vm = ExportsViewModel(page = { cursors += it; if (it == null) first else second })
        vm.onIntent(ExportsIntent.Opened)
        vm.onIntent(ExportsIntent.ShowMore)
        vm.onIntent(ExportsIntent.Refresh)
        assertEquals(listOf("a", "b"), vm.state.value.items.map { it.id })
        assertEquals(listOf(null, "c2", null), cursors)
    }

    @Test
    fun aRefusedPositionRestartsOnce() = runMviTest {
        var calls = 0
        val vm = ExportsViewModel(page = { cursor ->
            calls++
            if (cursor == null) first else PageOutcome.CursorInvalid
        })
        vm.onIntent(ExportsIntent.Opened)
        vm.onIntent(ExportsIntent.ShowMore)
        // The refused position restarted from the first page.
        assertEquals(listOf("a", "b"), vm.state.value.items.map { it.id })
        assertEquals(3, calls)
    }

    @Test
    fun walletRowsAreNamedFromTheWalletsFlow() = runMviTest {
        val names = MutableStateFlow(mapOf("w1" to "Home"))
        val vm = ExportsViewModel(page = { PageOutcome.Page(listOf(anExport("a", scope = ExportScope.Wallet, walletId = "w1")), null, false) }, walletNames = names)
        vm.onIntent(ExportsIntent.Opened)
        assertEquals(mapOf("w1" to "Home"), vm.state.value.walletNames)
        names.value = emptyMap()
        assertEquals(emptyMap(), vm.state.value.walletNames, "a wallet the phone lost reads as gone on screen")
    }

    @Test
    fun rowsAndNewNavigate() = runMviTest {
        val vm = ExportsViewModel(page = { first })
        vm.effects.test {
            vm.onIntent(ExportsIntent.RowClicked("a"))
            assertEquals(ExportsEffect.OpenDetail("a"), awaitItem())
            vm.onIntent(ExportsIntent.NewClicked)
            assertEquals(ExportsEffect.OpenRequest, awaitItem())
            vm.onIntent(ExportsIntent.Back)
            assertEquals(ExportsEffect.NavigateBack, awaitItem())
        }
    }

    @Test
    fun offlineAndErrorsShowABannerAndKeepWhatIsShown() = runMviTest {
        var outcome: PageOutcome = first
        val vm = ExportsViewModel(page = { outcome })
        vm.onIntent(ExportsIntent.Opened)
        outcome = PageOutcome.Offline
        vm.onIntent(ExportsIntent.Refresh)
        assertEquals(ExportsBanner.Offline, vm.state.value.banner)
        assertEquals(2, vm.state.value.items.size)
        outcome = PageOutcome.Failed(ApiErrorCode.INTERNAL_ERROR, "req-1")
        vm.onIntent(ExportsIntent.Refresh)
        assertEquals(ExportsBanner.Error(ApiErrorCode.INTERNAL_ERROR, "req-1"), vm.state.value.banner)
    }

    @Test
    fun emptyIsShownOnlyAfterTheFirstAnswer() = runMviTest {
        val vm = ExportsViewModel(page = { PageOutcome.Page(emptyList(), null, false) })
        assertFalse(vm.state.value.loaded)
        vm.onIntent(ExportsIntent.Opened)
        assertTrue(vm.state.value.loaded)
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test
    fun signOutClearsEverythingAndStateNeverHoldsAUrl() = runMviTest {
        val signedIn = MutableStateFlow(true)
        val vm = ExportsViewModel(page = { first }, signedIn = signedIn)
        vm.onIntent(ExportsIntent.Opened)
        signedIn.value = false
        assertEquals(ExportsState(), vm.state.value)
        assertFalse(vm.state.value.toString().contains("https://"))
    }
}
