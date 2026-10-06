package com.fares.saveme.feature.reports

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
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.budgets.budgetAccount
import com.fares.saveme.feature.budgets.profileView
import com.fares.saveme.feature.reports.data.ReportsApi
import com.fares.saveme.feature.reports.domain.DefaultReportsService
import com.fares.saveme.feature.reports.domain.LearnedPeriods
import com.fares.saveme.feature.reports.domain.ReportChoicesStore
import com.fares.saveme.feature.reports.domain.model.ReportScope
import com.fares.saveme.feature.reports.domain.model.ReportState
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import com.fares.saveme.feature.transactions.domain.TransactionReportLedger
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

/**
 * SC-005: 20,000 transactions in the range across 20 wallets, 50,000 before it, and a cross-wallet report with a 12-period
 * trend is computed from the phone's copy in under a second (best of seven).
 */
@RunWith(RobolectricTestRunner::class)
class ReportsPerformanceTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    private val stamp = "2026-10-01T00:00:00Z"

    private fun tx(i: Int, date: String) = TransactionEntity(
        "t$i", "u1", "w${i % 20}", if (i % 17 == 0) "w${(i + 1) % 20}" else null, if (i % 17 == 0) "transfer" else if (i % 5 == 0) "income" else "expense", date, 100L + i % 900, "EGP",
        if (i % 17 == 0) null else "c${i % 30}", null, null, null, false, "u1", null, null, 1, 1, stamp, stamp, null, stamp,
    )

    @Test
    fun aTwelvePeriodCrossWalletReportOverSeventyThousandRowsShowsWithinASecond() = runTest {
        val rows = (0 until 70_000).map { i ->
            val date = if (i < 20_000) "2026-10-%02d".format(1 + i % 28) else {
                val month = 11 - 1 - (i % 10) // 10 months before October
                val monthName = when {
                    month >= 1 -> "2026-%02d".format(month)
                    else -> "2025-%02d".format(12 + month)
                }
                "$monthName-%02d".format(1 + i % 28)
            }
            tx(i, date)
        }
        db.transactionDao().upsertAll(rows)
        val clock = FakeClock()
        val auth = AuthTestEnv { throw kotlinx.io.IOException("offline") }
        val scopes = FakeSyncScopeDao()
        val engine = SyncEngine(FakePendingChangeDao(), scopes, listOf(FakeHandler("transaction")), FakeSyncScheduler(), currentAccountId = { "u1" }, clock = clock)
        val wallets = FakeReportWallets().also { it.views.value = (0 until 20).map { i -> reportWalletView("w$i", opening = 10_000) } }
        val categories = FakeReportCategories()
        val service = DefaultReportsService(
            wallets = wallets, transactions = FakeReportTransactions(),
            ledger = TransactionReportLedger({ engine }, TransactionRepository(db.transactionDao(), scopes, { "u1" }, { false }, clock), { "u1" }),
            scheduled = FakeScheduledMoney(), categories = categories, profile = MutableStateFlow(profileView()), deviceZoneId = { "Africa/Cairo" },
            authState = MutableStateFlow<AuthState>(AuthState.SignedIn(budgetAccount)), choices = ReportChoicesStore().also { it.update("u1", ReportScope.AllWallets) { c -> c.copy(trendLength = 12) } },
            learned = LearnedPeriods(), api = ReportsApi(auth.client), runner = FakeSyncRunner(),
            now = MutableStateFlow(Instant.parse("2026-10-20T10:00:00Z")),
        )
        // Warm the JIT and the in-memory database twice, then measure seven cold subscriptions: a machine under load (another build
        // running) slows single runs, so the bound is on the fastest run, which a real regression would slow as well (SC-005).
        repeat(2) { service.observe(ReportScope.AllWallets).first { it is ReportState.Ready } }
        var report = (service.observe(ReportScope.AllWallets).first { it is ReportState.Ready } as ReportState.Ready).report
        val runs = (1..7).map { measureTimeMillis { report = (service.observe(ReportScope.AllWallets).first { it is ReportState.Ready } as ReportState.Ready).report } }
        val elapsed = runs.min()
        assertEquals(12, report.trend.size)
        assertTrue(report.summary!!.expenses > 0)
        assertTrue("the fastest of seven runs took $elapsed ms (all: $runs)", elapsed < 1_000)
    }
}
