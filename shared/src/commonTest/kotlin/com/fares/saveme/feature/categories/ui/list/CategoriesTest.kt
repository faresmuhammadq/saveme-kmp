package com.fares.saveme.feature.categories.ui.list

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.categories.domain.defaultCategory
import com.fares.saveme.feature.categories.domain.model.CategoriesOverview
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.RefusalReason
import com.fares.saveme.feature.categories.domain.model.SyncState
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.ownView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun overview(
    kind: CategoryKind = CategoryKind.Expense,
    own: List<com.fares.saveme.feature.categories.domain.model.CategoryView> = emptyList(),
    archived: List<com.fares.saveme.feature.categories.domain.model.CategoryView> = emptyList(),
    defaults: List<CategoryDisplay> = emptyList(),
    loaded: Boolean = true,
    missing: Boolean = false,
    updated: Instant? = null,
) = CategoriesOverview(kind, own, defaults, archived, updated, loaded, missing)

private fun default(id: String) = CategoryDisplay(id, id, "cart", "#2E7D32", "expense", CategorySource.Default, false)

class CategoriesReducerTest {
    @Test
    fun rowsCarryCaptionsAndStatusMarks() {
        val state = reduce(
            CategoriesState(),
            CategoriesChange.Stored(
                overview(
                    own = listOf(
                        ownView("a", state = SyncState.Waiting), ownView("b", state = SyncState.Clash(null, true)),
                        ownView("c", state = SyncState.Refused(RefusalReason.Other("r"), emptyMap())),
                        ownView("d", pendingDelete = true, state = SyncState.Refused(RefusalReason.InUse, emptyMap())),
                        ownView("e"),
                    ),
                    archived = listOf(ownView("f", archived = true)),
                    defaults = listOf(default("g")),
                ),
            ),
        )
        assertEquals(
            listOf(CategoryStatusUi.Waiting, CategoryStatusUi.Changed, CategoryStatusUi.NotSaved, CategoryStatusUi.NotDeleted, null),
            state.own.map { it.status },
        )
        assertEquals(CategoryCaptionUi.Archived, state.archived.single().caption)
        assertEquals(CategoryCaptionUi.Default, state.defaults.single().caption)
        assertFalse(state.defaults.single().clickable)
        assertTrue(state.own.all { it.clickable })
        assertEquals(1, state.waitingCount)
        assertTrue(state.loaded)
    }

    @Test
    fun anEmptyStoredListIsNotLoadedUntilARefreshFinishes() {
        val empty = reduce(CategoriesState(), CategoriesChange.Stored(overview(loaded = false)))
        assertFalse(empty.loaded)
        val done = reduce(empty, CategoriesChange.RefreshDone(offline = false, failed = false))
        assertTrue(done.loaded && done.own.isEmpty())
    }

    @Test
    fun refreshOutcomesSetOfflineAndFailed() {
        val offline = reduce(CategoriesState(refreshing = true), CategoriesChange.RefreshDone(offline = true, failed = false))
        assertTrue(offline.offline && !offline.refreshing && !offline.refreshFailed)
        assertTrue(reduce(offline, CategoriesChange.RefreshDone(offline = false, failed = true)).refreshFailed)
    }

    @Test
    fun theKindAndTheDefaultsNoteAreCarried() {
        assertEquals(CategoryKind.Income, reduce(CategoriesState(), CategoriesChange.Kind(CategoryKind.Income)).kind)
        assertTrue(reduce(CategoriesState(), CategoriesChange.Stored(overview(missing = true))).defaultsMissing)
    }
}

class CategoriesViewModelTest {
    private val stored = MutableStateFlow(overview())

    private fun vm(
        refresh: suspend (Boolean) -> RefreshOutcome = { RefreshOutcome.Done },
        verified: Boolean = true,
        overviewOf: (CategoryKind) -> kotlinx.coroutines.flow.Flow<CategoriesOverview> = { kind -> flowOf(stored.value.copy(kind = kind)) },
    ) = CategoriesViewModel(overviewOf, refresh, { verified })

    @Test
    fun openingForcesARefreshAndSetsLoadedEvenWhenEmpty() = runMviTest {
        var forced: Boolean? = null
        val vm = vm(refresh = { forced = it; RefreshOutcome.Done }, overviewOf = { flowOf(overview(loaded = false)) })
        assertFalse(vm.state.value.loaded)
        vm.onIntent(CategoriesIntent.Opened)
        assertEquals(true, forced)
        assertTrue(vm.state.value.loaded)
    }

    @Test
    fun theKindSwitchReadsTheOtherViewAndPullRefreshes() = runMviTest {
        val asked = mutableListOf<CategoryKind>()
        val vm = vm(overviewOf = { kind -> asked += kind; flowOf(overview(kind = kind, own = listOf(ownView("x-${kind.wire}", kind = kind.wire)))) })
        assertEquals(listOf("x-expense"), vm.state.value.own.map { it.id })
        vm.onIntent(CategoriesIntent.KindChosen(CategoryKind.Income))
        assertEquals(CategoryKind.Income, vm.state.value.kind)
        assertEquals(listOf("x-income"), vm.state.value.own.map { it.id })
        assertEquals(listOf(CategoryKind.Expense, CategoryKind.Income), asked)
    }

    @Test
    fun offlineAndFailedRefreshesKeepTheRows() = runMviTest {
        val offline = vm(refresh = { RefreshOutcome.Offline }, overviewOf = { flowOf(overview(own = listOf(ownView("a")), updated = Instant.parse("2026-10-02T10:00:00Z"))) })
        offline.onIntent(CategoriesIntent.Refresh)
        assertTrue(offline.state.value.offline)
        assertEquals(1, offline.state.value.own.size)
        assertEquals(Instant.parse("2026-10-02T10:00:00Z"), offline.state.value.updatedAt)
        val failed = vm(refresh = { RefreshOutcome.Failed }, overviewOf = { flowOf(overview(own = listOf(ownView("a")))) })
        failed.onIntent(CategoriesIntent.Refresh)
        assertTrue(failed.state.value.refreshFailed)
        assertEquals(1, failed.state.value.own.size)
    }

    @Test
    fun createOpensTheEditorWithTheSelectedKindWhenVerified() = runMviTest {
        val vm = vm()
        vm.onIntent(CategoriesIntent.KindChosen(CategoryKind.Income))
        vm.effects.test {
            vm.onIntent(CategoriesIntent.CreateClicked)
            assertEquals(CategoriesEffect.OpenEditor(null, CategoryKind.Income), awaitItem())
        }
    }

    @Test
    fun anUnverifiedPersonGetsTheVerifyPrompt() = runMviTest {
        val vm = vm(verified = false)
        vm.effects.test {
            vm.onIntent(CategoriesIntent.CreateClicked)
            assertEquals(CategoriesEffect.RequireVerification, awaitItem())
        }
    }

    @Test
    fun onlyOwnRowsOpenTheEditorAndDefaultsDoNot() = runMviTest {
        val vm = vm(overviewOf = { flowOf(overview(own = listOf(ownView("mine")), archived = listOf(ownView("old", archived = true)), defaults = listOf(default("d1")))) })
        vm.effects.test {
            vm.onIntent(CategoriesIntent.CategoryClicked("d1"))
            vm.onIntent(CategoriesIntent.CategoryClicked("mine"))
            vm.onIntent(CategoriesIntent.CategoryClicked("old"))
            assertEquals(CategoriesEffect.OpenEditor("mine", CategoryKind.Expense), awaitItem())
            assertEquals(CategoriesEffect.OpenEditor("old", CategoryKind.Expense), awaitItem())
            vm.onIntent(CategoriesIntent.Back)
            assertEquals(CategoriesEffect.NavigateBack, awaitItem())
        }
    }
}
