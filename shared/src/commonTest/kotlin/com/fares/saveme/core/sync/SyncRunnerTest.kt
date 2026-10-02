package com.fares.saveme.core.sync

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.EngineHarness.Companion.ok
import com.fares.saveme.core.sync.FakeSyncApi.Companion.page
import com.fares.saveme.core.sync.FakeSyncApi.Companion.row
import com.fares.saveme.feature.account.FakeSyncScopeDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** One run at a time, upload before reads, merged triggers, floors and outcomes (spec 009 FR-014, FR-015, FR-026). */
class SyncRunnerTest {

    /** The runner's scope: not `backgroundScope`, whose work `advanceUntilIdle` does not wait for. */
    private fun TestScope.foregroundScope() = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

    private class Harness(scope: CoroutineScope) {
        val dao = FakePendingChangeDao()
        val scopes = FakeSyncScopeDao()
        val scheduler = FakeSyncScheduler()
        val clock = FakeClock()
        val handler = FakeHandler()
        val api = FakeSyncApi()
        val wallets = FakeWalletScopes()
        val lock = SyncLock()
        val order = mutableListOf<String>()
        val engine = SyncEngine(dao, scopes, listOf(handler), scheduler, currentAccountId = { "u1" }, clock = clock, syncApi = api, lock = lock)
        val puller = FeedPuller(api, scopes, { emptyList() }, { wallets }, lock, { "u1" }, clock)
        val runner = DefaultSyncRunner(engine, puller, { wallets }, { "u1" }, scope, clock)

        init {
            handler.answer = { order += "send ${it.recordId}"; ok(2) }
            api.user = { order += "read user"; ApiResult.Success(FakeSyncApi.emptyPage("u-end")) }
            api.wallet = { id, _ -> order += "read $id"; ApiResult.Success(FakeSyncApi.emptyPage("$id-end")) }
        }

        suspend fun save(id: String, scope: SyncScope = SyncScope(ScopeKind.User, "u1")) = engine.enqueue("note", id, scope, 1, "{}")
    }

    @Test
    fun theUploadRunsBeforeTheReads() = runTest {
        val h = Harness(foregroundScope())
        h.save("n1")
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.Everything, force = true))
        assertEquals(listOf("send n1", "read user"), h.order)
    }

    @Test
    fun aRunWhoseUploadFoundNoConnectionSkipsItsReadsAndReportsOffline() = runTest {
        val h = Harness(foregroundScope())
        h.handler.answer = { ApiResult.Offline }
        h.save("n1")
        assertEquals(ReadOutcome.Offline, h.runner.refresh(ReadTarget.Everything, force = true))
        assertEquals(emptyList(), h.api.userCalls)
        assertEquals(ReadOutcome.Offline, h.runner.refresh(ReadTarget.Wallet("w1"), force = true))
        assertEquals(emptyList(), h.api.walletCalls)
    }

    @Test
    fun requestsDuringARunAreMergedIntoOneNextRunAndEveryCallerGetsItsRunsOutcome() = runTest {
        val h = Harness(foregroundScope())
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        h.api.user = {
            h.order += "read user"
            if (h.api.userCalls.size == 1) {
                started.complete(Unit)
                gate.await()
            }
            ApiResult.Success(FakeSyncApi.emptyPage("u-end"))
        }
        val outcomes = mutableListOf<Pair<String, ReadOutcome>>()
        launch { outcomes += "first" to h.runner.refresh(ReadTarget.User, force = true) }
        started.await()
        launch { outcomes += "second" to h.runner.refresh(ReadTarget.User, force = true) }
        launch { outcomes += "third" to h.runner.refresh(ReadTarget.User, force = true) }
        advanceUntilIdle()
        assertEquals(1, h.api.userCalls.size, "still inside the first run")
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, h.api.userCalls.size, "the two later requests share one run")
        assertEquals(setOf("first", "second", "third"), outcomes.map { it.first }.toSet())
        assertTrue(outcomes.all { it.second == ReadOutcome.Done })
    }

    @Test
    fun anUnforcedReadOfAScopeWithinAMinuteIsSkippedAndAForcedOneIsNot() = runTest {
        val h = Harness(foregroundScope())
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.User, force = false))
        assertEquals(1, h.api.userCalls.size)
        h.clock.now = h.clock.now + 30.seconds
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.User, force = false))
        assertEquals(1, h.api.userCalls.size)
        h.runner.refresh(ReadTarget.User, force = true)
        assertEquals(2, h.api.userCalls.size)
        h.clock.now = h.clock.now + 61.seconds
        h.runner.refresh(ReadTarget.User, force = false)
        assertEquals(3, h.api.userCalls.size)
    }

    @Test
    fun aFailedReadDoesNotStartTheFloor() = runTest {
        val h = Harness(foregroundScope())
        h.api.user = { ApiResult.Offline }
        assertEquals(ReadOutcome.Offline, h.runner.refresh(ReadTarget.User, force = false))
        assertEquals(ReadOutcome.Offline, h.runner.refresh(ReadTarget.User, force = false))
        assertEquals(2, h.api.userCalls.size)
    }

    @Test
    fun everythingReadsThePersonsScopeThenEveryFollowedWalletIncludingOnesGainedInThisRun() = runTest {
        val h = Harness(foregroundScope())
        h.wallets.followed = listOf("w1")
        h.api.user = {
            h.order += "read user"
            h.wallets.followed = listOf("w1", "w-new") // an own membership arrived on this page
            ApiResult.Success(FakeSyncApi.emptyPage("u-end"))
        }
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.Everything, force = true))
        assertEquals(listOf("read user", "read w1", "read w-new"), h.order)
    }

    @Test
    fun aTargetedWalletIsReadAloneAndAPlaceholderWalletIsNeverRead() = runTest {
        val h = Harness(foregroundScope())
        h.wallets.accepted = { it != "placeholder" }
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.Wallet("w1"), force = true))
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.Wallet("placeholder"), force = true))
        assertEquals(listOf<Pair<String, String?>>("w1" to null), h.api.walletCalls)
        assertEquals(emptyList(), h.api.userCalls)
    }

    @Test
    fun theOutcomeIsDoneWhenEveryReadCompletedOfflineWhenAnyWasUnreachableAndFailedOtherwise() = runTest {
        val h = Harness(foregroundScope())
        h.wallets.followed = listOf("w1", "w2")
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.Everything, force = true))

        h.api.wallet = { id, _ -> if (id == "w1") FakeSyncApi.failure(ApiErrorCode.INTERNAL_ERROR, 500).let { it } else ApiResult.Success(FakeSyncApi.emptyPage("x")) }
        assertEquals(ReadOutcome.Failed, h.runner.refresh(ReadTarget.Everything, force = true))

        h.api.wallet = { id, _ -> if (id == "w2") ApiResult.Offline else FakeSyncApi.failure(ApiErrorCode.INTERNAL_ERROR, 500) }
        assertEquals(ReadOutcome.Offline, h.runner.refresh(ReadTarget.Everything, force = true))
    }

    @Test
    fun anUploadOnlyRunReadsTheScopesItWroteToAndNothingElse() = runTest {
        val h = Harness(foregroundScope())
        h.wallets.followed = listOf("w1", "w2")
        h.save("t1", SyncScope(ScopeKind.Wallet, "w2"))
        h.runner.upload()
        assertEquals(listOf("send t1", "read w2"), h.order)
        assertEquals(emptyList(), h.api.userCalls)
    }

    @Test
    fun theBackgroundEntryUploadsThenReadsOnlyTheWrittenScopes() = runTest {
        val h = Harness(foregroundScope())
        h.save("c1") // user scope
        h.runner.runBackground()
        assertEquals(listOf("send c1", "read user"), h.order)
        assertEquals(emptyList(), h.api.walletCalls)
    }

    @Test
    fun heldWalletsWhoseMembershipWasNotSeenAreReadInTheSameRunAfterACompleteUserRead() = runTest {
        val h = Harness(foregroundScope())
        h.wallets.unconfirmedFor = { seen -> if ("w1" in seen) listOf("w2") else listOf("w2") }
        h.api.user = {
            ApiResult.Success(page(listOf(row("wallet_member", "m1", extra = mapOf("wallet_id" to kotlinx.serialization.json.JsonPrimitive("w1")))), sync = "u1"))
        }
        // The runner's puller has no wallet_member applier here, so nothing is "seen"; the user read is still complete from the start.
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.User, force = true))
        assertEquals(listOf(emptySet<String>()), h.wallets.unconfirmedCalls)
        assertEquals(listOf<Pair<String, String?>>("w2" to null), h.api.walletCalls)
    }

    @Test
    fun cancellingAnswersWhoeverIsWaitingAsFailed() = runTest {
        val h = Harness(foregroundScope())
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        h.api.user = { started.complete(Unit); gate.await(); ApiResult.Success(FakeSyncApi.emptyPage("u")) }
        var outcome: ReadOutcome? = null
        launch { outcome = h.runner.refresh(ReadTarget.User, force = true) }
        started.await()
        h.runner.cancel()
        advanceUntilIdle()
        assertEquals(ReadOutcome.Failed, outcome)
    }
}
