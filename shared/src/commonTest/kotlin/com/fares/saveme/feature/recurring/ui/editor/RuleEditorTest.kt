package com.fares.saveme.feature.recurring.ui.editor

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.FrozenMembers
import com.fares.saveme.feature.recurring.domain.model.Frequency
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.PreviewRound
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.RuleDraft
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.domain.model.RulePrefill
import com.fares.saveme.feature.recurring.domain.model.RuleResult
import com.fares.saveme.feature.recurring.domain.model.RuleView
import com.fares.saveme.feature.recurring.domain.model.SchedulePreview
import com.fares.saveme.feature.recurring.ui.testRule
import com.fares.saveme.feature.recurring.ui.testRuleView
import com.fares.saveme.feature.transactions.FakeCategories
import com.fares.saveme.feature.transactions.display
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val cash = WalletChoice("w1", "Cash", "EGP")
private val bank = WalletChoice("w2", "Bank", "EGP")
private val dollars = WalletChoice("w3", "Dollars", "USD")
private val start = LocalDate(2026, 10, 31)

private fun created(block: RuleEditorState.() -> RuleEditorState = { this }) =
    RuleEditorState(loaded = true, walletId = "w1", currency = "EGP", start = start, dayText = "31", walletChoices = listOf(cash, bank)).block()

class RuleEditorReducerTest {
    @Test
    fun theDayFollowsTheStartDateUntilThePersonTypesOne() {
        val state = created()
        val moved = reduce(reduce(state, RuleEditorChange.DatePickerOpen(DateTarget.Start)), RuleEditorChange.Date(LocalDate(2026, 11, 15)))
        assertEquals("15", moved.dayText)
        val typed = reduce(state, RuleEditorChange.Day("5"))
        assertTrue(typed.dayTouched)
        assertEquals("5", reduce(reduce(typed, RuleEditorChange.DatePickerOpen(DateTarget.Start)), RuleEditorChange.Date(LocalDate(2026, 11, 15))).dayText)
    }

    @Test
    fun theFrequencyHidesOrShowsTheDay() {
        assertTrue(created().showsDay)
        assertFalse(reduce(created(), RuleEditorChange.FrequencySet(Frequency.Weekly)).showsDay)
        assertTrue(reduce(created { copy(frequency = Frequency.Daily) }, RuleEditorChange.FrequencySet(Frequency.Yearly)).showsDay)
    }

    @Test
    fun aGameyaIsAMonthlyExpenseAndAnInstallmentIsNeverIncome() {
        val income = created { copy(kind = TransactionKind.Income, categoryId = "ci", frequency = Frequency.Weekly, intervalText = "2") }
        val gameya = reduce(income, RuleEditorChange.PresetSet(Preset.Gameya))
        assertEquals(TransactionKind.Expense, gameya.kind)
        assertNull(gameya.categoryId)
        assertEquals(Frequency.Monthly, gameya.frequency)
        assertEquals("1", gameya.intervalText)
        assertFalse(gameya.kindEditable)
        val installment = reduce(income, RuleEditorChange.PresetSet(Preset.Installment))
        assertEquals(TransactionKind.Expense, installment.kind)
        assertEquals(listOf(TransactionKind.Expense, TransactionKind.Transfer), installment.kindOptions)
        val back = reduce(gameya, RuleEditorChange.PresetSet(Preset.Custom))
        assertEquals(Preset.Custom, back.preset)
        assertTrue(back.kindEditable)
    }

    @Test
    fun theKindClearsWhatNoLongerApplies() {
        val state = created { copy(categoryId = "c1", category = display("c1", "G")) }
        val transfer = reduce(state, RuleEditorChange.Kind(TransactionKind.Transfer))
        assertEquals(TransactionKind.Transfer, transfer.kind)
        assertNull(transfer.categoryId)
        val withDestination = reduce(transfer, RuleEditorChange.Destination(bank))
        assertEquals("w2", withDestination.destinationId)
        assertNull(reduce(withDestination, RuleEditorChange.Kind(TransactionKind.Income)).destinationId)
        // A gam'eya cannot become income.
        assertEquals(TransactionKind.Expense, reduce(created { copy(preset = Preset.Gameya) }, RuleEditorChange.Kind(TransactionKind.Income)).kind)
    }

    @Test
    fun walletSwitchClearsTheDestinationAndRechecksTheAmount() {
        val state = created { copy(kind = TransactionKind.Transfer, destinationId = "w2", destinationName = "Bank", amountText = "10.123") }
        val next = reduce(state, RuleEditorChange.Wallet(dollars))
        assertEquals("USD", next.currency)
        assertNull(next.destinationId)
    }

    @Test
    fun amountProblemsAppearAsTheAmountIsTyped() {
        val bad = reduce(created(), RuleEditorChange.Amount("12.345"))
        assertTrue(RuleField.Amount in bad.errors)
        assertFalse(RuleField.Amount in reduce(bad, RuleEditorChange.Amount("12.34")).errors)
        assertFalse(RuleField.Amount in reduce(bad, RuleEditorChange.Amount("")).errors)
    }

    @Test
    fun countAndRoundsKeepOnlyDigits() {
        assertEquals("12", reduce(created(), RuleEditorChange.Count("1a2")).countText)
        assertEquals("4", reduce(created(), RuleEditorChange.PayoutRound("4x")).payoutRoundText)
    }

    @Test
    fun frozenMembersLockTheirFieldsInEditModeOnly() {
        val edit = created { copy(ruleId = "r1", frozen = FrozenMembers(schedule = true, amountAndCount = true)) }
        assertFalse(edit.scheduleEditable)
        assertFalse(edit.amountEditable)
        assertFalse(edit.countEditable)
        assertTrue(edit.anyFrozen)
        // The kind, wallet, destination and preset of a saved rule never change (FR-012).
        assertFalse(edit.kindEditable || edit.walletEditable || edit.destinationEditable || edit.presetEditable)
        val fromDate = edit.copy(fromDateMode = true)
        assertTrue(fromDate.scheduleEditable && fromDate.amountEditable)
        assertEquals(RuleEditorMode.FromDate, fromDate.mode)
        assertEquals(RuleEditorMode.ReadOnly, edit.copy(readOnly = ReadOnlyReason.Ended).mode)
        assertFalse(edit.copy(readOnly = ReadOnlyReason.Viewer).canSave)
    }

    @Test
    fun saveNeedsADestinationOrACategoryToBePossible() {
        val transfer = created { copy(kind = TransactionKind.Transfer) }
        assertTrue(transfer.destinationsEmpty)
        assertFalse(transfer.canSave)
        assertTrue(reduce(transfer, RuleEditorChange.Destinations(listOf(bank))).canSave)
        val none = reduce(created(), RuleEditorChange.Choices(CategoryChoices(null, emptyList(), emptyList())))
        assertTrue(none.categoriesEmpty && !none.canSave)
    }

    @Test
    fun customDraftsReadTheEndMode() {
        val base = created { copy(amountText = "4500", categoryId = "c1", intervalText = "2") }
        val never = assertIs<DraftResult.Ready>(base.buildDraft()).draft
        assertEquals(450_000, never.amountMinor)
        assertEquals(2, never.interval)
        assertEquals(31, never.anchorDay)
        assertNull(never.endDate)
        assertNull(never.occurrenceCount)
        val onDate = assertIs<DraftResult.Ready>(base.copy(endMode = EndMode.OnDate, endDate = LocalDate(2027, 1, 31)).buildDraft()).draft
        assertEquals(LocalDate(2027, 1, 31), onDate.endDate)
        assertNull(onDate.occurrenceCount)
        val after = assertIs<DraftResult.Ready>(base.copy(endMode = EndMode.After, countText = "6").buildDraft()).draft
        assertEquals(6, after.occurrenceCount)
        assertNull(after.endDate)
        val weekly = assertIs<DraftResult.Ready>(base.copy(frequency = Frequency.Weekly).buildDraft()).draft
        assertNull(weekly.anchorDay)
    }

    @Test
    fun presetDraftsAreMonthlyWithTheirRoundsAndPayout() {
        val gameya = created {
            copy(preset = Preset.Gameya, amountText = "1000", categoryId = "c1", countText = "10", payoutRoundText = "4", payoutCategoryId = "ci", frequency = Frequency.Weekly, intervalText = "9")
        }
        val draft = assertIs<DraftResult.Ready>(gameya.buildDraft()).draft
        assertEquals(Frequency.Monthly, draft.frequency)
        assertEquals(1, draft.interval)
        assertEquals(10, draft.occurrenceCount)
        assertEquals(4, draft.payoutRound)
        assertEquals("ci", draft.payoutCategoryId)
        assertNull(draft.endDate)
        val installment = created { copy(preset = Preset.Installment, amountText = "10000", basis = AmountBasis.Total, categoryId = "c1", countText = "12", payoutRoundText = "3", payoutCategoryId = "ci") }
        val total = assertIs<DraftResult.Ready>(installment.buildDraft()).draft
        assertEquals(AmountBasis.Total, total.amountBasis)
        assertEquals(12, total.occurrenceCount)
        assertNull(total.payoutRound)
        assertNull(total.payoutCategoryId)
    }

    @Test
    fun unreadableFieldsGiveProblemsInsteadOfADraft() {
        val problems = assertIs<DraftResult.Problems>(created { copy(endMode = EndMode.After, intervalText = "") }.buildDraft()).errors
        assertEquals(RuleFieldProblem.Required, problems[RuleField.Interval])
        assertEquals(RuleFieldProblem.Required, problems[RuleField.Count])
        assertTrue(RuleField.Amount in problems)
        val fromMode = created { copy(ruleId = "r1", fromDateMode = true, amountText = "10", from = null) }
        assertEquals(RuleFieldProblem.Required, assertIs<DraftResult.Problems>(fromMode.buildDraft()).errors[RuleField.From])
    }

    @Test
    fun theViewKeepsRightsAndFrozenMembersCurrentButNotTheTypedFields() {
        val typed = created { copy(ruleId = "r1", amountText = "99") }
        val view = testRuleView(frozen = FrozenMembers(schedule = true), readOnly = true, role = com.fares.saveme.feature.wallets.domain.model.WalletRole.Viewer)
        val next = reduce(typed, RuleEditorChange.Stored(view))
        assertEquals("99", next.amountText)
        assertTrue(next.frozen.schedule)
        assertEquals(ReadOnlyReason.Viewer, next.readOnly)
        assertEquals(ReadOnlyReason.Ended, reduce(typed, RuleEditorChange.Stored(testRuleView(testRule(status = "ended"), readOnly = true))).readOnly)
        assertEquals(ReadOnlyReason.Unknown, reduce(typed, RuleEditorChange.Stored(testRuleView(testRule(frequency = "hourly"), readOnly = true))).readOnly)
    }
}

class RuleEditorViewModelTest {
    private val categories = FakeCategories()
    private val created = mutableListOf<Pair<String, RuleDraft>>()
    private val saved = mutableListOf<Triple<String, RuleDraft, Long>>()
    private val split = mutableListOf<Triple<String, LocalDate, Long>>()
    private val previews = mutableListOf<RuleDraft>()

    private fun vm(
        ruleId: String? = null,
        walletId: String? = null,
        fromDate: Boolean = false,
        prefill: RulePrefill? = null,
        view: Flow<RuleView?> = flowOf(null),
        wallets: List<WalletChoice> = listOf(cash, bank, dollars),
        destinations: List<WalletChoice> = listOf(bank),
        create: suspend (String, RuleDraft) -> RuleResult = { w, d -> created += w to d; RuleResult.Created("new") },
        save: suspend (String, RuleDraft, Long) -> RuleResult = { i, d, v -> saved += Triple(i, d, v); RuleResult.Saved },
    ) = RuleEditorViewModel(
        args = RuleEditorArgs(walletId, ruleId, fromDate, prefill),
        observeRule = { view },
        writableWallets = flowOf(wallets),
        defaultWalletId = { null },
        destinations = { flowOf(destinations) },
        categoryChoices = { kind, current -> categories.observeChoices(CategoryKind.fromWire(kind.wire)!!, current) },
        displays = { categories.observeDisplays(it) },
        walletName = { id -> flowOf(wallets.firstOrNull { it.id == id }?.name) },
        preview = { draft -> previews += draft; SchedulePreview(listOf(PreviewRound(1, draft.startDate, draft.amountMinor, false))) },
        create = create,
        save = save,
        changeFrom = { id, from, _, base -> split += Triple(id, from, base); RuleResult.Created("new") },
        today = { LocalDate(2026, 10, 3) },
    )

    @Test
    fun creatingStartsOnTodayInTheChosenWalletWithADayFromTheStart() = runMviTest {
        val store = vm(walletId = "w2")
        val state = store.state.value
        assertTrue(state.loaded && state.isCreate)
        assertEquals("w2", state.walletId)
        assertEquals(LocalDate(2026, 10, 3), state.start)
        assertEquals("3", state.dayText)
        assertEquals(listOf(bank), state.destinationChoices)
        assertEquals(listOf("c1", "c2", "d1"), state.categoryChoices!!.let { it.own + it.defaults }.map { it.id })
    }

    @Test
    fun aPrefillFromTheTransactionEditorFillsTheForm() = runMviTest {
        val prefill = RulePrefill("w1", TransactionKind.Expense, 450_000, "c1", null, "Rent", "memo", "Landlord", LocalDate(2026, 10, 1))
        val state = vm(prefill = prefill).state.value
        assertEquals("4500.00", state.amountText)
        assertEquals("c1", state.categoryId)
        assertEquals("Groceries", state.category?.name)
        assertEquals("Rent", state.note)
        assertEquals("Landlord", state.person)
        assertEquals(LocalDate(2026, 10, 1), state.start)
        assertEquals("1", state.dayText)
    }

    @Test
    fun thePreviewIsRecomputedAfterEachChange() = runMviTest {
        val store = vm()
        assertTrue(store.state.value.preview.rounds.isEmpty())
        store.onIntent(RuleEditorIntent.AmountChanged("100"))
        store.onIntent(RuleEditorIntent.CategoryClicked)
        store.onIntent(RuleEditorIntent.CategoryChosen("c1"))
        assertEquals(1, store.state.value.preview.rounds.size)
        assertEquals(10_000, store.state.value.preview.rounds.single().amountMinor)
        val before = previews.size
        store.onIntent(RuleEditorIntent.IntervalChanged("2"))
        assertTrue(previews.size > before)
        assertEquals(2, previews.last().interval)
    }

    @Test
    fun savingCreatesTheRuleAndCloses() = runMviTest {
        val store = vm()
        store.onIntent(RuleEditorIntent.AmountChanged("4500"))
        store.onIntent(RuleEditorIntent.CategoryChosen("c1"))
        store.onIntent(RuleEditorIntent.NoteChanged("Rent"))
        store.effects.test {
            store.onIntent(RuleEditorIntent.SaveClicked)
            // Spec 017: a create is told apart from an edit so the app can offer notifications once.
            assertEquals(RuleEditorEffect.Created, awaitItem())
            assertEquals(RuleEditorEffect.Close, awaitItem())
        }
        val (wallet, draft) = created.single()
        assertEquals("w1", wallet)
        assertEquals(450_000, draft.amountMinor)
        assertEquals("c1", draft.categoryId)
        assertEquals(Preset.Custom, draft.preset)
        assertFalse(store.state.value.saving)
    }

    @Test
    fun anEditSaveAndACancelEmitOnlyClose() = runMviTest {
        val edit = vm(ruleId = "r1", view = flowOf(testRuleView(testRule(version = 5))))
        edit.effects.test {
            edit.onIntent(RuleEditorIntent.AmountChanged("5000"))
            edit.onIntent(RuleEditorIntent.SaveClicked)
            assertEquals(RuleEditorEffect.Close, awaitItem())
            expectNoEvents()
        }
        val cancel = vm()
        cancel.effects.test {
            cancel.onIntent(RuleEditorIntent.Back)
            assertEquals(RuleEditorEffect.Close, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun saveReportsServiceAnswers() = runMviTest {
        val unverified = vm(create = { _, _ -> RuleResult.NotVerified })
        unverified.onIntent(RuleEditorIntent.AmountChanged("10"))
        unverified.onIntent(RuleEditorIntent.CategoryChosen("c1"))
        unverified.effects.test {
            unverified.onIntent(RuleEditorIntent.SaveClicked)
            assertEquals(RuleEditorEffect.RequireVerification, awaitItem())
        }
        val invalid = vm(create = { _, _ -> RuleResult.Invalid(mapOf(RuleField.Interval to RuleFieldProblem.OutOfRange(1, 365))) })
        invalid.onIntent(RuleEditorIntent.AmountChanged("10"))
        invalid.onIntent(RuleEditorIntent.CategoryChosen("c1"))
        invalid.onIntent(RuleEditorIntent.SaveClicked)
        assertEquals(RuleFieldProblem.OutOfRange(1, 365), invalid.state.value.errors[RuleField.Interval])
        val denied = vm(create = { _, _ -> RuleResult.NotAllowed })
        denied.onIntent(RuleEditorIntent.AmountChanged("10"))
        denied.onIntent(RuleEditorIntent.CategoryChosen("c1"))
        denied.effects.test {
            denied.onIntent(RuleEditorIntent.SaveClicked)
            assertEquals(RuleEditorEffect.ShowMessage(EditorMessage.NotAllowed), awaitItem())
        }
    }

    @Test
    fun savingWithoutAnAmountShowsTheProblemAndSendsNothing() = runMviTest {
        val store = vm()
        store.onIntent(RuleEditorIntent.CategoryChosen("c1"))
        store.onIntent(RuleEditorIntent.SaveClicked)
        assertTrue(RuleField.Amount in store.state.value.errors)
        assertTrue(created.isEmpty())
    }

    @Test
    fun aGameyaIsCreatedWithItsRoundsAndPayout() = runMviTest {
        val store = vm()
        store.onIntent(RuleEditorIntent.PresetChosen(Preset.Gameya))
        store.onIntent(RuleEditorIntent.AmountChanged("1000"))
        store.onIntent(RuleEditorIntent.CategoryChosen("c1"))
        store.onIntent(RuleEditorIntent.CountChanged("10"))
        store.onIntent(RuleEditorIntent.PayoutRoundChanged("4"))
        store.onIntent(RuleEditorIntent.PayoutCategoryChosen("ci"))
        store.onIntent(RuleEditorIntent.SaveClicked)
        val draft = created.single().second
        assertEquals(Preset.Gameya, draft.preset)
        assertEquals(10, draft.occurrenceCount)
        assertEquals(4, draft.payoutRound)
        assertEquals("ci", draft.payoutCategoryId)
    }

    @Test
    fun editingStartsFromWhatThePhoneShowsAndSendsTheBaseVersion() = runMviTest {
        val store = vm(ruleId = "r1", view = flowOf(testRuleView(testRule(version = 5))))
        val state = store.state.value
        assertEquals("4500.00", state.amountText)
        assertEquals(Frequency.Monthly, state.frequency)
        assertEquals("1", state.dayText)
        assertEquals("Rent", state.note)
        assertEquals(5, state.baseVersion)
        assertEquals(RuleEditorMode.Edit, state.mode)
        store.onIntent(RuleEditorIntent.AmountChanged("5000"))
        store.onIntent(RuleEditorIntent.SaveClicked)
        val (id, draft, base) = saved.single()
        assertEquals("r1", id)
        assertEquals(500_000, draft.amountMinor)
        assertEquals(5, base)
    }

    @Test
    fun aFrozenRuleOffersTheSplitAndNothingMovesTheLockedFields() = runMviTest {
        val view = testRuleView(frozen = FrozenMembers(schedule = true))
        val store = vm(ruleId = "r1", view = flowOf(view))
        val state = store.state.value
        assertTrue(state.anyFrozen && state.canSplit && !state.scheduleEditable)
        store.onIntent(RuleEditorIntent.FrequencyClicked)
        assertFalse(store.state.value.frequencySheetOpen)
        store.onIntent(RuleEditorIntent.FrequencyChosen(Frequency.Daily))
        assertEquals(Frequency.Monthly, store.state.value.frequency)
        store.effects.test {
            store.onIntent(RuleEditorIntent.ChangeFromDateClicked)
            assertEquals(RuleEditorEffect.OpenFromDate("r1", "w1"), awaitItem())
        }
    }

    @Test
    fun aPresetRuleCannotBeSplit() = runMviTest {
        val view = testRuleView(testRule(preset = "installment", count = 12), frozen = FrozenMembers(schedule = true, amountAndCount = true))
        val store = vm(ruleId = "r1", view = flowOf(view))
        assertFalse(store.state.value.canSplit)
        store.effects.test {
            store.onIntent(RuleEditorIntent.ChangeFromDateClicked)
            expectNoEvents()
        }
    }

    @Test
    fun fromDateModeShowsTheDateFirstAndSplitsOnSave() = runMviTest {
        val view = testRuleView(frozen = FrozenMembers(schedule = true), nextDate = LocalDate(2026, 12, 1))
        val store = vm(ruleId = "r1", fromDate = true, view = flowOf(view))
        val state = store.state.value
        assertEquals(RuleEditorMode.FromDate, state.mode)
        assertEquals(LocalDate(2026, 12, 1), state.from)
        assertTrue(state.scheduleEditable && state.amountEditable)
        store.onIntent(RuleEditorIntent.AmountChanged("5000"))
        store.onIntent(RuleEditorIntent.SaveClicked)
        assertEquals(Triple("r1", LocalDate(2026, 12, 1), 3L), split.single())
        assertTrue(saved.isEmpty())
        assertEquals(LocalDate(2026, 12, 1), previews.last().startDate)
    }

    @Test
    fun aViewerSeesTheRuleButCannotSave() = runMviTest {
        val view = testRuleView(readOnly = true, role = com.fares.saveme.feature.wallets.domain.model.WalletRole.Viewer)
        val store = vm(ruleId = "r1", view = flowOf(view))
        assertEquals(ReadOnlyReason.Viewer, store.state.value.readOnly)
        store.onIntent(RuleEditorIntent.SaveClicked)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun aRefusedRulesFieldsBecomeProblems() = runMviTest {
        val view = testRuleView(syncState = RecurringSyncState.Refused("VALIDATION_FAILED", null, mapOf("interval" to "OUT_OF_RANGE", "amount" to "REQUIRED")))
        val state = vm(ruleId = "r1", view = flowOf(view)).state.value
        assertIs<RuleFieldProblem.OutOfRange>(state.errors[RuleField.Interval])
        assertEquals(RuleFieldProblem.Required, state.errors[RuleField.Amount])
    }

    @Test
    fun aRuleThatIsGoneClosesWithAMessage() = runMviTest {
        val store = vm(ruleId = "r1", view = flowOf(null))
        store.effects.test {
            assertEquals(RuleEditorEffect.ShowMessage(EditorMessage.Gone), awaitItem())
            assertEquals(RuleEditorEffect.Close, awaitItem())
        }
    }

    @Test
    fun noWritableWalletShowsTheEmptyStateAndOffersToCreateOne() = runMviTest {
        val store = vm(wallets = emptyList())
        assertTrue(store.state.value.noWallet)
        store.effects.test {
            store.onIntent(RuleEditorIntent.CreateWalletClicked)
            assertEquals(RuleEditorEffect.OpenWalletEditor, awaitItem())
        }
    }

    @Test
    fun theViewFlowKeepsStatusCurrentWhileTyping() = runMviTest {
        val flow = MutableStateFlow<RuleView?>(testRuleView())
        val store = vm(ruleId = "r1", view = flow)
        store.onIntent(RuleEditorIntent.AmountChanged("77"))
        flow.value = testRuleView(frozen = FrozenMembers(schedule = true))
        assertEquals("77", store.state.value.amountText)
        assertTrue(store.state.value.frozen.schedule)
    }
}
