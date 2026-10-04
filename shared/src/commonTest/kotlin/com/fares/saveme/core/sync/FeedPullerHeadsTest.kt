package com.fares.saveme.core.sync

import com.fares.saveme.core.database.FeedPlace
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.FakeSyncApi.Companion.failure
import com.fares.saveme.core.sync.FakeSyncApi.Companion.head
import com.fares.saveme.core.sync.FakeSyncApi.Companion.headsPage
import com.fares.saveme.feature.account.FakeSyncScopeDao
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Reading the heads: all pages or nothing, offline stops, a changed account drops it (spec 011 FR-006, FR-009, FR-010, FR-021). */
class FeedPullerHeadsTest {

    private class Harness {
        var account: String? = "u1"
        val api = FakeSyncApi()
        val scopes = FakeSyncScopeDao()
        val clock = FakeClock()
        val puller = FeedPuller(api, scopes, { emptyList() }, { null }, SyncLock(), { account }, clock)
        val run = ReadRun("u1")
    }

    @Test
    fun theHeadsAreReadPageByPageUntilThereIsNoMore() = runTest {
        val h = Harness()
        h.api.heads = { cursor ->
            ApiResult.Success(
                when (cursor) {
                    null -> headsPage(head("a", 1), head("b", 2), next = "n1")
                    "n1" -> headsPage(head("c", 3), next = "n2")
                    else -> headsPage(head("d", 4))
                },
            )
        }
        val listed = assertIs<HeadsRead.Listed>(h.puller.heads(h.run))
        assertEquals(listOf<String?>(null, "n1", "n2"), h.api.headsCalls)
        assertEquals(setOf("a", "b", "c", "d"), listed.heads.keys)
        assertEquals(h.clock.now, listed.startedAt)
        assertEquals(3L, listed.heads.getValue("c").changeSeq)
    }

    @Test
    fun anEmptyAnswerIsAListWithNobodyInIt() = runTest {
        val h = Harness()
        val listed = assertIs<HeadsRead.Listed>(h.puller.heads(h.run))
        assertEquals(emptyMap(), listed.heads)
    }

    @Test
    fun noConnectionOnAnyPageIsOffline() = runTest {
        val h = Harness()
        h.api.heads = { ApiResult.Offline }
        assertEquals(HeadsRead.Offline, h.puller.heads(h.run))
        val second = Harness()
        second.api.heads = { cursor -> if (cursor == null) ApiResult.Success(headsPage(head("a", 1), next = "n1")) else ApiResult.Timeout }
        assertEquals(HeadsRead.Offline, second.puller.heads(second.run))
    }

    @Test
    fun aRefusalOnAnyPageFailsTheWholeReadSoNothingIsSkippedOnAPartialList() = runTest {
        val first = Harness()
        first.api.heads = { failure(ApiErrorCode.INTERNAL_ERROR, 500) }
        assertEquals(HeadsRead.Failed, first.puller.heads(first.run))
        val second = Harness()
        second.api.heads = { cursor -> if (cursor == null) ApiResult.Success(headsPage(head("a", 1), next = "n1")) else failure(ApiErrorCode.INVALID_CURSOR, 400) }
        assertEquals(HeadsRead.Failed, second.puller.heads(second.run))
        assertEquals(listOf<String?>(null, "n1"), second.api.headsCalls)
    }

    @Test
    fun anUnreadableAnswerFails() = runTest {
        val h = Harness()
        h.api.heads = { ApiResult.Failure(com.fares.saveme.core.network.ProblemDetails(status = 200, code = HttpSyncApi.UNREADABLE), ApiErrorCode.UNKNOWN, null) }
        assertEquals(HeadsRead.Failed, h.puller.heads(h.run))
    }

    @Test
    fun aPageThatSaysThereIsMoreButNamesNoCursorFails() = runTest {
        val h = Harness()
        h.api.heads = { ApiResult.Success(HeadsPageDto(emptyList(), com.fares.saveme.core.network.CollectionMeta(null, true, 200))) }
        assertEquals(HeadsRead.Failed, h.puller.heads(h.run))
        assertEquals(1, h.api.headsCalls.size)
    }

    @Test
    fun aChangeOfAccountBetweenPagesDropsEverythingRead() = runTest {
        val h = Harness()
        h.api.heads = { cursor ->
            if (cursor == null) {
                h.account = "u2"
                ApiResult.Success(headsPage(head("a", 1), next = "n1"))
            } else {
                ApiResult.Success(headsPage(head("b", 2)))
            }
        }
        assertEquals(HeadsRead.Failed, h.puller.heads(h.run))
        assertEquals(1, h.api.headsCalls.size, "no further page is asked for")
    }

    @Test
    fun markingAWalletUnchangedWritesOnlyTheReadStamp() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("wallet:wa", "c", 7)
        val at = FakeClock().now
        h.puller.markUnchanged(SyncScope(ScopeKind.Wallet, "wa"), at)
        val row = h.scopes.rows.value.getValue("wallet:wa")
        assertEquals(at.syncStamp(), row.feedReadAt)
        assertEquals(FeedPlace("c", 7), h.puller.place(SyncScope(ScopeKind.Wallet, "wa")))
    }

    @Test
    fun forgettingAWalletClearsCursorPositionAndStamp() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("wallet:wa", "c", 7)
        h.scopes.markFeedRead("wallet:wa", "2026-10-01T00:00:00.000Z")
        h.puller.forget(SyncScope(ScopeKind.Wallet, "wa"))
        val row = h.scopes.rows.value.getValue("wallet:wa")
        assertEquals(null, row.feedCursor)
        assertEquals(null, row.feedPosition)
        assertEquals(null, row.feedReadAt)
    }
}
