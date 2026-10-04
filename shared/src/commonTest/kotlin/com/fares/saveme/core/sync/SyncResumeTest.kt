package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.FakeSyncApi.Companion.page
import com.fares.saveme.core.sync.FakeSyncApi.Companion.row
import com.fares.saveme.feature.account.FakeSyncScopeDao
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Process death in the middle of an upload or a read (spec 009 FR-027, US5-S2). */
class SyncResumeTest {

    @Test
    fun rowsLeftSendingByAKilledBatchReturnToWaitingAndAreSentAgain() = runTest {
        val h = BatchHarness()
        h.save("a")
        h.save("b")
        // The process died after the rows were marked Sending and before the answer.
        h.dao.upsert(h.row("a")!!.copy(state = "Sending"))
        h.dao.upsert(h.row("b")!!.copy(state = "Sending"))
        h.engine.drain()
        assertEquals(listOf("a", "b"), h.api.batches.single().map { it.id })
        assertNull(h.row("a"))
    }

    @Test
    fun aReadKilledAfterPageTwoOfFourResumesFromPageTwosPositionAndALaterPageNeverAppliesAnOlderCopy() = runTest {
        val scopes = FakeSyncScopeDao()
        val api = FakeSyncApi()
        val applier = RecordingApplier(ScopeKind.User, "category")
        val stored = mutableMapOf<String, Long>() // id -> version, with the guard the appliers have
        val guarded = object : FeedApplier by applier {
            override suspend fun apply(scope: SyncScope, records: List<kotlinx.serialization.json.JsonElement>) {
                for (r in records) {
                    val o = r as kotlinx.serialization.json.JsonObject
                    val id = (o["id"] as kotlinx.serialization.json.JsonPrimitive).content
                    val v = (o["version"] as kotlinx.serialization.json.JsonPrimitive).content.toLong()
                    if (v >= (stored[id] ?: 0L)) stored[id] = v
                }
                applier.apply(scope, records)
            }
        }
        scopes.storeFeedPage("user:u1", "p0", null)
        var killAfterPage = 2
        api.user = { cursor ->
            when (cursor) {
                "p0" -> ApiResult.Success(page(listOf(row("category", "a", version = 2)), sync = "p1", next = "p1"))
                "p1" -> ApiResult.Success(page(listOf(row("category", "a", version = 3)), sync = "p2", next = "p2"))
                "p2" -> if (killAfterPage-- == 2) ApiResult.Offline else ApiResult.Success(page(listOf(row("category", "a", version = 3), row("category", "b")), sync = "p3", next = "p3"))
                else -> ApiResult.Success(page(listOf(row("category", "c")), sync = "p4"))
            }
        }
        val puller = FeedPuller(api, scopes, { listOf(guarded) }, { null }, SyncLock(), { "u1" })
        assertEquals(ScopeRead.Offline, puller.read(SyncScope(ScopeKind.User, "u1")))
        assertEquals("p2", scopes.feedCursor("user:u1"), "after page 2 of 4")

        assertEquals(ScopeRead.Done::class, puller.read(SyncScope(ScopeKind.User, "u1"))::class)
        assertEquals(listOf<String?>("p0", "p1", "p2", "p2", "p3"), api.userCalls)
        assertEquals(3L, stored["a"], "an older copy never replaced the newer one")
        assertEquals("p4", scopes.feedCursor("user:u1"))
    }
}
