package com.fares.saveme.feature.transactions.ui.editor

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.transactions.FakeCategories
import com.fares.saveme.feature.transactions.domain.ConfirmFromDraftResult
import com.fares.saveme.feature.transactions.domain.DueItem
import com.fares.saveme.feature.transactions.domain.DueState
import com.fares.saveme.feature.transactions.domain.model.CreateResult
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import com.fares.saveme.feature.transactions.viewOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val day = LocalDate(2026, 10, 2)
private val cashWallet = WalletChoice("w1", "Cash", "EGP")
private val bankWallet = WalletChoice("w2", "Bank", "EGP")

private fun rent(id: String = "o1") = DueItem(
    id = id, walletId = "w1", ruleId = "r1", description = "Rent", date = LocalDate(2026, 10, 1), amountMinor = 450_000,
    currency = "EGP", kindWire = "expense", state = DueState.Due,
)

class TransactionEditorRecurringTest {
    private val created = mutableListOf<TransactionDraft>()
    private val confirmed = mutableListOf<Pair<String, TransactionDraft>>()
    private val matchQueries = mutableListOf<List<Any?>>()
    private val categories = FakeCategories()
    private val matchFlow = MutableStateFlow(emptyList<DueItem>())
    private val ruleFlow = MutableStateFlow(false)
    private var confirmResult: ConfirmFromDraftResult = ConfirmFromDraftResult.Confirmed("o1")

    private fun vm(
        id: String? = null,
        view: Flow<com.fares.saveme.feature.transactions.domain.model.TransactionView?> = flowOf(null),
        canEdit: Boolean = true,
    ) = TransactionEditorViewModel(
        transactionId = id,
        walletId = null,
        observeTransaction = { _, _ -> view },
        writableWallets = flowOf(if (canEdit) listOf(cashWallet, bankWallet) else emptyList()),
        defaultWalletId = { null },
        destinations = { flowOf(listOf(bankWallet)) },
        categoryChoices = { kind, current -> categories.observeChoices(CategoryKind.fromWire(kind.wire)!!, current) },
        displays = { categories.observeDisplays(it) },
        create = { created += it; CreateResult.Created("new") },
        save = { _, _, _, _ -> error("not used") },
        delete = { error("not used") },
        keepMine = {}, useTheirs = {}, tryAgain = {}, discard = {},
        today = { day },
        matches = { wallet, kind, category, destination, date -> matchQueries += listOf(wallet, kind, category, destination, date); matchFlow },
        confirmFromDraft = { occurrence, draft -> confirmed += occurrence to draft; confirmResult },
        ruleOnPhone = { ruleFlow },
    )

    @Test
    fun makeRecurringHandsOverTheFormAndRecordsNothing() = runMviTest {
        val store = vm()
        store.onIntent(TransactionEditorIntent.AmountChanged("4500"))
        store.onIntent(TransactionEditorIntent.CategoryChosen("c1"))
        store.onIntent(TransactionEditorIntent.NoteChanged("Rent"))
        store.effects.test {
            store.onIntent(TransactionEditorIntent.MakeRecurringClicked)
            val effect = awaitItem() as TransactionEditorEffect.MakeRecurring
            assertEquals("w1", effect.draft.walletId)
            assertEquals(TransactionKind.Expense, effect.draft.kind)
            assertEquals(450_000, effect.draft.amountMinor)
            assertEquals("c1", effect.draft.categoryId)
            assertEquals("Rent", effect.draft.description)
            assertEquals(day, effect.draft.date)
            assertFalse(effect.draft.toString().contains("Rent"))
        }
        assertTrue(created.isEmpty())
    }

    @Test
    fun makeRecurringWithoutAnAmountHandsOverZeroAndABadAmountIsReported() = runMviTest {
        val store = vm()
        store.effects.test {
            store.onIntent(TransactionEditorIntent.MakeRecurringClicked)
            assertEquals(0, (awaitItem() as TransactionEditorEffect.MakeRecurring).draft.amountMinor)
            store.onIntent(TransactionEditorIntent.AmountChanged("1.234"))
            store.onIntent(TransactionEditorIntent.MakeRecurringClicked)
            expectNoEvents()
        }
    }

    @Test
    fun makeRecurringIsOnlyOfferedWhileRecordingToSomeoneWhoCanRecord() = runMviTest {
        assertTrue(vm().state.value.canMakeRecurring)
        assertFalse(vm(canEdit = false).state.value.canMakeRecurring)
        val editing = vm(id = "t1", view = flowOf(viewOf("t1")))
        assertFalse(editing.state.value.canMakeRecurring)
        editing.effects.test {
            editing.onIntent(TransactionEditorIntent.MakeRecurringClicked)
            expectNoEvents()
        }
    }

    @Test
    fun theOfferFollowsWhatIsTypedAndShowsAtMostThree() = runMviTest {
        val store = vm()
        store.onIntent(TransactionEditorIntent.CategoryChosen("c1"))
        val last = matchQueries.last()
        assertEquals(listOf("w1", "expense", "c1", null, day), last)
        matchFlow.value = listOf(rent("o1"), rent("o2"), rent("o3"), rent("o4"))
        assertEquals(listOf("o1", "o2", "o3"), store.state.value.matches.map { it.id })
        store.onIntent(TransactionEditorIntent.DateChosen(LocalDate(2026, 10, 5)))
        assertEquals(LocalDate(2026, 10, 5), matchQueries.last()[4])
    }

    @Test
    fun acceptingTheOfferConfirmsWithTheTypedValuesAndRecordsNothing() = runMviTest {
        val store = vm()
        store.onIntent(TransactionEditorIntent.CategoryChosen("c1"))
        store.onIntent(TransactionEditorIntent.AmountChanged("4400"))
        store.onIntent(TransactionEditorIntent.NoteChanged("Rent Oct"))
        matchFlow.value = listOf(rent())
        store.effects.test {
            store.onIntent(TransactionEditorIntent.MatchClicked("o1"))
            assertEquals(TransactionEditorEffect.OpenOccurrence("o1"), awaitItem())
        }
        val (occurrence, draft) = confirmed.single()
        assertEquals("o1", occurrence)
        assertEquals(440_000, draft.amountMinor)
        assertEquals("Rent Oct", draft.description)
        assertEquals(day, draft.date)
        assertTrue(created.isEmpty())
    }

    @Test
    fun ignoringTheOfferRecordsNormally() = runMviTest {
        val store = vm()
        store.onIntent(TransactionEditorIntent.CategoryChosen("c1"))
        store.onIntent(TransactionEditorIntent.AmountChanged("10"))
        matchFlow.value = listOf(rent())
        store.effects.test {
            store.onIntent(TransactionEditorIntent.SaveClicked)
            assertEquals(TransactionEditorEffect.Close, awaitItem())
        }
        assertEquals(1, created.size)
        assertTrue(confirmed.isEmpty())
    }

    @Test
    fun theOfferNeedsAValidAmountAndAnAnswerOtherThanConfirmedIsReported() = runMviTest {
        val store = vm()
        store.onIntent(TransactionEditorIntent.CategoryChosen("c1"))
        matchFlow.value = listOf(rent())
        store.onIntent(TransactionEditorIntent.MatchClicked("o1"))
        assertTrue(confirmed.isEmpty())
        store.onIntent(TransactionEditorIntent.AmountChanged("10"))
        confirmResult = ConfirmFromDraftResult.NotVerified
        store.effects.test {
            store.onIntent(TransactionEditorIntent.MatchClicked("o1"))
            assertEquals(TransactionEditorEffect.RequireVerification, awaitItem())
            confirmResult = ConfirmFromDraftResult.NotAllowed
            store.onIntent(TransactionEditorIntent.MatchClicked("o1"))
            assertEquals(TransactionEditorEffect.ShowMessage(EditorMessage.NotAllowed), awaitItem())
        }
    }

    @Test
    fun aProducedTransactionOffersTheRuleOnlyWhenItIsOnThePhone() = runMviTest {
        val produced = viewOf("t1").copy(fromRecurringRule = true, recurringRuleId = "r1")
        val store = vm(id = "t1", view = flowOf(produced))
        assertEquals("r1", store.state.value.ruleId)
        assertFalse(store.state.value.canOpenRule)
        ruleFlow.value = true
        assertTrue(store.state.value.canOpenRule)
        store.effects.test {
            store.onIntent(TransactionEditorIntent.OpenRuleClicked)
            assertEquals(TransactionEditorEffect.OpenRule("r1"), awaitItem())
        }
    }

    @Test
    fun anOrdinaryTransactionHasNoRuleToOpen() = runMviTest {
        val store = vm(id = "t1", view = flowOf(viewOf("t1")))
        ruleFlow.value = true
        assertNull(store.state.value.ruleId)
        assertFalse(store.state.value.canOpenRule)
    }
}
