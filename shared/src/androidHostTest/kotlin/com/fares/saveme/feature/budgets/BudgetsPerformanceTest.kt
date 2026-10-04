package com.fares.saveme.feature.budgets

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.sync.FakeClock
import com.fares.saveme.core.sync.FakeHandler
import com.fares.saveme.core.sync.FakePendingChangeDao
import com.fares.saveme.core.sync.FakeSyncRunner
import com.fares.saveme.core.sync.FakeSyncScheduler
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.account.FakeSyncScopeDao
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.budgets.data.BudgetApi
import com.fares.saveme.feature.budgets.data.BudgetProgressRepository
import com.fares.saveme.feature.budgets.data.BudgetRepository
import com.fares.saveme.feature.budgets.data.dto.BudgetProgressDto
import com.fares.saveme.feature.budgets.data.dto.BudgetPeriodDto
import com.fares.saveme.feature.budgets.data.dto.MoneyDto
import com.fares.saveme.feature.budgets.data.local.BudgetEntity
import com.fares.saveme.feature.budgets.data.local.BudgetProgressEntity
import com.fares.saveme.feature.budgets.data.toStored
import com.fares.saveme.feature.budgets.domain.DefaultBudgetsService
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import com.fares.saveme.feature.transactions.domain.TransactionOwnSpending
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.system.measureTimeMillis
import kotlin.time.Instant

/** SC-008: 50 budgets over 20,000 transactions in the current period show within a second from the phone's copy; a 200-row page is stored fast. */
@RunWith(RobolectricTestRunner::class)
class BudgetsPerformanceTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    private val stamp = "2026-10-01T00:00:00Z"

    private fun budget(i: Int) = BudgetEntity(
        "b$i", "u1", if (i % 5 == 0) null else "c${i % 10}", "monthly", 5_000_000L + i, if (i % 7 == 0) "USD" else "EGP",
        null, 1, 1, "2026-09-%02dT00:00:00Z".format(1 + i % 28), stamp, stamp,
    )

    private fun tx(i: Int) = TransactionEntity(
        "t$i", "u1", if (i % 2 == 0) "w1" else "w2", null, "expense", "2026-10-%02d".format(1 + i % 28), 100L + i % 900,
        if (i % 11 == 0) "USD" else "EGP", "c${i % 10}", null, null, null, false, if (i % 13 == 0) "u2" else "u1", null, null, 1, 1, stamp, stamp, null, stamp,
    )

    @Test
    fun fiftyBudgetsOverTwentyThousandTransactionsShowWithinASecond() = runTest {
        db.transactionDao().upsertAll((0 until 20_000).map { tx(it) })
        db.budgetDao().upsertAll50((0 until 50).map { budget(it) })
        val clock = FakeClock()
        val auth = AuthTestEnv { throw kotlinx.io.IOException("offline") }
        val scopes = FakeSyncScopeDao()
        val engine = SyncEngine(FakePendingChangeDao(), scopes, listOf(FakeHandler("transaction"), FakeHandler("budget")), FakeSyncScheduler(), currentAccountId = { "u1" }, clock = clock)
        val spending = TransactionOwnSpending(
            { engine }, TransactionRepository(db.transactionDao(), scopes, { "u1" }, { false }, clock), { "u1" },
            memberWalletIds = { MutableStateFlow(setOf("w1", "w2")) }, ruleAuthorOf = { null },
        )
        val service = DefaultBudgetsService(
            repository = BudgetRepository(db.budgetDao(), { "u1" }, { false }, clock), progress = BudgetProgressRepository(db.budgetProgressDao()),
            api = BudgetApi(auth.client), engine = { engine }, authState = MutableStateFlow<AuthState>(AuthState.SignedIn(budgetAccount)),
            categories = FakeBudgetCategories(), wallets = FakeBudgetWallets("EGP", "USD"), spending = spending, profile = MutableStateFlow(profileView()),
            deviceZoneId = { "Africa/Cairo" }, runner = FakeSyncRunner(), now = MutableStateFlow(Instant.parse("2026-10-20T10:00:00Z")), clock = clock,
        )
        // Warm the JIT and the in-memory database once, then measure the screen's data from a cold subscription.
        service.observeOverview().first()
        var overview = service.observeOverview().first()
        val elapsed = (1..3).minOf { measureTimeMillis { overview = service.observeOverview().first() } }
        assertEquals(50, overview.active.size)
        assertTrue("overview took $elapsed ms", elapsed < 1_000)
        assertTrue(overview.active.any { (it.figures?.spentMinor ?: 0) > 0 })
    }

    @Test
    fun aTwoHundredRowProgressPageIsStoredUnderTwoHundredMilliseconds() = runTest {
        val rows: List<BudgetProgressEntity> = (0 until 200).map { i ->
            BudgetProgressDto(
                "b$i", BudgetPeriodDto("2026-10-01", "2026-10-31", 31, 20), true, MoneyDto(5_000_000, "EGP"), MoneyDto(1_000L + i, "EGP"),
                null, null, false, listOf("USD", "KWD"),
            ).toStored("u1", 900L + i, listOf("USD"), stamp)
        }
        val dao = db.budgetProgressDao()
        dao.replaceWalk("u1", rows, emptyList())
        // The best of five: a loaded build machine can stall any single run, and the claim is about what the store costs.
        val elapsed = (1..5).minOf { measureTimeMillis { kotlinx.coroutines.runBlocking { dao.replaceWalk("u1", rows, emptyList()) } } }
        assertEquals(200, dao.getAll().size)
        assertTrue("storing a page took $elapsed ms", elapsed < 200)
    }
}

private suspend fun com.fares.saveme.feature.budgets.data.local.BudgetDao.upsertAll50(list: List<BudgetEntity>) = list.forEach { upsert(it) }
