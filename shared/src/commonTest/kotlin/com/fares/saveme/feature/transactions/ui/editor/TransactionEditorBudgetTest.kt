package com.fares.saveme.feature.transactions.ui.editor

import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.transactions.FakeCategories
import com.fares.saveme.feature.transactions.domain.BudgetChoice
import com.fares.saveme.feature.transactions.domain.model.CreateResult
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.domain.model.WalletChoice
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun category(id: String, name: String, source: CategorySource = CategorySource.Own, key: String? = null) =
    CategoryDisplay(id, name, null, null, "expense", source, false, key)

private val groceries = category("c1", "Groceries")
private val transport = category("c2", "Transport")
private val dining = category("d1", "Dining", CategorySource.Default)
private val other = category("d9", "Other", CategorySource.Default, OTHER_EXPENSE_KEY)

/** Her category, named by the budget's list; not one of this person's own. */
private val partnersRent = category("p1", "Rent", CategorySource.Partner)

private val home = BudgetChoice("b1", "Home", "EGP", listOf(groceries, partnersRent), frequent = true)
private val trip = BudgetChoice("b2", "Trip", "USD", listOf(dining), frequent = false)

private val all = CategoryChoices(current = null, own = listOf(groceries, transport), defaults = listOf(dining, other))

class TransactionEditorBudgetTest {
    private val created = mutableListOf<TransactionDraft>()
    private val categories = FakeCategories().also {
        it.own.value = listOf(groceries, transport)
        it.defaults.value = listOf(dining, other)
    }

    private fun vm(budgets: List<BudgetChoice> = listOf(home, trip)) = TransactionEditorViewModel(
        transactionId = null,
        walletId = null,
        observeTransaction = { _, _ -> flowOf(null) },
        writableWallets = flowOf(listOf(WalletChoice("w1", "Cash", "EGP"))),
        defaultWalletId = { null },
        destinations = { flowOf(emptyList()) },
        categoryChoices = { kind, current -> categories.observeChoices(CategoryKind.fromWire(kind.wire)!!, current) },
        displays = { categories.observeDisplays(it) },
        create = { created += it; CreateResult.Created("new") },
        save = { _, _, _, _ -> error("not used") },
        delete = { error("not used") },
        keepMine = {}, useTheirs = {}, tryAgain = {}, discard = {},
        today = { LocalDate(2026, 10, 2) },
        budgets = flowOf(budgets),
    )

    @Test
    fun aBudgetLimitsCategoriesToItsListAndOther() {
        val limited = all.limitedTo(home)
        assertEquals(listOf("c1", "p1"), limited.own.map { it.id }) // her Rent is offered, Transport is not
        assertEquals(listOf("d9"), limited.defaults.map { it.id }) // Dining is not in the list; Other always is
    }

    @Test
    fun theBudgetsCurrencyMustMatchTheWallet() = runMviTest {
        val store = vm()
        assertEquals(listOf("b1"), store.state.value.budgetsForWallet.map { it.id })
    }

    @Test
    fun choosingABudgetDropsACategoryThatDoesNotFit() = runMviTest {
        val store = vm()
        store.onIntent(TransactionEditorIntent.CategoryChosen("c2"))
        store.onIntent(TransactionEditorIntent.BudgetChosen("b1"))
        assertEquals("b1", store.state.value.budgetId)
        assertNull(store.state.value.categoryId)
        assertEquals(listOf("c1", "p1"), store.state.value.visibleCategoryChoices!!.own.map { it.id })
    }

    @Test
    fun aCategoryOfTheListIsKeptAndPartnersCategoryCanBeChosen() = runMviTest {
        val store = vm()
        store.onIntent(TransactionEditorIntent.BudgetChosen("b1"))
        store.onIntent(TransactionEditorIntent.CategoryChosen("p1"))
        assertEquals("p1", store.state.value.categoryId)
        store.onIntent(TransactionEditorIntent.BudgetChosen("b1"))
        assertEquals("p1", store.state.value.categoryId)
    }

    @Test
    fun takingTheBudgetOutDropsPartnersCategoryAndAsksAgain() = runMviTest {
        val store = vm()
        store.onIntent(TransactionEditorIntent.BudgetChosen("b1"))
        store.onIntent(TransactionEditorIntent.CategoryChosen("p1"))
        store.onIntent(TransactionEditorIntent.BudgetChosen(null))
        assertNull(store.state.value.budgetId)
        assertNull(store.state.value.categoryId)
        assertEquals(all.own.map { it.id }, store.state.value.visibleCategoryChoices!!.own.map { it.id })
    }

    @Test
    fun onlyExpensesCanJoinABudget() = runMviTest {
        val store = vm()
        store.onIntent(TransactionEditorIntent.BudgetChosen("b1"))
        store.onIntent(TransactionEditorIntent.KindChosen(TransactionKind.Income))
        assertNull(store.state.value.budgetId)
        assertFalse(store.state.value.budgetVisible)
        store.onIntent(TransactionEditorIntent.BudgetChosen("b1"))
        assertNull(store.state.value.budgetId)
    }

    @Test
    fun theDraftCarriesTheBudget() = runMviTest {
        val store = vm()
        store.onIntent(TransactionEditorIntent.AmountChanged("45"))
        store.onIntent(TransactionEditorIntent.BudgetChosen("b1"))
        store.onIntent(TransactionEditorIntent.CategoryChosen("c1"))
        store.onIntent(TransactionEditorIntent.SaveClicked)
        assertEquals("b1", created.single().sharedBudgetId)
    }

    @Test
    fun withoutBudgetsThePickerStaysHidden() = runMviTest {
        val store = vm(budgets = emptyList())
        assertFalse(store.state.value.budgetVisible)
        assertTrue(store.state.value.budgetEditable)
    }
}
