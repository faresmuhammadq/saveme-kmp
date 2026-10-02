package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.FakeSyncApi.Companion.failure
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A request-level answer is every item's answer, as the single write would have had it (spec 009 FR-020, FR-023). */
class SyncEngineBatchRequestTest {

    private suspend fun BatchHarness.three() = listOf("a", "b", "c").forEach { save(it) }

    @Test
    fun noConnectionOrNoAnswerInTimeSettlesEveryItemAsOfflineAndStopsTheRun() = runTest {
        for (answer in listOf<ApiResult<List<BatchItemResultDto>>>(ApiResult.Offline, ApiResult.Timeout)) {
            val h = BatchHarness()
            h.three()
            h.api.batchAnswer = { answer }
            val report = h.engine.drain()
            assertTrue(report.offline)
            listOf("a", "b", "c").forEach {
                val row = h.row(it)!!
                assertEquals("Waiting", row.state)
                assertEquals(1, row.attempts)
            }
            assertEquals(1, h.api.batches.size)
        }
    }

    @Test
    fun aTimedOutBatchIsResentNextRunWithTheSameItems() = runTest {
        val h = BatchHarness()
        h.three()
        var answer: ApiResult<List<BatchItemResultDto>> = ApiResult.Timeout
        h.api.batchAnswer = { items -> if (answer is ApiResult.Timeout) answer else ApiResult.Success(items.map { FakeSyncApi.appliedResult(it) }) }
        h.engine.drain()
        answer = ApiResult.Success(emptyList())
        h.clock.now = h.clock.now + 5.seconds
        h.engine.drain()
        assertEquals(h.api.batches[0].map { it.id }, h.api.batches[1].map { it.id })
        assertTrue(h.dao.rows.value.isEmpty())
    }

    @Test
    fun anEndedSessionLeavesEveryItemUnchanged() = runTest {
        val h = BatchHarness()
        h.three()
        h.api.batchAnswer = { failure(ApiErrorCode.UNAUTHENTICATED, 401) }
        h.engine.drain()
        listOf("a", "b", "c").forEach {
            val row = h.row(it)!!
            assertEquals("Waiting", row.state)
            assertEquals(0, row.attempts)
            assertNull(row.nextAttemptAt)
        }
    }

    @Test
    fun aBusyServerOrARateLimitBacksEveryItemOffHonouringTheWaitTheServerAsked() = runTest {
        for ((code, status) in listOf(ApiErrorCode.RATE_LIMITED to 429, ApiErrorCode.INTERNAL_ERROR to 500, ApiErrorCode.SERVICE_UNAVAILABLE to 503)) {
            val h = BatchHarness()
            h.three()
            h.api.batchAnswer = { failure(code, status, retryAfter = 90) }
            h.engine.drain()
            listOf("a", "b", "c").forEach {
                val row = h.row(it)!!
                assertEquals("Waiting", row.state, "$code")
                assertEquals(1, row.attempts)
                val wait = Instant.parse(row.nextAttemptAt!!) - h.clock.now()
                assertTrue(wait >= 90.seconds, "$code waits for what the server asked: $wait")
            }
        }
    }

    @Test
    fun anUpdateRequiredAnswerRefusesEveryItemAsTheSingleWriteWould() = runTest {
        val h = BatchHarness()
        h.three()
        h.api.batchAnswer = { failure(ApiErrorCode.CLIENT_VERSION_UNSUPPORTED, 426) }
        h.engine.drain()
        listOf("a", "b", "c").forEach {
            val row = assertNotNull(h.row(it))
            assertEquals("Refused", row.state)
            assertTrue(row.refusalJson!!.contains("CLIENT_VERSION_UNSUPPORTED"))
        }
    }

    @Test
    fun aRequestLevelAnswerNeverSendsTheLaterBatchesOfTheSameRun() = runTest {
        val h = BatchHarness()
        (1..60).forEach { h.save("n$it") }
        h.api.batchAnswer = { ApiResult.Offline }
        h.engine.drain()
        assertEquals(1, h.api.batches.size, "the run stops")
        assertEquals("Waiting", h.row("n51")!!.state)
        assertEquals(0, h.row("n51")!!.attempts, "rows never sent are untouched")
    }
}
