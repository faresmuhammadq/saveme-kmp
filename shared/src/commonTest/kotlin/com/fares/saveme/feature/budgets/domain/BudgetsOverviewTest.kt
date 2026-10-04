package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetJson
import com.fares.saveme.feature.budgets.domain.model.FigureSource
import com.fares.saveme.feature.budgets.domain.model.PeriodState
import com.fares.saveme.feature.budgets.domain.model.SyncState
import com.fares.saveme.feature.budgets.expense
import com.fares.saveme.feature.budgets.profileView
import com.fares.saveme.feature.categories.domain.model.CategorySource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The budgets screen's data: phone figures, lists, period line (FR-012 to FR-016, R-15). */
class BudgetsOverviewTest {

    private val user = SyncScope(ScopeKind.User, "u1")

    @Test
    fun theBriefsExampleSeesSpentLeftShareAndPace() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        // Day 25 since always: on 2 Oct it is day 8 of 25 Sep - 24 Oct.
        env.profile.value = profileView(startDay = 25)
        env.seed(budgetJson("b1", categoryId = "c1", amount = 600_000))
        env.spending.set(expense("e1", "2026-09-26", 200_000), expense("e2", "2026-10-01", 150_000), expense("x", "2026-09-24", 90_000))
        val overview = env.service.observeOverview().first()
        val period = assertIs<PeriodState.Known>(overview.period).period
        assertEquals(LocalDate(2026, 9, 25), period.start)
        assertEquals(LocalDate(2026, 10, 24), period.end)
        assertEquals(LocalDate(2026, 10, 2), overview.today)
        assertEquals(22, overview.daysLeft)
        val figures = assertNotNull(overview.active.single().figures)
        assertEquals(350_000, figures.spentMinor)
        assertEquals(250_000, figures.remainingMinor)
        assertEquals(58, figures.percent)
        assertEquals(1_312_500L, figures.projectedMinor)
        assertEquals(FigureSource.Phone, figures.source)
        assertEquals(false, figures.includesWaiting)
    }

    @Test
    fun activeBudgetsAreOldestFirstAndArchivedOnesAreSeparate() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("late", categoryId = "c2", createdAt = "2026-09-10T00:00:00Z"))
        env.seed(budgetJson("early", categoryId = "c1", createdAt = "2026-09-01T00:00:00Z"))
        env.seed(budgetJson("old", categoryId = null, archivedAt = "2026-09-20T00:00:00Z", createdAt = "2026-08-01T00:00:00Z"))
        val overview = env.service.observeOverview().first()
        assertEquals(listOf("early", "late"), overview.active.map { it.id })
        assertEquals(listOf("old"), overview.archived.map { it.id })
        assertTrue(overview.archived.single().figures != null, "archived budgets show current-period figures")
        assertEquals("Groceries", overview.active.first().category?.name)
        assertNull(overview.archived.single().category, "all expenses has no category")
    }

    @Test
    fun twoBudgetsForTheSameCategoryAndCurrencyFromTwoPhonesAreBothShownAndBothCount() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("first", categoryId = "c1", amount = 600_000, createdAt = "2026-09-01T00:00:00Z"))
        env.seed(budgetJson("second", categoryId = "c1", amount = 600_000, createdAt = "2026-09-02T00:00:00Z"))
        env.spending.set(expense("e1", "2026-10-01", 45_000))
        val overview = env.service.observeOverview().first()
        assertEquals(listOf("first", "second"), overview.active.map { it.id }, "nothing is merged or refused")
        assertEquals(listOf(45_000L, 45_000L), overview.active.map { it.figures!!.spentMinor })
    }

    @Test
    fun theDaysLeftAreTheEndMinusTodayAndZeroOnTheLastDay() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.now.value = kotlin.time.Instant.parse("2026-10-31T10:00:00Z")
        assertEquals(0, env.service.observeOverview().first().daysLeft)
        env.now.value = kotlin.time.Instant.parse("2026-10-01T10:00:00Z")
        assertEquals(30, env.service.observeOverview().first().daysLeft)
    }

    @Test
    fun noPaceBeforeSpendingAndOtherCurrenciesAreNamed() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", categoryId = "c1"))
        val none = env.service.observeOverview().first().active.single().figures!!
        assertEquals(0, none.spentMinor)
        assertEquals(0L, none.projectedMinor, "the math returns zero; the screen omits it when nothing is spent")
        env.spending.set(expense("u", "2026-10-02", 50_000, currency = "USD"))
        val others = env.service.observeOverview().first().active.single().figures!!
        assertEquals(0, others.spentMinor)
        assertEquals(listOf("USD"), others.otherCurrencies)
    }

    @Test
    fun overTheLimitIsNegativeRemainingAndOverLimit() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", categoryId = "c1", amount = 600_000))
        env.spending.set(expense("e1", "2026-10-01", 650_000))
        val figures = env.service.observeOverview().first().active.single().figures!!
        assertTrue(figures.overLimit)
        assertEquals(-50_000, figures.remainingMinor)
        assertEquals(108, figures.percent)
    }

    @Test
    fun aWaitingLimitEditIsShownAtOnceWithRecomputedFigures() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", categoryId = "c1", amount = 600_000, version = 2))
        env.spending.set(expense("e1", "2026-10-01", 300_000))
        env.engine.enqueue("budget", "b1", user, 2, """{"categoryId":"c1","currency":"EGP","amountMinor":1200000}""")
        val view = env.service.observeOverview().first().active.single()
        assertEquals(1_200_000, view.limitMinor)
        assertEquals(25, view.figures!!.percent)
        assertIs<SyncState.Waiting>(view.syncState)
    }

    @Test
    fun aWaitingArchiveMovesTheBudgetToArchivedAndAWaitingDeletionHidesIt() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("keep", categoryId = "c1", version = 2))
        env.seed(budgetJson("arch", categoryId = "c2", version = 2))
        env.seed(budgetJson("del", categoryId = null, version = 2))
        env.engine.enqueue("budget", "arch", user, 2, """{"categoryId":"c2","currency":"EGP","amountMinor":600000,"archive":true}""")
        env.engine.enqueueDelete("budget", "del", user, 2)
        val overview = env.service.observeOverview().first()
        assertEquals(listOf("keep"), overview.active.map { it.id })
        assertEquals(listOf("arch"), overview.archived.map { it.id })
        assertNull(env.service.observeBudget("del").first(), "a deletion that waits hides the budget everywhere")
    }

    @Test
    fun anUnknownPeriodTypeIsReadOnlyWithItsLimitAndNoFigures() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("w", categoryId = "c1", periodType = "weekly", amount = 123_000))
        env.seedProgress("w")
        val view = env.service.observeOverview().first().active.single()
        assertTrue(view.readOnly)
        assertNull(view.figures, "the phone cannot derive that period, so neither its own nor the stored server figures apply")
        assertEquals(123_000, view.limitMinor)
    }

    @Test
    fun aCategoryThatCannotBeResolvedIsMarkedUnavailable() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1", categoryId = "unknown"))
        val category = env.service.observeOverview().first().active.single().category
        assertEquals(CategorySource.Unavailable, category?.source)
        assertEquals("", category?.name)
    }

    @Test
    fun theUpdatedStampIsTheLatestProgressRead() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("b1"))
        assertNull(env.service.observeOverview().first().updatedAt)
        env.seedProgress("b1")
        assertEquals("2026-10-02T09:00:00Z", env.service.observeOverview().first().updatedAt.toString())
    }

    @Test
    fun theBudgetIsLoadedOnceAnythingIsOnThePhone() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        assertEquals(false, env.service.observeOverview().first().loaded)
        env.seed(budgetJson("b1"))
        assertEquals(true, env.service.observeOverview().first().loaded)
    }
}
