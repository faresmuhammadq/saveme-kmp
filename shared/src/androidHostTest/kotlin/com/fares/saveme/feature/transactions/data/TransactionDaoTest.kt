package com.fares.saveme.feature.transactions.data

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The SQL behind the transaction reads and the balance carry, on a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
class TransactionDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    private fun tx(
        id: String,
        wallet: String = "w1",
        counter: String? = null,
        date: String = "2026-10-01",
        created: String = "2026-10-01T09:00:00Z",
        account: String = "u1",
        deletedAt: String? = null,
        kind: String = "expense",
        amount: Long = 1_000,
        carrySource: Long = 0,
        carryCounter: Long = 0,
        confirmed: String? = null,
        seq: Long = 1,
    ) = TransactionEntity(
        id, account, wallet, counter, kind, date, amount, "EGP", "c1", null, null, null, false, "u1", null, null, 1, seq,
        created, created, deletedAt, created, carrySource, carryCounter, confirmed,
    )

    @Test
    fun aWalletsListHasItsOwnRowsAndTransfersIntoItNewestFirstWithoutDeletedRows() = runTest {
        val dao = db.transactionDao()
        dao.upsertAll(
            listOf(
                tx("old", date = "2026-09-30"),
                tx("a", date = "2026-10-02", created = "2026-10-02T08:00:00Z"),
                tx("b", date = "2026-10-02", created = "2026-10-02T09:00:00Z"),
                tx("c", date = "2026-10-02", created = "2026-10-02T09:00:00Z"),
                tx("transfer-in", wallet = "w2", counter = "w1", kind = "transfer", date = "2026-10-01"),
                tx("other", wallet = "w3"),
                tx("deleted", deletedAt = "2026-10-02T10:00:00Z"),
                tx("someone-else", account = "u2"),
            ),
        )
        assertEquals(listOf("c", "b", "a", "transfer-in", "old"), dao.observeForWallet("u1", "w1").first().map { it.id })
        assertEquals(listOf("transfer-in"), dao.observeForWallet("u1", "w2").first().map { it.id })
    }

    @Test
    fun touchingFindsBothSidesAndGetAndDeleteWork() = runTest {
        val dao = db.transactionDao()
        dao.upsertAll(listOf(tx("a"), tx("tr", wallet = "w2", counter = "w1", kind = "transfer"), tx("z", wallet = "w3")))
        assertEquals(setOf("a", "tr"), dao.getTouching("w1").map { it.id }.toSet())
        dao.delete("a")
        assertNull(dao.get("a"))
        assertEquals(2, dao.getAll().size)
    }

    @Test
    fun relevantRowsAreTheGivenIdsPlusEveryRowWithACarry() = runTest {
        val dao = db.transactionDao()
        dao.upsertAll(
            listOf(
                tx("queued"), tx("plain"), tx("carrying", carrySource = -500, confirmed = "2026-10-02T10:00:00.000Z"),
            ),
        )
        assertEquals(setOf("queued", "carrying"), dao.observeRelevant(listOf("queued")).first().map { it.id }.toSet())
        assertEquals(listOf("carrying"), dao.observeCarried().first().map { it.id })
        assertEquals(1, dao.getCarried().size)
    }

    @Test
    fun clearingTheCarryOfAWalletClearsOnlyThatWalletsPartUpToATimeAndPurgesSettledDeletions() = runTest {
        val dao = db.transactionDao()
        dao.upsertAll(
            listOf(
                tx("early", carrySource = -500, confirmed = "2026-10-02T10:00:00.000Z"),
                tx("late", carrySource = -700, confirmed = "2026-10-02T10:00:05.000Z"),
                tx("hidden", deletedAt = "2026-10-02T10:00:00Z", carrySource = 900, confirmed = "2026-10-02T10:00:01.000Z"),
                tx("other-wallet", wallet = "w2", carrySource = -300, confirmed = "2026-10-02T10:00:00.000Z"),
            ),
        )
        dao.clearCarryFor("w1", "2026-10-02T10:00:02.000Z")
        dao.purgeSettledDeletions()
        assertEquals(0L, dao.get("early")!!.carrySourceMinor)
        assertNull(dao.get("early")!!.carryConfirmedAt)
        assertEquals(-700L, dao.get("late")!!.carrySourceMinor)
        assertNull(dao.get("hidden"))
        assertEquals("another wallet's carry waits for its own read", -300L, dao.get("other-wallet")!!.carrySourceMinor)
    }

    @Test
    fun aWalletCopyAtAPositionSettlesTheSourceCarryOfTheRowsItCountsAndNothingElse() = runTest {
        val dao = db.transactionDao()
        dao.upsertAll(
            listOf(
                tx("counted", carrySource = -500, confirmed = "2026-10-02T10:00:00.000Z", seq = 4),
                tx("at", carrySource = -600, confirmed = "2026-10-02T10:00:00.000Z", seq = 7),
                tx("later", carrySource = -700, confirmed = "2026-10-02T10:00:00.000Z", seq = 8),
                tx("placeholder-seq", carrySource = -800, confirmed = "2026-10-02T10:00:00.000Z", seq = 0),
                tx("into-w1", wallet = "w2", counter = "w1", carrySource = -900, carryCounter = 900, confirmed = "2026-10-02T10:00:00.000Z", seq = 3),
            ),
        )
        dao.clearSourceCarryCoveredBy("w1", 7)
        assertEquals(0L, dao.get("counted")!!.carrySourceMinor)
        assertEquals(0L, dao.get("at")!!.carrySourceMinor)
        assertEquals("still waits for its read", "2026-10-02T10:00:00.000Z", dao.get("at")!!.carryConfirmedAt)
        assertEquals(-700L, dao.get("later")!!.carrySourceMinor)
        assertEquals(-800L, dao.get("placeholder-seq")!!.carrySourceMinor)
        assertEquals("a transfer's position is its source's: the destination part waits for the read", 900L, dao.get("into-w1")!!.carryCounterMinor)
        assertEquals(-900L, dao.get("into-w1")!!.carrySourceMinor)
    }

    @Test
    fun aTransfersCarryClearsPerWalletAndTheRowIsSettledOnlyWhenBothPartsAre() = runTest {
        val dao = db.transactionDao()
        dao.upsert(tx("tr", counter = "w2", carrySource = -5_000, carryCounter = 5_000, confirmed = "2026-10-02T10:00:00.000Z"))
        dao.clearCarryFor("w1", "2026-10-02T10:00:05.000Z")
        assertEquals(0L, dao.get("tr")!!.carrySourceMinor)
        assertEquals(5_000L, dao.get("tr")!!.carryCounterMinor)
        assertEquals("2026-10-02T10:00:00.000Z", dao.get("tr")!!.carryConfirmedAt)
        dao.clearCarryFor("w2", "2026-10-02T10:00:05.000Z")
        assertEquals(0L, dao.get("tr")!!.carryCounterMinor)
        assertNull(dao.get("tr")!!.carryConfirmedAt)
    }

    @Test
    fun aDeletionStaysHiddenWhileItsCarryWaits() = runTest {
        val dao = db.transactionDao()
        dao.upsert(tx("hidden", deletedAt = "2026-10-02T10:00:00Z", carrySource = 900, confirmed = "2026-10-02T10:00:01.000Z"))
        dao.purgeSettledDeletions()
        assertTrue(dao.get("hidden") != null)
        assertTrue(dao.observeForWallet("u1", "w1").first().isEmpty())
    }

    @Test
    fun deleteAllEmptiesTheTable() = runTest {
        val dao = db.transactionDao()
        dao.upsertAll(listOf(tx("a"), tx("b")))
        dao.deleteAll()
        assertTrue(dao.getAll().isEmpty())
    }
}
