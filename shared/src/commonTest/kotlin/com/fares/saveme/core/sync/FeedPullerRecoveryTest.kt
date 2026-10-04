package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.FakeSyncApi.Companion.failure
import com.fares.saveme.core.sync.FakeSyncApi.Companion.page
import com.fares.saveme.core.sync.FakeSyncApi.Companion.row
import com.fares.saveme.feature.account.FakeSyncScopeDao
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An expired or invalid position is recovered scope by scope (spec 009 FR-008, FR-009, FR-007, US4). */
class FeedPullerRecoveryTest {

    private class Harness {
        val api = FakeSyncApi()
        val scopes = FakeSyncScopeDao()
        val wallets = FakeWalletScopes()
        val tx = RecordingApplier(ScopeKind.Wallet, "transaction")
        val categories = RecordingApplier(ScopeKind.User, "category")
        val puller = FeedPuller(api, scopes, { listOf(tx, categories) }, { wallets }, SyncLock(), { "u1" }, FakeClock())
        val a = SyncScope(ScopeKind.Wallet, "wa")
        val b = SyncScope(ScopeKind.Wallet, "wb")
        val user = SyncScope(ScopeKind.User, "u1")
    }

    @Test
    fun anExpiredWalletIsReadAgainFromTheStartAndSweptWhileTheOtherScopesAreUntouched() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("wallet:wa", "old-a", null)
        h.scopes.storeFeedPage("wallet:wb", "pos-b", null)
        h.scopes.storeFeedPage("user:u1", "pos-u", null)
        h.scopes.markFeedRead("wallet:wb", "2026-10-01T00:00:00.000Z")
        h.api.wallet = { id, cursor ->
            if (id == "wa" && cursor == "old-a") failure(ApiErrorCode.SYNC_CURSOR_EXPIRED, 410) else ApiResult.Success(page(listOf(row("transaction", "t1")), sync = "new-a"))
        }
        assertIs<ScopeRead.Done>(h.puller.read(h.a))

        assertEquals(listOf<Pair<String, String?>>("wa" to "old-a", "wa" to null), h.api.walletCalls)
        assertEquals("new-a", h.scopes.feedCursor("wallet:wa"))
        assertEquals(setOf("t1"), h.tx.swept.single().second, "swept after the read from the start")
        assertEquals("pos-b", h.scopes.feedCursor("wallet:wb"))
        assertEquals("2026-10-01T00:00:00.000Z", h.scopes.rows.value["wallet:wb"]!!.feedReadAt)
        assertEquals("pos-u", h.scopes.feedCursor("user:u1"))
        assertTrue(h.categories.swept.isEmpty())
    }

    @Test
    fun anExpiredPersonsScopeIsReadAgainAndTheWalletsAreNotSweptByIt() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("user:u1", "old-u", null)
        h.api.user = { cursor -> if (cursor == "old-u") failure(ApiErrorCode.SYNC_CURSOR_EXPIRED, 410) else ApiResult.Success(page(listOf(row("category", "c1")), sync = "new-u")) }
        val done = assertIs<ScopeRead.Done>(h.puller.read(h.user))
        assertEquals(listOf<String?>("old-u", null), h.api.userCalls)
        assertEquals(setOf("c1"), h.categories.swept.single().second)
        assertEquals(emptySet(), done.seenWalletIds, "reported for the held-wallet check (FR-011), not acted on here")
        assertEquals(emptyList(), h.wallets.lost)
        assertTrue(h.tx.swept.isEmpty())
    }

    @Test
    fun anInvalidPositionIsClearedAndReadAgainOnceAndASecondOneInTheSameRunFailsTheScope() = runTest {
        val h = Harness()
        val run = ReadRun("u1")
        h.scopes.storeFeedPage("wallet:wa", "bad", null)
        h.api.wallet = { _, cursor -> if (cursor == "bad") failure(ApiErrorCode.INVALID_CURSOR, 400) else ApiResult.Success(page(emptyList(), sync = "fresh")) }
        assertIs<ScopeRead.Done>(h.puller.read(h.a, run))
        assertEquals("fresh", h.scopes.feedCursor("wallet:wa"))

        // The same scope refuses a position again in the same run: it fails and the copy stays as it is.
        h.scopes.storeFeedPage("wallet:wa", "bad", null)
        h.tx.applied.clear()
        assertEquals(ScopeRead.Failed, h.puller.read(h.a, run))
        assertEquals("bad", h.scopes.feedCursor("wallet:wa"))
        assertTrue(h.tx.applied.isEmpty())
        assertTrue(h.tx.swept.size == 1, "no second sweep")

        // The next run starts clean.
        assertIs<ScopeRead.Done>(h.puller.read(h.a, ReadRun("u1")))
    }

    @Test
    fun aBadQueryOrABadIdIsAClientDefectAndFailsTheScopeWithoutRestarting() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("wallet:wa", "c0", null)
        h.api.wallet = { _, _ -> ApiResult.Failure(FakeSyncApi.problem(ApiErrorCode.UNKNOWN, 400).copy(code = "INVALID_QUERY_PARAMETER"), ApiErrorCode.UNKNOWN, null) }
        assertEquals(ScopeRead.Failed, h.puller.read(h.a))
        h.api.wallet = { _, _ -> ApiResult.Failure(FakeSyncApi.problem(ApiErrorCode.UNKNOWN, 422).copy(code = "INVALID_ID"), ApiErrorCode.UNKNOWN, null) }
        assertEquals(ScopeRead.Failed, h.puller.read(h.a))
        assertEquals("c0", h.scopes.feedCursor("wallet:wa"))
        assertEquals(2, h.api.walletCalls.size)
    }

    @Test
    fun aRefusalOfAPositionWithoutOneIsNotRetriedForever() = runTest {
        val h = Harness()
        h.api.user = { failure(ApiErrorCode.INVALID_CURSOR, 400) }
        assertEquals(ScopeRead.Failed, h.puller.read(h.user))
        assertEquals(1, h.api.userCalls.size)
    }

    @Test
    fun anInterruptedReadFromTheStartSweepsNothingAndLeavesThePositionEmpty() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("wallet:wa", "old", null)
        h.api.wallet = { _, cursor ->
            when (cursor) {
                "old" -> failure(ApiErrorCode.SYNC_CURSOR_EXPIRED, 410)
                null -> ApiResult.Success(page(listOf(row("transaction", "t1")), sync = "p1", next = "p1"))
                else -> ApiResult.Offline
            }
        }
        assertEquals(ScopeRead.Offline, h.puller.read(h.a))
        assertNull(h.scopes.feedCursor("wallet:wa"))
        assertTrue(h.tx.swept.isEmpty())
    }

    @Test
    fun aReadFromTheStartWhoseOwnNextPageExpiresFailsWithoutReadingEverythingAgain() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("wallet:wa", "old", null)
        h.api.wallet = { _, cursor ->
            when (cursor) {
                "old", "p1" -> failure(ApiErrorCode.SYNC_CURSOR_EXPIRED, 410)
                else -> ApiResult.Success(page(listOf(row("transaction", "t1")), sync = "p1", next = "p1"))
            }
        }
        assertEquals(ScopeRead.Failed, h.puller.read(h.a))
        // One restart for the stored position, then one read from the start that stops at its own expired page.
        assertEquals(listOf<Pair<String, String?>>("wa" to "old", "wa" to null, "wa" to "p1"), h.api.walletCalls)
        assertNull(h.scopes.feedCursor("wallet:wa"))
        assertTrue(h.tx.swept.isEmpty(), "nothing is swept on an incomplete read")
        assertEquals(1, h.tx.applied.size, "the rows of the first page stay applied (the version guard makes a later repeat harmless)")
    }

    @Test
    fun aReadFromTheStartPastAPurgeHorizonCompletesAtSmallPageSizesAndStoresItsPlaceAndPosition() = runTest {
        // Backend spec 017: a read from no cursor pages past the purge horizon, so no page of it answers 410.
        val h = Harness()
        h.api.wallet = { _, cursor ->
            when (cursor) {
                null -> ApiResult.Success(page(listOf(row("transaction", "t1")), sync = "p1", next = "p1", position = 41))
                "p1" -> ApiResult.Success(page(listOf(row("transaction", "t2")), sync = "p2", next = "p2", position = 42))
                else -> ApiResult.Success(page(listOf(row("transaction", "t3")), sync = "p3", position = 43))
            }
        }
        assertIs<ScopeRead.Done>(h.puller.read(h.a))
        assertEquals(listOf<Pair<String, String?>>("wa" to null, "wa" to "p1", "wa" to "p2"), h.api.walletCalls)
        assertEquals(com.fares.saveme.core.database.FeedPlace("p3", 43), h.scopes.feedPlace("wallet:wa"))
        assertEquals(setOf("t1", "t2", "t3"), h.tx.swept.single().second)
    }
}
