package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Change from a date": the new rule goes only after the old rule's change is settled (spec 010 R-09). */
class SyncEngineUntilSettledTest {

    private val wallet = SyncScope(ScopeKind.Wallet, "w1")

    private fun BatchHarness.followsOld() {
        handlers[0].prerequisitesOf["new"] = listOf(RecordKey("rule", "old", untilSettled = true))
    }

    private suspend fun BatchHarness.both() {
        save("old", base = 2, type = "rule", scope = wallet)
        save("new", base = 0, type = "rule", scope = wallet)
    }

    @Test
    fun theFollowingChangeIsHeldEvenWhenTheOldOneIsEarlierInTheSameRequestAndGoesNextRun() = runTest {
        val h = BatchHarness("rule")
        h.followsOld()
        h.both()
        h.engine.drain()
        assertEquals(listOf(listOf("old")), h.api.batches.map { b -> b.map { it.id } })
        assertNull(h.row("old", "rule"))
        val held = assertNotNull(h.row("new", "rule"))
        assertEquals("Waiting", held.state)
        assertEquals(0, held.attempts)
        assertTrue(h.scheduler.enqueued.isNotEmpty(), "a run is scheduled for what was held")

        h.engine.drain()
        assertEquals(listOf(listOf("old"), listOf("new")), h.api.batches.map { b -> b.map { it.id } })
        assertTrue(h.dao.rows.value.isEmpty())
    }

    @Test
    fun itIsHeldWhileTheOldChangeIsInAnyState() = runTest {
        for (state in listOf("Waiting", "Sending", "Clash", "Refused")) {
            val h = BatchHarness("rule")
            h.followsOld()
            h.both()
            h.dao.upsert(h.row("old", "rule")!!.copy(state = state, nextAttemptAt = "2099-01-01T00:00:00.000Z"))
            h.engine.drain()
            val held = assertNotNull(h.row("new", "rule"), state)
            assertEquals(0, held.attempts, state)
            assertTrue(h.api.batches.none { b -> b.any { it.id == "new" } }, state)
        }
    }

    @Test
    fun itIsHeldOnTheSingleRecordPathToo() = runTest {
        val h = EngineHarness()
        val rules = FakeHandler(recordType = "rule")
        val old = RecordKey("rule", "old", untilSettled = true)
        val handler = object : SyncHandler by rules {
            override suspend fun prerequisites(change: PendingChange) = if (change.recordId == "new") listOf(old) else emptyList()
        }
        val engine = SyncEngine(h.dao, h.scopes, listOf(handler), h.scheduler, currentAccountId = { "u1" }, clock = h.clock)
        rules.answer = { EngineHarness.failure(ApiErrorCode.VERSION_CONFLICT, current = EngineHarness.record(5)) }
        engine.enqueue("rule", "old", wallet, 2, "{}")
        engine.enqueue("rule", "new", wallet, 0, "{}")
        engine.drain()
        assertEquals(listOf("old"), rules.sent.map { it.recordId }, "the clash of the old change holds the new one")
        engine.useTheirs("rule", "old")
        rules.answer = { ApiResult.Success(EngineHarness.record(1)) }
        engine.drain()
        assertEquals(listOf("old", "new"), rules.sent.map { it.recordId })
    }

    @Test
    fun aKeyWithoutTheFlagKeepsSpec009Rule() = runTest {
        val h = BatchHarness("rule")
        h.handlers[0].prerequisitesOf["new"] = listOf(RecordKey("rule", "old"))
        h.both()
        h.engine.drain()
        assertEquals(listOf(listOf("old", "new")), h.api.batches.map { b -> b.map { it.id } }, "an update (base > 0) does not hold")
    }

    @Test
    fun noTryIsCountedWhileHeldAfterARefusalOfTheOldChange() = runTest {
        val h = BatchHarness("rule")
        h.followsOld()
        h.both()
        h.api.batchAnswer = { items -> ApiResult.Success(items.map { FakeSyncApi.rejected(it, ApiErrorCode.VALIDATION_FAILED, 422) }) }
        h.engine.drain()
        assertEquals("Refused", h.row("old", "rule")!!.state)
        h.engine.drain()
        assertEquals(0, h.row("new", "rule")!!.attempts)
    }
}
