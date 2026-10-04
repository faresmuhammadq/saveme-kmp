package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetJson
import com.fares.saveme.feature.budgets.expense
import com.fares.saveme.feature.budgets.progressBody
import com.fares.saveme.feature.budgets.progressJson
import com.fares.saveme.feature.budgets.progressPage
import com.fares.saveme.feature.budgets.touch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The progress reads (FR-018, research R-09). */
class BudgetsRefreshTest {

    @Test
    fun aRefreshReadsTheFeedsFirstThenEveryProgressPageWithoutADate() = runTest {
        val env = BudgetTestEnv(backgroundScope) { request ->
            if (request.url.parameters["cursor"] == null) jsonResponse(200, progressPage(listOf(progressJson("b1", spent = 50_000)), nextCursor = "p2", limit = 1))
            else jsonResponse(200, progressPage(listOf(progressJson("b2", spent = 7_000, limit = 100_000))))
        }
        env.signedIn()
        env.seed(budgetJson("b1", categoryId = "c1"))
        env.seed(budgetJson("b2", categoryId = null))
        assertEquals(RefreshOutcome.Done, env.service.refresh(force = true))
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.Everything to true), env.runner.refreshes)
        assertEquals(2, env.requests.size)
        assertEquals(setOf("limit"), env.requests[0].url.parameters.names(), "no date, filter or sort (FR-011)")
        assertEquals(setOf("limit", "cursor"), env.requests[1].url.parameters.names())
        assertEquals(50_000L, env.progressDao.rows.value.getValue("b1").spentMinor)
        assertEquals(7_000L, env.progressDao.rows.value.getValue("b2").spentMinor)
        assertEquals("EGP", env.progressDao.rows.value.getValue("b1").currency)
    }

    @Test
    fun theStoredLocalFiguresComeFromOneSnapshotTakenAfterTheFeedReads() = runTest {
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(200, progressPage(listOf(progressJson("b1", spent = 50_000, others = listOf("USD"))))) }
        env.signedIn()
        env.seed(budgetJson("b1", categoryId = "c1"))
        env.spending.set(expense("old", "2026-10-01", 1_000))
        // The feed read brings a transaction the phone did not have before the walk.
        env.runner.onRefresh = { _, _ -> env.spending.set(expense("old", "2026-10-01", 1_000), expense("fresh", "2026-10-02", 44_000), expense("usd", "2026-10-02", 5, currency = "USD")) }
        env.service.refresh(force = true)
        val stored = env.progressDao.rows.value.getValue("b1")
        assertEquals(45_000L, stored.localSpentMinor)
        assertEquals("USD", stored.localOtherCurrencies)
        assertEquals("USD", stored.otherCurrencies)
        assertEquals("2026-10-02T10:00:00Z", stored.readAt)
        assertEquals(listOf(LocalDate(2026, 10, 1) to LocalDate(2026, 10, 31)), env.spending.ranges.distinct().filter { it.first == LocalDate(2026, 10, 1) })
    }

    @Test
    fun anInvalidCursorRestartsTheWalkOnce() = runTest {
        var calls = 0
        val env = BudgetTestEnv(backgroundScope) { request ->
            calls++
            when {
                request.url.parameters["cursor"] == null -> jsonResponse(200, progressPage(listOf(progressJson("b1", spent = 1)), nextCursor = "p2", limit = 1))
                calls == 2 -> jsonResponse(400, problemBody(400, "INVALID_CURSOR"))
                else -> jsonResponse(200, progressPage(listOf(progressJson("b2", spent = 2))))
            }
        }
        env.signedIn()
        env.seed(budgetJson("b1")); env.seed(budgetJson("b2", categoryId = null))
        assertEquals(RefreshOutcome.Done, env.service.refresh(force = true))
        assertEquals(listOf(null, "p2", null, "p2"), env.requests.map { it.url.parameters["cursor"] })
        assertEquals(setOf("b1", "b2"), env.progressDao.rows.value.keys)
    }

    @Test
    fun aSecondInvalidCursorFailsTheWalkAndKeepsTheStoredRows() = runTest {
        val env = BudgetTestEnv(backgroundScope) { request ->
            if (request.url.parameters["cursor"] == null) jsonResponse(200, progressPage(listOf(progressJson("b1", spent = 1)), nextCursor = "p2", limit = 1))
            else jsonResponse(400, problemBody(400, "INVALID_CURSOR"))
        }
        env.signedIn()
        env.seed(budgetJson("b1"))
        env.seedProgress("b1", spent = 123)
        assertEquals(RefreshOutcome.Failed, env.service.refresh(force = true))
        assertEquals(4, env.requests.size, "restarted once, then gave up")
        assertEquals(123L, env.progressDao.rows.value.getValue("b1").spentMinor)
    }

    @Test
    fun offlineTimeoutAndServerErrorsKeepTheStoredRowsAndReportTheOutcome() = runTest {
        var mode = 0
        val env = BudgetTestEnv(backgroundScope) {
            when (mode) {
                0 -> throw kotlinx.io.IOException("offline")
                else -> jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE"))
            }
        }
        env.signedIn()
        env.seed(budgetJson("b1"))
        env.seedProgress("b1", spent = 123)
        assertEquals(RefreshOutcome.Offline, env.service.refresh(force = true))
        mode = 1
        assertEquals(RefreshOutcome.Failed, env.service.refresh(force = true))
        assertEquals(123L, env.progressDao.rows.value.getValue("b1").spentMinor)
    }

    @Test
    fun aFailedFeedReadStillAnswersWithItsOutcome() = runTest {
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(200, progressPage(emptyList())) }
        env.signedIn()
        env.runner.outcome = ReadOutcome.Offline
        assertEquals(RefreshOutcome.Offline, env.service.refresh(force = true))
        env.runner.outcome = ReadOutcome.Failed
        assertEquals(RefreshOutcome.Failed, env.service.refresh(force = true))
    }

    @Test
    fun aCompletedWalkRemovesRowsOfBudgetsItDidNotReturnButKeepsArchivedOnes() = runTest {
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(200, progressPage(listOf(progressJson("b1", spent = 9)))) }
        env.signedIn()
        env.seed(budgetJson("b1")); env.seed(budgetJson("b2", categoryId = null)); env.seed(budgetJson("old", categoryId = "c2", archivedAt = "2026-09-20T00:00:00Z"))
        env.seedProgress("b2"); env.seedProgress("old"); env.seedProgress("ghost")
        env.service.refresh(force = true)
        assertEquals(setOf("b1", "old"), env.progressDao.rows.value.keys)
    }

    @Test
    fun aRowForABudgetNotYetOnThePhoneIsStoredAndShownOnceItArrives() = runTest {
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(200, progressPage(listOf(progressJson("b9", spent = 321)))) }
        env.signedIn()
        env.service.refresh(force = true)
        assertEquals(321L, env.progressDao.rows.value.getValue("b9").spentMinor)
        assertEquals(0L, env.progressDao.rows.value.getValue("b9").localSpentMinor)
    }

    @Test
    fun withoutForceAWalkWithinTheLastMinuteIsSkippedAndAPullAlwaysReads() = runTest {
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(200, progressPage(emptyList())) }
        env.signedIn()
        env.service.refresh(force = false)
        env.service.refresh(force = false)
        assertEquals(1, env.requests.size)
        env.clock.now = env.clock.now + 30.seconds
        env.service.refresh(force = false)
        assertEquals(1, env.requests.size)
        env.clock.now = env.clock.now + 31.seconds
        env.service.refresh(force = false)
        assertEquals(2, env.requests.size)
        env.service.refresh(force = true)
        assertEquals(3, env.requests.size)
        assertEquals(listOf(false, false, false, false, true), env.runner.refreshes.map { it.second })
    }

    @Test
    fun aDifferentPersonSigningInNeverInheritsTheFloor() = runTest {
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(200, progressPage(emptyList())) }
        env.signedIn()
        env.service.refresh(force = false)
        env.authState.value = com.fares.saveme.feature.auth.domain.AuthState.SignedIn(com.fares.saveme.feature.budgets.budgetAccount.copy(userId = "u2"))
        env.service.refresh(force = false)
        assertEquals(2, env.requests.size)
    }

    @Test
    fun aFailedWalkDoesNotStartTheFloor() = runTest {
        var offline = true
        val env = BudgetTestEnv(backgroundScope) { if (offline) throw kotlinx.io.IOException("offline") else jsonResponse(200, progressPage(emptyList())) }
        env.signedIn()
        env.service.refresh(force = false)
        offline = false
        assertEquals(RefreshOutcome.Done, env.service.refresh(force = false))
        assertEquals(2, env.requests.size)
    }

    // ---- one budget -----------------------------------------------------------------------------------------------

    @Test
    fun oneBudgetIsReadOnlyWhenTheServerHoldsItAndStoredLikeAWalkRow() = runTest {
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(200, progressBody(progressJson("b1", spent = 777))) }
        env.signedIn()
        env.repository.insertPlaceholder("fresh", "u1", "c1", "EGP", 100)
        env.seed(budgetJson("b1"))
        assertEquals(RefreshOutcome.Done, env.service.refreshOne("fresh"))
        assertEquals(0, env.requests.size, "a budget the server does not hold has no progress there")
        assertEquals(RefreshOutcome.Done, env.service.refreshOne("missing"))
        assertEquals(RefreshOutcome.Done, env.service.refreshOne("b1"))
        assertEquals(1, env.requests.size)
        assertTrue(env.requests.single().url.encodedPath.endsWith("/budgets/b1/progress"))
        assertEquals(777L, env.progressDao.rows.value.getValue("b1").spentMinor)
    }

    @Test
    fun anArchivedBudgetIsReadOneByOne() = runTest {
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(200, progressBody(progressJson("old", spent = 5))) }
        env.signedIn()
        env.seed(budgetJson("old", archivedAt = "2026-09-20T00:00:00Z"))
        env.service.refreshOne("old")
        assertNotNull(env.progressDao.rows.value["old"])
    }

    @Test
    fun aNotFoundOnOneBudgetIsIgnoredAndTheFloorIsPerBudget() = runTest {
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
        env.signedIn()
        env.seed(budgetJson("b1")); env.seed(budgetJson("b2", categoryId = null))
        assertEquals(RefreshOutcome.Done, env.service.refreshOne("b1"))
        assertNull(env.progressDao.rows.value["b1"])
        env.service.refreshOne("b1")
        assertEquals(1, env.requests.size, "within a minute")
        env.service.refreshOne("b2")
        assertEquals(2, env.requests.size)
        env.service.refreshOne("b1", force = true)
        assertEquals(3, env.requests.size)
    }

    @Test
    fun oneBudgetReportsOfflineAndFailure() = runTest {
        var offline = true
        val env = BudgetTestEnv(backgroundScope) { if (offline) throw kotlinx.io.IOException("offline") else jsonResponse(500, problemBody(500, "INTERNAL_ERROR")) }
        env.signedIn()
        env.seed(budgetJson("b1"))
        assertEquals(RefreshOutcome.Offline, env.service.refreshOne("b1", force = true))
        offline = false
        assertEquals(RefreshOutcome.Failed, env.service.refreshOne("b1", force = true))
    }
}
