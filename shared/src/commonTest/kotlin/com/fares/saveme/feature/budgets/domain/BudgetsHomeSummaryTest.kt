package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetJson
import com.fares.saveme.feature.budgets.expense
import com.fares.saveme.feature.budgets.profileView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Home's "Budgets" card (FR-017, research R-11). */
class BudgetsHomeSummaryTest {

    @Test
    fun upToThreeActiveBudgetsHighestShareFirstThenOldest() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("low", categoryId = "c1", amount = 1_000_000, createdAt = "2026-09-01T00:00:00Z"))
        env.seed(budgetJson("high", categoryId = "c2", amount = 100_000, createdAt = "2026-09-03T00:00:00Z"))
        env.seed(budgetJson("tieOld", categoryId = null, amount = 100_000, createdAt = "2026-09-02T00:00:00Z"))
        env.seed(budgetJson("tieNew", categoryId = "d1", amount = 100_000, createdAt = "2026-09-04T00:00:00Z"))
        env.seed(budgetJson("archived", categoryId = "c1", archivedAt = "2026-09-20T00:00:00Z", amount = 100_000))
        env.spending.set(expense("a", "2026-10-01", 50_000, category = "c2"), expense("b", "2026-10-01", 50_000, category = "d1"), expense("c", "2026-10-01", 10_000, category = "c1"))
        val top = BudgetsHomeSummary(env.service).observeTop(3).first()
        // c2 and d1 are 50%, all expenses is 110,000 of 100,000 = 110%, then the oldest of the ties.
        assertEquals(listOf("tieOld", "high", "tieNew"), top.map { it.id })
        assertEquals(110_000, top.first().spentMinor)
        assertTrue("archived" !in top.map { it.id })
    }

    @Test
    fun theNameIsNullForAllExpensesAndTheAppLanguageNameOtherwise() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("all", categoryId = null))
        env.seed(budgetJson("cat", categoryId = "c1", createdAt = "2026-09-02T00:00:00Z"))
        env.seed(budgetJson("lost", categoryId = "zz", createdAt = "2026-09-03T00:00:00Z"))
        val top = BudgetsHomeSummary(env.service).observeTop(3).first().associateBy { it.id }
        assertNull(top.getValue("all").categoryName)
        assertEquals("Groceries", top.getValue("cat").categoryName)
        assertEquals("", top.getValue("lost").categoryName)
        assertEquals("EGP", top.getValue("cat").currency)
    }

    @Test
    fun budgetsWithAnUnknownPeriodComeAfterTheOthersWithoutFigures() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.profile.value = profileView(startDay = 1, from = LocalDate(2026, 11, 1))
        env.seed(budgetJson("a", categoryId = "c1"))
        env.seedProgress("a", start = "2026-09-25", end = "2026-10-31", spent = 1_000, local = 0)
        env.seed(budgetJson("odd", categoryId = null, periodType = "weekly"))
        val known = BudgetsHomeSummary(env.service).observeTop(3).first()
        assertEquals(listOf("a", "odd"), known.map { it.id })
        assertNull(known.last().spentMinor)
        // Without a stored period for today the whole card has no figures.
        env.progressDao.rows.value = emptyMap()
        assertTrue(BudgetsHomeSummary(env.service).observeTop(3).first().all { it.spentMinor == null })
    }

    @Test
    fun aGlimpseSaysWhenItsFigureIncludesAWaitingChange() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("a", categoryId = "c1"))
        env.spending.snapshot.value = com.fares.saveme.feature.transactions.domain.SpendingSnapshot(
            listOf(expense("n", "2026-10-02", 200, waiting = true)), emptyList(), listOf(com.fares.saveme.feature.budgets.touch("2026-10-02")),
        )
        assertEquals(true, BudgetsHomeSummary(env.service).observeTop(3).first().single().includesWaiting)
    }

    @Test
    fun nothingActiveMeansAnEmptyCardList() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        assertEquals(emptyList(), BudgetsHomeSummary(env.service).observeTop(3).first())
    }

    @Test
    fun theAllExpensesBudgetStaysEvenWhenOthersRankHigher() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("all", categoryId = null, amount = 10_000_000, createdAt = "2026-09-01T00:00:00Z"))
        env.seed(budgetJson("c1", categoryId = "c1", amount = 10_000, createdAt = "2026-09-02T00:00:00Z"))
        env.seed(budgetJson("c2", categoryId = "c2", amount = 10_000, createdAt = "2026-09-03T00:00:00Z"))
        env.spending.set(expense("a", "2026-10-01", 9_000, category = "c1"), expense("b", "2026-10-01", 9_000, category = "c2"))
        val top = BudgetsHomeSummary(env.service).observeTop(2).first()
        assertEquals(2, top.size)
        assertTrue("all" in top.map { it.id }, "the ring card needs it")
    }
}
