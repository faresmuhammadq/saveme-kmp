package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.EngineHarness.Companion.ok
import com.fares.saveme.core.sync.EngineHarness.Companion.record
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** US1: changes reach the server, collapsed and in order (FR-001–FR-008). */
class SyncEngineSendTest {

    @Test
    fun aSaveIsStoredAtOnceAndScheduled() = runTest {
        val h = EngineHarness()
        h.save("n1", """{"t":"a"}""")
        val row = assertNotNull(h.row("n1"))
        assertEquals("Waiting", row.state)
        assertEquals("u1", row.accountId)
        assertEquals(1, h.scheduler.enqueued.size)
    }

    @Test
    fun aSuccessfulSendClearsTheRowAndStoresTheServerCopy() = runTest {
        val h = EngineHarness()
        h.handler.answer = { ok(2) }
        h.save("n1", "{}", base = 1)
        h.engine.drain()
        assertNull(h.row("n1"))
        assertEquals(listOf(record(2)), h.handler.applied)
        assertEquals(1L, h.handler.sent.single().baseVersion)
        assertNotNull(h.scopes.rows.value["user:u1"]?.lastPushedAt)
    }

    @Test
    fun severalEditsCollapseIntoOneSendWithTheLatestContentAndOldestBase() = runTest {
        val h = EngineHarness()
        h.save("n1", """{"t":"a"}""", base = 1)
        h.save("n1", """{"t":"b"}""", base = 9)
        h.save("n1", """{"t":"c"}""", base = 9)
        h.handler.answer = { ok(2) }
        h.engine.drain()
        val sent = h.handler.sent.single()
        assertEquals("""{"t":"c"}""", sent.payloadJson)
        assertEquals(1L, sent.baseVersion)
    }

    @Test
    fun anEditDuringASendIsSentNextOnTopOfTheNewVersion() = runTest {
        val h = EngineHarness()
        h.save("n1", """{"t":"a"}""", base = 1)
        var first = true
        h.handler.duringSend = { if (first) { first = false; h.save("n1", """{"t":"b"}""") } }
        h.handler.answer = { change -> ok(change.baseVersion + 1) }
        h.engine.drain()

        val row = assertNotNull(h.row("n1"), "the newer edit is still waiting")
        assertEquals("Waiting", row.state)
        assertEquals(2L, row.baseVersion)
        assertEquals("""{"t":"b"}""", row.payloadJson)

        h.engine.drain()
        assertEquals(2L, h.handler.sent.last().baseVersion)
        assertNull(h.row("n1"))
    }

    @Test
    fun recordsAreSentInTheOrderTheyWereFirstChanged() = runTest {
        val h = EngineHarness()
        h.save("n2", "{}")
        h.save("n1", "{}")
        h.save("n2", """{"x":1}""") // a later edit doesn't move n2 back in line
        h.handler.answer = { ok(2) }
        h.engine.drain()
        assertEquals(listOf("n2", "n1"), h.handler.sent.map { it.recordId })
    }

    @Test
    fun aDeleteBeforeTheFirstSendLeavesNothing() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}", base = 0)
        h.engine.enqueueDelete("note", "n1", h.scope, 0)
        assertNull(h.row("n1"))
    }

    @Test
    fun aDeleteOfASyncedRecordIsSentAsADeletion() = runTest {
        val h = EngineHarness()
        h.engine.enqueueDelete("note", "n1", h.scope, 4)
        h.handler.answer = { ApiResult.Success(record(5, deleted = true)) }
        h.engine.drain()
        val sent = h.handler.sent.single()
        assertEquals(true, sent.isDelete)
        assertEquals(4L, sent.baseVersion)
    }
}
