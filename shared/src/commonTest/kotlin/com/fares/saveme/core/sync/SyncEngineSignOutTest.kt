package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A run that is signed out sends and applies nothing afterwards (spec 009 FR-028). */
class SyncEngineSignOutTest {

    @Test
    fun anAnswerThatArrivesAfterSignOutIsNotApplied() = runTest {
        val h = BatchHarness()
        h.save("a")
        h.save("b")
        h.api.batchAnswer = { items ->
            h.account = null // the person signed out while the request was in flight
            ApiResult.Success(items.map { FakeSyncApi.appliedResult(it) })
        }
        h.engine.drain()
        assertEquals(emptyList(), h.handler.applied, "nothing is written under no account")
    }

    @Test
    fun aRunStopsBeforeTheNextBatchOncethePersonHasSignedOut() = runTest {
        val h = BatchHarness()
        (1..60).forEach { h.save("n$it") }
        h.api.batchAnswer = { items ->
            h.account = null
            ApiResult.Success(items.map { FakeSyncApi.appliedResult(it) })
        }
        h.engine.drain()
        assertEquals(1, h.api.batches.size)
        assertTrue(h.handler.applied.isEmpty())
    }

    @Test
    fun aPerRecordAnswerAfterSignOutIsNotAppliedEither() = runTest {
        val h = BatchHarness()
        h.handler.perRecordOnly += "x"
        h.handler.answer = { h.account = null; EngineHarness.ok(2) }
        h.save("x")
        h.engine.drain()
        assertEquals(emptyList(), h.handler.applied)
    }
}
