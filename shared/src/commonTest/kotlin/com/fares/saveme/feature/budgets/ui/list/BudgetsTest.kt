package com.fares.saveme.feature.budgets.ui.list

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.budgets.domain.model.PendingStartDay
import com.fares.saveme.feature.budgets.domain.model.PeriodState
import com.fares.saveme.feature.budgets.domain.model.RefusalKind
import com.fares.saveme.feature.budgets.domain.model.RefusalReason
import com.fares.saveme.feature.budgets.domain.model.SyncState
import com.fares.saveme.feature.budgets.ui.BudgetStatusUi
import com.fares.saveme.feature.budgets.ui.uiBudget
import com.fares.saveme.feature.budgets.ui.uiFigures
import com.fares.saveme.feature.budgets.ui.uiOverview
import com.fares.saveme.feature.budgets.ui.uiView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class BudgetsReducerTest {

    @Test
    fun rowsCarryFiguresStatusMarksAndTheSplitBetweenActiveAndArchived() {
        val state = reduce(
            BudgetsState(),
            BudgetsChange.Stored(
                uiOverview(
                    active = listOf(
                        uiView(uiBudget("a"), state = SyncState.Waiting),
                        uiView(uiBudget("b", category = null), name = null, state = SyncState.Clash(null, deletedElsewhere = true, mineIsDeletion = false)),
                        uiView(uiBudget("c", category = "c2"), name = "Transport", state = SyncState.Clash(uiBudget("c"), deletedElsewhere = false, mineIsDeletion = false)),
                        uiView(uiBudget("d", category = "c3"), state = SyncState.Refused(RefusalReason(RefusalKind.Invalid), emptyMap())),
                        uiView(uiBudget("e", category = "c4")),
                    ),
                    archived = listOf(uiView(uiBudget("f", archived = true))),
                ),
            ),
        )
        assertEquals(
            listOf(BudgetStatusUi.Waiting, BudgetStatusUi.DeletedElsewhere, BudgetStatusUi.Changed, BudgetStatusUi.NotSaved, null),
            state.active.map { it.status },
        )
        assertEquals("Groceries", state.active[0].categoryName)
        assertNull(state.active[1].categoryName, "all expenses")
        assertEquals(listOf("f"), state.archived.map { it.id })
        assertEquals(1, state.waitingCount)
        assertTrue(state.loaded)
    }

    @Test
    fun theProjectionIsDroppedWhileNothingIsSpent() {
        val state = reduce(
            BudgetsState(),
            BudgetsChange.Stored(uiOverview(active = listOf(uiView(figures = uiFigures(spent = 0, projected = 0)), uiView(uiBudget("x", category = "c2"), figures = uiFigures())))),
        )
        assertNull(state.active[0].figures!!.projectedMinor)
        assertEquals(1_312_500L, state.active[1].figures!!.projectedMinor)
    }

    @Test
    fun anUnknownPeriodHasNoFiguresAndNoPeriodLine() {
        val state = reduce(BudgetsState(), BudgetsChange.Stored(uiOverview(active = listOf(uiView(figures = null)), period = PeriodState.Unknown, today = null)))
        assertNull(state.period)
        assertNull(state.active.single().figures)
        assertFalse(state.active.single().readOnly)
    }

    @Test
    fun aBudgetOfAnUnknownPeriodTypeIsReadOnly() {
        val state = reduce(BudgetsState(), BudgetsChange.Stored(uiOverview(active = listOf(uiView(uiBudget(periodType = "weekly"), figures = null)))))
        assertTrue(state.active.single().readOnly)
    }

    @Test
    fun thePeriodLineAndThePendingStartDayAreCarried() {
        val state = reduce(
            BudgetsState(),
            BudgetsChange.Stored(uiOverview(pendingStartDay = PendingStartDay(1, LocalDate(2026, 11, 1)), updatedAt = Instant.parse("2026-10-02T09:00:00Z"))),
        )
        assertEquals(LocalDate(2026, 9, 25), state.period!!.start)
        assertEquals(LocalDate(2026, 10, 24), state.period!!.end)
        assertEquals(22, state.period!!.daysLeft)
        assertFalse(state.period!!.lastDay)
        assertEquals(PendingDayUi(1, LocalDate(2026, 11, 1)), state.pendingDay)
        assertEquals(Instant.parse("2026-10-02T09:00:00Z"), state.updatedAt)
        assertTrue(reduce(state, BudgetsChange.Stored(uiOverview(today = LocalDate(2026, 10, 24)))).period!!.lastDay)
    }

    @Test
    fun anEmptyStoredListIsNotLoadedUntilARefreshFinishes() {
        val empty = reduce(BudgetsState(), BudgetsChange.Stored(uiOverview(loaded = false)))
        assertFalse(empty.loaded)
        assertFalse(empty.isEmpty)
        val done = reduce(empty, BudgetsChange.RefreshDone(offline = false, failed = false))
        assertTrue(done.loaded && done.isEmpty)
    }

    @Test
    fun refreshOutcomesSetOfflineAndFailed() {
        val offline = reduce(BudgetsState(refreshing = true), BudgetsChange.RefreshDone(offline = true, failed = false))
        assertTrue(offline.offline && !offline.refreshing && !offline.refreshFailed)
        assertTrue(reduce(offline, BudgetsChange.RefreshDone(offline = false, failed = true)).refreshFailed)
        assertEquals("Africa/Cairo", reduce(BudgetsState(), BudgetsChange.TimeZone("Africa/Cairo")).timeZoneId)
    }
}

class BudgetsViewModelTest {
    private val stored = MutableStateFlow(uiOverview(loaded = false))

    private fun vm(
        refresh: suspend (Boolean) -> RefreshOutcome = { RefreshOutcome.Done },
        verified: Boolean = true,
    ) = BudgetsViewModel(overview = stored, refresh = refresh, isEmailVerified = { verified })

    @Test
    fun theScreenFollowsThePhonesCopyAndShowsTheEmptyStateAfterTheFirstRead() = runMviTest {
        val vm = vm()
        assertFalse(vm.state.value.loaded)
        vm.onIntent(BudgetsIntent.Opened)
        assertTrue(vm.state.value.isEmpty)
        stored.value = uiOverview(active = listOf(uiView()))
        assertEquals(listOf("b1"), vm.state.value.active.map { it.id })
        assertFalse(vm.state.value.isEmpty)
    }

    @Test
    fun openingReadsWithinTheFloorAndAPullAlwaysReads() = runMviTest {
        val forced = mutableListOf<Boolean>()
        val vm = vm(refresh = { force -> forced += force; RefreshOutcome.Done })
        vm.onIntent(BudgetsIntent.Opened)
        vm.onIntent(BudgetsIntent.Refresh)
        assertEquals(listOf(false, true), forced)
    }

    @Test
    fun anOfflineOrFailedReadIsShownWithoutDroppingWhatIsOnThePhone() = runMviTest {
        stored.value = uiOverview(active = listOf(uiView()))
        val offline = vm(refresh = { RefreshOutcome.Offline })
        offline.onIntent(BudgetsIntent.Refresh)
        assertTrue(offline.state.value.offline)
        assertEquals(1, offline.state.value.active.size)
        val failed = vm(refresh = { RefreshOutcome.Failed })
        failed.onIntent(BudgetsIntent.Refresh)
        assertTrue(failed.state.value.refreshFailed)
    }

    @Test
    fun addOpensTheEditorForAVerifiedPersonAndThePromptOtherwise() = runMviTest {
        val verified = vm()
        verified.effects.test {
            verified.onIntent(BudgetsIntent.AddClicked)
            assertEquals(BudgetsEffect.OpenEditor(null), awaitItem())
        }
        val unverified = vm(verified = false)
        unverified.effects.test {
            unverified.onIntent(BudgetsIntent.AddClicked)
            assertEquals(BudgetsEffect.RequireVerification, awaitItem())
        }
    }

    @Test
    fun openingABudgetOpensItsEditorAndAnUnknownIdDoesNothing() = runMviTest {
        stored.value = uiOverview(active = listOf(uiView()), archived = listOf(uiView(uiBudget("old", archived = true))))
        val vm = vm()
        vm.effects.test {
            vm.onIntent(BudgetsIntent.BudgetClicked("old"))
            assertEquals(BudgetsEffect.OpenEditor("old"), awaitItem())
            vm.onIntent(BudgetsIntent.BudgetClicked("nope"))
            vm.onIntent(BudgetsIntent.Back)
            assertEquals(BudgetsEffect.NavigateBack, awaitItem())
        }
    }
}
