package com.fares.saveme.feature.recurring.ui.occurrence

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.recurring.domain.model.ConfirmDraft
import com.fares.saveme.feature.recurring.domain.model.ConfirmField
import com.fares.saveme.feature.recurring.domain.model.OccurrencePending
import com.fares.saveme.feature.recurring.domain.model.OccurrenceResult
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.OccurrenceStatus
import com.fares.saveme.feature.recurring.domain.model.OccurrenceView
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.ui.testOccurrence
import com.fares.saveme.feature.recurring.ui.testOccurrenceView
import com.fares.saveme.feature.transactions.FakeCategories
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun stateOf(view: OccurrenceView) = reduce(OccurrenceScreenState("o1"), OccurrenceChange.Loaded(view, OccurrenceDetails(null, null)))

class OccurrenceReducerTest {
    @Test
    fun aDueOccurrenceCanBeConfirmedSkippedAndEdited() {
        val state = stateOf(testOccurrenceView())
        assertTrue(state.canConfirm && state.canSkip && state.canEdit)
        assertFalse(state.canUnskip || state.canChangeConfirmation || state.canOpenTransaction)
        assertEquals(TransactionKind.Expense, state.kind)
        assertEquals("EGP", state.currency)
    }

    @Test
    fun missedAndUpcomingBehaveLikeDue() {
        assertTrue(stateOf(testOccurrenceView(state = OccurrenceState.Missed)).canConfirm)
        assertTrue(stateOf(testOccurrenceView(state = OccurrenceState.Upcoming)).canSkip)
    }

    @Test
    fun aSkippedOccurrenceCanBeUndoneOrConfirmedButNotSkippedOrEdited() {
        val state = stateOf(testOccurrenceView(testOccurrence(status = "skipped"), OccurrenceState.Skipped))
        assertTrue(state.canUnskip && state.canConfirm)
        assertFalse(state.canSkip || state.canEdit)
    }

    @Test
    fun aConfirmedOccurrenceIsReadOnlyWithAnOpenTransaction() {
        val state = stateOf(testOccurrenceView(testOccurrence(status = "confirmed", transactionId = "t1"), OccurrenceState.Confirmed))
        assertTrue(state.serverConfirmed && state.canOpenTransaction)
        assertFalse(state.canConfirm || state.canSkip || state.canEdit || state.canUnskip)
    }

    @Test
    fun aWaitingConfirmationCanBeChangedOrCancelledButNotSkipped() {
        val pending = OccurrencePending(OccurrenceStatus.Confirmed, LocalDate(2026, 10, 2), 400_000, "c1", "Rent", null, null, "t9")
        val state = stateOf(testOccurrenceView(pending = pending, state = OccurrenceState.Confirmed, syncState = RecurringSyncState.Waiting))
        assertTrue(state.confirmationWaiting && state.canChangeConfirmation)
        assertFalse(state.canConfirm || state.canSkip || state.canEdit || state.canOpenTransaction)
    }

    @Test
    fun aViewerAnUnknownStatusAndAClashOfferNoActions() {
        val viewer = stateOf(testOccurrenceView(canWrite = false))
        assertFalse(viewer.canConfirm || viewer.canSkip || viewer.canEdit)
        val unknown = stateOf(testOccurrenceView(testOccurrence(status = "paid"), OccurrenceState.Unknown))
        assertFalse(unknown.canConfirm || unknown.canSkip || unknown.canEdit)
        val clash = stateOf(testOccurrenceView(syncState = RecurringSyncState.Clash(deletedElsewhere = false, mineIsDeletion = false)))
        assertFalse(clash.canConfirm || clash.canSkip || clash.canEdit)
        val refused = stateOf(testOccurrenceView(syncState = RecurringSyncState.Refused("FORBIDDEN", null, emptyMap())))
        assertFalse(refused.canConfirm)
    }

    @Test
    fun theNoticeIsKeptFromTheFirstViewEvenWhenLaterViewsClearIt() {
        val first = stateOf(testOccurrenceView(testOccurrence(notice = "already_confirmed", status = "confirmed", transactionId = "t1"), OccurrenceState.Confirmed))
        assertEquals("already_confirmed", first.notice)
        val later = reduce(first, OccurrenceChange.Stored(testOccurrenceView(testOccurrence(notice = null, status = "confirmed", transactionId = "t1"), OccurrenceState.Confirmed), OccurrenceDetails(null, null)))
        assertEquals("already_confirmed", later.notice)
    }

    @Test
    fun theFormOpensOnTheShownValuesAndCloses() {
        val state = reduce(stateOf(testOccurrenceView()), OccurrenceChange.FormOpened(OccurrenceForm.Confirm))
        assertEquals(OccurrenceForm.Confirm, state.form)
        assertEquals("4500.00", state.amountText)
        assertEquals(LocalDate(2026, 10, 1), state.date)
        assertEquals("c1", state.formCategoryId)
        assertEquals("Rent", state.note)
        val closed = reduce(state, OccurrenceChange.FormClosed)
        assertEquals(OccurrenceForm.None, closed.form)
    }

    @Test
    fun aWaitingConfirmationsFormShowsItsOwnValues() {
        val pending = OccurrencePending(OccurrenceStatus.Confirmed, LocalDate(2026, 10, 2), 400_000, "c2", "Cheaper", "m", "P", "t9")
        val state = reduce(stateOf(testOccurrenceView(pending = pending, state = OccurrenceState.Confirmed)), OccurrenceChange.FormOpened(OccurrenceForm.Confirm))
        assertEquals("4000.00", state.amountText)
        assertEquals("c2", state.formCategoryId)
        assertEquals("Cheaper", state.note)
        assertEquals("P", state.person)
    }

    @Test
    fun typingClearsProblemsAndChecksLengths() {
        val open = reduce(stateOf(testOccurrenceView()), OccurrenceChange.FormOpened(OccurrenceForm.Edit))
        val withError = reduce(open, OccurrenceChange.Errors(mapOf(ConfirmField.Amount to RuleFieldProblem.Required)))
        assertTrue(ConfirmField.Amount !in reduce(withError, OccurrenceChange.Amount("1")).errors)
        val long = reduce(open, OccurrenceChange.Note("x".repeat(101)))
        assertEquals(RuleFieldProblem.TooLong(100), long.errors[ConfirmField.Note])
        assertTrue(ConfirmField.Note !in reduce(long, OccurrenceChange.Note("ok")).errors)
    }

    @Test
    fun aTransferHasNoCategoryField() {
        val state = stateOf(testOccurrenceView(testOccurrence(kind = "transfer", categoryId = null, counterWalletId = "w2")))
        assertFalse(state.needsCategory)
        assertTrue(state.canConfirm)
    }
}

class OccurrenceViewModelTest {
    private val categories = FakeCategories()
    private val calls = mutableListOf<String>()
    private val confirmed = mutableListOf<ConfirmDraft>()
    private val edited = mutableListOf<ConfirmDraft>()

    private fun vm(
        view: Flow<OccurrenceView?> = flowOf(testOccurrenceView()),
        openConfirm: Boolean = false,
        confirm: suspend (String, ConfirmDraft) -> OccurrenceResult = { _, d -> confirmed += d; OccurrenceResult.Done },
        skip: suspend (String) -> OccurrenceResult = { calls += "skip"; OccurrenceResult.Done },
    ) = OccurrenceViewModel(
        occurrenceId = "o1",
        openConfirm = openConfirm,
        observe = { view },
        displays = { categories.observeDisplays(it) },
        categoryChoices = { kind, current -> categories.observeChoices(CategoryKind.fromWire(kind.wire)!!, current) },
        walletName = { flowOf("Bank") },
        confirm = confirm,
        edit = { _, d -> edited += d; OccurrenceResult.Done },
        skip = skip,
        unskip = { calls += "unskip"; OccurrenceResult.Done },
        cancelConfirmation = { calls += "cancel" },
        dismissNotice = { calls += "dismiss" },
        keepMine = { calls += "keepMine" },
        useTheirs = { calls += "useTheirs" },
        tryAgain = { calls += "tryAgain" },
        discard = { calls += "discard" },
    )

    @Test
    fun theOccurrenceLoadsWithItsCategoryAndChoices() = runMviTest {
        val state = vm().state.value
        assertTrue(state.loaded)
        assertEquals("Groceries", state.category?.name)
        assertEquals(listOf("c1", "c2", "d1"), state.categoryChoices!!.let { it.own + it.defaults }.map { it.id })
    }

    @Test
    fun confirmingSendsTheFormValuesAndCloses() = runMviTest {
        val store = vm()
        store.onIntent(OccurrenceIntent.ConfirmClicked)
        assertEquals(OccurrenceForm.Confirm, store.state.value.form)
        store.onIntent(OccurrenceIntent.AmountChanged("4000"))
        store.onIntent(OccurrenceIntent.DateChosen(LocalDate(2026, 10, 2)))
        store.onIntent(OccurrenceIntent.CategoryChosen("c2"))
        store.onIntent(OccurrenceIntent.NoteChanged("Cheaper"))
        store.effects.test {
            store.onIntent(OccurrenceIntent.SaveClicked)
            assertEquals(OccurrenceEffect.Close, awaitItem())
        }
        val draft = confirmed.single()
        assertEquals(400_000, draft.amountMinor)
        assertEquals("c2", draft.categoryId)
        assertEquals("Cheaper", draft.description)
        assertEquals(LocalDate(2026, 10, 2), draft.date)
    }

    @Test
    fun theConfirmFormCanOpenStraightAway() = runMviTest {
        assertEquals(OccurrenceForm.Confirm, vm(openConfirm = true).state.value.form)
        // Never for something that cannot be confirmed.
        val viewer = vm(view = flowOf(testOccurrenceView(canWrite = false)), openConfirm = true)
        assertEquals(OccurrenceForm.None, viewer.state.value.form)
    }

    @Test
    fun editingUsesTheEditCall() = runMviTest {
        val store = vm()
        store.onIntent(OccurrenceIntent.EditClicked)
        assertEquals(OccurrenceForm.Edit, store.state.value.form)
        store.onIntent(OccurrenceIntent.AmountChanged("4200"))
        store.onIntent(OccurrenceIntent.SaveClicked)
        assertEquals(420_000, edited.single().amountMinor)
        assertTrue(confirmed.isEmpty())
    }

    @Test
    fun anUnreadableAmountShowsTheProblemAndSendsNothing() = runMviTest {
        val store = vm()
        store.onIntent(OccurrenceIntent.ConfirmClicked)
        store.onIntent(OccurrenceIntent.AmountChanged(""))
        store.onIntent(OccurrenceIntent.SaveClicked)
        assertTrue(ConfirmField.Amount in store.state.value.errors)
        assertTrue(confirmed.isEmpty())
        assertTrue(store.state.value.formOpen)
    }

    @Test
    fun serviceAnswersBecomeEffectsOrFieldProblems() = runMviTest {
        val unverified = vm(confirm = { _, _ -> OccurrenceResult.NotVerified })
        unverified.onIntent(OccurrenceIntent.ConfirmClicked)
        unverified.effects.test {
            unverified.onIntent(OccurrenceIntent.SaveClicked)
            assertEquals(OccurrenceEffect.RequireVerification, awaitItem())
        }
        val invalid = vm(confirm = { _, _ -> OccurrenceResult.Invalid(mapOf(ConfirmField.Amount to RuleFieldProblem.OutOfRange(1, 5))) })
        invalid.onIntent(OccurrenceIntent.ConfirmClicked)
        invalid.onIntent(OccurrenceIntent.SaveClicked)
        assertEquals(RuleFieldProblem.OutOfRange(1, 5), invalid.state.value.errors[ConfirmField.Amount])
        val final = vm(confirm = { _, _ -> OccurrenceResult.ReadOnly })
        final.onIntent(OccurrenceIntent.ConfirmClicked)
        final.effects.test {
            final.onIntent(OccurrenceIntent.SaveClicked)
            assertEquals(OccurrenceEffect.ShowMessage(OccurrenceMessage.ConfirmedFinal), awaitItem())
        }
    }

    @Test
    fun skipAndUndoCallTheServiceAndStayOnScreen() = runMviTest {
        val store = vm()
        store.onIntent(OccurrenceIntent.SkipClicked)
        assertEquals(listOf("skip"), calls)
        assertFalse(store.state.value.busy)
        val skipped = vm(view = flowOf(testOccurrenceView(testOccurrence(status = "skipped"), OccurrenceState.Skipped)))
        skipped.onIntent(OccurrenceIntent.UnskipClicked)
        assertEquals(listOf("skip", "unskip"), calls)
        // Nothing to skip once it is skipped.
        skipped.onIntent(OccurrenceIntent.SkipClicked)
        assertEquals(2, calls.size)
    }

    @Test
    fun aWaitingConfirmationCanBeChangedOrCancelled() = runMviTest {
        val pending = OccurrencePending(OccurrenceStatus.Confirmed, LocalDate(2026, 10, 2), 400_000, "c1", "Rent", null, null, "t9")
        val store = vm(view = flowOf(testOccurrenceView(pending = pending, state = OccurrenceState.Confirmed, syncState = RecurringSyncState.Waiting)))
        store.onIntent(OccurrenceIntent.ConfirmClicked)
        assertEquals("4000.00", store.state.value.amountText)
        store.onIntent(OccurrenceIntent.FormCancelled)
        store.onIntent(OccurrenceIntent.CancelConfirmationClicked)
        assertEquals(listOf("cancel"), calls)
    }

    @Test
    fun theNoticeIsDismissedOnOpenButStillShown() = runMviTest {
        val view = testOccurrenceView(testOccurrence(status = "confirmed", transactionId = "t1", notice = "already_confirmed"), OccurrenceState.Confirmed)
        val store = vm(view = flowOf(view))
        assertEquals(listOf("dismiss"), calls)
        assertEquals("already_confirmed", store.state.value.notice)
    }

    @Test
    fun clashAndRefusalChoicesCallTheService() = runMviTest {
        val store = vm(view = flowOf(testOccurrenceView(syncState = RecurringSyncState.Clash(false, false))))
        store.onIntent(OccurrenceIntent.KeepMine)
        store.onIntent(OccurrenceIntent.UseTheirs)
        store.onIntent(OccurrenceIntent.AcceptDeletion)
        store.onIntent(OccurrenceIntent.TryAgain)
        store.onIntent(OccurrenceIntent.Discard)
        assertEquals(listOf("keepMine", "useTheirs", "useTheirs", "tryAgain", "discard"), calls)
    }

    @Test
    fun openingTheTransactionAndTheRuleSendEffects() = runMviTest {
        val view = testOccurrenceView(testOccurrence(status = "confirmed", transactionId = "t1"), OccurrenceState.Confirmed)
        val store = vm(view = flowOf(view))
        store.effects.test {
            store.onIntent(OccurrenceIntent.OpenTransactionClicked)
            assertEquals(OccurrenceEffect.OpenTransaction("t1", "w1"), awaitItem())
            store.onIntent(OccurrenceIntent.OpenRuleClicked)
            assertEquals(OccurrenceEffect.OpenRule("r1"), awaitItem())
            store.onIntent(OccurrenceIntent.Back)
            assertEquals(OccurrenceEffect.Close, awaitItem())
        }
    }

    @Test
    fun anOccurrenceThatVanishesClosesWithAMessageUnlessThePersonResolvedIt() = runMviTest {
        val gone = vm(view = flowOf(null))
        gone.effects.test {
            assertEquals(OccurrenceEffect.ShowMessage(OccurrenceMessage.Gone), awaitItem())
            assertEquals(OccurrenceEffect.Close, awaitItem())
        }
        val flow = MutableStateFlow<OccurrenceView?>(testOccurrenceView(syncState = RecurringSyncState.Clash(true, false)))
        val resolved = vm(view = flow)
        resolved.effects.test {
            resolved.onIntent(OccurrenceIntent.AcceptDeletion)
            flow.value = null
            assertEquals(OccurrenceEffect.Close, awaitItem())
        }
    }

    @Test
    fun theViewFlowKeepsTheStatusCurrentWithoutTouchingTheForm() = runMviTest {
        val flow = MutableStateFlow<OccurrenceView?>(testOccurrenceView())
        val store = vm(view = flow)
        store.onIntent(OccurrenceIntent.ConfirmClicked)
        store.onIntent(OccurrenceIntent.AmountChanged("123"))
        flow.value = testOccurrenceView(syncState = RecurringSyncState.Refused("FORBIDDEN", "r-1", emptyMap()))
        assertEquals("123", store.state.value.amountText)
        assertTrue(store.state.value.view?.syncState is RecurringSyncState.Refused)
        assertNull(store.state.value.errors[ConfirmField.Amount])
    }
}
