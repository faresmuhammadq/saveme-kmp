package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.EngineHarness.Companion.failure
import com.fares.saveme.core.sync.EngineHarness.Companion.ok
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** US1: retries never give up and follow the backoff (FR-009). */
class SyncEngineRetryTest {

    @Test
    fun offlineWaitsForAConnectionWithNoDelay() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.handler.answer = { ApiResult.Offline }
        h.engine.drain()
        val row = h.row("n1")!!
        assertEquals("Waiting", row.state)
        assertEquals(1, row.attempts)
        assertNull(row.nextAttemptAt)
        assertEquals(Duration.ZERO, h.scheduler.enqueued.last())
    }

    @Test
    fun repeatedUnreachableBacksOffSoItNeverLoops() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.handler.answer = { ApiResult.Offline }
        val waits = (1..6).map {
            h.engine.drain()
            h.scheduler.enqueued.last().also { h.clock.now += it }
        }
        // First retry as soon as a connection exists; then 2 s, 5 s, 15 s, 30 s, capped at 30 s.
        assertEquals(listOf(Duration.ZERO, 2.seconds, 5.seconds, 15.seconds, 30.seconds, 30.seconds), waits)
    }

    @Test
    fun aRunBeforeTheWaitIsOverSchedulesTheRest() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.handler.answer = { failure(ApiErrorCode.RATE_LIMITED, retryAfter = 90) }
        h.engine.drain()
        h.clock.now += 30.seconds
        h.scheduler.enqueued.clear()

        h.engine.drain()

        assertEquals(1, h.handler.sent.size, "not due yet")
        assertEquals(listOf(60.seconds), h.scheduler.enqueued, "otherwise it waits until the app is opened")
    }

    @Test
    fun aRowWithNoHandlerIsNotRescheduledInALoop() = runTest {
        val h = EngineHarness()
        h.engine.enqueue("unknown", "x1", h.scope, 1, "{}")
        h.scheduler.enqueued.clear()
        h.engine.drain()
        assertEquals(emptyList(), h.scheduler.enqueued)
    }

    @Test
    fun aWaitEndingOnAWholeSecondIsDueOnTime() = runTest {
        // Instant.toString() drops a zero fraction: "…:02Z" would sort after "…:02.001Z".
        val h = EngineHarness()
        h.save("n1", "{}")
        h.handler.answer = { failure(ApiErrorCode.INTERNAL_ERROR) }
        h.engine.drain()
        h.clock.now += 2.seconds + kotlin.time.Duration.parse("1ms")
        h.handler.answer = { ok(2) }
        h.engine.drain()
        assertNull(h.row("n1"))
    }

    @Test
    fun offlineStopsTheRestOfTheDrain() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.save("n2", "{}")
        h.handler.answer = { ApiResult.Timeout }
        h.engine.drain()
        assertEquals(1, h.handler.sent.size, "no connection for the second either")
    }

    @Test
    fun theServersWaitIsHonoured() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.handler.answer = { failure(ApiErrorCode.RATE_LIMITED, retryAfter = 90) }
        h.engine.drain()
        assertEquals("2026-10-02T10:01:30.000Z", h.row("n1")!!.nextAttemptAt)
        assertEquals(90.seconds, h.scheduler.enqueued.last())
    }

    @Test
    fun serverErrorsBackOffUpToFiveMinutesAndNeverStop() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.handler.answer = { failure(ApiErrorCode.INTERNAL_ERROR) }
        val waits = (1..9).map {
            h.engine.drain()
            h.scheduler.enqueued.last().also { h.clock.now += it }
        }
        assertEquals(listOf(2.seconds, 5.seconds, 15.seconds, 30.seconds, 60.seconds, 2.minutes, 5.minutes, 5.minutes, 5.minutes), waits)
        assertEquals("Waiting", h.row("n1")!!.state)

        h.handler.answer = { ok(2) }
        h.engine.drain()
        assertNull(h.row("n1"))
    }

    @Test
    fun aRowIsNotSentBeforeItIsDue() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.handler.answer = { failure(ApiErrorCode.SERVICE_UNAVAILABLE) }
        h.engine.drain()
        h.engine.drain()
        assertEquals(1, h.handler.sent.size)
    }

    @Test
    fun syncNowMakesEveryWaitingRowDue() = runTest {
        val h = EngineHarness()
        h.save("n1", "{}")
        h.handler.answer = { failure(ApiErrorCode.SERVICE_UNAVAILABLE) }
        h.engine.drain()
        h.engine.syncNow()
        assertEquals(Duration.ZERO, h.scheduler.enqueued.last())
        h.engine.drain()
        assertEquals(2, h.handler.sent.size)
    }
}
