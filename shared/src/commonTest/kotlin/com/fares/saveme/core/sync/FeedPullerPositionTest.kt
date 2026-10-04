package com.fares.saveme.core.sync

import com.fares.saveme.core.database.FeedPlace
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

/** The integer position is stored with the cursor of the same page and forgotten with it (spec 011 FR-001, FR-002). */
class FeedPullerPositionTest {

    private class Harness {
        val api = FakeSyncApi()
        val scopes = FakeSyncScopeDao()
        val tx = RecordingApplier(ScopeKind.Wallet, "transaction")
        val lost = mutableListOf<String>()
        val wallets = object : WalletScopes {
            override suspend fun followed() = emptyList<String>()
            override suspend fun isAccepted(walletId: String) = true
            override suspend fun unconfirmed(seenWalletIds: Set<String>) = emptyList<String>()
            override suspend fun accessLost(walletId: String) {
                lost += walletId
                scopes.clearFeed("wallet:$walletId")
            }
        }
        val puller = FeedPuller(api, scopes, { listOf(tx) }, { wallets }, SyncLock(), { "u1" }, FakeClock())
        val wallet = SyncScope(ScopeKind.Wallet, "wa")
    }

    @Test
    fun anIncrementalReadStoresEachPagesPositionWithItsCursor() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("wallet:wa", "c0", 5)
        val seen = mutableListOf<FeedPlace?>()
        h.api.wallet = { _, cursor ->
            seen += h.scopes.feedPlace("wallet:wa")
            when (cursor) {
                "c0" -> ApiResult.Success(page(listOf(row("transaction", "t1")), sync = "c1", next = "c1", position = 10))
                else -> ApiResult.Success(page(listOf(row("transaction", "t2")), sync = "c2", position = 20))
            }
        }
        assertIs<ScopeRead.Done>(h.puller.read(h.wallet))
        assertEquals(listOf<FeedPlace?>(FeedPlace("c0", 5), FeedPlace("c1", 10)), seen, "the first page's pair was stored before the second was asked for")
        assertEquals(FeedPlace("c2", 20), h.scopes.feedPlace("wallet:wa"))
    }

    @Test
    fun aReadFromTheStartStoresTheCursorAndPositionOnlyAfterItsLastPage() = runTest {
        val h = Harness()
        val seen = mutableListOf<FeedPlace?>()
        h.api.wallet = { _, cursor ->
            seen += h.scopes.feedPlace("wallet:wa")
            if (cursor == null) ApiResult.Success(page(listOf(row("transaction", "t1")), sync = "c1", next = "c1", position = 10))
            else ApiResult.Success(page(listOf(row("transaction", "t2")), sync = "c2", position = 20))
        }
        assertIs<ScopeRead.Done>(h.puller.read(h.wallet))
        assertEquals(listOf<FeedPlace?>(null, null), seen, "nothing stored while the sweep still needs every page")
        assertEquals(FeedPlace("c2", 20), h.scopes.feedPlace("wallet:wa"))
    }

    @Test
    fun aPageWithoutAPositionStoresNullOverAnEarlierValue() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("wallet:wa", "c0", 5)
        h.api.wallet = { _, _ -> ApiResult.Success(page(emptyList(), sync = "c1")) }
        assertIs<ScopeRead.Done>(h.puller.read(h.wallet))
        assertEquals(FeedPlace("c1", null), h.scopes.feedPlace("wallet:wa"))
    }

    @Test
    fun anExpiredPlaceIsForgottenWithItsPositionBeforeTheRestart() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("wallet:wa", "old", 5)
        val atRestart = mutableListOf<FeedPlace?>()
        h.api.wallet = { _, cursor ->
            if (cursor == "old") failure(ApiErrorCode.SYNC_CURSOR_EXPIRED, 410)
            else {
                atRestart += h.scopes.feedPlace("wallet:wa")
                ApiResult.Success(page(emptyList(), sync = "new", position = 30))
            }
        }
        assertIs<ScopeRead.Done>(h.puller.read(h.wallet))
        assertEquals(listOf<FeedPlace?>(FeedPlace(null, null)), atRestart)
        assertEquals(FeedPlace("new", 30), h.scopes.feedPlace("wallet:wa"))
    }

    @Test
    fun aRefusedPlaceIsForgottenWithItsPositionBeforeTheRestart() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("wallet:wa", "bad", 5)
        val atRestart = mutableListOf<FeedPlace?>()
        h.api.wallet = { _, cursor ->
            if (cursor == "bad") failure(ApiErrorCode.INVALID_CURSOR, 400)
            else {
                atRestart += h.scopes.feedPlace("wallet:wa")
                ApiResult.Success(page(emptyList(), sync = "fresh", position = 31))
            }
        }
        assertIs<ScopeRead.Done>(h.puller.read(h.wallet))
        assertEquals(listOf<FeedPlace?>(FeedPlace(null, null)), atRestart)
        assertEquals(FeedPlace("fresh", 31), h.scopes.feedPlace("wallet:wa"))
    }

    @Test
    fun aWalletWhoseFeedIsGoneLeavesNoPosition() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("wallet:wa", "c0", 5)
        h.api.wallet = { _, _ -> failure(ApiErrorCode.NOT_FOUND, 404) }
        val read = assertIs<ScopeRead.Done>(h.puller.read(h.wallet))
        assertEquals(true, read.lost)
        assertEquals(listOf("wa"), h.lost)
        assertEquals(FeedPlace(null, null), h.scopes.feedPlace("wallet:wa"))
        assertNull(h.scopes.feedPlace("wallet:wa")!!.position)
    }
}
