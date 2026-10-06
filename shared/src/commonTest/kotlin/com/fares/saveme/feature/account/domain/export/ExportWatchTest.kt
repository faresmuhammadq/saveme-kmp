package com.fares.saveme.feature.account.domain.export

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Polling is 5 s while queued or running, bounded at 10 minutes or 120 reads (FR-010, FR-011, SC-004). */
class ExportWatchTest {

    /** The clock follows the test's virtual time, so the 10-minute window is testable. */
    private fun service(env: ExportTestEnv, scope: TestScope) =
        DefaultExportService(env.api, now = { Base + scope.testScheduler.currentTime.milliseconds })

    @Test
    fun readsAtOnceThenEveryFiveSecondsUntilReady() = runTest {
        val env = ExportTestEnv(scripted(listOf(exportJson(status = "queued"), exportJson(status = "running"), exportJson(status = "running"), exportJson(status = "ready"))))
        env.signedIn()
        val events = service(env, this).watch("e1").toList()
        assertEquals(listOf(ExportStatus.Queued, ExportStatus.Running, ExportStatus.Running, ExportStatus.Ready), events.map { assertIs<WatchEvent.Status>(it).export.status })
        assertEquals(4, env.requests.size)
        assertEquals(15_000L, currentTime)
    }

    @Test
    fun aFinalStatusEndsAfterOneRead() = runTest {
        for (status in listOf("ready", "failed", "expired", "archiving")) {
            val env = ExportTestEnv(scripted(listOf(exportJson(status = status))))
            env.signedIn()
            val events = service(env, this).watch("e1").toList()
            assertEquals(1, events.size, status)
            assertEquals(1, env.requests.size, status)
        }
        assertEquals(0L, currentTime)
    }

    @Test
    fun notFoundOfflineAndFailureEndThePolling() = runTest {
        val gone = ExportTestEnv { jsonResponse(404, problemBody(404, "NOT_FOUND")) }
        gone.signedIn()
        assertEquals(listOf(WatchEvent.NotFound), service(gone, this).watch("e1").toList())
        val offline = ExportTestEnv { throw IOException("offline") }
        offline.signedIn()
        assertEquals(listOf(WatchEvent.Offline), service(offline, this).watch("e1").toList())
        val broken = ExportTestEnv { jsonResponse(500, problemBody(500, "INTERNAL_ERROR")) }
        broken.signedIn()
        assertEquals(listOf(WatchEvent.Failed(ApiErrorCode.INTERNAL_ERROR, "req-42")), service(broken, this).watch("e1").toList())
    }

    @Test
    fun offlineAfterAnActiveReadStopsAtOnce() = runTest {
        var n = 0
        val env = ExportTestEnv { if (++n == 1) jsonResponse(200, envelope(exportJson(status = "running"))) else throw IOException("offline") }
        env.signedIn()
        val events = service(env, this).watch("e1").toList()
        assertIs<WatchEvent.Status>(events[0])
        assertEquals(WatchEvent.Offline, events[1])
        assertEquals(2, env.requests.size)
    }

    @Test
    fun stillActiveAfterTenMinutesSaysStillPreparing() = runTest {
        // Requested 9 minutes 58 seconds before the first read: the second read, 5 s later, is past the window.
        val requested = "2026-10-06T09:50:02Z"
        val env = ExportTestEnv(scripted(listOf(exportJson(status = "running", requestedAt = requested))))
        env.signedIn()
        val events = service(env, this).watch("e1").toList()
        assertEquals(2, env.requests.size)
        assertIs<WatchEvent.StillPreparing>(events.last())
        assertEquals(5_000L, currentTime)
    }

    @Test
    fun aHundredAndTwentyReadsEndThePollingEvenInsideTheWindow() = runTest {
        val env = ExportTestEnv(scripted(listOf(exportJson(status = "running"))))
        env.signedIn()
        // The window starts now, so only the read cap can end it: 120 reads, 119 waits of 5 s.
        val events = service(env, this).watch("e1", windowStart = Base + 1.minutes).toList()
        assertEquals(120, env.requests.size)
        assertIs<WatchEvent.StillPreparing>(events.last())
        assertEquals(119 * 5_000L, currentTime)
    }

    @Test
    fun cancellingTheCollectorStopsTheReads() = runTest {
        val env = ExportTestEnv(scripted(listOf(exportJson(status = "running"))))
        env.signedIn()
        val job = launch { service(env, this@runTest).watch("e1").toList() }
        // The first read goes through the mock engine on a real thread; wait for it, then cancel during the 5 s pause.
        withContext(Dispatchers.Default) { while (env.requests.isEmpty()) delay(5) }
        job.cancel()
        val reads = env.requests.size
        advanceTimeBy(60.seconds)
        assertEquals(reads, env.requests.size)
    }
}
