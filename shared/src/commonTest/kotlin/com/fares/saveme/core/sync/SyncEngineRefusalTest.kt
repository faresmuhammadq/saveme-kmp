package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.sync.EngineHarness.Companion.failure
import com.fares.saveme.core.sync.EngineHarness.Companion.ok
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** US4: refusals are kept with the reason and never retried on their own (FR-014). */
class SyncEngineRefusalTest {

    @Test
    fun permanentRefusalsAreKeptWithTheirReason() = runTest {
        listOf(
            ApiErrorCode.VALIDATION_FAILED, ApiErrorCode.NOT_FOUND, ApiErrorCode.FORBIDDEN, ApiErrorCode.MALFORMED_JSON,
        ).forEach { code ->
            val h = EngineHarness()
            h.save("n1", "{}")
            h.handler.answer = { failure(code, pointer = "/title") }
            h.engine.drain()
            val row = h.row("n1")!!
            assertEquals("Refused", row.state, code.name)
            val refusal = h.engine.get("note", "n1")!!.refusal!!
            assertEquals(code.name, refusal.code)
            assertEquals(mapOf("title" to "INVALID"), refusal.fields)

            h.engine.drain()
            assertEquals(1, h.handler.sent.size, "$code is not retried")
        }
    }

    @Test
    fun editingARefusedChangeSendsItAgain() = runTest {
        val h = EngineHarness()
        h.save("n1", """{"t":"bad"}""")
        h.handler.answer = { failure(ApiErrorCode.VALIDATION_FAILED, pointer = "/t") }
        h.engine.drain()

        h.save("n1", """{"t":"good"}""")
        assertEquals("Waiting", h.row("n1")!!.state)
        h.handler.answer = { ok(2) }
        h.engine.drain()
        assertEquals("""{"t":"good"}""", h.handler.sent.last().payloadJson)
        assertNull(h.row("n1"))
    }

    @Test
    fun discardingDropsItAndRestoresTheServerView() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.handler.answer = { failure(ApiErrorCode.VALIDATION_FAILED) }
        h.engine.drain()
        h.engine.discard("note", "n1")
        assertNull(h.row("n1"))
        assertEquals(listOf("n1"), h.handler.discarded)
    }

    @Test
    fun anEndedSessionLeavesTheChangeAlone() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.handler.answer = { failure(ApiErrorCode.UNAUTHENTICATED) }
        h.engine.drain()
        assertEquals("Waiting", h.row("n1")!!.state)
        assertEquals(0, h.row("n1")!!.attempts)
    }

    @Test
    fun aCategoryInUseRefusalIsKeptWithItsCodeAndNotRetried() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.handler.answer = { failure(ApiErrorCode.CATEGORY_IN_USE) }
        h.engine.drain()
        assertEquals("Refused", h.row("n1")!!.state)
        assertEquals("CATEGORY_IN_USE", h.engine.get("note", "n1")!!.refusal!!.code)
        h.engine.drain()
        assertEquals(1, h.handler.sent.size)
    }
}
