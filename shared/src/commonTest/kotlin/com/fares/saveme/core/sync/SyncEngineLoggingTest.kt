package com.fares.saveme.core.sync

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.sync.EngineHarness.Companion.failure
import com.fares.saveme.core.sync.EngineHarness.Companion.ok
import com.fares.saveme.core.sync.EngineHarness.Companion.record
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Spec 004 §4 / P-09.2: the engine logs type, id and state, never what was typed. */
class SyncEngineLoggingTest {

    private val lines = mutableListOf<String>()
    private val writer = object : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) { lines += "$tag $message" }
    }

    init {
        Logger.setLogWriters(writer)
        Logger.setMinSeverity(Severity.Verbose)
    }

    @AfterTest
    fun reset() = Logger.setLogWriters(emptyList())

    @Test
    fun payloadsNeverReachTheLog() = runTest {
        val h = EngineHarness()
        val marker = "SECRET-AMOUNT-12500"
        listOf(
            { _: PendingChange -> ok(2) },
            { _: PendingChange -> failure(ApiErrorCode.VERSION_CONFLICT, current = record(5)) },
            { _: PendingChange -> failure(ApiErrorCode.VALIDATION_FAILED) },
            { _: PendingChange -> failure(ApiErrorCode.INTERNAL_ERROR) },
        ).forEachIndexed { i, answer ->
            h.save("n$i", """{"amount":"$marker"}""")
            h.handler.answer = answer
            h.engine.drain()
        }
        assertTrue(lines.any { it.startsWith("sync") }, "the engine does log")
        assertFalse(lines.any { marker in it }, lines.joinToString("\n"))
        assertFalse(h.row("n1").toString().contains(marker))
    }
}
