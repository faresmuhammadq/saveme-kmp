package com.fares.saveme.feature.transactions.ui.editor

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.transactions.FakeCategories
import com.fares.saveme.feature.transactions.display
import com.fares.saveme.feature.transactions.domain.model.AddedBy
import com.fares.saveme.feature.transactions.domain.model.Counterpart
import com.fares.saveme.feature.transactions.domain.model.CreateResult
import com.fares.saveme.feature.transactions.domain.model.RefusalKind
import com.fares.saveme.feature.transactions.domain.model.RefusalReason
import com.fares.saveme.feature.transactions.domain.model.SaveResult
import com.fares.saveme.feature.transactions.domain.model.Transaction
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.TransactionSyncState
import com.fares.saveme.feature.transactions.domain.model.TransactionView
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.fares.saveme.feature.transactions.viewOf
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
import kotlin.time.Instant

private val today = LocalDate(2026, 10, 2)
private val cash = WalletChoice("w1", "Cash", "EGP")
private val bank = WalletChoice("w2", "Bank", "EGP")
private val dollars = WalletChoice("w3", "Dollars", "USD")

class TransactionEditorReducerTest {
    private fun state(extra: TransactionEditorState.() -> TransactionEditorState = { this }) =
        TransactionEditorState(loaded = true, walletId = "w1", currency = "EGP", date = today, walletChoices = listOf(cash, bank, dollars)).extra()

    @Test
    fun typingTheAmountChecksItAgainstTheWalletsCurrency() {
        assertEquals(TransactionFieldProblem.AmountNotPositive, reduce(state(), TransactionEditorChange.Amount("0")).errors[TransactionField.Amount])
        assertEquals(TransactionFieldProblem.AmountDigits(2), reduce(state(), TransactionEditorChange.Amount("1.234")).errors[TransactionField.Amount])
        assertNull(reduce(state(), TransactionEditorChange.Amount("1.23")).errors[TransactionField.Amount])
        // An empty amount shows no error until Save is tried.
        assertNull(reduce(state(), TransactionEditorChange.Amount("")).errors[TransactionField.Amount])
    }

    @Test
    fun switchingTheWalletKeepsTheAmountRechecksItAndClearsTheDestination() {
        val typed = state { copy(amountText = "1.234", destinationId = "w2", destinationName = "Bank", kindWire = "transfer") }
        val usd = reduce(typed, TransactionEditorChange.Wallet(dollars))
        assertEquals("1.234", usd.amountText)
        assertEquals("USD", usd.currency)
        assertNull(usd.destinationId)
        assertEquals(TransactionFieldProblem.AmountDigits(2), usd.errors[TransactionField.Amount])
        val kwd = reduce(typed, TransactionEditorChange.Wallet(WalletChoice("k", "Kuwait", "KWD")))
        assertNull(kwd.errors[TransactionField.Amount])
    }

    @Test
    fun switchingTheKindClearsWhatNoLongerApplies() {
        val expense = state { copy(categoryId = "c1", category = display("c1", "Groceries")) }
        val income = reduce(expense, TransactionEditorChange.Kind(TransactionKind.Income))
        assertNull(income.categoryId)
        assertNull(income.category)
        val transfer = reduce(state { copy(destinationId = "w2", destinationName = "Bank", kindWire = "transfer") }, TransactionEditorChange.Kind(TransactionKind.Expense))
        assertNull(transfer.destinationId)
        assertEquals("expense", transfer.kindWire)
    }

    @Test
    fun theKindAndTheWalletAreReadOnlyWhileEditing() {
        val edit = TransactionEditorState(transactionId = "t1", loaded = true, kindWire = "transfer", walletId = "w1", currency = "EGP")
        assertEquals("transfer", reduce(edit, TransactionEditorChange.Kind(TransactionKind.Expense)).kindWire)
        assertEquals("w1", reduce(edit, TransactionEditorChange.Wallet(bank)).walletId)
        assertFalse(edit.kindEditable)
        assertFalse(edit.walletEditable)
    }

    @Test
    fun textLimitsAreCountedInCodePointsAfterTrimming() {
        assertEquals(TransactionFieldProblem.TooLong(100), reduce(state(), TransactionEditorChange.Note("x".repeat(101))).errors[TransactionField.Note])
        assertNull(reduce(state(), TransactionEditorChange.Note("😀".repeat(100))).errors[TransactionField.Note])
        assertNull(reduce(state(), TransactionEditorChange.Note(" " + "x".repeat(100) + " ")).errors[TransactionField.Note])
        assertEquals(TransactionFieldProblem.TooLong(1000), reduce(state(), TransactionEditorChange.Details("x".repeat(1001))).errors[TransactionField.Details])
        assertEquals(TransactionFieldProblem.TooLong(100), reduce(state(), TransactionEditorChange.Person("x".repeat(101))).errors[TransactionField.Person])
    }

    @Test
    fun choosingACategoryAndADateClearsTheirErrors() {
        val withErrors = state { copy(errors = mapOf(TransactionField.Category to TransactionFieldProblem.CategoryRequired, TransactionField.Date to TransactionFieldProblem.DateOutOfRange)) }
        val next = reduce(reduce(withErrors, TransactionEditorChange.Category(display("c1", "Groceries"))), TransactionEditorChange.Date(LocalDate(2026, 9, 1)))
        assertEquals(emptyMap(), next.errors)
        assertEquals("c1", next.categoryId)
        assertEquals(LocalDate(2026, 9, 1), next.date)
    }

    @Test
    fun saveIsUnavailableForATransferWithoutADestinationAndWhenNoCategoryCanBePicked() {
        val transfer = state { copy(kindWire = "transfer") }
        assertFalse(transfer.canSave)
        assertTrue(transfer.destinationsEmpty)
        // With a wallet that can receive, Save is available and asks for the destination when it is tried.
        assertTrue(reduce(transfer, TransactionEditorChange.Destinations(listOf(bank))).canSave)
        assertEquals(bank.id, reduce(reduce(transfer, TransactionEditorChange.Destinations(listOf(bank))), TransactionEditorChange.Destination(bank)).destinationId)
        val noCategories = reduce(state(), TransactionEditorChange.Choices(CategoryChoices(null, emptyList(), emptyList())))
        assertTrue(noCategories.categoriesEmpty)
        assertFalse(noCategories.canSave)
        assertTrue(reduce(noCategories, TransactionEditorChange.Choices(CategoryChoices(null, listOf(display("c1", "G")), emptyList()))).canSave)
    }

    @Test
    fun theStatusFollowsTheViewAndARefusalsFieldsBecomeProblems() {
        val refused = viewOf(sync = TransactionSyncState.Refused(RefusalReason(RefusalKind.Invalid), mapOf(TransactionField.Amount to "OUT_OF_RANGE")))
        val next = reduce(TransactionEditorState(transactionId = "t1", loaded = true), TransactionEditorChange.Stored(refused, emptyList()))
        assertIs<EditorSync.Refused>(next.sync)
        assertEquals(TransactionFieldProblem.Server("OUT_OF_RANGE"), next.errors[TransactionField.Amount])
        assertEquals(EditorSync.Waiting, reduce(next, TransactionEditorChange.Stored(viewOf(sync = TransactionSyncState.Waiting), emptyList())).sync)
    }

    @Test
    fun aClashListsOnlyTheDifferingFields() {
        val mine = viewOf(amount = 6_000, description = "Taxi").shown
        val theirs = Transaction(
            "t1", "w1", null, "expense", LocalDate(2026, 10, 1), 4_550, "EGP", "c1", "Cab", null, null, false, "u1", null, null, 6,
            Instant.parse("2026-10-01T09:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"),
        )
        val lines = clashLines(mine, theirs) { id -> id?.let { display(it, it) } }
        assertEquals(listOf(ClashField.Amount, ClashField.Note), lines.map { it.field })
        assertEquals(ClashValue.Money(6_000, "EGP"), lines[0].mine)
        assertEquals(ClashValue.Money(4_550, "EGP"), lines[0].theirs)
    }

    @Test
    fun aDeleteClashAndATombstoneClashShowNoLines() {
        val view = viewOf(pendingDelete = true, sync = TransactionSyncState.Clash(null, deletedElsewhere = true, mineIsDeletion = true))
        val next = reduce(TransactionEditorState(transactionId = "t1", loaded = true), TransactionEditorChange.Stored(view, emptyList()))
        val clash = assertIs<EditorSync.Clash>(next.sync)
        assertTrue(clash.deletedElsewhere && clash.isDelete && clash.lines.isEmpty())
    }
}

class TransactionEditorViewModelTest {
    private val created = mutableListOf<TransactionDraft>()
    private val saved = mutableListOf<Triple<String, TransactionDraft, Long>>()
    private val savedLastSeen = mutableListOf<Transaction?>()
    private val calls = mutableListOf<String>()
    private val categories = FakeCategories()

    private fun vm(
        id: String? = null,
        walletId: String? = null,
        view: Flow<TransactionView?> = flowOf(null),
        wallets: List<WalletChoice> = listOf(cash, bank, dollars),
        destinations: List<WalletChoice> = listOf(bank),
        default: String? = null,
        create: suspend (TransactionDraft) -> CreateResult = { created += it; CreateResult.Created("new") },
        save: suspend (String, TransactionDraft, Long, Transaction?) -> SaveResult = { i, d, v, last -> saved += Triple(i, d, v); savedLastSeen += last; SaveResult.Saved },
        delete: suspend (String) -> SaveResult = { calls += "delete"; SaveResult.Saved },
    ) = TransactionEditorViewModel(
        transactionId = id,
        walletId = walletId,
        observeTransaction = { _, _ -> view },
        writableWallets = flowOf(wallets),
        defaultWalletId = { default },
        destinations = { flowOf(destinations) },
        categoryChoices = { kind, current -> categories.observeChoices(com.fares.saveme.feature.categories.domain.model.CategoryKind.fromWire(kind.wire)!!, current) },
        displays = { categories.observeDisplays(it) },
        create = create,
        save = save,
        delete = delete,
        keepMine = { calls += "keepMine" },
        useTheirs = { calls += "useTheirs" },
        tryAgain = { calls += "tryAgain" },
        discard = { calls += "discard" },
        today = { today },
    )

    private fun existing(
        id: String = "t1",
        amount: Long = 4_550,
        sync: TransactionSyncState = TransactionSyncState.None,
        canEdit: Boolean = true,
        canDelete: Boolean = true,
        kind: String = "expense",
        counterWalletId: String? = null,
        category: String? = "c1",
        currency: String = "EGP",
        walletCurrency: String = currency,
        pendingDelete: Boolean = false,
    ): TransactionView {
        val base = viewOf(id = id, amount = amount, kind = kind, counterWalletId = counterWalletId, category = category, sync = sync, pendingDelete = pendingDelete)
        return base.copy(
            server = Transaction(id, "w1", counterWalletId, kind, LocalDate(2026, 10, 1), amount, currency, category, null, null, null, false, "u1", null, null, 7, base.createdAt, base.createdAt),
            shown = base.shown.copy(currency = currency),
            canEdit = canEdit, canDelete = canDelete, walletCurrency = walletCurrency,
            counterpart = counterWalletId?.let { Counterpart.Named("Bank") },
        )
    }

    @Test
    fun createModeOpensWithTheKeysWalletExpenseTodayAndAnEmptyAmount() = runMviTest {
        val vm = vm(walletId = "w2")
        val state = vm.state.value
        assertTrue(state.loaded && state.isCreate)
        assertEquals("w2", state.walletId)
        assertEquals("expense", state.kindWire)
        assertEquals(today, state.date)
        assertEquals("", state.amountText)
        assertEquals("EGP", state.currency)
        assertEquals(listOf("c1", "c2"), state.categoryChoices!!.own.map { it.id })
    }

    @Test
    fun homesPreselectionIsTheDefaultWalletAndAKeyWalletThatCantBeWrittenFallsBack() = runMviTest {
        assertEquals("w3", vm(default = "w3").state.value.walletId)
        assertEquals("w1", vm(walletId = "gone").state.value.walletId)
        assertEquals("w3", vm(walletId = "gone", default = "w3").state.value.walletId)
    }

    @Test
    fun withNoWritableWalletTheEditorSaysSoAndOffersToCreateOne() = runMviTest {
        val vm = vm(wallets = emptyList())
        assertTrue(vm.state.value.noWallet)
        assertFalse(vm.state.value.canSave)
        vm.effects.test {
            vm.onIntent(TransactionEditorIntent.CreateWalletClicked)
            assertEquals(TransactionEditorEffect.OpenWalletEditor, awaitItem())
        }
    }

    @Test
    fun theCategorySheetIsFedByTheChoicesForTheKindAndTheKindSwitchChangesThem() = runMviTest {
        val vm = vm()
        vm.onIntent(TransactionEditorIntent.CategoryClicked)
        assertTrue(vm.state.value.categorySheetOpen)
        vm.onIntent(TransactionEditorIntent.CategoryChosen("d1"))
        assertEquals("d1", vm.state.value.categoryId)
        assertFalse(vm.state.value.categorySheetOpen)
        vm.onIntent(TransactionEditorIntent.KindChosen(TransactionKind.Income))
        assertNull(vm.state.value.categoryId)
        assertEquals(listOf("ci"), vm.state.value.categoryChoices!!.own.map { it.id })
        // A partner's category is never in the list, so it can't be chosen.
        vm.onIntent(TransactionEditorIntent.CategoryChosen("partner"))
        assertNull(vm.state.value.categoryId)
    }

    @Test
    fun theDatePickerResultIsKept() = runMviTest {
        val vm = vm()
        vm.onIntent(TransactionEditorIntent.DateClicked)
        assertTrue(vm.state.value.datePickerOpen)
        vm.onIntent(TransactionEditorIntent.DateChosen(LocalDate(2026, 9, 30)))
        assertEquals(LocalDate(2026, 9, 30), vm.state.value.date)
        assertFalse(vm.state.value.datePickerOpen)
    }

    @Test
    fun saveBuildsTheDraftFromWhatWasTypedAndClosesOnSuccess() = runMviTest {
        val vm = vm()
        vm.onIntent(TransactionEditorIntent.AmountChanged("45.50"))
        vm.onIntent(TransactionEditorIntent.CategoryChosen("c1"))
        vm.onIntent(TransactionEditorIntent.NoteChanged("Lunch"))
        vm.onIntent(TransactionEditorIntent.MoreToggled)
        vm.onIntent(TransactionEditorIntent.PersonChanged("Ahmed"))
        vm.onIntent(TransactionEditorIntent.ClearedToggled(true))
        vm.effects.test {
            vm.onIntent(TransactionEditorIntent.SaveClicked)
            assertEquals(TransactionEditorEffect.Close, awaitItem())
        }
        val draft = created.single()
        assertEquals(4_550, draft.amountMinor)
        assertEquals("c1", draft.categoryId)
        assertEquals("Lunch", draft.description)
        assertEquals("Ahmed", draft.counterparty)
        assertEquals(true, draft.cleared)
        assertEquals(today, draft.date)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun aBadAmountIsReportedAndNothingIsSent() = runMviTest {
        val vm = vm()
        vm.onIntent(TransactionEditorIntent.CategoryChosen("c1"))
        vm.onIntent(TransactionEditorIntent.SaveClicked)
        assertEquals(TransactionFieldProblem.AmountEmpty, vm.state.value.errors[TransactionField.Amount])
        vm.onIntent(TransactionEditorIntent.AmountChanged("0"))
        vm.onIntent(TransactionEditorIntent.SaveClicked)
        assertEquals(TransactionFieldProblem.AmountNotPositive, vm.state.value.errors[TransactionField.Amount])
        assertTrue(created.isEmpty())
    }

    @Test
    fun unverifiedAndInvalidResultsAreRoutedAndTheEditorStaysOpen() = runMviTest {
        val vm = vm(create = { CreateResult.NotVerified })
        vm.onIntent(TransactionEditorIntent.AmountChanged("1"))
        vm.onIntent(TransactionEditorIntent.CategoryChosen("c1"))
        vm.effects.test {
            vm.onIntent(TransactionEditorIntent.SaveClicked)
            assertEquals(TransactionEditorEffect.RequireVerification, awaitItem())
        }
        val invalid = vm(create = { CreateResult.Invalid(mapOf(TransactionField.Category to TransactionFieldProblem.CategoryRequired)) })
        invalid.onIntent(TransactionEditorIntent.AmountChanged("1"))
        invalid.onIntent(TransactionEditorIntent.CategoryChosen("c1"))
        invalid.onIntent(TransactionEditorIntent.SaveClicked)
        assertEquals(TransactionFieldProblem.CategoryRequired, invalid.state.value.errors[TransactionField.Category])
    }

    @Test
    fun aTransferHasADestinationChoiceInsteadOfACategory() = runMviTest {
        val vm = vm(destinations = listOf(bank))
        vm.onIntent(TransactionEditorIntent.KindChosen(TransactionKind.Transfer))
        assertFalse(vm.state.value.destinationsEmpty)
        vm.onIntent(TransactionEditorIntent.DestinationClicked)
        vm.onIntent(TransactionEditorIntent.DestinationChosen("w2"))
        vm.onIntent(TransactionEditorIntent.AmountChanged("500"))
        vm.onIntent(TransactionEditorIntent.SaveClicked)
        val draft = created.single()
        assertEquals("transfer", draft.kindWire)
        assertEquals("w2", draft.counterWalletId)
        assertNull(draft.categoryId)
        val empty = vm(destinations = emptyList())
        empty.onIntent(TransactionEditorIntent.KindChosen(TransactionKind.Transfer))
        assertTrue(empty.state.value.destinationsEmpty)
        assertFalse(empty.state.value.canSave)
    }

    @Test
    fun editModeStartsFromWhatThePhoneShowsWithKindAndWalletsReadOnlyAndTheBaseVersion() = runMviTest {
        val vm = vm(id = "t1", walletId = "w1", view = flowOf(existing(amount = 4_550)))
        val state = vm.state.value
        assertEquals("45.50", state.amountText)
        assertEquals("expense", state.kindWire)
        assertEquals(7, state.baseVersion)
        assertFalse(state.kindEditable)
        assertEquals("c1", state.categoryId)
        assertEquals("Groceries", state.category!!.name)
        vm.onIntent(TransactionEditorIntent.AmountChanged("60"))
        vm.onIntent(TransactionEditorIntent.SaveClicked)
        val (id, draft, base) = saved.single()
        assertEquals("t1", id)
        assertEquals(6_000, draft.amountMinor)
        assertEquals(7, base)
    }

    @Test
    fun theCurrentCategoryStaysSelectedEvenWhenItIsArchivedOrAPartnersOrUnknown() = runMviTest {
        categories.displays["x"] = display("x", "Old coffee", archived = true)
        val vm = vm(id = "t1", walletId = "w1", view = flowOf(existing(category = "x")))
        assertEquals("Old coffee", vm.state.value.category!!.name)
        assertEquals("x", vm.state.value.categoryChoices!!.current!!.id)
        val partner = vm(id = "t1", walletId = "w1", view = flowOf(existing(category = "someone-elses")))
        assertEquals(CategorySource.Unavailable, partner.state.value.category!!.source)
        assertEquals("someone-elses", partner.state.value.categoryId)
    }

    @Test
    fun aClashOffersKeepMineAndUseTheirsAndTheLatterReloadsTheFields() = runMviTest {
        val view = MutableStateFlow<TransactionView?>(existing())
        val vm = vm(id = "t1", walletId = "w1", view = view)
        val theirs = Transaction(
            "t1", "w1", null, "expense", LocalDate(2026, 10, 1), 9_000, "EGP", "c1", null, null, null, false, "u1", null, null, 9,
            Instant.parse("2026-10-01T09:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"),
        )
        view.value = existing(amount = 6_000, sync = TransactionSyncState.Clash(theirs, deletedElsewhere = false, mineIsDeletion = false))
        val clash = assertIs<EditorSync.Clash>(vm.state.value.sync)
        assertEquals(listOf(ClashField.Amount), clash.lines.map { it.field })
        vm.onIntent(TransactionEditorIntent.KeepMine)
        assertEquals(listOf("keepMine"), calls)
        view.value = existing(amount = 9_000)
        vm.onIntent(TransactionEditorIntent.UseTheirs)
        assertEquals("useTheirs", calls.last())
        assertEquals("90.00", vm.state.value.amountText)
    }

    @Test
    fun aTombstoneClashOffersRestoreWithMyChangeAndAcceptTheDeletion() = runMviTest {
        val view = MutableStateFlow<TransactionView?>(existing(sync = TransactionSyncState.Clash(null, deletedElsewhere = true, mineIsDeletion = false)))
        val vm = vm(id = "t1", walletId = "w1", view = view)
        val clash = assertIs<EditorSync.Clash>(vm.state.value.sync)
        assertTrue(clash.deletedElsewhere && !clash.isDelete)
        vm.onIntent(TransactionEditorIntent.RestoreWithMine)
        vm.onIntent(TransactionEditorIntent.AcceptDeletion)
        assertEquals(listOf("keepMine", "useTheirs"), calls)
    }

    @Test
    fun aDeleteClashOffersDeleteAnywayAndKeepTheTransaction() = runMviTest {
        val view = existing(pendingDelete = true, sync = TransactionSyncState.Clash(null, deletedElsewhere = false, mineIsDeletion = true))
        val vm = vm(id = "t1", walletId = "w1", view = flowOf(view))
        assertTrue(assertIs<EditorSync.Clash>(vm.state.value.sync).isDelete)
        vm.onIntent(TransactionEditorIntent.DeleteAnyway)
        vm.onIntent(TransactionEditorIntent.KeepTransaction)
        assertEquals(listOf("keepMine", "useTheirs"), calls)
    }

    @Test
    fun aRefusalOffersTryAgainOnlyForNotVerifiedAndDiscardAlways() = runMviTest {
        val notVerified = existing(sync = TransactionSyncState.Refused(RefusalReason(RefusalKind.NotVerified), emptyMap()))
        val vm = vm(id = "t1", walletId = "w1", view = flowOf(notVerified))
        assertTrue(assertIs<EditorSync.Refused>(vm.state.value.sync).reason.canRetryUnchanged)
        vm.onIntent(TransactionEditorIntent.TryAgain)
        vm.onIntent(TransactionEditorIntent.Discard)
        assertEquals(listOf("tryAgain", "discard"), calls)
        val other = existing(sync = TransactionSyncState.Refused(RefusalReason(RefusalKind.NoRightHere), emptyMap()))
        assertFalse(assertIs<EditorSync.Refused>(vm(id = "t1", walletId = "w1", view = flowOf(other)).state.value.sync).reason.canRetryUnchanged)
    }

    @Test
    fun editingAfterACurrencyChangedRefusalShowsTheWalletsCurrencyAndAnEmptyAmount() = runMviTest {
        val refused = existing(
            amount = 4_550, currency = "EGP", walletCurrency = "USD",
            sync = TransactionSyncState.Refused(RefusalReason(RefusalKind.CurrencyChanged), mapOf(TransactionField.Currency to "CURRENCY_MISMATCH")),
        )
        val vm = vm(id = "t1", walletId = "w1", view = flowOf(refused))
        assertEquals("USD", vm.state.value.currency)
        assertEquals("", vm.state.value.amountText)
        assertTrue(vm.state.value.currencyChanged)
        vm.onIntent(TransactionEditorIntent.AmountChanged("5"))
        assertFalse(vm.state.value.currencyChanged)
    }

    @Test
    fun deleteAsksForConfirmationThenDeletesAndCloses() = runMviTest {
        val vm = vm(id = "t1", walletId = "w1", view = flowOf(existing()))
        vm.onIntent(TransactionEditorIntent.AmountChanged("99"))
        vm.onIntent(TransactionEditorIntent.DeleteClicked)
        assertTrue(vm.state.value.confirmDelete)
        // The confirmation names what the transaction was when the editor opened, not what was typed since.
        assertEquals(4_550, vm.state.value.shownAmountMinor)
        vm.onIntent(TransactionEditorIntent.DeleteCancelled)
        assertFalse(vm.state.value.confirmDelete)
        vm.onIntent(TransactionEditorIntent.DeleteClicked)
        vm.effects.test {
            vm.onIntent(TransactionEditorIntent.DeleteConfirmed)
            assertEquals(TransactionEditorEffect.Close, awaitItem())
        }
        assertEquals(listOf("delete"), calls)
        assertFalse(vm.state.value.confirmDelete)
    }

    @Test
    fun aViewerSeesTheTransactionReadOnlyWithNoSaveOrDelete() = runMviTest {
        val vm = vm(id = "t1", walletId = "w1", view = flowOf(existing(canEdit = false, canDelete = false)))
        val state = vm.state.value
        assertEquals(EditorMode.ReadOnly, state.mode)
        assertFalse(state.canSave)
        assertFalse(state.canDelete)
        vm.onIntent(TransactionEditorIntent.DeleteClicked)
        assertFalse(vm.state.value.confirmDelete)
        vm.onIntent(TransactionEditorIntent.SaveClicked)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun aTransferShowsBothWalletsReadOnlyInEditMode() = runMviTest {
        val vm = vm(id = "t1", walletId = "w1", view = flowOf(existing(kind = "transfer", counterWalletId = "w2", category = null)))
        assertEquals("w2", vm.state.value.destinationId)
        assertEquals("Bank", vm.state.value.destinationName)
        vm.onIntent(TransactionEditorIntent.DestinationClicked)
        assertFalse(vm.state.value.destinationSheetOpen)
    }

    @Test
    fun theMissingTransactionClosesTheEditorWithAMessageButNotAfterThePersonsOwnChoice() = runMviTest {
        val gone = vm(id = "t1", walletId = "w1", view = flowOf(null))
        gone.effects.test {
            assertEquals(TransactionEditorEffect.ShowMessage(EditorMessage.Gone), awaitItem())
            assertEquals(TransactionEditorEffect.Close, awaitItem())
        }
        val view = MutableStateFlow<TransactionView?>(existing(sync = TransactionSyncState.Clash(null, deletedElsewhere = true, mineIsDeletion = false)))
        val vm = vm(id = "t1", walletId = "w1", view = view)
        vm.onIntent(TransactionEditorIntent.AcceptDeletion)
        vm.effects.test {
            view.value = null
            assertEquals(TransactionEditorEffect.Close, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun aTransactionRemovedWhileThePersonTypesKeepsTheTypingAndSavesOnTheCopyTheyOpened() = runMviTest {
        val view = MutableStateFlow<TransactionView?>(existing())
        val vm = vm(id = "t1", walletId = "w1", view = view)
        vm.onIntent(TransactionEditorIntent.AmountChanged("60"))
        vm.effects.test {
            // A refresh found it deleted elsewhere: nothing closes, the typing stays, Delete is no longer offered.
            view.value = null
            expectNoEvents()
            assertEquals("60", vm.state.value.amountText)
            assertFalse(vm.state.value.canDelete)
            assertTrue(vm.state.value.canSave)

            vm.onIntent(TransactionEditorIntent.SaveClicked)
            assertEquals(TransactionEditorEffect.Close, awaitItem())
        }
        val (id, draft, base) = saved.single()
        assertEquals("t1", id)
        assertEquals(6_000, draft.amountMinor)
        assertEquals(7, base)
        // The server copy the editor opened on travels with the save, so the change can wait on it.
        assertEquals(7, savedLastSeen.single()!!.version)
    }

    @Test
    fun aTransactionRemovedWithNothingTypedClosesWithTheMessageAndASaveOfAGoneOneSaysSo() = runMviTest {
        val view = MutableStateFlow<TransactionView?>(existing())
        val vm = vm(id = "t1", walletId = "w1", view = view)
        vm.effects.test {
            view.value = null
            assertEquals(TransactionEditorEffect.ShowMessage(EditorMessage.Gone), awaitItem())
            assertEquals(TransactionEditorEffect.Close, awaitItem())
        }
        assertNull(savedLastSeen.firstOrNull())

        val other = vm(id = "t1", walletId = "w1", view = flowOf(existing()), save = { _, _, _, _ -> SaveResult.NoLongerAvailable })
        other.effects.test {
            other.onIntent(TransactionEditorIntent.SaveClicked)
            assertEquals(TransactionEditorEffect.ShowMessage(EditorMessage.Gone), awaitItem())
            assertEquals(TransactionEditorEffect.Close, awaitItem())
        }
    }

    @Test
    fun addedByAndTheRecurringRuleCaptionsComeFromTheView() = runMviTest {
        val view = existing().copy(addedBy = AddedBy.Member("Omar"), fromRecurringRule = true)
        val state = vm(id = "t1", walletId = "w1", view = flowOf(view)).state.value
        assertEquals(AddedBy.Member("Omar"), state.addedBy)
        assertTrue(state.fromRecurringRule)
    }
}
