package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.sync.EngineHarness.Companion.failure
import com.fares.saveme.core.sync.EngineHarness.Companion.ok
import com.fares.saveme.core.sync.EngineHarness.Companion.record
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** US3: the server never merges; every clash waits for the person (FR-011–FR-013). */
class SyncEngineClashTest {

    @Test
    fun aClashIsKeptAndDoesNotBlockOtherRecords() = runTest {
        val h = EngineHarness()
        h.save("n1", """{"t":"mine"}""", base = 3)
        h.save("n2", "{}")
        h.handler.answer = { c -> if (c.recordId == "n1") failure(ApiErrorCode.VERSION_CONFLICT, current = record(6)) else ok(2) }
        h.engine.drain()

        val row = h.row("n1")!!
        assertEquals("Clash", row.state)
        assertEquals(record(6).toString(), row.clashJson)
        assertNull(h.row("n2"), "the other record was still sent")

        h.engine.drain()
        assertEquals(2, h.handler.sent.size, "a clash is never resent on its own")
    }

    @Test
    fun keepMineResendsOnTopOfTheOtherVersion() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}", base = 3)
        h.handler.answer = { failure(ApiErrorCode.VERSION_CONFLICT, current = record(6)) }
        h.engine.drain()

        h.engine.keepMine("note", "n1")
        h.handler.answer = { ok(7) }
        h.engine.drain()
        assertEquals(6L, h.handler.sent.last().baseVersion)
        assertEquals(false, h.handler.sent.last().restore)
        assertNull(h.row("n1"))
    }

    @Test
    fun useTheirsTakesTheServerCopyAndDropsTheChange() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.handler.answer = { failure(ApiErrorCode.VERSION_CONFLICT, current = record(6)) }
        h.engine.drain()

        h.engine.useTheirs("note", "n1")
        assertEquals(record(6), h.handler.applied.last())
        assertNull(h.row("n1"))
        assertEquals(listOf("n1"), h.handler.discarded)
    }

    @Test
    fun aDeletionElsewhereIsOnlyUndoneByAnExplicitRestore() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}", base = 3)
        h.handler.answer = { failure(ApiErrorCode.VERSION_CONFLICT, current = record(6, deleted = true)) }
        h.engine.drain()
        assertEquals("Clash", h.row("n1")!!.state, "an edit never silently restores")

        h.engine.keepMine("note", "n1") // "Restore with my change"
        h.handler.answer = { ok(7) }
        h.engine.drain()
        assertEquals(true, h.handler.sent.last().restore)
        assertEquals(6L, h.handler.sent.last().baseVersion)
    }

    @Test
    fun aFinalDeletionCanOnlyBeAccepted() = runTest {
        val h = EngineHarness()
        val wallets = FakeHandler(recordType = "wallet", canRestore = false)
        val engine = SyncEngine(h.dao, h.scopes, listOf(wallets), h.scheduler, currentAccountId = { "u1" }, clock = h.clock)
        engine.enqueue("wallet", "w1", h.scope, 3, "{}")
        wallets.answer = { failure(ApiErrorCode.VERSION_CONFLICT, current = record(6, deleted = true)) }
        engine.drain()

        engine.keepMine("wallet", "w1")
        assertEquals("Clash", h.dao.rows.value["wallet" to "w1"]!!.state, "no restore for a final deletion")
        engine.useTheirs("wallet", "w1")
        assertNull(h.dao.rows.value["wallet" to "w1"])
    }

    @Test
    fun aConflictWithoutTheCurrentRecordIsRetried() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.handler.answer = { failure(ApiErrorCode.VERSION_CONFLICT) }
        h.engine.drain()
        assertEquals("Waiting", h.row("n1")!!.state)
        assertEquals(1, h.row("n1")!!.attempts)
    }
}
