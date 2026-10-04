package com.fares.saveme.feature.recurring.data

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.feature.recurring.data.local.OccurrenceEntity
import com.fares.saveme.feature.recurring.data.local.RecurringRuleEntity
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
import kotlin.time.measureTime

/** The SQL behind the recurring reads, on a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
class RecurringDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    private val stamp = "2026-10-02T09:00:00Z"

    private fun rule(id: String, wallet: String = "w1", account: String = "u1", status: String = "active", created: String = stamp, follows: String? = null) =
        RecurringRuleEntity(
            id, account, wallet, "custom", status, "expense", 450_000, "per_occurrence", "EGP", "c1", null, "Rent", null, null, false,
            "monthly", 1, 1, "2026-11-01", null, null, null, null, "Africa/Cairo", "u1", 1, 1, created, created, stamp, follows,
        )

    private fun occurrence(
        id: String, rule: String = "r1", wallet: String = "w1", account: String = "u1", date: String = "2026-11-01", ordinal: Int = 1,
        transaction: String? = null,
    ) = OccurrenceEntity(
        id, account, wallet, rule, ordinal, false, date, date, "scheduled", "expense", 450_000, "EGP", "c1", null, "Rent", null, null, false,
        transaction, null, 1, 1, stamp, stamp, stamp,
    )

    @Test
    fun rulesAreReadByAccountAndWalletOldestFirstAndFollowersAreFound() = runTest {
        val dao = db.recurringRuleDao()
        dao.upsert(rule("b", created = "2026-10-02T10:00:00Z"))
        dao.upsert(rule("a", created = "2026-10-02T09:00:00Z"))
        dao.upsert(rule("other-wallet", wallet = "w2"))
        dao.upsert(rule("someone-else", account = "u2"))
        dao.upsert(rule("next", follows = "a"))
        assertEquals(listOf("a", "next", "b"), dao.observeForWallet("u1", "w1").first().map { it.id }.sortedBy { listOf("a", "next", "b").indexOf(it) })
        assertEquals(setOf("a", "b", "next", "other-wallet"), dao.observeAll("u1").first().map { it.id }.toSet())
        assertEquals(listOf("next"), dao.getFollowing("a").map { it.id })
        dao.deleteForWallet("w2")
        assertNull(dao.get("other-wallet"))
        dao.delete("a")
        assertNull(dao.get("a"))
        dao.deleteAll()
        assertTrue(dao.observeAll("u1").first().isEmpty())
    }

    @Test
    fun occurrencesAreReadByWalletRuleAndTransactionIdInDateOrderPerAccount() = runTest {
        val dao = db.occurrenceDao()
        dao.upsert(occurrence("late", date = "2026-12-01", ordinal = 2))
        dao.upsert(occurrence("early", date = "2026-11-01"))
        dao.upsert(occurrence("confirmed", date = "2026-10-01", ordinal = 0, transaction = "tx"))
        dao.upsert(occurrence("other-rule", rule = "r2", date = "2026-11-05"))
        dao.upsert(occurrence("other-account", account = "u2"))
        assertEquals(listOf("confirmed", "early", "other-rule", "late"), dao.observeForWallet("u1", "w1").first().map { it.id })
        assertEquals(listOf("confirmed", "early", "other-account", "late"), dao.observeForRule("r1").first().map { it.id })
        assertEquals(listOf("confirmed"), dao.getByTransactionId("tx").map { it.id })
        dao.setNotice("early", "already_confirmed")
        assertEquals("already_confirmed", dao.get("early")!!.localNotice)
        dao.setNotice("early", null)
        assertNull(dao.get("early")!!.localNotice)
        dao.deleteForRule("r2")
        assertNull(dao.get("other-rule"))
        dao.deleteAll()
        assertEquals(emptyList<OccurrenceEntity>(), dao.getForWallet("w1"))
    }

    @Test
    fun aListOfTwoThousandOccurrencesIsReadUnderASecond() = runTest {
        val dao = db.occurrenceDao()
        repeat(50) { r ->
            repeat(40) { n ->
                val date = "2027-%02d-%02d".format((n / 28) + 1, (n % 28) + 1)
                dao.upsert(occurrence("o-$r-$n", rule = "r$r", date = date, ordinal = n))
            }
        }
        val took = measureTime { assertEquals(2_000, dao.observeForWallet("u1", "w1").first().size) }
        assertTrue("took $took", took.inWholeMilliseconds < 1_000)
    }
}
