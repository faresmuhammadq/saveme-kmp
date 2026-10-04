package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetJson
import com.fares.saveme.feature.budgets.domain.model.FigureSource
import com.fares.saveme.feature.budgets.expense
import com.fares.saveme.feature.budgets.progressJson
import com.fares.saveme.feature.budgets.progressPage
import com.fares.saveme.feature.budgets.touch
import com.fares.saveme.feature.transactions.domain.SpendingSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The brief's checkpoint on the phone: instant offline movement, server figures after sync, no jump (US3-S1 to S3, SC-002). */
class BudgetsOfflineToServerTest {

    private suspend fun figures(env: BudgetTestEnv) = env.service.observeOverview().first().active.single().figures!!

    @Test
    fun readThenOfflineExpenseThenAcceptedThenReadAgain() = runTest {
        // The server answers 50,200 once the expense is accepted, covering 5,000 the phone does not hold.
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(200, progressPage(listOf(progressJson("b1", spent = 50_200)))) }
        env.signedIn()
        env.seed(budgetJson("b1", categoryId = "c1", amount = 600_000))
        // 1. A read: the server says 50,000 (the phone holds 45,000 of it); nothing changed since, so the server's figure shows.
        env.spending.set(expense("e1", "2026-10-01", 45_000))
        env.seedProgress("b1", spent = 50_000, local = 45_000)
        val read = figures(env)
        assertEquals(FigureSource.Server, read.source)
        assertEquals(50_000, read.spentMinor)

        // 2. Offline, a 200 EGP Groceries expense: the budget moves at once and says a change waits.
        env.spending.snapshot.value = SpendingSnapshot(
            listOf(expense("e1", "2026-10-01", 45_000), expense("e2", "2026-10-02", 200, waiting = true)),
            listOf(expense("e1", "2026-10-01", 45_000)),
            listOf(touch("2026-10-02")),
        )
        val waiting = figures(env)
        assertEquals(FigureSource.Phone, waiting.source)
        assertEquals(45_200, waiting.spentMinor)
        assertEquals(true, waiting.includesWaiting)

        // 3. The server accepts it: the note goes and the figure does not jump back to the older server figure.
        env.spending.set(expense("e1", "2026-10-01", 45_000), expense("e2", "2026-10-02", 200))
        val accepted = figures(env)
        assertEquals(FigureSource.Phone, accepted.source)
        assertEquals(45_200, accepted.spentMinor)
        assertEquals(false, accepted.includesWaiting)

        // 4. The next progress read: the server's figures equal the sync and are shown.
        env.service.refresh(force = true)
        val reread = figures(env)
        assertEquals(FigureSource.Server, reread.source)
        assertEquals(50_200, reread.spentMinor)
        assertEquals(8, reread.percent)
    }

    @Test
    fun aNewSyncedExpenseFromAnotherPhoneNeverLeavesAStaleServerFigure() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", categoryId = "c1"))
        env.spending.set(expense("e1", "2026-10-01", 45_000))
        env.seedProgress("b1", spent = 45_000, local = 45_000)
        assertEquals(FigureSource.Server, figures(env).source)
        env.spending.set(expense("e1", "2026-10-01", 45_000), expense("e2", "2026-10-02", 1_000))
        val changed = figures(env)
        assertEquals(FigureSource.Phone, changed.source)
        assertEquals(46_000, changed.spentMinor)
    }

    @Test
    fun aLimitEditShowsThePhonesFiguresAtOnce() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", categoryId = "c1", amount = 600_000, version = 2))
        env.spending.set(expense("e1", "2026-10-01", 45_000))
        env.seedProgress("b1", spent = 45_000, local = 45_000)
        env.service.saveLimit("b1", 900_000, 2)
        val edited = figures(env)
        assertEquals(FigureSource.Phone, edited.source)
        assertEquals(900_000, edited.limitMinor)
        assertEquals(5, edited.percent)
    }

    @Test
    fun aBudgetCreatedOnThisPhoneShowsThePhonesEstimateUntilItSyncs() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.spending.set(expense("e1", "2026-10-01", 45_000))
        env.service.create(com.fares.saveme.feature.budgets.domain.model.BudgetDraft("c1", "EGP", 600_000))
        val estimate = figures(env)
        assertEquals(FigureSource.Phone, estimate.source)
        assertEquals(45_000, estimate.spentMinor)
    }

    @Test
    fun aTransactionSpendingInThePreviousPeriodAfterTheDayTurnsReadsTheNewPeriod() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", categoryId = "c1"))
        env.seedProgress("b1", start = "2026-10-01", end = "2026-10-31", spent = 45_000)
        env.spending.set(expense("e1", "2026-10-31", 45_000), expense("e2", "2026-11-01", 100))
        env.now.value = kotlin.time.Instant.parse("2026-11-01T10:00:00Z")
        val november = figures(env)
        assertEquals(FigureSource.Phone, november.source, "the stored period is October's, not the current one")
        assertEquals(100, november.spentMinor)
    }
}
