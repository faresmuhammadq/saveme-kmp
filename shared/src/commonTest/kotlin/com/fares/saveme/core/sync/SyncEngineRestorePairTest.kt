package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.FakeSyncApi.Companion.appliedResult
import com.fares.saveme.core.sync.FakeSyncApi.Companion.rejected
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Restore with my change" leaves as restore (n) + upsert (n + 1) in one request and is settled as one change (spec 011 US3, FR-014 - FR-016). */
class SyncEngineRestorePairTest {

    private val wallet = SyncScope(ScopeKind.Wallet, "w1")

    private suspend fun BatchHarness.pair(id: String, base: Long = 4) = saveRestore(id, base, type = "transaction", scope = wallet)

    private suspend fun BatchHarness.edit(id: String) = save(id, type = "transaction", scope = wallet)

    private fun harness() = BatchHarness("transaction")

    private fun JsonObject.version() = (get("version") as JsonPrimitive).content.toLong()

    /** Answers the first item of the pair with [first] and the second with [second]; every other item is applied. */
    private fun BatchHarness.answerPair(first: (BatchItemDto) -> BatchItemResultDto, second: (BatchItemDto) -> BatchItemResultDto) {
        api.batchAnswer = { items ->
            var seen = 0
            ApiResult.Success(
                items.map { item ->
                    if (item.id == "pair") {
                        if (seen++ == 0) first(item) else second(item)
                    } else {
                        appliedResult(item)
                    }
                },
            )
        }
    }

    @Test
    fun aRestoreWithFiveOtherEditsLeavesInOneRequestWithThePairConsecutiveInQueueOrder() = runTest {
        val h = harness()
        h.edit("a")
        h.edit("b")
        h.pair("pair")
        h.edit("c")
        h.edit("d")
        h.edit("e")
        h.engine.drain()

        assertEquals(1, h.api.batches.size)
        val items = h.api.batches.single()
        assertEquals(listOf("a", "b", "pair", "pair", "c", "d", "e"), items.map { it.id })
        assertEquals(listOf("upsert", "upsert", "restore", "upsert", "upsert", "upsert", "upsert"), items.map { it.op })
        assertEquals(listOf(4L, 5L), items.filter { it.id == "pair" }.map { (it.data as JsonObject).version() })
        assertEquals(emptyList(), h.handler.sent, "nothing went per record")
        assertTrue(h.dao.rows.value.isEmpty())
    }

    @Test
    fun aPairIsNeverSplitAcrossTwoRequests() = runTest {
        val fits = harness()
        (1..48).forEach { fits.edit("n$it") }
        fits.pair("pair")
        fits.engine.drain()
        assertEquals(listOf(50), fits.api.batches.map { it.size })
        assertEquals(listOf("pair", "pair"), fits.api.batches.single().takeLast(2).map { it.id })

        val spills = harness()
        (1..49).forEach { spills.edit("n$it") }
        spills.pair("pair")
        spills.engine.drain()
        assertEquals(listOf(49, 2), spills.api.batches.map { it.size })
        assertEquals(listOf("restore", "upsert"), spills.api.batches[1].map { it.op })
        assertTrue(spills.dao.rows.value.isEmpty())
    }

    @Test
    fun bothItemsAppliedAcceptTheChangeWithTheSecondItemsRecord() = runTest {
        val h = harness()
        h.pair("pair", base = 4)
        h.engine.drain()
        assertNull(h.row("pair", "transaction"))
        assertEquals(1, h.handler.applied.size, "one answer for the change")
        assertEquals(6L, (h.handler.applied.single() as JsonObject).version(), "the upsert's record: n + 2")
        assertNotNull(h.scopes.rows.value["wallet:w1"]?.lastPushedAt)
    }

    @Test
    fun aClashOnTheSecondItemIsAClashOnItsCurrentCopy() = runTest {
        val h = harness()
        h.pair("pair")
        val current = h.record(9)
        h.answerPair({ appliedResult(it, version = 5) }, { rejected(it, ApiErrorCode.VERSION_CONFLICT, 409, current) })
        h.engine.drain()
        val row = assertNotNull(h.row("pair", "transaction"))
        assertEquals("Clash", row.state)
        assertEquals(current.toString(), row.clashJson)
        assertTrue(h.handler.applied.isEmpty())
    }

    @Test
    fun aRefusalOfTheSecondItemIsNotSavedButAServerFailureIsRetried() = runTest {
        val refused = harness()
        refused.pair("pair")
        refused.answerPair({ appliedResult(it, version = 5) }, { rejected(it, ApiErrorCode.VALIDATION_FAILED, 422) })
        refused.engine.drain()
        val row = assertNotNull(refused.row("pair", "transaction"))
        assertEquals("Refused", row.state)
        assertTrue("VALIDATION_FAILED" in row.refusalJson.orEmpty())

        val failing = harness()
        failing.pair("pair")
        failing.answerPair({ appliedResult(it, version = 5) }, { rejected(it, ApiErrorCode.INTERNAL_ERROR, 500) })
        failing.engine.drain()
        val waiting = assertNotNull(failing.row("pair", "transaction"))
        assertEquals("Waiting", waiting.state)
        assertEquals(1, waiting.attempts)
        assertNotNull(waiting.nextAttemptAt)
    }

    @Test
    fun aClashOnTheFirstItemIsAClashOnItsCurrentCopyAndTheSecondIsIgnored() = runTest {
        val h = harness()
        h.pair("pair")
        val current = h.record(8)
        h.answerPair({ rejected(it, ApiErrorCode.VERSION_CONFLICT, 409, current) }, { rejected(it, ApiErrorCode.VALIDATION_FAILED, 422) })
        h.engine.drain()
        val row = assertNotNull(h.row("pair", "transaction"))
        assertEquals("Clash", row.state)
        assertEquals(current.toString(), row.clashJson)
        assertNull(row.refusalJson)
    }

    @Test
    fun anAnswerThatDoesNotMatchItsItemsFallsBackToTheSingleRestoreWrite() = runTest {
        val short = harness()
        short.pair("pair")
        short.handler.answer = { EngineHarness.ok(6) }
        short.api.batchAnswer = { items -> ApiResult.Success(items.take(1).map { appliedResult(it) }) }
        short.engine.drain()
        assertEquals(listOf("pair"), short.handler.sent.map { it.recordId }, "the per-record path sends the restore once")
        assertTrue(short.handler.sent.single().restore)
        assertNull(short.row("pair", "transaction"))

        val wrongId = harness()
        wrongId.pair("pair")
        wrongId.handler.answer = { EngineHarness.ok(6) }
        wrongId.api.batchAnswer = { items -> ApiResult.Success(items.map { appliedResult(it.copy(id = "other")) }) }
        wrongId.engine.drain()
        assertEquals(1, wrongId.handler.sent.size)
    }

    @Test
    fun aRequestThatFailsAsAWholeIsOneFailureOfTheChangeNotTwo() = runTest {
        val offline = harness()
        offline.pair("pair")
        offline.api.batchAnswer = { ApiResult.Offline }
        val report = offline.engine.drain()
        assertTrue(report.offline)
        assertEquals(1, offline.row("pair", "transaction")!!.attempts)
        assertEquals("Waiting", offline.row("pair", "transaction")!!.state)

        val unavailable = harness()
        unavailable.pair("pair")
        unavailable.api.batchAnswer = { FakeSyncApi.failure(ApiErrorCode.SERVICE_UNAVAILABLE, 503) }
        unavailable.engine.drain()
        assertEquals(1, unavailable.row("pair", "transaction")!!.attempts)
    }

    @Test
    fun aPairWhoseWalletCreateWaitsIsHeldWithoutATry() = runTest {
        val h = BatchHarness("wallet", "transaction")
        h.save("w1", base = 0, type = "wallet", scope = wallet)
        h.dao.upsert(h.row("w1", "wallet")!!.copy(state = "Clash"))
        h.handlers[1].prerequisitesOf["pair"] = listOf(RecordKey("wallet", "w1"))
        h.saveRestore("pair", type = "transaction", scope = wallet)
        h.engine.drain()
        val held = assertNotNull(h.row("pair", "transaction"))
        assertEquals("Waiting", held.state)
        assertEquals(0, held.attempts)
        assertEquals(emptyList(), h.api.batches)
    }

    @Test
    fun aPairWhoseEarlierPrerequisiteWasNotAcceptedReturnsToWaitingWithoutATry() = runTest {
        val h = BatchHarness("wallet", "transaction")
        h.save("w1", base = 0, type = "wallet", scope = wallet)
        h.handlers[1].prerequisitesOf["pair"] = listOf(RecordKey("wallet", "w1"))
        h.saveRestore("pair", type = "transaction", scope = wallet)
        h.api.batchAnswer = { items -> ApiResult.Success(items.map { if (it.id == "w1") rejected(it, ApiErrorCode.VALIDATION_FAILED, 422) else appliedResult(it) }) }
        h.engine.drain()
        val back = assertNotNull(h.row("pair", "transaction"))
        assertEquals("Waiting", back.state)
        assertEquals(0, back.attempts)
        assertEquals("Refused", h.row("w1", "wallet")!!.state)
    }

    @Test
    fun anEditMadeWhileThePairIsInFlightIsRebasedOnTheSecondItemsVersionAndSentNext() = runTest {
        val h = harness()
        h.pair("pair", base = 4)
        h.api.batchAnswer = { items ->
            h.save("pair", """{"edited":true}""", base = 4, type = "transaction", scope = wallet)
            ApiResult.Success(items.map { appliedResult(it, version = (it.data as JsonObject).version() + 1) })
        }
        h.engine.drain()
        val row = assertNotNull(h.row("pair", "transaction"))
        assertEquals("Waiting", row.state)
        assertEquals(6L, row.baseVersion)
        assertTrue(!row.restore, "the next send is an ordinary save on top of the restored record")
    }
}
