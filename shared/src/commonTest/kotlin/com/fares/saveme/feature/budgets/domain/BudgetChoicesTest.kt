package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetJson
import com.fares.saveme.feature.budgets.profileView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the editor offers (FR-003, FR-004). */
class BudgetChoicesTest {

    private val user = SyncScope(ScopeKind.User, "u1")

    @Test
    fun theCategoriesAreC5sExpenseChoices() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        val choices = env.service.observeChoices("EGP", null).first()
        assertEquals(listOf("c1", "c2"), choices.own.map { it.id })
        assertEquals(listOf("d1"), choices.defaults.map { it.id })
        assertEquals(null, choices.current)
    }

    @Test
    fun theCurrentCategoryStaysListedWhateverItsState() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.categories.displays["gone"] = com.fares.saveme.feature.budgets.categoryDisplay("gone", "Old")
        assertEquals("gone", env.service.observeChoices("EGP", "gone").first().current?.id)
    }

    @Test
    fun currenciesAreTheWalletsPlusTheProfileDefaultWithTheDefaultFirstAndSelected() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.profile.value = profileView(currency = "KWD")
        val choices = env.service.observeChoices("KWD", null).first()
        assertEquals(listOf("KWD", "EGP", "USD"), choices.currencies)
        assertEquals("KWD", choices.defaultCurrency)
    }

    @Test
    fun withoutAProfileTheFirstWalletCurrencyIsTheDefaultAndNothingTheFallback() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.profile.value = null
        assertEquals("EGP", env.service.observeChoices("EGP", null).first().defaultCurrency)
        env.wallets.list.value = env.wallets.list.value.copy(active = emptyList())
        assertEquals(DefaultBudgetsService.FALLBACK_CURRENCY, env.service.observeChoices("EGP", null).first().defaultCurrency)
    }

    @Test
    fun anArchivedWalletsCurrencyIsStillOffered() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.wallets.list.value = env.wallets.list.value.copy(archived = listOf(com.fares.saveme.feature.budgets.walletView("old", "GBP", archived = true)))
        assertTrue("GBP" in env.service.observeChoices("EGP", null).first().currencies)
    }

    @Test
    fun alreadyBudgetedIsKeyedByCategoryOrAllForTheChosenCurrencyOnly() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", categoryId = "c1", currency = "EGP"))
        env.seed(budgetJson("b2", categoryId = null, currency = "EGP"))
        env.seed(budgetJson("b3", categoryId = "c2", currency = "USD"))
        val egp = env.service.observeChoices("EGP", null).first()
        assertEquals(setOf("c1", BudgetValidation.ALL_KEY), egp.alreadyBudgeted)
        assertTrue(egp.isAlreadyBudgeted("c1"))
        assertTrue(egp.isAlreadyBudgeted(null))
        assertFalse(egp.isAlreadyBudgeted("c2"))
        assertEquals("EGP", egp.forCurrency)
        val usd = env.service.observeChoices("USD", null).first()
        assertEquals(setOf("c2"), usd.alreadyBudgeted)
    }

    @Test
    fun archivedDeletedAndWaitingBudgetsAreReadWithTheirChangesApplied() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("archived", categoryId = "c1", archivedAt = "2026-09-20T00:00:00Z"))
        env.seed(budgetJson("deleted", categoryId = "c2", version = 2))
        env.engine.enqueueDelete("budget", "deleted", user, 2)
        env.repository.insertPlaceholder("waiting", "u1", "d1", "EGP", 100)
        env.engine.enqueue("budget", "waiting", user, 0, """{"categoryId":"d1","currency":"EGP","amountMinor":100}""")
        assertEquals(setOf("d1"), env.service.observeChoices("EGP", null).first().alreadyBudgeted)
    }

    @Test
    fun theStartDayComesFromTheServersProfileCopy() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.profile.value = profileView(startDay = 25)
        assertEquals(25, env.service.observeChoices("EGP", null).first().startDay)
        assertEquals(25, env.service.observeStartDay().first())
        env.profile.value = null
        assertEquals(1, env.service.observeStartDay().first())
    }
}
