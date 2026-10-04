package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.FakeSyncApi.Companion.failure
import com.fares.saveme.core.sync.FakeSyncApi.Companion.page
import com.fares.saveme.core.sync.FakeSyncApi.Companion.row
import com.fares.saveme.feature.account.FakeSyncScopeDao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Which wallets are read, gained and lost (spec 009 FR-002, FR-010, FR-011, US3). */
class FeedPullerWalletsTest {

    private class Harness {
        val api = FakeSyncApi()
        val scopes = FakeSyncScopeDao()
        val clock = FakeClock()
        val wallets = FakeWalletScopes()
        val members = RecordingApplier(ScopeKind.User, "wallet_member")
        val walletMembers = RecordingApplier(ScopeKind.Wallet, "wallet_member")
        val walletRecords = RecordingApplier(ScopeKind.Wallet, "wallet")
        val lock = SyncLock()
        val puller = FeedPuller(api, scopes, { listOf(members, walletMembers, walletRecords) }, { wallets }, lock, { "u1" }, clock)
        val engine = SyncEngine(FakePendingChangeDao(), scopes, emptyList(), FakeSyncScheduler(), currentAccountId = { "u1" }, clock = clock, syncApi = api, lock = lock)
        val runner = DefaultSyncRunner(engine, puller, { wallets }, { "u1" }, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()), clock)
    }

    private fun membership(id: String, walletId: String) =
        row("wallet_member", id, extra = mapOf("wallet_id" to JsonPrimitive(walletId), "member_user_id" to JsonPrimitive("u1")))

    @Test
    fun anEverythingRunReadsThePersonsScopeThenEveryFollowedWalletIncludingOnesGainedInThisRunFromTheStart() = runTest {
        val h = Harness()
        h.wallets.followed = listOf("w1")
        h.api.user = { h.wallets.followed = listOf("w1", "w-new"); ApiResult.Success(page(listOf(membership("m9", "w-new")), sync = "u1")) }
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.Everything, force = true))
        assertEquals(listOf<String?>(null), h.api.userCalls)
        assertEquals(listOf<Pair<String, String?>>("w1" to null, "w-new" to null), h.api.walletCalls, "the gained wallet is read from the beginning")
        assertEquals(1, h.members.applied.size)
    }

    @Test
    fun aWalletFeedNotFoundForAnAcceptedWalletMeansAccessIsLost() = runTest {
        val h = Harness()
        h.api.wallet = { _, _ -> failure(ApiErrorCode.NOT_FOUND, 404) }
        val read = h.puller.read(SyncScope(ScopeKind.Wallet, "w1"))
        assertEquals(ScopeRead.Done::class, read::class)
        assertTrue((read as ScopeRead.Done).lost)
        assertEquals(listOf("w1"), h.wallets.lost)
    }

    @Test
    fun aPlaceholderWalletIsNeverReadSoItIsNeverTreatedAsLost() = runTest {
        val h = Harness()
        h.wallets.followed = listOf("fresh")
        h.wallets.accepted = { false }
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.Everything, force = true))
        assertEquals(emptyList(), h.api.walletCalls)
        assertEquals(emptyList(), h.wallets.lost)
    }

    @Test
    fun aWalletOnlyMissingFromACompleteReadFromTheStartIsReadAndKeptOnA200() = runTest {
        val h = Harness()
        h.wallets.followed = listOf("kept")
        h.wallets.unconfirmedFor = { listOf("kept") } // held, but its membership was not returned
        h.api.user = { ApiResult.Success(page(emptyList(), sync = "u1")) }
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.User, force = true))
        assertEquals(listOf<Pair<String, String?>>("kept" to null), h.api.walletCalls, "its feed decides")
        assertEquals(emptyList(), h.wallets.lost, "a 200 keeps it")
    }

    @Test
    fun aWalletOnlyMissingFromACompleteReadFromTheStartIsRemovedOnlyByA404() = runTest {
        val h = Harness()
        h.wallets.unconfirmedFor = { listOf("gone") }
        h.api.wallet = { _, _ -> failure(ApiErrorCode.NOT_FOUND, 404) }
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.User, force = true))
        assertEquals(listOf("gone"), h.wallets.lost)
    }

    @Test
    fun theUnconfirmedCheckHappensOnlyAfterACompleteReadFromTheStart() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("user:u1", "c0", null)
        h.api.user = { ApiResult.Success(page(emptyList(), sync = "u1")) }
        h.runner.refresh(ReadTarget.User, force = true)
        assertEquals(emptyList(), h.wallets.unconfirmedCalls, "a read with a position proves nothing about missing wallets")
    }

    @Test
    fun aMemberTombstoneOnAWalletFeedGoesToTheWalletMemberApplierOnly() = runTest {
        val h = Harness()
        h.scopes.storeFeedPage("wallet:w1", "c0", null)
        h.api.wallet = { _, _ -> ApiResult.Success(page(listOf(row("wallet_member", "m2", version = 2, extra = mapOf("deleted_at" to JsonPrimitive("2026-10-02T00:00:00Z")))), sync = "c1")) }
        assertIs<ScopeRead.Done>(h.puller.read(SyncScope(ScopeKind.Wallet, "w1")))
        assertEquals(1, h.walletMembers.applied.size)
        assertEquals(emptyList(), h.members.applied, "the person's own-membership applier is for the user feed")
        assertEquals(emptyList(), h.wallets.lost)
    }
}
