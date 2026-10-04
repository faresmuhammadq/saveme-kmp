package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.sync.EngineHarness.Companion.failure
import com.fares.saveme.core.sync.EngineHarness.Companion.record
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A handler may take the server's copy of a clash without asking (spec 010 R-06). */
class SyncEngineAcceptClashTest {

    @Test
    fun anAcceptedClashAppliesTheServerCopyDropsTheChangeAndAsksNothing() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}", base = 3)
        h.handler.accepts = { _, _ -> true }
        h.handler.answer = { failure(ApiErrorCode.VERSION_CONFLICT, current = record(6)) }
        h.engine.drain()

        assertNull(h.row("n1"))
        assertEquals(record(6), h.handler.applied.last())
        assertEquals(listOf("n1"), h.handler.discarded)
        assertEquals(1, h.handler.sent.size)
    }

    @Test
    fun byDefaultTheClashWaitsForThePerson() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}", base = 3)
        h.handler.answer = { failure(ApiErrorCode.VERSION_CONFLICT, current = record(6)) }
        h.engine.drain()
        assertEquals("Clash", assertNotNull(h.row("n1")).state)
        assertEquals(emptyList(), h.handler.discarded)
    }

    @Test
    fun theHandlerSeesTheChangeAndTheServerCopy() = runTest {
        val h = EngineHarness()
        h.save("n1", """{"a":1}""", base = 3)
        var seen: Pair<PendingChange, kotlinx.serialization.json.JsonElement>? = null
        h.handler.accepts = { c, cur -> seen = c to cur; false }
        h.handler.answer = { failure(ApiErrorCode.VERSION_CONFLICT, current = record(6)) }
        h.engine.drain()
        assertEquals("""{"a":1}""", seen!!.first.payloadJson)
        assertEquals(record(6), seen!!.second)
    }

    @Test
    fun aBatchItemClashIsAcceptedToo() = runTest {
        val h = BatchHarness()
        h.save("n1", base = 3)
        h.save("n2", base = 3)
        h.handler.accepts = { c, _ -> c.recordId == "n1" }
        h.api.batchAnswer = { items ->
            ApiResult2.success(
                items.map { FakeSyncApi.rejected(it, ApiErrorCode.VERSION_CONFLICT, 409, current = record(9)) },
            )
        }
        h.engine.drain()
        assertNull(h.row("n1"))
        assertEquals("Clash", h.row("n2")!!.state)
        assertEquals(listOf("n1"), h.handler.discarded)
        assertEquals(listOf(record(9)), h.handler.applied)
    }
}

private object ApiResult2 {
    fun <T> success(value: T) = com.fares.saveme.core.network.ApiResult.Success(value)
}
