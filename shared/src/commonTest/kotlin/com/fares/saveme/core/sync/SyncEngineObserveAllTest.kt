package com.fares.saveme.core.sync

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** `observeAll(type)` hands the transactions feature every row of its record type (spec 008 research R-21). */
class SyncEngineObserveAllTest {

    @Test
    fun itEmitsEveryRowOfTheTypeInAnyStateAndNeverAnotherTypes() = runTest {
        val h = EngineHarness()
        h.engine.observeAll("note").test {
            assertEquals(emptyList(), awaitItem())
            h.save("a", "{}")
            assertEquals(listOf("a"), awaitItem().map { it.recordId })
            h.engine.enqueue("other", "x", h.scope, 1, "{}")
            h.save("b", "{}")
            assertEquals(listOf("a", "b"), expectMostRecentItem().map { it.recordId })
            h.engine.discard("note", "a")
            assertEquals(listOf("b"), expectMostRecentItem().map { it.recordId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clashedAndRefusedRowsAreIncluded() = runTest {
        val h = EngineHarness()
        h.save("a", "{}")
        h.handler.answer = { EngineHarness.failure(com.fares.saveme.core.network.ApiErrorCode.VALIDATION_FAILED) }
        h.engine.drain()
        assertEquals(ChangeState.Refused, h.engine.observeAll("note").first().single().state)
    }
}
