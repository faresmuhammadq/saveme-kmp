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

/** The rows budgets sum over (spec 012 R-05): in the range, with a waiting change, or a phone-only confirmation; one account only. */
@RunWith(RobolectricTestRunner::class)
class OwnSpendingQueryTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    private fun tx(
        id: String, date: String = "2026-10-05", kind: String = "expense", account: String = "u1", deletedAt: String? = null, pending: Boolean = false,
    ) = TransactionEntity(
        id, account, "w1", null, kind, date, 1_000, "EGP", "c1", null, null, null, false, "u1", null, null, if (pending) 0 else 1, 7,
        "2026-10-01T00:00:00Z", "2026-10-01T00:00:00Z", deletedAt, "2026-10-01T00:00:00Z", pendingConfirmation = pending,
    )

    private suspend fun ids(waiting: List<String> = emptyList()) =
        db.transactionDao().observeForSpending("u1", "2026-10-01", "2026-10-31", waiting).first().map { it.id }.toSet()

    @Test
    fun liveExpensesInTheRangeAreReadAndIncomeTransfersAndOtherDatesAreNot() = runTest {
        db.transactionDao().upsertAll(
            listOf(
                tx("first", date = "2026-10-01"), tx("last", date = "2026-10-31"), tx("before", date = "2026-09-30"), tx("after", date = "2026-11-01"),
                tx("income", kind = "income"), tx("transfer", kind = "transfer"), tx("deleted", deletedAt = "2026-10-06T00:00:00Z"),
            ),
        )
        assertEquals(setOf("first", "last"), ids())
    }

    @Test
    fun aRowWithAWaitingChangeIsReadWhateverItsStoredDateOrKind() = runTest {
        db.transactionDao().upsertAll(listOf(tx("moved", date = "2026-09-15"), tx("wasIncome", kind = "income"), tx("quiet", date = "2026-09-15")))
        assertEquals(setOf("moved", "wasIncome"), ids(listOf("moved", "wasIncome")))
    }

    @Test
    fun aPhoneOnlyConfirmationIsAlwaysReadAndAWaitingIdOfADeletedRowIsNot() = runTest {
        db.transactionDao().upsertAll(listOf(tx("pending", date = "2026-12-24", pending = true), tx("gone", deletedAt = "2026-10-06T00:00:00Z")))
        assertEquals(setOf("pending"), ids(listOf("gone")))
    }

    @Test
    fun anotherAccountsRowsAreNeverRead() = runTest {
        db.transactionDao().upsertAll(listOf(tx("mine"), tx("theirs", account = "u2"), tx("theirsPending", account = "u2", pending = true)))
        assertEquals(setOf("mine"), ids(listOf("theirs")))
    }

    @Test
    fun anEmptyWaitingSetIsAValidQuery() = runTest {
        assertEquals(emptySet<String>(), ids(emptyList()))
    }
}
