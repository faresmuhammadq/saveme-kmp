package com.fares.saveme.feature.budgets.ui.editor

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.budgets.domain.model.BudgetDraft
import com.fares.saveme.feature.budgets.domain.model.BudgetField
import com.fares.saveme.feature.budgets.domain.model.BudgetFieldProblem
import com.fares.saveme.feature.budgets.domain.model.BudgetView
import com.fares.saveme.feature.budgets.domain.model.CreateResult
import com.fares.saveme.feature.budgets.domain.model.RefusalKind
import com.fares.saveme.feature.budgets.domain.model.RefusalReason
import com.fares.saveme.feature.budgets.domain.model.SaveResult
import com.fares.saveme.feature.budgets.domain.model.SyncState
import com.fares.saveme.feature.budgets.ui.uiBudget
import com.fares.saveme.feature.budgets.ui.uiChoices
import com.fares.saveme.feature.budgets.ui.uiFigures
import com.fares.saveme.feature.budgets.ui.uiView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetEditorReducerTest {

    private val created = BudgetEditorState(loaded = true, currency = "EGP")

    @Test
    fun firstChoicesPickTheProfilesDefaultCurrency() {
        val state = reduce(BudgetEditorState(loaded = true), BudgetEditorChange.Choices(uiChoices(forCurrency = "", default = "KWD", currencies = listOf("KWD", "EGP"))))
        assertEquals("KWD", state.currency)
        assertEquals(listOf("KWD", "EGP"), state.choices!!.currencies)
        assertEquals(25, state.startDay)
    }

    @Test
    fun chooseCategoryOrAllExpensesMarksItChosen() {
        assertFalse(created.categoryChosen)
        val all = reduce(created, BudgetEditorChange.Category(null))
        assertTrue(all.categoryChosen)
        assertNull(all.categoryId)
        val one = reduce(all, BudgetEditorChange.Category("c1"))
        assertEquals("c1", one.categoryId)
        assertFalse(one.categorySheetOpen)
    }

    @Test
    fun aCurrencySwitchClearsAChosenCategoryAlreadyBudgetedInTheNewCurrency() {
        val chosen = reduce(created, BudgetEditorChange.Category("c1"))
        val switched = reduce(chosen, BudgetEditorChange.Currency("USD"))
        assertEquals("USD", switched.currency)
        assertTrue(switched.categoryChosen, "kept until the new choices say otherwise")
        val clash = reduce(switched, BudgetEditorChange.Choices(uiChoices(forCurrency = "USD", already = setOf("c1"))))
        assertFalse(clash.categoryChosen)
        assertNull(clash.categoryId)
        val fine = reduce(switched, BudgetEditorChange.Choices(uiChoices(forCurrency = "USD", already = setOf("c2"))))
        assertTrue(fine.categoryChosen)
        // Choices still computed for the old currency never clear anything.
        assertTrue(reduce(switched, BudgetEditorChange.Choices(uiChoices(forCurrency = "EGP", already = setOf("c1")))).categoryChosen)
    }

    @Test
    fun allExpensesIsClearedWhenItIsAlreadyBudgetedInTheChosenCurrency() {
        val chosen = reduce(created, BudgetEditorChange.Category(null))
        assertFalse(reduce(chosen, BudgetEditorChange.Choices(uiChoices(already = setOf("all")))).categoryChosen)
    }

    @Test
    fun saveIsEnabledOnlyForAChosenCategoryAndAValidLimit() {
        assertFalse(created.canSave)
        val chosen = reduce(created, BudgetEditorChange.Category("c1"))
        assertFalse(chosen.canSave)
        assertFalse(reduce(chosen, BudgetEditorChange.Limit("abc")).canSave)
        assertFalse(reduce(chosen, BudgetEditorChange.Limit("0")).canSave)
        assertFalse(reduce(chosen, BudgetEditorChange.Limit("1.234")).canSave)
        assertTrue(reduce(chosen, BudgetEditorChange.Limit("6000")).canSave)
        assertFalse(reduce(reduce(chosen, BudgetEditorChange.Limit("6000")), BudgetEditorChange.Saving(true)).canSave)
    }

    @Test
    fun typingClearsTheLimitProblem() {
        val problem = created.copy(errors = mapOf(BudgetField.Limit to BudgetFieldProblem.LimitNotPositive))
        assertTrue(reduce(problem, BudgetEditorChange.Limit("5")).errors.isEmpty())
    }

    @Test
    fun theCurrencyOfAnExistingBudgetNeverChanges() {
        val edit = created.copy(budgetId = "b1", currency = "EGP")
        assertEquals("EGP", reduce(edit, BudgetEditorChange.Currency("USD")).currency)
        assertEquals(null, reduce(edit, BudgetEditorChange.Category("c9")).categoryId, "category is read-only after creation")
        assertFalse(edit.categoryEditable)
    }

    @Test
    fun editSaveNeedsAChangedValidLimit() {
        val edit = BudgetEditorState(budgetId = "b1", loaded = true, currency = "EGP", limitText = "6000.00", shownLimitMinor = 600_000, categoryChosen = true)
        assertFalse(edit.canSave)
        assertTrue(reduce(edit, BudgetEditorChange.Limit("7000")).canSave)
        assertFalse(reduce(edit, BudgetEditorChange.Limit("6000")).canSave, "same value")
        assertFalse(reduce(edit, BudgetEditorChange.Limit("-1")).canSave)
        assertFalse(reduce(edit.copy(readOnly = true), BudgetEditorChange.Limit("7000")).canSave)
    }

    @Test
    fun storedViewsDriveStatusProgressAndTheOfferedActions() {
        val edit = BudgetEditorState(budgetId = "b1", loaded = true)
        val active = reduce(edit, BudgetEditorChange.Stored(uiView()))
        assertTrue(active.canArchive && active.canDelete && !active.archived)
        assertEquals(EditorSync.None, active.sync)
        assertEquals("b1", active.row!!.id)
        val archived = reduce(edit, BudgetEditorChange.Stored(uiView(uiBudget(archived = true))))
        assertTrue(archived.archived && !archived.canArchive && archived.canDelete)
        val waiting = reduce(edit, BudgetEditorChange.Stored(uiView(state = SyncState.Waiting)))
        assertEquals(EditorSync.Waiting, waiting.sync)
        val deleting = reduce(edit, BudgetEditorChange.Stored(uiView(pendingDelete = true, state = SyncState.Waiting)))
        assertFalse(deleting.canArchive || deleting.canDelete)
        val unknown = reduce(edit, BudgetEditorChange.Stored(uiView(uiBudget(periodType = "weekly"), figures = null)))
        assertTrue(unknown.readOnly && !unknown.canArchive)
    }

    @Test
    fun aClashListsOnlyTheMembersThatDiffer() {
        val mine = uiView(state = SyncState.Clash(uiBudget(limit = 900_000, archived = true), deletedElsewhere = false, mineIsDeletion = false), pending = BudgetDraft("c1", "EGP", 700_000))
        val sync = reduce(BudgetEditorState(budgetId = "b1"), BudgetEditorChange.Stored(mine)).sync as EditorSync.Clash
        assertEquals(listOf(ClashField.Limit, ClashField.Archived), sync.lines.map { it.field })
        assertEquals(ClashValue.Limit(700_000, "EGP"), sync.lines[0].mine)
        assertEquals(ClashValue.Limit(900_000, "EGP"), sync.lines[0].theirs)
        val same = uiView(state = SyncState.Clash(uiBudget(limit = 700_000), deletedElsewhere = false, mineIsDeletion = false), pending = BudgetDraft("c1", "EGP", 700_000))
        assertEquals(emptyList(), (reduce(BudgetEditorState(budgetId = "b1"), BudgetEditorChange.Stored(same)).sync as EditorSync.Clash).lines)
        val deleted = uiView(state = SyncState.Clash(null, deletedElsewhere = true, mineIsDeletion = true), pendingDelete = true)
        val gone = reduce(BudgetEditorState(budgetId = "b1"), BudgetEditorChange.Stored(deleted)).sync as EditorSync.Clash
        assertTrue(gone.deletedElsewhere && gone.isDelete)
    }

    @Test
    fun aRefusalCarriesItsReasonAndItsFieldProblems() {
        val refused = uiView(state = SyncState.Refused(RefusalReason(RefusalKind.LimitInvalid), mapOf(BudgetField.Limit to "OUT_OF_RANGE")))
        val state = reduce(BudgetEditorState(budgetId = "b1"), BudgetEditorChange.Stored(refused))
        assertEquals(EditorSync.Refused(RefusalReason(RefusalKind.LimitInvalid)), state.sync)
        assertEquals(BudgetFieldProblem.Server("OUT_OF_RANGE"), state.errors[BudgetField.Limit])
    }
}

class BudgetEditorViewModelTest {
    private val view = MutableStateFlow<BudgetView?>(uiView())
    private val created = mutableListOf<BudgetDraft>()
    private val saved = mutableListOf<Triple<String, Long, Long>>()
    private val calls = mutableListOf<String>()
    private var createResult: CreateResult = CreateResult.Created("new")
    private var saveResult: SaveResult = SaveResult.Saved
    private var archiveResult: SaveResult = SaveResult.Saved

    private fun create(id: String? = null) = BudgetEditorViewModel(
        budgetId = id,
        observeBudget = { view },
        observeChoices = { currency, _ -> flowOf(uiChoices(forCurrency = currency, already = if (currency == "EGP") setOf("c1") else emptySet())) },
        observeStartDay = flowOf(25),
        create = { created += it; createResult },
        saveLimit = { i, limit, base -> saved += Triple(i, limit, base); saveResult },
        archive = { calls += "archive $it"; archiveResult },
        delete = { calls += "delete $it"; SaveResult.Saved },
        keepMine = { calls += "keepMine $it" },
        useTheirs = { calls += "useTheirs $it" },
        tryAgain = { calls += "tryAgain $it" },
        discard = { calls += "discard $it" },
        refreshOne = { calls += "refreshOne $it" },
    )

    @Test
    fun creatingStartsWithTheDefaultCurrencyTheStartDayAndNothingChosen() = runMviTest {
        val vm = create()
        val state = vm.state.value
        assertTrue(state.loaded && state.isCreate)
        assertEquals("EGP", state.currency)
        assertEquals(25, state.startDay)
        assertFalse(state.categoryChosen)
        assertFalse(state.canSave)
    }

    @Test
    fun anAlreadyBudgetedCategoryCannotBeChosenAndAnotherCanAndSaveCreates() = runMviTest {
        val vm = create()
        vm.onIntent(BudgetEditorIntent.CategoryClicked)
        assertTrue(vm.state.value.categorySheetOpen)
        vm.onIntent(BudgetEditorIntent.CategoryChosen("c1"))
        assertFalse(vm.state.value.categoryChosen, "c1 is already budgeted in EGP")
        vm.onIntent(BudgetEditorIntent.CategoryChosen("c2"))
        assertEquals("c2", vm.state.value.categoryId)
        assertFalse(vm.state.value.categorySheetOpen)
        vm.onIntent(BudgetEditorIntent.LimitChanged("6000"))
        vm.effects.test {
            vm.onIntent(BudgetEditorIntent.SaveClicked)
            assertEquals(BudgetEditorEffect.Saved, awaitItem())
        }
        assertEquals(listOf(BudgetDraft("c2", "EGP", 600_000)), created)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun switchingCurrencyClearsTheChosenCategoryWhenItIsBudgetedThereAndKeepsTheLimitText() = runMviTest {
        val vm = create()
        vm.onIntent(BudgetEditorIntent.CategoryChosen("c2"))
        vm.onIntent(BudgetEditorIntent.LimitChanged("5000"))
        vm.onIntent(BudgetEditorIntent.CurrencyClicked)
        assertTrue(vm.state.value.currencySheetOpen)
        vm.onIntent(BudgetEditorIntent.CurrencyChosen("USD"))
        assertEquals("USD", vm.state.value.currency)
        assertEquals("c2", vm.state.value.categoryId, "c2 is not budgeted in USD")
        assertEquals("5000", vm.state.value.limitText)
        assertFalse(vm.state.value.currencySheetOpen)
        vm.onIntent(BudgetEditorIntent.CurrencyChosen("EGP"))
        vm.onIntent(BudgetEditorIntent.CategoryChosen("c2"))
        assertEquals("c2", vm.state.value.categoryId)
    }

    @Test
    fun anInvalidLimitIsNotSentAndShowsItsProblem() = runMviTest {
        val vm = create()
        vm.onIntent(BudgetEditorIntent.CategoryChosen("c2"))
        vm.onIntent(BudgetEditorIntent.LimitChanged("0"))
        vm.onIntent(BudgetEditorIntent.SaveClicked)
        assertEquals(emptyList(), created)
        vm.onIntent(BudgetEditorIntent.LimitChanged("1.234"))
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun anUnverifiedAddressShowsThePromptAndAServerSideDuplicateMarksTheCategory() = runMviTest {
        val vm = create()
        vm.onIntent(BudgetEditorIntent.CategoryChosen("c2"))
        vm.onIntent(BudgetEditorIntent.LimitChanged("10"))
        createResult = CreateResult.NotVerified
        vm.effects.test {
            vm.onIntent(BudgetEditorIntent.SaveClicked)
            assertEquals(BudgetEditorEffect.RequireVerification, awaitItem())
        }
        createResult = CreateResult.AlreadyBudgeted
        vm.onIntent(BudgetEditorIntent.SaveClicked)
        assertEquals(BudgetFieldProblem.AlreadyBudgeted, vm.state.value.errors[BudgetField.Category])
        createResult = CreateResult.Invalid(mapOf(BudgetField.Limit to BudgetFieldProblem.LimitOutOfRange))
        vm.onIntent(BudgetEditorIntent.SaveClicked)
        assertEquals(BudgetFieldProblem.LimitOutOfRange, vm.state.value.errors[BudgetField.Limit])
    }

    // ---- edit mode ------------------------------------------------------------------------------------------------

    @Test
    fun editStartsFromWhatThePhoneShowsAndRemembersTheVersion() = runMviTest {
        view.value = uiView(uiBudget(limit = 600_000, version = 3), pending = BudgetDraft("c1", "EGP", 700_000))
        val vm = create("b1")
        val state = vm.state.value
        assertEquals("7000.00", state.limitText)
        assertEquals(3L, state.baseVersion)
        assertEquals(700_000L, state.shownLimitMinor)
        assertEquals("EGP", state.currency)
        assertEquals("c1", state.categoryId)
        assertEquals(25, state.startDay)
        assertEquals(listOf("refreshOne b1"), calls, "opening a budget the server holds reads its progress")
        assertFalse(state.canSave)
    }

    @Test
    fun editSavesOnlyTheLimitOnTheShownVersion() = runMviTest {
        val vm = create("b1")
        vm.onIntent(BudgetEditorIntent.LimitChanged("7000"))
        assertTrue(vm.state.value.canSave)
        vm.effects.test {
            vm.onIntent(BudgetEditorIntent.SaveClicked)
            assertEquals(BudgetEditorEffect.Saved, awaitItem())
        }
        assertEquals(listOf(Triple("b1", 700_000L, 3L)), saved)
    }

    @Test
    fun theStatusFollowsTheQueueWhileTheTypedLimitIsKept() = runMviTest {
        val vm = create("b1")
        vm.onIntent(BudgetEditorIntent.LimitChanged("7000"))
        view.value = uiView(state = SyncState.Waiting)
        assertEquals(EditorSync.Waiting, vm.state.value.sync)
        assertEquals("7000", vm.state.value.limitText)
    }

    @Test
    fun archiveAndDeleteAskFirstAndThenLeave() = runMviTest {
        val vm = create("b1")
        vm.onIntent(BudgetEditorIntent.ArchiveClicked)
        assertEquals(EditorConfirm.Archive, vm.state.value.confirm)
        vm.onIntent(BudgetEditorIntent.DismissConfirm)
        assertEquals(EditorConfirm.None, vm.state.value.confirm)
        assertEquals(listOf("refreshOne b1"), calls, "nothing archived yet")
        vm.effects.test {
            vm.onIntent(BudgetEditorIntent.ArchiveClicked)
            vm.onIntent(BudgetEditorIntent.ConfirmArchive)
            assertEquals(BudgetEditorEffect.NavigateBack, awaitItem())
        }
        vm.onIntent(BudgetEditorIntent.DeleteClicked)
        assertEquals(EditorConfirm.Delete, vm.state.value.confirm)
        vm.effects.test {
            vm.onIntent(BudgetEditorIntent.ConfirmDelete)
            assertEquals(BudgetEditorEffect.NavigateBack, awaitItem())
        }
        assertEquals(listOf("refreshOne b1", "archive b1", "delete b1"), calls)
        assertEquals(EditorConfirm.None, vm.state.value.confirm)
    }

    @Test
    fun anArchivedBudgetOffersNoArchive() = runMviTest {
        view.value = uiView(uiBudget(archived = true))
        val vm = create("b1")
        vm.onIntent(BudgetEditorIntent.ArchiveClicked)
        assertEquals(EditorConfirm.None, vm.state.value.confirm)
        assertTrue(vm.state.value.canDelete)
    }

    @Test
    fun clashAndRefusalChoicesCallTheService() = runMviTest {
        val vm = create("b1")
        listOf(
            BudgetEditorIntent.KeepMine, BudgetEditorIntent.UseTheirs, BudgetEditorIntent.AcceptDeletion, BudgetEditorIntent.TryAgain, BudgetEditorIntent.Discard,
        ).forEach { vm.onIntent(it) }
        assertEquals(
            listOf("refreshOne b1", "keepMine b1", "useTheirs b1", "useTheirs b1", "tryAgain b1", "discard b1"),
            calls,
        )
    }

    @Test
    fun aBudgetThatLeavesAfterThePersonsOwnChoiceClosesWithoutTheGoneMessage() = runMviTest {
        val vm = create("b1")
        vm.effects.test {
            vm.onIntent(BudgetEditorIntent.AcceptDeletion)
            view.value = null
            assertEquals(BudgetEditorEffect.NavigateBack, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun aBudgetThatVanishesOnItsOwnSaysItIsGone() = runMviTest {
        val vm = create("b1")
        vm.effects.test {
            view.value = null
            assertEquals(BudgetEditorEffect.ShowMessage(EditorMessage.Gone), awaitItem())
            assertEquals(BudgetEditorEffect.NavigateBack, awaitItem())
        }
    }

    @Test
    fun openingABudgetThePhoneDoesNotHoldSaysItIsGone() = runMviTest {
        view.value = null
        val vm = create("nope")
        vm.effects.test {
            assertEquals(BudgetEditorEffect.ShowMessage(EditorMessage.Gone), awaitItem())
            assertEquals(BudgetEditorEffect.NavigateBack, awaitItem())
        }
        assertFalse(vm.state.value.loaded)
    }

    @Test
    fun aSaveRefusedAsNoLongerAvailableClosesTheEditor() = runMviTest {
        saveResult = SaveResult.NoLongerAvailable
        val vm = create("b1")
        vm.onIntent(BudgetEditorIntent.LimitChanged("7000"))
        vm.effects.test {
            vm.onIntent(BudgetEditorIntent.SaveClicked)
            assertEquals(BudgetEditorEffect.NavigateBack, awaitItem())
        }
    }

    @Test
    fun backNavigatesBack() = runMviTest {
        val vm = create()
        vm.effects.test {
            vm.onIntent(BudgetEditorIntent.Back)
            assertEquals(BudgetEditorEffect.NavigateBack, awaitItem())
        }
    }
}
