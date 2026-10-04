package com.fares.saveme.feature.recurring.ui.list

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.recurring.domain.model.OccurrenceLists
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.ui.testOccurrence
import com.fares.saveme.feature.recurring.ui.testOccurrenceView
import com.fares.saveme.feature.recurring.ui.testRule
import com.fares.saveme.feature.recurring.ui.testRuleView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecurringReducerTest {
    @Test
    fun loadingEndsWhenOccurrencesArriveOrTheRefreshEnds() {
        val start = RecurringState()
        assertTrue(start.loading)
        assertFalse(reduce(start, RecurringChange.Occurrences(OccurrenceLists())).loading)
        assertFalse(reduce(start, RecurringChange.RefreshDone(RefreshOutcome.Offline)).loading)
    }

    @Test
    fun refreshOutcomesSetOfflineAndFailed() {
        val offline = reduce(RecurringState(), RecurringChange.RefreshDone(RefreshOutcome.Offline))
        assertTrue(offline.offline && !offline.refreshFailed)
        val failed = reduce(RecurringState(), RecurringChange.RefreshDone(RefreshOutcome.Failed))
        assertTrue(failed.refreshFailed && !failed.offline)
        val done = reduce(failed, RecurringChange.RefreshDone(RefreshOutcome.Done))
        assertFalse(done.refreshFailed || done.offline || done.refreshing)
    }

    @Test
    fun rulesAreGroupedByStatusAndWaitingChangesAreCounted() {
        val rules = listOf(
            testRuleView(testRule(id = "a", status = "active")),
            testRuleView(testRule(id = "p", status = "paused")),
            testRuleView(testRule(id = "e", status = "ended")),
            testRuleView(testRule(id = "w", status = "active"), syncState = RecurringSyncState.Waiting),
        )
        val due = testOccurrenceView(testOccurrence("o1"), OccurrenceState.Due, syncState = RecurringSyncState.Waiting)
        val state = RecurringState(loading = false, rules = rules, due = listOf(due))
        assertEquals(listOf("a", "w"), state.activeRules.map { it.id })
        assertEquals(listOf("p"), state.pausedRules.map { it.id })
        assertEquals(listOf("e"), state.endedRules.map { it.id })
        assertEquals(2, state.waitingCount)
        assertFalse(state.isEmpty)
        assertTrue(RecurringState(loading = false).isEmpty)
        assertFalse(RecurringState(loading = true).isEmpty)
    }

    @Test
    fun theAllWalletsModeHasNoWalletId() {
        assertTrue(RecurringState().allWallets)
        assertFalse(RecurringState(walletId = "w1").allWallets)
    }
}

class RecurringViewModelTest {
    private val refreshes = mutableListOf<Boolean>()

    private fun vm(
        walletId: String? = "w1",
        canAdd: Boolean = true,
        verified: Boolean = true,
        outcome: RefreshOutcome = RefreshOutcome.Done,
        occurrences: Flow<OccurrenceLists> = flowOf(OccurrenceLists()),
        rules: Flow<List<com.fares.saveme.feature.recurring.domain.model.RuleView>> = flowOf(emptyList()),
    ) = RecurringViewModel(
        walletId = walletId,
        walletName = flowOf("Cash"),
        occurrences = occurrences,
        rules = rules,
        canAdd = flowOf(canAdd),
        refresh = { force -> refreshes += force; outcome },
        isEmailVerified = { verified },
    )

    @Test
    fun theScreenShowsTheSectionsFromThePhonesCopy() = runMviTest {
        val due = testOccurrenceView(testOccurrence("o1"), OccurrenceState.Missed)
        val upcoming = testOccurrenceView(testOccurrence("o2"), OccurrenceState.Upcoming)
        val store = vm(occurrences = flowOf(OccurrenceLists(due = listOf(due), upcoming = listOf(upcoming))), rules = flowOf(listOf(testRuleView())))
        val state = store.state.value
        assertEquals("Cash", state.walletName)
        assertEquals(listOf("o1"), state.due.map { it.id })
        assertEquals(listOf("o2"), state.upcoming.map { it.id })
        assertEquals(1, state.rules.size)
        assertFalse(state.loading)
        assertTrue(state.canAdd)
    }

    @Test
    fun openingAsksOnceWithoutForceAndPullForces() = runMviTest {
        val store = vm(outcome = RefreshOutcome.Offline)
        store.onIntent(RecurringIntent.Opened)
        assertEquals(listOf(false), refreshes)
        assertTrue(store.state.value.offline)
        store.onIntent(RecurringIntent.PullToRefresh)
        assertEquals(listOf(false, true), refreshes)
        store.onIntent(RecurringIntent.Foregrounded)
        assertEquals(listOf(false, true, false), refreshes)
    }

    @Test
    fun addOpensTheEditorForTheShownWalletOnlyForWritersWithAVerifiedAddress() = runMviTest {
        vm(walletId = "w1").let { store ->
            store.effects.test {
                store.onIntent(RecurringIntent.AddClicked)
                assertEquals(RecurringEffect.OpenEditor("w1"), awaitItem())
            }
        }
        vm(walletId = null).let { store ->
            store.effects.test {
                store.onIntent(RecurringIntent.AddClicked)
                assertEquals(RecurringEffect.OpenEditor(null), awaitItem())
            }
        }
        vm(verified = false).let { store ->
            store.effects.test {
                store.onIntent(RecurringIntent.AddClicked)
                assertEquals(RecurringEffect.RequireVerification, awaitItem())
            }
        }
        vm(canAdd = false).let { store ->
            store.effects.test {
                store.onIntent(RecurringIntent.AddClicked)
                expectNoEvents()
            }
        }
    }

    @Test
    fun rowsAndBackSendEffects() = runMviTest {
        val store = vm()
        store.effects.test {
            store.onIntent(RecurringIntent.RuleClicked("r1"))
            assertEquals(RecurringEffect.OpenRule("r1"), awaitItem())
            store.onIntent(RecurringIntent.OccurrenceClicked("o1"))
            assertEquals(RecurringEffect.OpenOccurrence("o1"), awaitItem())
            store.onIntent(RecurringIntent.Back)
            assertEquals(RecurringEffect.Back, awaitItem())
        }
    }

    @Test
    fun theListFollowsTheRulesFlow() = runMviTest {
        val rules = MutableStateFlow(emptyList<com.fares.saveme.feature.recurring.domain.model.RuleView>())
        val store = vm(rules = rules)
        assertTrue(store.state.value.rules.isEmpty())
        rules.value = listOf(testRuleView())
        assertEquals(1, store.state.value.rules.size)
    }
}
