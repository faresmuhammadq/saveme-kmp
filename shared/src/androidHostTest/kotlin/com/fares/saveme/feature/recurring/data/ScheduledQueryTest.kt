package com.fares.saveme.feature.recurring.data

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.feature.recurring.data.local.OccurrenceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The occurrences reports read (spec 014 R-05), on real SQLite. */
@RunWith(RobolectricTestRunner::class)
class ScheduledQueryTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    private val stamp = "2026-10-02T09:00:00Z"

    private fun occurrence(
        id: String, wallet: String = "w1", counter: String? = null, date: String = "2026-10-28", status: String = "scheduled", account: String = "u1",
    ) = OccurrenceEntity(
        id, account, wallet, "r1", 1, false, date, date, status, "expense", 500_000, "EGP", "c1", counter, "Rent", null, null, false,
        null, null, 1, 1, stamp, stamp, stamp,
    )

    private suspend fun ids(waiting: List<String> = emptyList(), wallets: List<String> = listOf("w1", "w2")) =
        db.occurrenceDao().observeScheduledFor("u1", wallets, "2026-10-01", "2026-10-31", waiting).first().map { it.id }.toSet()

    @Test
    fun onlyScheduledOccurrencesInTheRangeForTheWalletsOrWithOneAsTheirDestination() = runTest {
        db.occurrenceDao().upsert(occurrence("rent"))
        db.occurrenceDao().upsert(occurrence("intoMine", wallet = "w9", counter = "w2"))
        db.occurrenceDao().upsert(occurrence("confirmed", status = "confirmed"))
        db.occurrenceDao().upsert(occurrence("skipped", status = "skipped"))
        db.occurrenceDao().upsert(occurrence("late", date = "2026-11-01"))
        db.occurrenceDao().upsert(occurrence("early", date = "2026-09-30"))
        db.occurrenceDao().upsert(occurrence("elsewhere", wallet = "w9"))
        db.occurrenceDao().upsert(occurrence("someoneElse", account = "u2"))
        assertEquals(setOf("rent", "intoMine"), ids())
    }

    @Test
    fun aWaitingIdIsReadWhateverItsStatusOrDateButOnlyForTheWallets() = runTest {
        db.occurrenceDao().upsert(occurrence("movedIn", date = "2026-12-01"))
        db.occurrenceDao().upsert(occurrence("skipped", status = "skipped"))
        db.occurrenceDao().upsert(occurrence("elsewhere", wallet = "w9", status = "skipped"))
        assertEquals(setOf("movedIn", "skipped"), ids(waiting = listOf("movedIn", "skipped", "elsewhere")))
        assertEquals(emptySet<String>(), ids())
    }
}
