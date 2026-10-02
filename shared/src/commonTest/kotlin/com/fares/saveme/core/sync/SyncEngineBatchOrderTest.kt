package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A change the batch cannot carry keeps its place in the order (spec 009 FR-018, US1-S8). */
class SyncEngineBatchOrderTest {

    @Test
    fun aPerRecordRowFlushesTheBatchBeforeItIsSentAloneAndBatchingResumesAfterIt() = runTest {
        val h = BatchHarness()
        h.handler.perRecordOnly += "archive"
        h.handler.answer = { EngineHarness.ok(2) }
        listOf("a", "b", "archive", "c", "d").forEach { h.save(it) }
        h.engine.drain()

        assertEquals(listOf("batch 2: a, b", "send archive", "batch 2: c, d"), h.log)
        assertTrue(h.dao.rows.value.isEmpty())
    }

    @Test
    fun anArchiveAndARestoreInTheMiddleOfTenEditsKeepTheirPlace() = runTest {
        val h = BatchHarness()
        h.handler.perRecordOnly += setOf("archive", "restore")
        h.handler.answer = { EngineHarness.ok(2) }
        (1..4).forEach { h.save("e$it") }
        h.save("archive")
        (5..7).forEach { h.save("e$it") }
        h.save("restore")
        (8..10).forEach { h.save("e$it") }
        h.engine.drain()

        assertEquals(
            listOf(
                "batch 4: e1, e2, e3, e4", "send archive", "batch 3: e5, e6, e7", "send restore", "batch 3: e8, e9, e10",
            ),
            h.log,
        )
    }

    @Test
    fun aRowCreatedBeforeAPerRecordRowIsSentBeforeItEvenWhenItIsAnItemOfTheLaterBatch() = runTest {
        val h = BatchHarness()
        h.handler.perRecordOnly += "mid"
        h.handler.answer = { EngineHarness.ok(2) }
        h.save("first")
        h.save("mid")
        h.engine.drain()
        assertEquals(listOf("batch 1: first", "send mid"), h.log)
    }

    @Test
    fun aPerRecordFailureThatCannotReachTheServerStopsTheRunAndLeavesLaterRowsWaiting() = runTest {
        val h = BatchHarness()
        h.handler.perRecordOnly += "mid"
        h.handler.answer = { ApiResult.Offline }
        h.save("mid")
        h.save("later")
        val report = h.engine.drain()
        assertTrue(report.offline)
        assertEquals(emptyList(), h.api.batches)
        assertEquals("Waiting", h.row("later")!!.state)
    }
}
