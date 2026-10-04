package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.FakeSyncApi.Companion.page
import com.fares.saveme.core.sync.FakeSyncApi.Companion.row
import com.fares.saveme.feature.account.FakeSyncScopeDao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Paging, grouping, unknown kinds and when a position is stored (spec 009 FR-003, FR-006, FR-027). */
class FeedPullerPagingTest {

    private class Harness(account: String? = "u1") {
        val api = FakeSyncApi()
        val scopes = FakeSyncScopeDao()
        val clock = FakeClock()
        val log = mutableListOf<String>()
        val categories = RecordingApplier(ScopeKind.User, "category", log)
        val members = RecordingApplier(ScopeKind.User, "wallet_member", log)
        val profile = RecordingApplier(ScopeKind.User, "profile", log)
        var signedIn: String? = account
        val puller = FeedPuller(api, scopes, { listOf(categories, members, profile) }, { null }, SyncLock(), { signedIn }, clock)
        val user = SyncScope(ScopeKind.User, "u1")
    }

    @Test
    fun rowsReachTheirAppliersInFeedOrderWithConsecutiveRowsOfOneTypeTogether() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("user:u1", "c0", null)
        h.api.user = {
            ApiResult.Success(
                page(listOf(row("category", "a"), row("category", "b"), row("wallet_member", "m1"), row("category", "c")), sync = "c1"),
            )
        }
        assertIs<ScopeRead.Done>(h.puller.read(h.user))
        assertEquals(listOf("category x2", "wallet_member x1", "category x1"), h.log)
        assertEquals(listOf("a", "b"), h.categories.applied[0].second.map { it.jsonObject["id"]!!.jsonPrimitive.content })
        assertEquals("c1", h.scopes.feedCursor("user:u1"))
    }

    @Test
    fun unknownKindsAreSkippedAndThePositionStillAdvances() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("user:u1", "c0", null)
        h.api.user = { ApiResult.Success(page(listOf(row("budget", "b1"), row("invitation", "i1"), row("recurring_rule", "r1"), row("category", "a")), sync = "c1")) }
        assertIs<ScopeRead.Done>(h.puller.read(h.user))
        assertEquals(listOf("category x1"), h.log)
        assertEquals("c1", h.scopes.feedCursor("user:u1"))
    }

    @Test
    fun pagesAreReadWhileMoreRemainAndEachPageIsSentTheServersNextCursor() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("user:u1", "c0", null)
        h.api.user = { cursor ->
            ApiResult.Success(
                when (cursor) {
                    "c0" -> page(listOf(row("category", "a")), sync = "p1", next = "p1")
                    "p1" -> page(listOf(row("category", "b")), sync = "p2", next = "p2")
                    else -> page(listOf(row("category", "c")), sync = "p3")
                },
            )
        }
        assertIs<ScopeRead.Done>(h.puller.read(h.user))
        assertEquals(listOf<String?>("c0", "p1", "p2"), h.api.userCalls)
        assertEquals(3, h.categories.applied.size)
        assertEquals("p3", h.scopes.feedCursor("user:u1"))
    }

    @Test
    fun anEmptyLastPageStoresThePositionTheServerGaveBack() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("user:u1", "c0", null)
        h.api.user = { ApiResult.Success(FakeSyncApi.emptyPage("c0-again")) }
        assertIs<ScopeRead.Done>(h.puller.read(h.user))
        assertEquals("c0-again", h.scopes.feedCursor("user:u1"))
    }

    @Test
    fun thePositionIsStoredOnlyAfterAPagesRowsAreApplied() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("user:u1", "c0", null)
        h.categories.failOnApply = { calls -> calls == 1 } // the second page's rows crash
        h.api.user = { cursor ->
            ApiResult.Success(
                if (cursor == "c0") page(listOf(row("category", "a")), sync = "p1", next = "p1") else page(listOf(row("category", "b")), sync = "p2"),
            )
        }
        assertEquals(ScopeRead.Failed, h.puller.read(h.user))
        assertEquals("p1", h.scopes.feedCursor("user:u1"), "the position of the last page that was applied")
        assertNull(h.scopes.rows.value["user:u1"]?.feedReadAt, "an unfinished read stamps nothing")

        // The next run resumes from p1 and applies page two.
        h.categories.failOnApply = { false }
        assertIs<ScopeRead.Done>(h.puller.read(h.user))
        assertEquals("p1", h.api.userCalls.last())
        assertEquals("p2", h.scopes.feedCursor("user:u1"))
    }

    @Test
    fun theReadStampIsTheStartOfTheReadAndOnlyAfterTheLastPage() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("user:u1", "c0", null)
        h.clock.now = kotlin.time.Instant.parse("2026-10-02T10:00:00Z")
        h.api.user = { cursor ->
            h.clock.now = h.clock.now + kotlin.time.Duration.parse("5s")
            ApiResult.Success(if (cursor == "c0") page(listOf(row("category", "a")), sync = "p1", next = "p1") else FakeSyncApi.emptyPage("p2"))
        }
        val done = assertIs<ScopeRead.Done>(h.puller.read(h.user))
        assertEquals(kotlin.time.Instant.parse("2026-10-02T10:00:00Z"), done.startedAt)
        assertEquals("2026-10-02T10:00:00.000Z", h.scopes.rows.value["user:u1"]!!.feedReadAt)
    }

    @Test
    fun noConnectionHalfwayLeavesThePositionOfTheLastAppliedPage() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("user:u1", "c0", null)
        h.api.user = { cursor -> if (cursor == "c0") ApiResult.Success(page(listOf(row("category", "a")), sync = "p1", next = "p1")) else ApiResult.Offline }
        assertEquals(ScopeRead.Offline, h.puller.read(h.user))
        assertEquals("p1", h.scopes.feedCursor("user:u1"))
        assertNull(h.scopes.rows.value["user:u1"]?.feedReadAt)

        h.api.user = { ApiResult.Timeout }
        assertEquals(ScopeRead.Offline, h.puller.read(h.user))
    }

    @Test
    fun aReadFromTheStartStoresItsPositionOnlyAtTheEndAndSweepsEveryApplierOfTheScopeWithWhatItSaw() = runTest {
        val h = Harness()
        h.api.user = { cursor ->
            ApiResult.Success(
                if (cursor == null) page(listOf(row("category", "a"), row("wallet_member", "m1", extra = mapOf("wallet_id" to kotlinx.serialization.json.JsonPrimitive("w1")))), sync = "p1", next = "p1")
                else page(listOf(row("category", "b")), sync = "p2"),
            )
        }
        val done = assertIs<ScopeRead.Done>(h.puller.read(h.user))
        assertEquals(listOf<String?>(null, "p1"), h.api.userCalls)
        assertEquals("p2", h.scopes.feedCursor("user:u1"))
        assertEquals(setOf("a", "b"), h.categories.swept.single().second)
        assertEquals(setOf("m1"), h.members.swept.single().second)
        assertEquals(emptySet(), h.profile.swept.single().second, "an applier that saw nothing is still swept")
        assertEquals(setOf("w1"), done.seenWalletIds, "the wallets of the own memberships this read returned")
    }

    @Test
    fun aReadFromTheStartThatIsInterruptedSweepsNothingAndLeavesThePositionEmpty() = runTest {
        val h = Harness()
        h.api.user = { cursor -> if (cursor == null) ApiResult.Success(page(listOf(row("category", "a")), sync = "p1", next = "p1")) else ApiResult.Offline }
        assertEquals(ScopeRead.Offline, h.puller.read(h.user))
        assertNull(h.scopes.feedCursor("user:u1"), "the sweep needs every page's ids, so it starts again")
        assertTrue(h.categories.swept.isEmpty())
    }

    @Test
    fun aReadWithAPositionNeverSweeps() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("user:u1", "c0", null)
        h.api.user = { ApiResult.Success(page(listOf(row("category", "a")), sync = "c1")) }
        h.puller.read(h.user)
        assertTrue(h.categories.swept.isEmpty())
    }

    @Test
    fun nothingIsAppliedOrStoredAfterTheAccountChangesMidRead() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("user:u1", "c0", null)
        h.api.user = {
            h.signedIn = null // signed out while the answer was on its way
            ApiResult.Success(page(listOf(row("category", "a")), sync = "c1"))
        }
        assertEquals(ScopeRead.Failed, h.puller.read(h.user))
        assertTrue(h.categories.applied.isEmpty())
        assertEquals("c0", h.scopes.feedCursor("user:u1"))
    }

    @Test
    fun aPageWithoutAPositionIsNotTrusted() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("user:u1", "c0", null)
        h.api.user = { ApiResult.Success(page(listOf(row("category", "a")), sync = "")) }
        assertEquals(ScopeRead.Failed, h.puller.read(h.user))
        assertTrue(h.categories.applied.isEmpty())
    }
}
