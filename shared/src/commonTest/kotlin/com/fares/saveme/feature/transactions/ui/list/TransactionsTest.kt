package com.fares.saveme.feature.transactions.ui.list

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.transactions.display
import com.fares.saveme.feature.transactions.domain.DayGrouping
import com.fares.saveme.feature.transactions.domain.TransactionFilter
import com.fares.saveme.feature.transactions.domain.model.Direction
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.TransactionList
import com.fares.saveme.feature.transactions.domain.model.TransactionQuery
import com.fares.saveme.feature.transactions.domain.model.TransactionSyncState
import com.fares.saveme.feature.transactions.viewOf
import com.fares.saveme.feature.wallets.domain.model.Wallet
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.WalletView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val today = LocalDate(2026, 10, 17)

class TransactionsReducerTest {
    @Test
    fun theLoadingStateEndsWhenRowsOrAStoredReadArriveOrTheRefreshEnds() {
        val start = TransactionsState()
        assertTrue(start.loading)
        val rows = reduce(start, TransactionsChange.Data(TransactionList("w1", "EGP", emptyList(), null, loaded = false, canAdd = true), emptyList()))
        assertTrue(rows.loading)
        val loaded = reduce(start, TransactionsChange.Data(TransactionList("w1", "EGP", emptyList(), Instant.parse("2026-10-02T10:00:00Z"), loaded = true, canAdd = true), emptyList()))
        assertFalse(loaded.loading)
        assertTrue(loaded.isEmpty)
        assertFalse(reduce(start, TransactionsChange.RefreshDone(RefreshOutcome.Offline)).loading)
    }

    @Test
    fun refreshOutcomesSetOfflineAndFailed() {
        val offline = reduce(TransactionsState(), TransactionsChange.RefreshDone(RefreshOutcome.Offline))
        assertTrue(offline.offline && !offline.refreshFailed)
        val failed = reduce(TransactionsState(), TransactionsChange.RefreshDone(RefreshOutcome.Failed))
        assertTrue(failed.refreshFailed && !failed.offline)
        val done = reduce(failed, TransactionsChange.RefreshDone(RefreshOutcome.Done))
        assertFalse(done.refreshFailed || done.offline)
    }

    @Test
    fun chipsListTheActiveFiltersAndNoMatchesIsToldFromEmpty() {
        val query = TransactionQuery(from = today, to = today, categoryIds = setOf("c2", "c1"), kinds = setOf(TransactionKind.Income, TransactionKind.Expense))
        val state = TransactionsState(loading = false, query = query, totalCount = 3)
        assertEquals(
            listOf(FilterChip.DateRange(today, today), FilterChip.Category("c1"), FilterChip.Category("c2"), FilterChip.Kind(TransactionKind.Expense), FilterChip.Kind(TransactionKind.Income)),
            state.chips,
        )
        assertTrue(state.noMatches)
        assertFalse(state.isEmpty)
        assertTrue(TransactionsState(loading = false, totalCount = 0).isEmpty)
    }

    @Test
    fun clearingAChipRemovesOnlyThatFilter() {
        val query = TransactionQuery(from = today, categoryIds = setOf("c1", "c2"), kinds = setOf(TransactionKind.Income))
        assertEquals(query.copy(from = null, to = null), query.without(FilterChip.DateRange(today, null)))
        assertEquals(query.copy(categoryIds = setOf("c2")), query.without(FilterChip.Category("c1")))
        assertEquals(query.copy(kinds = emptySet()), query.without(FilterChip.Kind(TransactionKind.Income)))
    }

    @Test
    fun theBalanceLineFollowsTheWalletsDisplayedBalance() {
        val view = WalletView(
            Wallet("w1", "cash", "Cash", "EGP", 0, 100, null, null, null, false, WalletRole.Owner, 1, Instant.parse("2026-09-01T00:00:00Z")),
            null, false, com.fares.saveme.feature.wallets.domain.model.SyncState.None, displayedBalanceMinor = 95,
        )
        val state = reduce(TransactionsState(), TransactionsChange.Wallet(view))
        assertEquals(95, state.balanceMinor)
        assertEquals("Cash", state.walletName)
        assertEquals(state, reduce(state, TransactionsChange.Wallet(null)))
    }

    @Test
    fun rowsCarryTheirStatusInWords() {
        assertEquals(RowStatus.None, viewOf().toRow(null).status)
        assertEquals(RowStatus.Waiting, viewOf(sync = TransactionSyncState.Waiting).toRow(null).status)
        assertEquals(RowStatus.Changed, viewOf(sync = TransactionSyncState.Clash(null, true, false)).toRow(null).status)
        assertEquals(RowStatus.NotSaved, viewOf(sync = TransactionSyncState.Refused(com.fares.saveme.feature.transactions.domain.model.RefusalReason(com.fares.saveme.feature.transactions.domain.model.RefusalKind.Other), emptyMap())).toRow(null).status)
        assertEquals(Direction.Out, viewOf(kind = "expense").toRow(null).direction)
    }
}

class TransactionsViewModelTest {
    private val queries = mutableListOf<TransactionQuery>()
    private val refreshes = mutableListOf<Boolean>()
    private val all = MutableStateFlow(
        listOf(
            viewOf("plumber", "2026-10-17", 1_000, category = "c1", description = "Plumber"),
            viewOf("salary", "2026-10-16", 5_000, kind = "income", category = "ci", description = "Salary"),
            viewOf("old", "2026-09-02", 700, category = "c2", description = "Taxi"),
        ),
    )
    private val choices = MutableStateFlow(listOf(display("c2", "Transport"), display("c1", "Groceries")))
    private val displays = CategoryDisplays({ id -> if (id == "gone") CategoryDisplay("gone", "", null, null, null, CategorySource.Unavailable, false) else display(id, "Name $id") })
    private val wallet = MutableStateFlow<WalletView?>(null)

    private fun list(query: TransactionQuery): Flow<TransactionList> {
        queries += query
        return all.map { views ->
            val filtered = TransactionFilter.apply(views, query)
            TransactionList("w1", "EGP", DayGrouping.group(filtered, "w1"), Instant.parse("2026-10-02T10:00:00Z"), true, canAdd = true, totalCount = views.size, categoryIds = views.mapNotNull { it.shown.categoryId }.toSet())
        }
    }

    private fun vm(
        verified: Boolean = true,
        refresh: suspend (Boolean) -> RefreshOutcome = { refreshes += it; RefreshOutcome.Done },
    ) = TransactionsViewModel(
        walletId = "w1", wallet = wallet, list = ::list, displays = flowOf(displays), filterChoices = choices, refresh = refresh,
        isEmailVerified = { verified }, today = { today },
    )

    @Test
    fun theScreenShowsThePhonesCopyGroupedByDayWithResolvedCategories() = runMviTest {
        val vm = vm()
        val state = vm.state.value
        assertEquals(listOf(LocalDate(2026, 10, 17), LocalDate(2026, 10, 16), LocalDate(2026, 9, 2)), state.days.map { it.date })
        assertEquals("Name c1", state.days[0].rows.single().category!!.name)
        assertEquals(3, state.totalCount)
        assertFalse(state.loading)
        assertEquals(today, state.today)
    }

    @Test
    fun openingAndForegroundingRefreshWithoutForceAndPullForces() = runMviTest {
        val vm = vm()
        vm.onIntent(TransactionsIntent.Opened)
        vm.onIntent(TransactionsIntent.Foregrounded)
        vm.onIntent(TransactionsIntent.PullToRefresh)
        assertEquals(listOf(false, false, true), refreshes)
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun anOfflineRefreshShowsTheOfflineStateAndAFailedOneTheAlert() = runMviTest {
        val offline = vm(refresh = { RefreshOutcome.Offline })
        offline.onIntent(TransactionsIntent.Opened)
        assertTrue(offline.state.value.offline)
        val failed = vm(refresh = { RefreshOutcome.Failed })
        failed.onIntent(TransactionsIntent.PullToRefresh)
        assertTrue(failed.state.value.refreshFailed)
    }

    @Test
    fun theLoadingStateShowsUntilTheFirstReadWhenThePhoneHasNothingYet() = runMviTest {
        val vm = TransactionsViewModel(
            walletId = "w1", wallet = flowOf(null),
            list = { flowOf(TransactionList("w1", "EGP", emptyList(), null, loaded = false, canAdd = true)) },
            displays = flowOf(displays), filterChoices = flowOf(emptyList()), refresh = { RefreshOutcome.Done },
            isEmailVerified = { true }, today = { today },
        )
        assertTrue(vm.state.value.loading)
        assertFalse(vm.state.value.isEmpty)
        vm.onIntent(TransactionsIntent.Opened)
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun rowAndAddIntentsAreEffectsAndAddNeedsAVerifiedAddress() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(TransactionsIntent.RowClicked("plumber"))
            assertEquals(TransactionsEffect.OpenEditor("plumber", "w1"), awaitItem())
            vm.onIntent(TransactionsIntent.AddClicked)
            assertEquals(TransactionsEffect.OpenEditor(null, "w1"), awaitItem())
            vm.onIntent(TransactionsIntent.Back)
            assertEquals(TransactionsEffect.Back, awaitItem())
        }
        val unverified = vm(verified = false)
        unverified.effects.test {
            unverified.onIntent(TransactionsIntent.AddClicked)
            assertEquals(TransactionsEffect.RequireVerification, awaitItem())
        }
    }

    @Test
    fun typingSearchesAsYouGoAndClearAllResetsEverything() = runMviTest {
        val vm = vm()
        vm.onIntent(TransactionsIntent.SearchChanged("plumb"))
        assertEquals(listOf("plumber"), vm.state.value.days.flatMap { it.rows }.map { it.id })
        assertEquals("plumb", vm.state.value.searchText)
        vm.onIntent(TransactionsIntent.SearchChanged("zzz"))
        assertTrue(vm.state.value.noMatches)
        vm.onIntent(TransactionsIntent.ClearAll)
        assertEquals(TransactionQuery(), vm.state.value.query)
        assertEquals("", vm.state.value.searchText)
        assertEquals(3, vm.state.value.days.flatMap { it.rows }.size)
    }

    @Test
    fun searchTextIsLimitedToOneHundredCharacters() = runMviTest {
        val vm = vm()
        vm.onIntent(TransactionsIntent.SearchChanged("x".repeat(150)))
        assertEquals(100, vm.state.value.searchText.length)
    }

    @Test
    fun theMonthPresetsUseTheProfilesToday() = runMviTest {
        val vm = vm()
        vm.onIntent(TransactionsIntent.ThisMonth)
        assertEquals(LocalDate(2026, 10, 1), vm.state.value.query.from)
        assertEquals(LocalDate(2026, 10, 31), vm.state.value.query.to)
        assertEquals(2, vm.state.value.days.flatMap { it.rows }.size)
        vm.onIntent(TransactionsIntent.LastMonth)
        assertEquals(LocalDate(2026, 9, 1), vm.state.value.query.from)
        assertEquals(listOf("old"), vm.state.value.days.flatMap { it.rows }.map { it.id })
        vm.onIntent(TransactionsIntent.DateRange(LocalDate(2026, 10, 16), LocalDate(2026, 10, 16)))
        assertEquals(listOf("salary"), vm.state.value.days.flatMap { it.rows }.map { it.id })
    }

    @Test
    fun categoryAndKindTogglesCombineAndChipsClearThem() = runMviTest {
        val vm = vm()
        vm.onIntent(TransactionsIntent.CategoryToggled("c1"))
        vm.onIntent(TransactionsIntent.CategoryToggled("c2"))
        assertEquals(setOf("plumber", "old"), vm.state.value.days.flatMap { it.rows }.map { it.id }.toSet())
        vm.onIntent(TransactionsIntent.KindToggled(TransactionKind.Expense))
        assertEquals(3, vm.state.value.chips.size)
        vm.onIntent(TransactionsIntent.ChipCleared(FilterChip.Category("c2")))
        assertEquals(listOf("plumber"), vm.state.value.days.flatMap { it.rows }.map { it.id })
        vm.onIntent(TransactionsIntent.CategoryToggled("c1"))
        assertEquals(1, vm.state.value.chips.size)
    }

    @Test
    fun theCategoryFilterOffersWhatTheWalletUsesPlusWhatCanBeChosenOnceEach() = runMviTest {
        val vm = vm()
        // Used: c1, ci, c2 (resolved through C5's display); choosable: c2, c1.
        assertEquals(listOf("c1", "ci", "c2"), vm.state.value.filterChoices.map { it.id }.sortedBy { listOf("c1", "ci", "c2").indexOf(it) })
        assertEquals(3, vm.state.value.filterChoices.size)
        choices.value = choices.value + display("c9", "Zoo")
        assertEquals(4, vm.state.value.filterChoices.size)
    }

    @Test
    fun theFilterSheetOpensAndCloses() = runMviTest {
        val vm = vm()
        vm.onIntent(TransactionsIntent.FiltersOpened)
        assertTrue(vm.state.value.filterSheetOpen)
        vm.onIntent(TransactionsIntent.FiltersDismissed)
        assertFalse(vm.state.value.filterSheetOpen)
    }

    @Test
    fun aNewStoreStartsWithoutFiltersSoLeavingTheScreenResetsThem() = runMviTest {
        val first = vm()
        first.onIntent(TransactionsIntent.SearchChanged("x"))
        assertEquals(TransactionQuery(), vm().state.value.query)
        assertNull(vm().state.value.chips.firstOrNull())
    }
}
