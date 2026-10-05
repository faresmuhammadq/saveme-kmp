package com.fares.saveme.core.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** `pendingIn` is read-only: it counts what waits in one scope (spec 013: the leave confirmation). */
class SyncEnginePendingInTest {

    @Test
    fun itReturnsOnlyTheRowsOfThatScopeInAnyStateOldestFirst() = runTest {
        val h = EngineHarness()
        val w1 = SyncScope(ScopeKind.Wallet, "w1")
        val w2 = SyncScope(ScopeKind.Wallet, "w2")
        h.engine.enqueue("note", "a", w1, 1, "{}")
        h.engine.enqueue("note", "b", w2, 1, "{}")
        h.engine.enqueue("note", "c", w1, 1, "{}")
        h.engine.enqueue("note", "d", h.scope, 1, "{}")
        assertEquals(listOf("a", "c"), h.engine.pendingIn(w1).map { it.recordId })
        assertEquals(listOf("b"), h.engine.pendingIn(w2).map { it.recordId })
        assertEquals(listOf("d"), h.engine.pendingIn(h.scope).map { it.recordId })
        assertEquals(emptyList(), h.engine.pendingIn(SyncScope(ScopeKind.Wallet, "other")))
    }

    @Test
    fun itChangesNothing() = runTest {
        val h = EngineHarness()
        val w1 = SyncScope(ScopeKind.Wallet, "w1")
        h.engine.enqueue("note", "a", w1, 1, "{}")
        h.engine.pendingIn(w1)
        assertEquals(1, h.engine.pendingIn(w1).size)
        assertEquals(ChangeState.Waiting, h.engine.get("note", "a")!!.state)
    }
}
