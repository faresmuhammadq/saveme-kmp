package com.fares.saveme.core.sync

import com.fares.saveme.core.database.FeedPlace
import com.fares.saveme.core.database.SyncScopeEntity
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.EngineHarness.Companion.ok
import com.fares.saveme.core.sync.FakeSyncApi.Companion.emptyPage
import com.fares.saveme.core.sync.FakeSyncApi.Companion.failure
import com.fares.saveme.core.sync.FakeSyncApi.Companion.head
import com.fares.saveme.core.sync.FakeSyncApi.Companion.headsPage
import com.fares.saveme.feature.account.FakeSyncScopeDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** An Everything refresh reads the heads once and reads only the wallets that moved (spec 011 US1, FR-004 - FR-009). */
class SyncRunnerHeadsTest {

    private fun TestScope.foregroundScope() = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

    private class Harness(scope: CoroutineScope) {
        val dao = FakePendingChangeDao()
        val scopes = FakeSyncScopeDao()
        val scheduler = FakeSyncScheduler()
        val clock = FakeClock()
        val handler = FakeHandler()
        val api = FakeSyncApi()
        val wallets = FakeWalletScopes()
        val tx = RecordingApplier(ScopeKind.Wallet, "transaction")
        val lock = SyncLock()
        val engine = SyncEngine(dao, scopes, listOf(handler), scheduler, currentAccountId = { "u1" }, clock = clock, syncApi = api, lock = lock)
        val puller = FeedPuller(api, scopes, { listOf(tx) }, { wallets }, lock, { "u1" }, clock)
        val runner = DefaultSyncRunner(engine, puller, { wallets }, { "u1" }, scope, clock)
        val heads = mutableMapOf<String, WalletHeadDto>()

        init {
            handler.answer = { ok(2) }
            api.user = { ApiResult.Success(emptyPage("u-end", 900)) }
            api.wallet = { id, _ -> ApiResult.Success(emptyPage("$id-end", 5000)) }
            api.heads = { ApiResult.Success(headsPage(*heads.values.toTypedArray())) }
            scopes.rows.value = mapOf("user:u1" to SyncScopeEntity("user:u1", "", feedCursor = "u-0", feedPosition = 899))
        }

        /** A followed wallet whose stored position is [position] and whose head is [headAt] (default: the same). */
        suspend fun follow(id: String, position: Long? = 100, headAt: Long? = position, purged: Long = 0) {
            wallets.followed = wallets.followed + id
            if (position != null) scopes.storeFeedPage("wallet:$id", "c-$id", position)
            if (headAt != null) heads[id] = head(id, headAt, purged)
        }

        fun stamp(id: String) = scopes.rows.value["wallet:$id"]?.feedReadAt
    }

    @Test
    fun tenQuietWalletsCostTwoRequests() = runTest {
        val h = Harness(foregroundScope())
        (1..10).forEach { h.follow("w$it", position = 100L + it) }

        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.Everything, force = false))

        assertEquals(1, h.api.userCalls.size)
        assertEquals(1, h.api.headsCalls.size)
        assertEquals(emptyList(), h.api.walletCalls)
        val stamp = h.clock.now.syncStamp()
        (1..10).forEach { assertEquals(stamp, h.stamp("w$it"), "w$it is shown as read when the heads were asked") }
        assertEquals(FeedPlace("c-w3", 103), h.puller.place(SyncScope(ScopeKind.Wallet, "w3")), "a skipped wallet keeps its place")
    }

    @Test
    fun onlyTheWalletWhoseHeadMovedIsReadFromItsStoredCursor() = runTest {
        val h = Harness(foregroundScope())
        (1..4).forEach { h.follow("w$it") }
        h.follow("moved", position = 100, headAt = 104)

        h.runner.refresh(ReadTarget.Everything, force = false)

        assertEquals(listOf<Pair<String, String?>>("moved" to "c-moved"), h.api.walletCalls)
        assertEquals(FeedPlace("moved-end", 5000), h.puller.place(SyncScope(ScopeKind.Wallet, "moved")))
    }

    @Test
    fun aHorizonAboveThePositionReadsFromTheStartAndSweepsWithoutSendingTheExpiredCursor() = runTest {
        val h = Harness(foregroundScope())
        h.follow("old", position = 100, headAt = 300, purged = 150)
        h.follow("quiet")

        h.runner.refresh(ReadTarget.Everything, force = false)

        assertEquals(listOf<Pair<String, String?>>("old" to null), h.api.walletCalls)
        assertEquals(listOf("wallet:old"), h.tx.swept.map { it.first.key }, "the from-the-start read sweeps")
    }

    @Test
    fun aFollowedWalletThatIsNotListedIsReadAndOnlyA404RemovesIt() = runTest {
        val h = Harness(foregroundScope())
        h.follow("quiet")
        h.follow("gone", position = 100, headAt = null)
        h.follow("unlisted", position = 100, headAt = null)
        h.api.wallet = { id, _ -> if (id == "gone") failure(ApiErrorCode.NOT_FOUND, 404) else ApiResult.Success(emptyPage("$id-end", 6)) }

        h.runner.refresh(ReadTarget.Everything, force = false)

        assertEquals(setOf("gone", "unlisted"), h.api.walletCalls.map { it.first }.toSet())
        assertEquals(listOf("gone"), h.wallets.lost)
    }

    @Test
    fun aListedWalletThePersonDoesNotFollowIsNeverRead() = runTest {
        val h = Harness(foregroundScope())
        h.follow("mine")
        h.heads["stranger"] = head("stranger", 77)

        h.runner.refresh(ReadTarget.Everything, force = false)

        assertEquals(emptyList(), h.api.walletCalls)
        assertNull(h.stamp("stranger"))
        assertTrue(h.wallets.lost.isEmpty())
    }

    @Test
    fun noConnectionForTheHeadsReadsNoWalletAndReportsOffline() = runTest {
        val h = Harness(foregroundScope())
        (1..3).forEach { h.follow("w$it", headAt = 999) }
        h.api.heads = { ApiResult.Offline }

        assertEquals(ReadOutcome.Offline, h.runner.refresh(ReadTarget.Everything, force = false))
        assertEquals(emptyList(), h.api.walletCalls)
    }

    @Test
    fun heads500OrUnreadableReadsEveryWalletAndTheOutcomeComesFromThoseReads() = runTest {
        val h = Harness(foregroundScope())
        (1..3).forEach { h.follow("w$it") }
        h.api.heads = { failure(ApiErrorCode.INTERNAL_ERROR, 500) }
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.Everything, force = true))
        assertEquals(setOf("w1", "w2", "w3"), h.api.walletCalls.map { it.first }.toSet())

        val unreadable = Harness(foregroundScope())
        (1..2).forEach { unreadable.follow("w$it") }
        unreadable.api.heads = { ApiResult.Failure(com.fares.saveme.core.network.ProblemDetails(status = 200, code = HttpSyncApi.UNREADABLE), ApiErrorCode.UNKNOWN, null) }
        unreadable.api.wallet = { _, _ -> failure(ApiErrorCode.INTERNAL_ERROR, 500) }
        assertEquals(ReadOutcome.Failed, unreadable.runner.refresh(ReadTarget.Everything, force = true))
        assertEquals(2, unreadable.api.walletCalls.size)
    }

    @Test
    fun aWalletTheUploadWroteToIsReadWhateverItsHeadSays() = runTest {
        val h = Harness(foregroundScope())
        h.follow("quiet")
        h.follow("written")
        h.engine.enqueue("note", "n1", SyncScope(ScopeKind.Wallet, "written"), 1, "{}")

        h.runner.refresh(ReadTarget.Everything, force = false)

        assertEquals(listOf("written"), h.api.walletCalls.map { it.first })
        assertEquals(1, h.api.headsCalls.size)
        assertEquals(listOf<Pair<String, String?>>("written" to "c-written"), h.api.walletCalls)
    }

    @Test
    fun aWalletWithNoPlaceOrNoPositionIsReadWithoutConsultingTheHeads() = runTest {
        val h = Harness(foregroundScope())
        h.follow("quiet")
        h.follow("placeless", position = null, headAt = 5)
        h.follow("positionless", position = 100, headAt = 100)
        h.scopes.storeFeedPage("wallet:positionless", "c-positionless", null)

        h.runner.refresh(ReadTarget.Everything, force = false)

        assertEquals(setOf("placeless", "positionless"), h.api.walletCalls.map { it.first }.toSet())
        assertEquals(listOf<String?>(null), h.api.walletCalls.filter { it.first == "placeless" }.map { it.second })
        assertEquals(listOf<String?>("c-positionless"), h.api.walletCalls.filter { it.first == "positionless" }.map { it.second })
    }

    @Test
    fun aWalletHeldButNotConfirmedByTheUserReadIsReadWhateverItsHeadSays() = runTest {
        val h = Harness(foregroundScope())
        h.scopes.rows.value = h.scopes.rows.value - "user:u1" // a read from the start, so the held-wallet check runs
        h.follow("quiet")
        h.follow("held")
        h.wallets.unconfirmedFor = { listOf("held") }

        h.runner.refresh(ReadTarget.Everything, force = false)

        assertEquals(listOf("held"), h.api.walletCalls.map { it.first })
    }

    @Test
    fun aWalletAlsoNamedInTheSameMergedRunIsReadAndAnEverythingRunMakesOnlyOneHeadsRead() = runTest {
        val h = Harness(foregroundScope())
        h.follow("quiet")
        h.follow("named")
        launch { h.runner.refresh(ReadTarget.Everything, force = false) }
        launch { h.runner.refresh(ReadTarget.Wallet("named"), force = true) }
        advanceUntilIdle()

        assertEquals(listOf("named"), h.api.walletCalls.map { it.first })
        assertEquals(1, h.api.headsCalls.size)
    }

    @Test
    fun aRunWithOnlyWalletsThatBypassTheHeadsMakesNoHeadsRequest() = runTest {
        val h = Harness(foregroundScope())
        h.follow("written")
        h.engine.enqueue("note", "n1", SyncScope(ScopeKind.Wallet, "written"), 1, "{}")

        h.runner.refresh(ReadTarget.Everything, force = false)
        assertEquals(emptyList(), h.api.headsCalls)
        assertEquals(listOf("written"), h.api.walletCalls.map { it.first })
    }

    @Test
    fun aWalletOrUserRefreshNeverReadsTheHeads() = runTest {
        val h = Harness(foregroundScope())
        h.follow("w1")
        h.follow("w2")
        h.runner.refresh(ReadTarget.Wallet("w1"), force = true)
        h.runner.refresh(ReadTarget.User, force = true)
        assertEquals(emptyList(), h.api.headsCalls)
        assertEquals(listOf("w1"), h.api.walletCalls.map { it.first })
    }

    @Test
    fun aForcedEverythingRefreshStillSkipsWalletsWhoseHeadEqualsTheirPosition() = runTest {
        val h = Harness(foregroundScope())
        h.follow("w1")
        h.follow("w2", position = 100, headAt = 101)
        h.runner.refresh(ReadTarget.Everything, force = true)
        assertEquals(listOf("w2"), h.api.walletCalls.map { it.first })
    }

    @Test
    fun aWalletWithinTheFloorIsNeitherReadNorACandidateAndASkippedWalletIsFlooredNextTime() = runTest {
        val h = Harness(foregroundScope())
        h.follow("w1")
        h.runner.refresh(ReadTarget.Everything, force = false)
        assertEquals(1, h.api.headsCalls.size)

        h.clock.now = h.clock.now + 30.seconds
        h.runner.refresh(ReadTarget.Everything, force = false)
        assertEquals(1, h.api.headsCalls.size, "w1 was stamped as read, so it is within the floor and not a candidate")
        assertEquals(emptyList(), h.api.walletCalls)

        h.clock.now = h.clock.now + 61.seconds
        h.runner.refresh(ReadTarget.Everything, force = false)
        assertEquals(2, h.api.headsCalls.size)
    }

    @Test
    fun aUserReadThatFailedStillReadsTheHeads() = runTest {
        val h = Harness(foregroundScope())
        h.follow("w1")
        h.api.user = { failure(ApiErrorCode.INTERNAL_ERROR, 500) }
        assertEquals(ReadOutcome.Failed, h.runner.refresh(ReadTarget.Everything, force = false))
        assertEquals(1, h.api.headsCalls.size)
        assertEquals(emptyList(), h.api.walletCalls)
    }

    @Test
    fun aWalletTheServerHasNotAcceptedIsDoneWithoutAReadOrAHeadsRequest() = runTest {
        val h = Harness(foregroundScope())
        h.follow("fresh")
        h.wallets.accepted = { false }
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.Everything, force = false))
        assertEquals(emptyList(), h.api.headsCalls)
        assertEquals(emptyList(), h.api.walletCalls)
    }
}
