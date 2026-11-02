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

/** Which Shared Budgets are read, gained and lost (backend ADR-0027 rule 9): a third kind of feed scope, read only by a member. */
class FeedPullerSharedBudgetsTest {

    private class FakeBudgetScopes : SharedBudgetScopes {
        var followed: List<String> = emptyList()
        var accepted: (String) -> Boolean = { true }
        var unconfirmedFor: (Set<String>) -> List<String> = { emptyList() }
        val lost = mutableListOf<String>()

        override suspend fun followed() = followed
        override suspend fun isAccepted(budgetId: String) = accepted(budgetId)
        override suspend fun unconfirmed(seenBudgetIds: Set<String>) = unconfirmedFor(seenBudgetIds)
        override suspend fun accessLost(budgetId: String) {
            lost += budgetId
        }
    }

    private class Harness {
        val api = FakeSyncApi()
        val scopes = FakeSyncScopeDao()
        val clock = FakeClock()
        val budgets = FakeBudgetScopes()
        val ownMembers = RecordingApplier(ScopeKind.User, "shared_budget_member")
        val budgetRecords = RecordingApplier(ScopeKind.SharedBudget, "shared_budget")
        val budgetEntries = RecordingApplier(ScopeKind.SharedBudget, "shared_budget_entry")
        val lock = SyncLock()
        val puller = FeedPuller(
            api, scopes, { listOf(ownMembers, budgetRecords, budgetEntries) }, { null }, lock, { "u1" }, clock, sharedBudgetScopes = { budgets },
        )
        val engine = SyncEngine(FakePendingChangeDao(), scopes, emptyList(), FakeSyncScheduler(), currentAccountId = { "u1" }, clock = clock, syncApi = api, lock = lock)
        val runner = DefaultSyncRunner(
            engine, puller, { null }, { "u1" }, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()), clock,
            sharedBudgetScopes = { budgets },
        )
    }

    private fun membership(id: String, budgetId: String) =
        row("shared_budget_member", id, extra = mapOf("shared_budget_id" to JsonPrimitive(budgetId), "user_id" to JsonPrimitive("u1")))

    @Test
    fun aBudgetScopeIsReadFromItsOwnFeedAndItsRowsGoToItsAppliers() = runTest {
        val h = Harness()
        h.api.sharedBudget = { _, _ -> ApiResult.Success(page(listOf(row("shared_budget", "s1"), row("shared_budget_entry", "e1")), sync = "s1")) }
        val read = h.puller.read(SyncScope(ScopeKind.SharedBudget, "s1"))
        assertIs<ScopeRead.Done>(read)
        assertEquals(listOf<Pair<String, String?>>("s1" to null), h.api.sharedBudgetCalls)
        assertEquals(1, h.budgetRecords.applied.size)
        assertEquals(1, h.budgetEntries.applied.size)
        assertEquals("shared_budget:s1", SyncScope(ScopeKind.SharedBudget, "s1").key)
    }

    @Test
    fun aCompleteReadOfThePersonsScopeFromTheStartNamesTheBudgetsOfTheirOwnMemberships() = runTest {
        val h = Harness()
        h.api.user = { ApiResult.Success(page(listOf(membership("m1", "s1"), membership("m2", "s2")), sync = "u1")) }
        val read = h.puller.read(SyncScope(ScopeKind.User, "u1"))
        assertEquals(setOf("s1", "s2"), (read as ScopeRead.Done).seenSharedBudgetIds)
    }

    @Test
    fun anEverythingRunReadsEveryFollowedBudgetIncludingOnesGainedInThisRunFromTheStart() = runTest {
        val h = Harness()
        h.budgets.followed = listOf("s1")
        h.api.user = { h.budgets.followed = listOf("s1", "s-new"); ApiResult.Success(page(listOf(membership("m9", "s-new")), sync = "u1")) }
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.Everything, force = true))
        assertEquals(listOf<Pair<String, String?>>("s1" to null, "s-new" to null), h.api.sharedBudgetCalls)
    }

    @Test
    fun aBudgetFeedNotFoundMeansAccessIsLost() = runTest {
        val h = Harness()
        h.api.sharedBudget = { _, _ -> failure(ApiErrorCode.NOT_FOUND, 404) }
        val read = h.puller.read(SyncScope(ScopeKind.SharedBudget, "s1"))
        assertTrue((read as ScopeRead.Done).lost)
        assertEquals(listOf("s1"), h.budgets.lost)
    }

    @Test
    fun aPlaceholderBudgetIsNeverReadSoItIsNeverTreatedAsLost() = runTest {
        val h = Harness()
        h.budgets.followed = listOf("fresh")
        h.budgets.accepted = { false }
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.Everything, force = true))
        assertEquals(emptyList(), h.api.sharedBudgetCalls)
        assertEquals(emptyList(), h.budgets.lost)
    }

    @Test
    fun aBudgetOnlyMissingFromACompleteReadFromTheStartIsRemovedOnlyByA404() = runTest {
        val h = Harness()
        h.budgets.unconfirmedFor = { listOf("gone") }
        h.api.sharedBudget = { _, _ -> failure(ApiErrorCode.NOT_FOUND, 404) }
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.User, force = true))
        assertEquals(listOf("gone"), h.budgets.lost)
    }

    @Test
    fun aNamedBudgetIsReadWithoutTheRestOfTheAccount() = runTest {
        val h = Harness()
        assertEquals(ReadOutcome.Done, h.runner.refresh(ReadTarget.SharedBudget("s7"), force = true))
        assertEquals(listOf<Pair<String, String?>>("s7" to null), h.api.sharedBudgetCalls)
        assertEquals(emptyList(), h.api.userCalls)
    }
}
