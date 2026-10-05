package com.fares.saveme.feature.transactions.data

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The rows and the balance effect reports read (spec 014 R-03), on real SQLite: the fakes in commonTest follow these. */
@RunWith(RobolectricTestRunner::class)
class ReportLedgerQueryTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    private fun tx(
        id: String,
        wallet: String = "w1",
        counter: String? = null,
        kind: String = "expense",
        date: String = "2026-10-05",
        amount: Long = 1_000,
        account: String = "u1",
        deletedAt: String? = null,
        pending: Boolean = false,
        version: Long = 1,
    ) = TransactionEntity(
        id, account, wallet, counter, kind, date, amount, "EGP", "c1", null, null, null, false, "u1", null, null, if (pending) 0 else version, 7,
        "2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z", deletedAt, "2026-10-01T00:00:00Z", pendingConfirmation = pending,
    )

    private suspend fun rows(wallets: List<String> = listOf("w1", "w2"), waiting: List<String> = emptyList()) =
        db.transactionDao().observeForReport("u1", wallets, "2026-10-01", "2026-10-31", waiting).first().map { it.id }.toSet()

    private suspend fun before(wallets: List<String> = listOf("w1", "w2"), on: String = "2026-10-01") =
        db.transactionDao().observeBalancesBefore("u1", wallets, on).first().associate { it.walletId to it.total }

    @Test
    fun rowsTouchingTheWalletsAsSourceOrDestinationInTheRangeAreRead() = runTest {
        db.transactionDao().upsertAll(
            listOf(
                tx("first", date = "2026-10-01"), tx("last", date = "2026-10-31", wallet = "w2"), tx("toMe", wallet = "w9", counter = "w1", kind = "transfer"),
                tx("before", date = "2026-09-30"), tx("after", date = "2026-11-01"), tx("elsewhere", wallet = "w9"),
                tx("deleted", deletedAt = "2026-10-06T00:00:00Z"), tx("income", kind = "income"),
            ),
        )
        assertEquals(setOf("first", "last", "toMe", "income"), rows())
    }

    @Test
    fun waitingIdsAndPhoneOnlyConfirmationsAreReadWhateverTheirDateOrWallet() = runTest {
        db.transactionDao().upsertAll(
            listOf(tx("moved", date = "2026-09-15"), tx("quiet", date = "2026-09-15"), tx("pending", date = "2026-12-24", wallet = "w9", pending = true)),
        )
        assertEquals(setOf("moved", "pending"), rows(waiting = listOf("moved")))
    }

    @Test
    fun oneAccountOnlyAndAnEmptyWaitingSetIsAValidQuery() = runTest {
        db.transactionDao().upsertAll(listOf(tx("mine"), tx("theirs", account = "u2"), tx("theirsPending", account = "u2", pending = true)))
        assertEquals(setOf("mine"), rows(waiting = listOf("theirs")))
        assertEquals(setOf("mine"), rows())
    }

    @Test
    fun theBalanceBeforeSumsTheSignedEffectOfSourceAndDestinationLegsPerWallet() = runTest {
        db.transactionDao().upsertAll(
            listOf(
                tx("i", kind = "income", date = "2026-09-01", amount = 5_000), tx("e", date = "2026-09-02", amount = 1_200),
                tx("t", kind = "transfer", date = "2026-09-03", amount = 700, wallet = "w1", counter = "w2"),
                tx("fromElsewhere", kind = "transfer", date = "2026-09-04", amount = 300, wallet = "w9", counter = "w1"),
                tx("toElsewhere", kind = "transfer", date = "2026-09-05", amount = 200, wallet = "w2", counter = "w9"),
            ),
        )
        assertEquals(mapOf("w1" to 5_000 - 1_200 - 700 + 300L, "w2" to 700 - 200L), before())
        assertEquals(mapOf("w1" to 5_000 - 1_200 - 700 + 300L), before(wallets = listOf("w1")))
    }

    @Test
    fun deletedRowsUnknownKindsPlaceholdersAndRowsOnOrAfterTheDayAreLeftOut() = runTest {
        db.transactionDao().upsertAll(
            listOf(
                tx("deleted", kind = "income", date = "2026-09-05", deletedAt = "2026-09-06T00:00:00Z"), tx("unknown", kind = "refund", date = "2026-09-05"),
                tx("placeholder", date = "2026-09-05", version = 0), tx("pending", date = "2026-09-05", pending = true),
                tx("onTheDay", date = "2026-10-01", amount = 50), tx("kept", date = "2026-09-30", amount = 7),
                tx("someoneElse", date = "2026-09-05", account = "u2", amount = 99),
            ),
        )
        assertEquals(mapOf("w1" to -7L), before())
    }

    @Test
    fun aWalletWithNothingBeforeIsAbsent() = runTest {
        db.transactionDao().upsertAll(listOf(tx("later", date = "2026-10-15")))
        assertEquals(emptyMap<String, Long>(), before())
    }

    private suspend fun daily(wallets: List<String> = listOf("w1", "w2")) =
        db.transactionDao().observeDailySums("u1", wallets, "2026-10-01", "2026-10-31", "2026-08-01", "2026-11-30").first()
            .associate { (it.date to it.kind) to it.total }

    @Test
    fun theDaysAroundTheRangeAreSummedPerDayAndKindForTheWalletsOnly() = runTest {
        db.transactionDao().upsertAll(
            listOf(
                tx("a", date = "2026-09-05", amount = 300), tx("b", date = "2026-09-05", amount = 50, wallet = "w2"), tx("c", kind = "income", date = "2026-09-05", amount = 1_000),
                tx("later", date = "2026-11-04", amount = 7), tx("inRange", date = "2026-10-10"), tx("tooOld", date = "2026-07-31"), tx("tooLate", date = "2026-12-01"),
                tx("transfer", kind = "transfer", date = "2026-09-06", wallet = "w1", counter = "w2"), tx("elsewhere", date = "2026-09-05", wallet = "w9"),
                tx("deleted", date = "2026-09-05", deletedAt = "2026-09-06T00:00:00Z"), tx("placeholder", date = "2026-09-05", version = 0),
                tx("someoneElse", date = "2026-09-05", account = "u2"),
            ),
        )
        assertEquals(
            mapOf(("2026-09-05" to "expense") to 350L, ("2026-09-05" to "income") to 1_000L, ("2026-11-04" to "expense") to 7L),
            daily(),
        )
        assertEquals(mapOf(("2026-09-05" to "expense") to 300L, ("2026-09-05" to "income") to 1_000L, ("2026-11-04" to "expense") to 7L), daily(listOf("w1")))
    }
}
