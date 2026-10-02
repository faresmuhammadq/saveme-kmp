package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.Envelope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The same answers through the batch path and through per-record sends leave the same queue and copies (spec 009 FR-019). */
class SyncEngineParityTest {

    private fun decode(body: String): List<BatchItemResultDto> =
        AppJson.decodeFromString(Envelope.serializer(ListSerializer(BatchItemResultDto.serializer())), body).data

    private val answers = decode(OpenApiSyncExamples.MIXED_RESULTS) + decode(OpenApiSyncExamples.VALIDATION_REFUSAL)

    private suspend fun BatchHarness.queue() {
        val wallet = SyncScope(ScopeKind.Wallet, "w1")
        save("0192f7a1-0000-7000-8000-0000000000c2", base = 0, type = "category")
        save("0192f7b0-0000-7000-8000-0000000000e3", base = 0, type = "transaction", scope = wallet)
        save("0192f7b0-0000-7000-8000-0000000000e2", base = 2, type = "transaction", scope = wallet)
        save("0192f7b0-0000-7000-8000-0000000000e4", base = 1, type = "transaction", scope = wallet)
        save("0192f7b0-0000-7000-8000-0000000000e5", base = 0, type = "transaction", scope = wallet)
    }

    private fun BatchHarness.rows() = dao.rows.value.values.sortedBy { it.createdOrder }
        .map { listOf(it.recordType, it.recordId, it.state, it.baseVersion, it.attempts, it.clashJson, it.refusalJson?.replace(Regex("\"request_id\":\"[^\"]*\""), "")) }

    @Test
    fun theSameAnswersLeaveIdenticalRowsCopiesAndStatusThroughBothPaths() = runTest {
        val batched = BatchHarness("category", "transaction")
        batched.queue()
        batched.api.batchAnswer = { items -> ApiResult.Success(items.map { item -> answers.first { it.id == item.id } }) }
        batched.engine.drain()

        val single = BatchHarness("category", "transaction")
        single.handlers.forEach { handler ->
            handler.answer = { change ->
                val result = answers.first { it.id == change.recordId }
                if (result.outcome == "applied") ApiResult.Success(result.record!!)
                else ApiResult.Failure(result.problem!!, ApiErrorCode.from(result.problem.code), result.problem.retryAfterSeconds)
            }
            single.handlers.forEach { }
        }
        single.queue()
        single.handlers.forEach { h -> answers.forEach { h.perRecordOnly += it.id } }
        single.engine.drain()

        assertEquals(single.rows(), batched.rows())
        assertEquals(single.handlers.flatMap { it.applied }.toSet(), batched.handlers.flatMap { it.applied }.toSet())
        assertNotNull(batched.scopes.rows.value["user:u1"]?.lastPushedAt)
        assertEquals(single.scopes.rows.value.keys, batched.scopes.rows.value.keys)
        // Two accepted, one clash, two refusals.
        assertEquals(listOf("Clash", "Refused", "Refused"), batched.rows().map { it[2] as String })
    }
}
