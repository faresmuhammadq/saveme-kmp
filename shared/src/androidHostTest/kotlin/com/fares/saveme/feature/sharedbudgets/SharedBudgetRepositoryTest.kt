package com.fares.saveme.feature.sharedbudgets

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.sharedbudgets.data.OwnSharedBudgetMembershipFeedApplier
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetEntryFeedApplier
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetFeedApplier
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetLineFeedApplier
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetMemberFeedApplier
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetRepository
import com.fares.saveme.feature.sharedbudgets.domain.DefaultSharedBudgetScopes
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetLinksImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The device's copy of Shared Budgets over a real Room database: version guard, tombstones, own membership, access loss, sweeps. */
@RunWith(RobolectricTestRunner::class)
class SharedBudgetRepositoryTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val queued = mutableSetOf<String>()
    private val repository = SharedBudgetRepository(db.sharedBudgetDao(), db.syncScopeDao(), { "u1" }, { it in queued })
    private val dao = db.sharedBudgetDao()
    private val user = SyncScope(ScopeKind.User, "u1")
    private fun budget(id: String) = SyncScope(ScopeKind.SharedBudget, id)

    @After
    fun close() = db.close()

    private fun json(text: String): JsonElement = AppJson.parseToJsonElement(text)

    private fun budgetJson(id: String, version: Long = 1, name: String = "Home", deletedAt: String? = null) = """
        {"id":"$id","owner_id":"u1","name":"$name","currency":"EGP","start_day":1,"timezone":"Africa/Cairo","limit_minor":2000000,
        "archived_at":null,"limit_history":[{"applies_from":"2026-10-01","limit_minor":2000000}],
        "start_day_history":[{"start_day":1,"applies_from":null}],"version":$version,"change_seq":5,
        "created_at":"2026-10-01T10:00:00Z","updated_at":"2026-10-01T10:00:00Z","deleted_at":${deletedAt?.let { "\"$it\"" } ?: "null"}}
    """.trimIndent()

    private fun memberJson(id: String, budgetId: String, userId: String, version: Long = 1, deletedAt: String? = null) = """
        {"id":"$id","shared_budget_id":"$budgetId","user_id":"$userId","display_name":"Sara","role":"member","read_only":false,
        "version":$version,"change_seq":2,"created_at":"t","updated_at":"2026-10-01T10:00:00Z",
        "deleted_at":${deletedAt?.let { "\"$it\"" } ?: "null"}}
    """.trimIndent()

    private fun entryJson(id: String, budgetId: String, amount: Long = 1200, version: Long = 1, deletedAt: String? = null) = """
        {"id":"$id","shared_budget_id":"$budgetId","created_by":"u2","category_id":"c1","date":"2026-10-03","amount_minor":$amount,
        "description":null,"detached_at":null,"version":$version,"change_seq":4,"created_at":"t","updated_at":"t",
        "deleted_at":${deletedAt?.let { "\"$it\"" } ?: "null"}}
    """.trimIndent()

    @Test
    fun aBudgetIsAppliedWhenItsVersionIsTheSameOrNewerAndIgnoredWhenLower() = runTest {
        val applier = SharedBudgetFeedApplier(repository)
        assertEquals(ScopeKind.SharedBudget, applier.scopeKind)
        applier.apply(budget("s1"), listOf(json(budgetJson("s1", version = 3, name = "Three"))))
        applier.apply(budget("s1"), listOf(json(budgetJson("s1", version = 2, name = "Two"))))
        assertEquals("Three", dao.get("s1")!!.name)
        applier.apply(budget("s1"), listOf(json(budgetJson("s1", version = 4, name = "Four"))))
        assertEquals("Four", dao.get("s1")!!.name)
        assertTrue("the limit history is kept as the server sent it", dao.get("s1")!!.limitHistory.contains("2026-10-01"))
    }

    @Test
    fun aTombstonedBudgetGoesWithItsChildrenUnlessAChangeWaitsForIt() = runTest {
        SharedBudgetFeedApplier(repository).apply(budget("s1"), listOf(json(budgetJson("s1")), json(budgetJson("queued"))))
        SharedBudgetEntryFeedApplier(repository).apply(budget("s1"), listOf(json(entryJson("e1", "s1"))))
        queued += "queued"
        SharedBudgetFeedApplier(repository).apply(
            budget("s1"),
            listOf(json(budgetJson("s1", version = 2, deletedAt = "2026-10-02T00:00:00Z")), json(budgetJson("queued", version = 2, deletedAt = "2026-10-02T00:00:00Z"))),
        )
        assertNull(dao.get("s1"))
        assertTrue("its entries go with it", dao.entries("s1").isEmpty())
        assertNotNull("a budget with a change waiting stays", dao.get("queued"))
    }

    @Test
    fun linesMembersAndEntriesFollowTheSameGuardAndTombstoneRules() = runTest {
        val members = SharedBudgetMemberFeedApplier(repository)
        val entries = SharedBudgetEntryFeedApplier(repository)
        val lines = SharedBudgetLineFeedApplier(repository)
        entries.apply(budget("s1"), listOf(json(entryJson("e1", "s1", amount = 100, version = 3))))
        entries.apply(budget("s1"), listOf(json(entryJson("e1", "s1", amount = 999, version = 2))))
        assertEquals(100L, dao.entry("e1")!!.amountMinor)
        entries.apply(budget("s1"), listOf(json(entryJson("e1", "s1", version = 4, deletedAt = "2026-10-05T00:00:00Z"))))
        assertNull("a tombstone removes the entry", dao.entry("e1"))
        members.apply(budget("s1"), listOf(json(memberJson("m2", "s1", "u2"))))
        members.apply(budget("s1"), listOf(json(memberJson("m2", "s1", "u2", version = 2, deletedAt = "2026-10-05T00:00:00Z"))))
        assertNull("an ended membership removes only that member", dao.member("m2"))
        lines.apply(
            budget("s1"),
            listOf(json("""{"id":"l1","shared_budget_id":"s1","category_id":"c1","limit_minor":500,"name":"Groceries","icon":null,"color":null,"limit_history":[],"version":1,"change_seq":3,"created_at":"t","updated_at":"t","deleted_at":null}""")),
        )
        assertEquals("Groceries", dao.line("l1")!!.name)
    }

    @Test
    fun aLiveOwnMembershipMakesTheBudgetFollowedAndAnEndedOneRemovesItFromThePhone() = runTest {
        val own = OwnSharedBudgetMembershipFeedApplier(repository)
        val scopes = DefaultSharedBudgetScopes(repository) { "u1" }
        SharedBudgetFeedApplier(repository).apply(budget("s1"), listOf(json(budgetJson("s1"))))
        SharedBudgetEntryFeedApplier(repository).apply(budget("s1"), listOf(json(entryJson("e1", "s1"))))
        db.syncScopeDao().storeFeedPage(budget("s1").key, "cursor", 5)

        own.apply(user, listOf(json(memberJson("m1", "s1", "u1"))))
        assertEquals(listOf("s1"), scopes.followed())
        assertTrue(scopes.isAccepted("s1"))
        assertEquals(listOf("s1"), repository.observeMine("u1").first().map { it.id })

        own.apply(user, listOf(json(memberJson("m1", "s1", "u1", version = 2, deletedAt = "2026-10-05T00:00:00Z"))))
        assertNull(dao.get("s1"))
        assertTrue(dao.entries("s1").isEmpty())
        assertNull("its stored position goes too", db.syncScopeDao().feedCursor(budget("s1").key))
    }

    @Test
    fun aMembershipOfAnotherPersonOnTheUserFeedIsIgnored() = runTest {
        OwnSharedBudgetMembershipFeedApplier(repository).apply(user, listOf(json(memberJson("m9", "s1", "someone-else"))))
        assertNull(dao.member("m9"))
    }

    @Test
    fun aBudgetMadeOnThisPhoneIsNeverReadOrRemovedByAnAccessLoss() = runTest {
        val scopes = DefaultSharedBudgetScopes(repository) { "u1" }
        dao.upsert(
            com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetEntity("fresh", "u1", "New", "EGP", 1, "Africa/Cairo", null, null, "[]", "[]", 0, 0, "t", "t", "t"),
        )
        assertEquals("a placeholder has no feed yet", false, scopes.isAccepted("fresh"))
        scopes.accessLost("fresh")
        assertNotNull(dao.get("fresh"))
    }

    @Test
    fun aCompleteReadFromTheStartRemovesWhatTheServerNoLongerHas() = runTest {
        SharedBudgetEntryFeedApplier(repository).apply(budget("s1"), listOf(json(entryJson("e1", "s1")), json(entryJson("e2", "s1"))))
        SharedBudgetEntryFeedApplier(repository).sweep(budget("s1"), setOf("e1"))
        assertEquals(listOf("e1"), dao.entries("s1").map { it.id })
        SharedBudgetFeedApplier(repository).apply(budget("s1"), listOf(json(budgetJson("s1"))))
        SharedBudgetFeedApplier(repository).sweep(budget("s1"), emptySet())
        assertNull("a budget the feed no longer returns is lost", dao.get("s1"))
    }

    @Test
    fun theHeldBudgetsAreTheOnesTheServerHasAccepted() = runTest {
        SharedBudgetFeedApplier(repository).apply(budget("s1"), listOf(json(budgetJson("s1")), json(budgetJson("s2"))))
        assertEquals(setOf("s1", "s2"), DefaultSharedBudgetScopes(repository) { "u1" }.unconfirmed(emptySet()).toSet())
        assertEquals(listOf("s2"), DefaultSharedBudgetScopes(repository) { "u1" }.unconfirmed(setOf("s1")))
    }

    @Test
    fun transactionsOfferOnlyBudgetsThePersonMayRecordIntoWithFrequentOnesFirst() = runTest {
        SharedBudgetFeedApplier(repository).apply(
            budget("s1"),
            listOf(json(budgetJson("s1", name = "Home")), json(budgetJson("s2", name = "Trip")), json(budgetJson("s3", name = "Locked")), json(budgetJson("s4", name = "Alpha"))),
        )
        val own = SharedBudgetMemberFeedApplier(repository)
        own.apply(budget("s1"), listOf(json(memberJson("m1", "s1", "u1"))))
        own.apply(budget("s2"), listOf(json(memberJson("m2", "s2", "u1"))))
        own.apply(budget("s4"), listOf(json(memberJson("m4", "s4", "u1"))))
        own.apply(budget("s3"), listOf(json(memberJson("m3", "s3", "u1").replace("\"read_only\":false", "\"read_only\":true"))))
        SharedBudgetLineFeedApplier(repository).apply(
            budget("s1"),
            listOf(json("""{"id":"l1","shared_budget_id":"s1","category_id":"c9","limit_minor":500,"name":"Rent","icon":null,"color":null,"limit_history":[],"version":1,"change_seq":3,"created_at":"t","updated_at":"t","deleted_at":null}""")),
        )
        val links = SharedBudgetLinksImpl(repository, { "u1" }, { flowOf(listOf("s2", "s3", "gone")) })
        val choices = links.observeChoices().first()
        assertEquals("a read-only membership and a budget the person is not in are not offered", listOf("s2", "s4", "s1"), choices.map { it.id })
        assertEquals(listOf(true, false, false), choices.map { it.frequent })
        assertEquals("the budget's own list, which may name a partner's category", listOf("c9"), choices.last().categories.map { it.id })
    }
}
