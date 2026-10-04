package com.fares.saveme.feature.recurring.ui.rule

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.recurring.domain.model.CatchUpOutcome
import com.fares.saveme.feature.recurring.domain.model.FrozenMembers
import com.fares.saveme.feature.recurring.domain.model.OccurrenceLists
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.RuleDraft
import com.fares.saveme.feature.recurring.domain.model.RuleResult
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import com.fares.saveme.feature.recurring.domain.model.RuleView
import com.fares.saveme.feature.recurring.domain.model.SchedulePreview
import com.fares.saveme.feature.recurring.ui.testOccurrence
import com.fares.saveme.feature.recurring.ui.testOccurrenceView
import com.fares.saveme.feature.recurring.ui.testRule
import com.fares.saveme.feature.recurring.ui.testRuleView
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun stateOf(view: RuleView) = reduce(RuleDetailState("r1"), RuleDetailChange.Loaded(view, null, SchedulePreview.Empty))

class RuleDetailReducerTest {
    @Test
    fun anActiveRuleOfAnOwnerOffersEverythingButResume() {
        val state = stateOf(testRuleView())
        assertTrue(state.canEdit && state.canChangeFrom && state.canPause && state.canEnd && state.canDelete && state.canCatchUp)
        assertFalse(state.canResume)
        assertEquals(ReadOnlyReason.None, state.readOnlyReason)
    }

    @Test
    fun aPausedRuleCanBeResumedAndNotPaused() {
        val state = stateOf(testRuleView(testRule(status = "paused")))
        assertTrue(state.canResume && state.canEnd)
        assertFalse(state.canPause)
    }

    @Test
    fun anEndedRuleOnlyCanBeDeleted() {
        val state = stateOf(testRuleView(testRule(status = "ended"), readOnly = true))
        assertTrue(state.canDelete)
        assertFalse(state.canEdit || state.canPause || state.canEnd || state.canCatchUp || state.canChangeFrom)
        assertEquals(ReadOnlyReason.Ended, state.readOnlyReason)
    }

    @Test
    fun aViewerSeesTheRuleButCannotChangeOrDeleteIt() {
        val state = stateOf(testRuleView(role = WalletRole.Viewer, readOnly = true))
        assertFalse(state.canEdit || state.canDelete || state.canCatchUp)
        assertEquals(ReadOnlyReason.Viewer, state.readOnlyReason)
    }

    @Test
    fun aRuleWithUnknownValuesIsReadOnlyWithNoPreview() {
        val state = stateOf(testRuleView(testRule(frequency = "hourly"), readOnly = true))
        assertEquals(ReadOnlyReason.Unknown, state.readOnlyReason)
        assertFalse(state.showPreview || state.canEdit)
        assertTrue(state.canDelete)
    }

    @Test
    fun splittingIsOnlyForCustomRulesWithAnAmountPerRoundThatTheServerKnows() {
        assertFalse(stateOf(testRuleView(testRule(preset = "installment"))).canChangeFrom)
        assertFalse(stateOf(testRuleView(testRule(basis = "total", count = 12))).canChangeFrom)
        assertFalse(stateOf(testRuleView(testRule(version = 0))).canChangeFrom)
    }

    @Test
    fun thePreviewStandsInUntilTheServerSendsDates() {
        val unsynced = stateOf(testRuleView(testRule(version = 0)).copy(hasOccurrences = false))
        assertTrue(unsynced.showPreview)
        assertFalse(unsynced.datesUpdateAfterSync)
        val synced = stateOf(testRuleView())
        assertFalse(synced.showPreview)
        val editing = stateOf(testRuleView().copy(hasPendingChange = true))
        assertTrue(editing.datesUpdateAfterSync)
    }
}

class RuleDetailViewModelTest {
    private val statusCalls = mutableListOf<Triple<String, RuleStatus, Long>>()
    private val deleted = mutableListOf<String>()
    private val choices = mutableListOf<String>()
    private var catchUpOutcome: CatchUpOutcome = CatchUpOutcome.Done

    private fun vm(
        rule: Flow<RuleView?> = flowOf(testRuleView()),
        occurrences: OccurrenceLists = OccurrenceLists(),
        statusResult: RuleResult = RuleResult.Saved,
        deleteResult: RuleResult = RuleResult.Saved,
    ) = RuleDetailViewModel(
        ruleId = "r1",
        observe = { rule },
        observeOccurrences = { _, _ -> flowOf(occurrences) },
        ruleTitle = { flowOf("Rent") },
        preview = { _: RuleDraft -> SchedulePreview.Empty },
        setStatus = { id, status, base -> statusCalls += Triple(id, status, base); statusResult },
        delete = { id -> deleted += id; deleteResult },
        catchUp = { catchUpOutcome },
        keepMine = { choices += "keep:$it" },
        useTheirs = { choices += "theirs:$it" },
        tryAgain = { choices += "retry:$it" },
        discard = { choices += "discard:$it" },
    )

    @Test
    fun theScreenShowsTheRuleAndItsDatesFromThePhonesCopy() = runMviTest {
        val due = testOccurrenceView(testOccurrence("o1"), OccurrenceState.Due)
        val store = vm(occurrences = OccurrenceLists(due = listOf(due)))
        val state = store.state.value
        assertTrue(state.loaded)
        assertEquals("r1", state.view?.id)
        assertEquals(1, state.occurrenceCount)
    }

    @Test
    fun editAndChangeFromOpenTheEditorOfTheRulesWallet() = runMviTest {
        val store = vm()
        store.effects.test {
            store.onIntent(RuleDetailIntent.EditClicked)
            assertEquals(RuleDetailEffect.OpenEditor("w1", "r1", fromDate = false), awaitItem())
            store.onIntent(RuleDetailIntent.ChangeFromClicked)
            assertEquals(RuleDetailEffect.OpenEditor("w1", "r1", fromDate = true), awaitItem())
        }
    }

    @Test
    fun pauseAndResumeSendTheStatusWithTheBaseVersion() = runMviTest {
        vm().onIntent(RuleDetailIntent.PauseClicked)
        assertEquals(listOf(Triple("r1", RuleStatus.Paused, 3L)), statusCalls)
        statusCalls.clear()
        vm(flowOf(testRuleView(testRule(status = "paused")))).onIntent(RuleDetailIntent.ResumeClicked)
        assertEquals(listOf(Triple("r1", RuleStatus.Active, 3L)), statusCalls)
    }

    @Test
    fun endingAndDeletingAskFirst() = runMviTest {
        val store = vm()
        store.onIntent(RuleDetailIntent.EndClicked)
        assertEquals(RuleDialog.End, store.state.value.dialog)
        assertTrue(statusCalls.isEmpty())
        store.onIntent(RuleDetailIntent.DialogDismissed)
        assertEquals(RuleDialog.None, store.state.value.dialog)
        store.onIntent(RuleDetailIntent.EndClicked)
        store.onIntent(RuleDetailIntent.DialogConfirmed)
        assertEquals(listOf(Triple("r1", RuleStatus.Ended, 3L)), statusCalls)
        store.onIntent(RuleDetailIntent.DeleteClicked)
        assertEquals(RuleDialog.Delete, store.state.value.dialog)
        store.onIntent(RuleDetailIntent.DialogConfirmed)
        assertEquals(listOf("r1"), deleted)
    }

    @Test
    fun aRefusedChangeIsReportedByItsResult() = runMviTest {
        val store = vm(statusResult = RuleResult.NotVerified)
        store.effects.test {
            store.onIntent(RuleDetailIntent.PauseClicked)
            assertEquals(RuleDetailEffect.RequireVerification, awaitItem())
        }
        val viewer = vm(statusResult = RuleResult.NotAllowed)
        viewer.effects.test {
            viewer.onIntent(RuleDetailIntent.PauseClicked)
            assertEquals(RuleDetailEffect.ShowMessage(RuleDetailMessage.NotAllowed), awaitItem())
        }
    }

    @Test
    fun aViewerCannotPauseOrDelete() = runMviTest {
        val store = vm(flowOf(testRuleView(role = WalletRole.Viewer, readOnly = true)))
        store.onIntent(RuleDetailIntent.PauseClicked)
        store.onIntent(RuleDetailIntent.DeleteClicked)
        assertTrue(statusCalls.isEmpty())
        assertEquals(RuleDialog.None, store.state.value.dialog)
    }

    @Test
    fun clashAndRefusalChoicesPassTheRuleId() = runMviTest {
        val store = vm(flowOf(testRuleView(syncState = RecurringSyncState.Clash(deletedElsewhere = false, mineIsDeletion = false))))
        store.onIntent(RuleDetailIntent.KeepMine)
        assertEquals(listOf("keep:r1"), choices)
        val other = vm(flowOf(testRuleView(syncState = RecurringSyncState.Clash(deletedElsewhere = true, mineIsDeletion = false))))
        other.onIntent(RuleDetailIntent.AcceptDeletion)
        other.onIntent(RuleDetailIntent.Discard)
        other.onIntent(RuleDetailIntent.TryAgain)
        assertEquals(listOf("keep:r1", "theirs:r1", "discard:r1", "retry:r1"), choices)
    }

    @Test
    fun aHeldSplitShowsTheTitleOfTheRuleItWaitsFor() = runMviTest {
        val store = vm(flowOf(testRuleView(syncState = RecurringSyncState.Held("r0"))))
        assertEquals("Rent", store.state.value.heldOnTitle)
    }

    @Test
    fun checkForNewDatesReportsEachOutcome() = runMviTest {
        val store = vm()
        store.onIntent(RuleDetailIntent.CatchUpClicked)
        assertEquals(CatchUpNote.Done, store.state.value.catchUpNote)
        catchUpOutcome = CatchUpOutcome.NeedsConnection
        store.onIntent(RuleDetailIntent.CatchUpClicked)
        assertEquals(CatchUpNote.Offline, store.state.value.catchUpNote)
        catchUpOutcome = CatchUpOutcome.Failed("req-1")
        store.onIntent(RuleDetailIntent.CatchUpClicked)
        assertEquals(CatchUpNote.Failed("req-1"), store.state.value.catchUpNote)
        assertFalse(store.state.value.catchingUp)
    }

    @Test
    fun checkForNewDatesIsNotOfferedToAViewerOrForAnEndedRule() = runMviTest {
        catchUpOutcome = CatchUpOutcome.Done
        val viewer = vm(flowOf(testRuleView(role = WalletRole.Viewer, readOnly = true)))
        viewer.onIntent(RuleDetailIntent.CatchUpClicked)
        assertNull(viewer.state.value.catchUpNote)
        val ended = vm(flowOf(testRuleView(testRule(status = "ended"), readOnly = true)))
        ended.onIntent(RuleDetailIntent.CatchUpClicked)
        assertNull(ended.state.value.catchUpNote)
    }

    @Test
    fun aRuleThatDisappearsClosesTheScreen() = runMviTest {
        val source = MutableStateFlow<RuleView?>(testRuleView())
        val store = vm(source)
        store.effects.test {
            source.value = null
            assertEquals(RuleDetailEffect.ShowMessage(RuleDetailMessage.Gone), awaitItem())
            assertEquals(RuleDetailEffect.Close, awaitItem())
        }
    }

    @Test
    fun aRuleThePersonDeletedClosesWithoutAGoneMessage() = runMviTest {
        val source = MutableStateFlow<RuleView?>(testRuleView())
        val store = vm(source)
        store.effects.test {
            store.onIntent(RuleDetailIntent.DeleteClicked)
            store.onIntent(RuleDetailIntent.DialogConfirmed)
            source.value = null
            assertEquals(RuleDetailEffect.Close, awaitItem())
        }
    }

    @Test
    fun anOccurrenceRowOpensTheOccurrence() = runMviTest {
        val store = vm()
        store.effects.test {
            store.onIntent(RuleDetailIntent.OccurrenceClicked("o9"))
            assertEquals(RuleDetailEffect.OpenOccurrence("o9"), awaitItem())
        }
    }
}
