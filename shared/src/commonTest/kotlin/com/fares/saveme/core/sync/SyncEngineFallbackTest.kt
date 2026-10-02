package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.FakeSyncApi.Companion.failure
import com.fares.saveme.core.network.ProblemDetails
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A request refused for a reason that is not about the changes is sent per record this run (spec 009 FR-021, US5). */
class SyncEngineFallbackTest {

    private fun refusals() = listOf<ApiResult<List<BatchItemResultDto>>>(
        failure(ApiErrorCode.VALIDATION_FAILED, 422),
        failure(ApiErrorCode.MALFORMED_JSON, 400),
        failure(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, 415),
        failure(ApiErrorCode.NOT_ACCEPTABLE, 406),
        failure(ApiErrorCode.NOT_FOUND, 404),
        ApiResult.Failure(ProblemDetails(status = 405), ApiErrorCode.UNKNOWN, null),
        ApiResult.Failure(ProblemDetails(status = 404), ApiErrorCode.UNKNOWN, null), // an HTML page from a server without the endpoint
        ApiResult.Failure(ProblemDetails(status = 200, code = HttpSyncApi.UNREADABLE), ApiErrorCode.UNKNOWN, null),
    )

    @Test
    fun everyShapeOrEndpointRefusalSendsTheSameRowsPerRecordInTheSameOrderInThisRun() = runTest {
        for (refusal in refusals()) {
            val h = BatchHarness()
            h.handler.answer = { EngineHarness.ok(2) }
            listOf("a", "b", "c").forEach { h.save(it) }
            h.api.batchAnswer = { refusal }
            h.engine.drain()
            assertEquals(listOf("a", "b", "c"), h.handler.sent.map { it.recordId }, "$refusal")
            assertTrue(h.dao.rows.value.isEmpty(), "$refusal")
        }
    }

    @Test
    fun aResultListThatDoesNotMatchTheItemsFallsBackToo() = runTest {
        val mismatches = listOf<(List<BatchItemDto>) -> List<BatchItemResultDto>>(
            { items -> items.drop(1).map { FakeSyncApi.appliedResult(it) } }, // count
            { items -> items.reversed().map { FakeSyncApi.appliedResult(it) } }, // order
            { items -> items.map { FakeSyncApi.appliedResult(it.copy(id = it.id + "x")) } }, // id
            { items -> items.map { FakeSyncApi.appliedResult(it.copy(type = "other")) } }, // type
            { items -> items.map { FakeSyncApi.appliedResult(it).copy(record = null) } }, // applied without a record
        )
        for (mismatch in mismatches) {
            val h = BatchHarness()
            h.handler.answer = { EngineHarness.ok(2) }
            listOf("a", "b").forEach { h.save(it) }
            h.api.batchAnswer = { ApiResult.Success(mismatch(it)) }
            h.engine.drain()
            assertEquals(listOf("a", "b"), h.handler.sent.map { it.recordId })
        }
    }

    @Test
    fun theNextRunTriesTheBatchAgain() = runTest {
        val h = BatchHarness()
        h.handler.answer = { EngineHarness.ok(2) }
        h.save("a")
        h.api.batchAnswer = { failure(ApiErrorCode.NOT_FOUND, 404) }
        h.engine.drain()
        h.save("b")
        h.api.batchAnswer = { items -> ApiResult.Success(items.map { FakeSyncApi.appliedResult(it) }) }
        h.engine.drain()
        assertEquals(2, h.api.batches.size)
        assertEquals(listOf("a"), h.handler.sent.map { it.recordId })
    }

    @Test
    fun theFallbackSettlesEachRowAsItsPerRecordAnswerAndAStillHeldDependentWaits() = runTest {
        val h = BatchHarness("wallet", "transaction")
        h.handlers[0].answer = { failure(ApiErrorCode.FEATURE_NOT_ENTITLED, 403) }
        h.handlers[1].answer = { EngineHarness.ok(1) }
        h.handlers[1].prerequisitesOf["t1"] = listOf(RecordKey("wallet", "w1"))
        h.save("w1", base = 0, type = "wallet", scope = SyncScope(ScopeKind.Wallet, "w1"))
        h.save("t1", base = 0, type = "transaction", scope = SyncScope(ScopeKind.Wallet, "w1"))
        h.api.batchAnswer = { failure(ApiErrorCode.NOT_FOUND, 404) }
        h.engine.drain()
        assertEquals("Refused", h.row("w1", "wallet")!!.state)
        assertEquals("Waiting", h.row("t1", "transaction")!!.state)
        assertEquals(emptyList(), h.handlers[1].sent, "the dependent was not sent after its prerequisite was refused")
    }
}
