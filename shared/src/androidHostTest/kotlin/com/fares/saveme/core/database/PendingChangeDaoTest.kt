package com.fares.saveme.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.sync.PendingChangeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The queue's SQL (spec 004 data-model §1); the engine's logic is tested on the fake. */
@RunWith(RobolectricTestRunner::class)
class PendingChangeDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val dao = db.pendingChangeDao()

    @After
    fun close() = db.close()

    private fun row(id: String, order: Long, account: String = "u1", state: String = "Waiting", next: String? = null) =
        PendingChangeEntity(
            recordType = "note", recordId = id, accountId = account, scopeKind = "user", scopeId = account,
            baseVersion = 1, payloadJson = "{}", isDelete = false, restore = false, revision = 0, createdOrder = order,
            state = state, attempts = 0, nextAttemptAt = next, clashJson = null, refusalJson = null,
            updatedAt = "2026-10-02T10:00:00Z",
        )

    @Test
    fun dueReturnsOnlyThisAccountsWaitingRowsThatAreDueOldestFirst() = runTest {
        dao.upsert(row("late", 1, next = "2026-10-02T10:05:00Z"))
        dao.upsert(row("b", 3))
        dao.upsert(row("a", 2, next = "2026-10-02T09:00:00Z"))
        dao.upsert(row("other", 0, account = "u2"))
        dao.upsert(row("clash", 4, state = "Clash"))

        assertEquals(listOf("a", "b"), dao.due("u1", "2026-10-02T10:00:00Z").map { it.recordId })
    }

    @Test
    fun nextDueAtIsTheEarliestWaitOfThisAccount() = runTest {
        assertNull(dao.nextDueAt("u1"))
        dao.upsert(row("late", 1, next = "2026-10-02T10:05:00.000Z"))
        dao.upsert(row("soon", 2, next = "2026-10-02T10:01:00.000Z"))
        dao.upsert(row("other", 3, account = "u2"))
        dao.upsert(row("clash", 4, state = "Clash"))
        assertEquals("2026-10-02T10:01:00.000Z", dao.nextDueAt("u1"))

        dao.upsert(row("now", 5))
        assertEquals("", dao.nextDueAt("u1"))
    }

    @Test
    fun forScopeReturnsOnlyThatScopesRowsInAnyStateOldestFirst() = runTest {
        fun wallet(id: String, walletId: String, order: Long, state: String = "Waiting") =
            row(id, order, state = state).copy(scopeKind = "wallet", scopeId = walletId)
        dao.upsert(wallet("b", "w1", 2, state = "Refused"))
        dao.upsert(wallet("a", "w1", 1))
        dao.upsert(wallet("c", "w2", 3))
        dao.upsert(row("user", 4))
        assertEquals(listOf("a", "b"), dao.forScope("wallet", "w1").map { it.recordId })
        assertEquals(listOf("c"), dao.forScope("wallet", "w2").map { it.recordId })
        assertEquals(listOf("user"), dao.forScope("user", "u1").map { it.recordId })
        assertEquals(emptyList<PendingChangeEntity>(), dao.forScope("wallet", "none"))
    }

    @Test
    fun countsAndResets() = runTest {
        dao.upsert(row("w", 1))
        dao.upsert(row("s", 2, state = "Sending"))
        dao.upsert(row("c", 3, state = "Clash"))
        dao.upsert(row("r", 4, state = "Refused"))
        assertEquals(2, dao.observeWaitingCount().first())
        assertEquals(2, dao.observeNeedsDecisionCount().first())
        assertEquals(4L, dao.maxCreatedOrder())

        dao.resetSending()
        assertEquals("Waiting", dao.get("note", "s")!!.state)

        dao.delete("note", "w")
        assertNull(dao.get("note", "w"))
        dao.deleteAll()
        assertEquals(0L, dao.maxCreatedOrder())
    }

    @Test
    fun markPushedCreatesOrUpdatesTheScopeRow() = runTest {
        db.syncScopeDao().markPushed("user:u1", "2026-10-02T10:00:00Z")
        db.syncScopeDao().markPushed("user:u1", "2026-10-02T11:00:00Z")
        assertEquals("2026-10-02T11:00:00Z", db.syncScopeDao().observeLastPushed().first())
    }
}
