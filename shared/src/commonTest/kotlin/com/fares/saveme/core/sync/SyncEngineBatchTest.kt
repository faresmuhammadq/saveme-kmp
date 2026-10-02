package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.FakeSyncApi.Companion.rejected
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US1: due changes leave through the batch upload and each answer settles on its own (spec 009 FR-017, FR-019). */
class SyncEngineBatchTest {

    @Test
    fun dueChangesArePackedOldestFirstAtMostFiftyPerRequest() = runTest {
        val h = BatchHarness()
        (1..51).forEach { h.save("n$it") }
        val report = h.engine.drain()

        assertEquals(listOf(50, 1), h.api.batches.map { it.size })
        assertEquals((1..50).map { "n$it" }, h.api.batches[0].map { it.id })
        assertEquals(listOf("n51"), h.api.batches[1].map { it.id })
        assertEquals(emptyList(), h.handler.sent, "nothing went per record")
        assertTrue(h.dao.rows.value.isEmpty())
        assertTrue(report.appliedAny)
        assertEquals(setOf(h.user), report.written)
    }

    @Test
    fun rowsAreMarkedSendingBeforeTheRequest() = runTest {
        val h = BatchHarness()
        h.save("n1")
        h.save("n2")
        var states = emptyList<String>()
        h.api.batchAnswer = { items ->
            states = items.map { h.row(it.id)!!.state }
            ApiResult.Success(items.map { FakeSyncApi.appliedResult(it) })
        }
        h.engine.drain()
        assertEquals(listOf("Sending", "Sending"), states)
    }

    @Test
    fun anAppliedItemClearsTheRowStoresTheAnswerAndStampsTheLastPush() = runTest {
        val h = BatchHarness()
        h.save("n1", base = 3)
        h.engine.drain()
        assertNull(h.row("n1"))
        assertEquals(1, h.handler.applied.size)
        assertEquals("n1", (h.handler.applied.single() as JsonObject)["id"]!!.jsonPrimitive.content)
        assertNotNull(h.scopes.rows.value["user:u1"]?.lastPushedAt)
    }

    @Test
    fun theItemCarriesTheBaseVersionAndTheBodyOfThePerRecordWrite() = runTest {
        val h = BatchHarness()
        h.save("n1", """{"t":"a"}""", base = 7)
        h.engine.drain()
        val item = h.api.batches.single().single()
        assertEquals("note", item.type)
        assertEquals("upsert", item.op)
        assertEquals("""{"t":"a"}""", (item.data as JsonObject)["payload"]!!.jsonPrimitive.content)
        assertEquals("7", (item.data as JsonObject)["version"].toString())
        assertNull(item.walletId)
    }

    @Test
    fun aChangeEditedDuringTheRequestIsRebasedOnTheReturnedVersionAndKeepsWaiting() = runTest {
        val h = BatchHarness()
        h.save("n1", """{"t":"a"}""", base = 1)
        h.api.batchAnswer = { items ->
            h.save("n1", """{"t":"b"}""") // the person edits while the request is in flight
            ApiResult.Success(items.map { FakeSyncApi.appliedResult(it, version = 2) })
        }
        h.engine.drain()
        val row = assertNotNull(h.row("n1"))
        assertEquals("Waiting", row.state)
        assertEquals(2L, row.baseVersion)
        assertEquals("""{"t":"b"}""", row.payloadJson)
    }

    @Test
    fun aConflictBecomesAClashWithTheCurrentRecordWhileTheOthersAreAccepted() = runTest {
        val h = BatchHarness()
        h.save("n1")
        h.save("n2")
        h.save("n3")
        h.api.batchAnswer = { items ->
            ApiResult.Success(
                items.map {
                    if (it.id == "n2") rejected(it, ApiErrorCode.VERSION_CONFLICT, 409, current = BatchHarness().record(5))
                    else FakeSyncApi.appliedResult(it)
                },
            )
        }
        h.engine.drain()
        assertNull(h.row("n1"))
        assertNull(h.row("n3"))
        val clash = assertNotNull(h.row("n2"))
        assertEquals("Clash", clash.state)
        assertTrue(clash.clashJson!!.contains("\"version\":5"))
    }

    @Test
    fun aValidationRefusalIsRefusedWithTheMappedFieldsAndNotRetried() = runTest {
        val h = BatchHarness()
        h.save("n1")
        h.api.batchAnswer = { items ->
            ApiResult.Success(
                listOf(
                    BatchItemResultDto(
                        items[0].type, items[0].id, "rejected", 422, null,
                        com.fares.saveme.core.network.ProblemDetails(
                            status = 422, code = "VALIDATION_FAILED", requestId = "req-9",
                            errors = listOf(com.fares.saveme.core.network.ProblemError("/amount_minor", "AMOUNT_NOT_INTEGER")),
                        ),
                    ),
                ),
            )
        }
        h.engine.drain()
        val row = assertNotNull(h.row("n1"))
        assertEquals("Refused", row.state)
        assertTrue(row.refusalJson!!.contains("amount_minor"))
        assertTrue(row.refusalJson!!.contains("req-9"))
        assertEquals(1, h.api.batches.size)
        h.engine.drain()
        assertEquals(1, h.api.batches.size, "a refused change is not sent again by itself")
    }

    @Test
    fun anUnexpectedFailureOfOneItemIsRetriedWithABackoffAndTheOthersAreNotAffected() = runTest {
        val h = BatchHarness()
        h.save("n1")
        h.save("n2")
        h.api.batchAnswer = { items ->
            ApiResult.Success(items.map { if (it.id == "n1") rejected(it, ApiErrorCode.INTERNAL_ERROR, 500) else FakeSyncApi.appliedResult(it) })
        }
        h.engine.drain()
        val row = assertNotNull(h.row("n1"))
        assertEquals("Waiting", row.state)
        assertEquals(1, row.attempts)
        assertNotNull(row.nextAttemptAt)
        assertNull(h.row("n2"))
        assertTrue(h.scheduler.enqueued.isNotEmpty())
    }

    @Test
    fun anItemAnsweredWithTheSameCodeAsTheSingleWriteMapsToTheSameTreatment() = runTest {
        // CATEGORY_IN_USE is a refusal (never retried) exactly as on the per-record path.
        val h = BatchHarness()
        h.save("n1")
        h.api.batchAnswer = { items -> ApiResult.Success(listOf(rejected(items[0], ApiErrorCode.CATEGORY_IN_USE, 409))) }
        h.engine.drain()
        assertEquals("Refused", h.row("n1")!!.state)
        assertTrue(h.row("n1")!!.refusalJson!!.contains("CATEGORY_IN_USE"))
    }

    @Test
    fun aHandlerWithoutBatchSupportOrAnEngineWithoutABatchClientSendsPerRecord() = runTest {
        val dao = FakePendingChangeDao()
        val handler = FakeHandler(answer = { EngineHarness.ok(2) })
        val engine = SyncEngine(dao, com.fares.saveme.feature.account.FakeSyncScopeDao(), listOf(handler), FakeSyncScheduler(), currentAccountId = { "u1" }, syncApi = FakeSyncApi())
        engine.enqueue("note", "n1", SyncScope(ScopeKind.User, "u1"), 1, "{}")
        engine.drain()
        assertEquals(listOf("n1"), handler.sent.map { it.recordId }, "FakeHandler.batchItem is null: per record, as in spec 004")
    }
}
