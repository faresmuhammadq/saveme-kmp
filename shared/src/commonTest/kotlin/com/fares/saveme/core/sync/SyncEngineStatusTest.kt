package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.sync.EngineHarness.Companion.failure
import com.fares.saveme.core.sync.EngineHarness.Companion.ok
import com.fares.saveme.core.sync.EngineHarness.Companion.record
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** US2: counts and last sync time for the account area (FR-015, FR-016). */
class SyncEngineStatusTest {

    @Test
    fun countsWaitingAndNeedsDecisionAndTheLastSync() = runTest {
        val h = EngineHarness()
        assertEquals(SyncStatus(), h.engine.status.first())

        h.save("ok", "{}")
        h.save("clash", "{}")
        h.save("refused", "{}")
        h.save("waiting", "{}")
        h.handler.answer = { change ->
            when (change.recordId) {
                "ok" -> ok(2)
                "clash" -> failure(ApiErrorCode.VERSION_CONFLICT, current = record(5))
                "refused" -> failure(ApiErrorCode.VALIDATION_FAILED, pointer = "/t")
                else -> failure(ApiErrorCode.SERVICE_UNAVAILABLE)
            }
        }
        h.engine.drain()

        val status = h.engine.status.first()
        assertEquals(1, status.waiting)
        assertEquals(2, status.needsDecision)
        assertEquals(h.clock.now, status.lastSyncedAt)
    }

    @Test
    fun aRecordsStateIsObservable() = runTest {
        val h = EngineHarness()
        assertNull(h.engine.observe("note", "n1").first())
        h.save("n1", "{}")
        assertEquals(ChangeState.Waiting, h.engine.observe("note", "n1").first()!!.state)
    }
}
