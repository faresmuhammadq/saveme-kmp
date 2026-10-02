package com.fares.saveme.core.sync

import com.fares.saveme.core.sync.EngineHarness.Companion.ok
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** FR-018 and FR-005: nothing is sent under another account; an interrupted send is resent. */
class SyncEngineAccountTest {

    @Test
    fun anotherAccountsChangesAreNeverSent() = runTest {
        val h = EngineHarness(account = "u1")
        h.save("n1", "{}")
        h.account = "u2"
        h.handler.answer = { ok(2) }
        h.engine.drain()
        assertTrue(h.handler.sent.isEmpty())
    }

    @Test
    fun nothingIsSentWhileSignedOut() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.account = null
        h.engine.drain()
        assertTrue(h.handler.sent.isEmpty())
    }

    @Test
    fun aBackgroundDrainLoadsTheSessionBeforeSending() = runTest {
        // A drain in a fresh process: no screen has restored the session yet.
        val h = EngineHarness()
        h.save("n1", "{}")
        h.account = null
        h.onLoad = { h.account = "u1" }
        h.handler.answer = { ok(2) }
        h.engine.drain()
        assertEquals(listOf("n1"), h.handler.sent.map { it.recordId })
    }

    @Test
    fun aChangeCanOnlyBeMadeWhileSignedIn() = runTest {
        val h = EngineHarness(account = null)
        assertFailsWith<IllegalStateException> { h.save("n1", "{}") }
    }

    @Test
    fun aSendInterruptedByProcessDeathIsSentAgain() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.dao.upsert(h.row("n1")!!.copy(state = "Sending")) // the process died mid-send
        h.handler.answer = { ok(2) }
        h.engine.drain()
        assertEquals(1, h.handler.sent.size)
        assertEquals(null, h.row("n1"))
    }
}
