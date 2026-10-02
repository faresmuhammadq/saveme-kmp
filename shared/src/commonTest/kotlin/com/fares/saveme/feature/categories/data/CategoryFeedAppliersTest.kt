package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryBody
import com.fares.saveme.feature.categories.categoryJson
import com.fares.saveme.feature.categories.referenceJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Own categories from the person's feed and partner references from a wallet's (spec 009 FR-004, FR-005, FR-007). */
class CategoryFeedAppliersTest {

    private val user = SyncScope(ScopeKind.User, "u1")
    private val wallet = SyncScope(ScopeKind.Wallet, "w1")

    private fun json(text: String): JsonElement = AppJson.parseToJsonElement(text)

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = CategoryTestEnv(scope) { jsonResponse(200, categoryBody()) }

    @Test
    fun aCategoryIsAppliedWhenItsVersionIsTheSameOrNewerAndIgnoredWhenLower() = runTest {
        val env = env(backgroundScope)
        val applier = CategoryFeedApplier(env.repository)
        applier.apply(user, listOf(json(categoryJson("a", name = "One", version = 3))))
        applier.apply(user, listOf(json(categoryJson("a", name = "Stale", version = 2))))
        assertEquals("One", env.categoryDao.rows.value.getValue("a").name)
        applier.apply(user, listOf(json(categoryJson("a", name = "Same", version = 3))))
        assertEquals("Same", env.categoryDao.rows.value.getValue("a").name)
        applier.apply(user, listOf(json(categoryJson("a", name = "Newer", version = 4, inUse = true))))
        assertEquals("Newer", env.categoryDao.rows.value.getValue("a").name)
        assertEquals(true, env.categoryDao.rows.value.getValue("a").inUse)
        assertEquals("u1", env.categoryDao.rows.value.getValue("a").accountId)
    }

    @Test
    fun aTombstoneRemovesTheCategoryUnlessAChangeWaitsForIt() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(categoryJson("gone", version = 2))
        env.seed(categoryJson("queued", version = 2))
        env.engine.enqueue("category", "queued", user, 2, "{}")
        val applier = CategoryFeedApplier(env.repository)
        applier.apply(user, listOf(json(categoryJson("gone", version = 3, deletedAt = "2026-10-02T00:00:00Z")), json(categoryJson("queued", version = 3, deletedAt = "2026-10-02T00:00:00Z"))))
        assertNull(env.categoryDao.rows.value["gone"])
        assertNotNull(env.categoryDao.rows.value["queued"], "it stays until the person settles the clash")
        assertEquals(2L, env.categoryDao.rows.value.getValue("queued").version)
    }

    @Test
    fun aFeedCopyNeverTouchesTheWaitingChange() = runTest {
        val env = env(backgroundScope)
        env.seed(categoryJson("a", version = 2))
        env.engine.enqueue("category", "a", user, 2, """{"kind":"expense","name":"Mine"}""")
        CategoryFeedApplier(env.repository).apply(user, listOf(json(categoryJson("a", name = "Theirs", version = 3))))
        assertEquals("Theirs", env.categoryDao.rows.value.getValue("a").name)
        assertEquals(2L, env.engine.get("category", "a")!!.baseVersion)
        assertEquals("""{"kind":"expense","name":"Mine"}""", env.engine.get("category", "a")!!.payloadJson)
    }

    @Test
    fun aSweepRemovesUnseenSyncedCategoriesButNeverPlaceholdersOrQueuedOnes() = runTest {
        val env = env(backgroundScope)
        env.seed(categoryJson("seen", version = 2))
        env.seed(categoryJson("gone", version = 2))
        env.seed(categoryJson("queued", version = 2))
        env.repository.insertPlaceholder("fresh", "u1", "expense", "New", null, null)
        env.engine.enqueue("category", "queued", user, 2, "{}")
        CategoryFeedApplier(env.repository).sweep(user, setOf("seen"))
        assertEquals(setOf("seen", "queued", "fresh"), env.categoryDao.rows.value.keys)
    }

    @Test
    fun aReferenceIsUpsertedPerWalletAndADeletionRemovesIt() = runTest {
        val env = env(backgroundScope)
        val applier = CategoryReferenceFeedApplier(env.references)
        applier.apply(wallet, listOf(json(referenceJson("r1", categoryId = "p1")), json(referenceJson("r2", categoryId = "p2"))))
        assertEquals(setOf("p1", "p2"), env.referenceDao.get("w1").map { it.categoryId }.toSet())
        applier.apply(wallet, listOf(json(referenceJson("r1", categoryId = "p1").replace("\"deleted_at\":null", "\"deleted_at\":\"2026-10-02T00:00:00Z\"").replace("\"version\":1", "\"version\":2"))))
        assertEquals(listOf("p2"), env.referenceDao.get("w1").map { it.categoryId })
    }

    @Test
    fun aReferenceSweepIsPerWallet() = runTest {
        val env = env(backgroundScope)
        env.seedReference("w1", "keep")
        env.seedReference("w1", "gone")
        env.seedReference("w2", "other")
        CategoryReferenceFeedApplier(env.references).sweep(wallet, setOf("r-w1-keep"))
        assertEquals(listOf("keep"), env.referenceDao.get("w1").map { it.categoryId })
        assertEquals(listOf("other"), env.referenceDao.get("w2").map { it.categoryId })
    }

    @Test
    fun theUpdatedTimeIsTheFeedReadTimeWithTheLegacyOneAsAFallback() = runTest {
        val env = env(backgroundScope)
        assertNull(env.repository.observeOwnRefreshedAt().first())
        env.scopeDao.upsert(com.fares.saveme.core.database.SyncScopeEntity("categories", "2026-09-30T08:00:00Z"))
        assertEquals("2026-09-30T08:00:00Z", env.repository.observeOwnRefreshedAt().first())
        env.scopeDao.markFeedRead("user:u1", "2026-10-02T10:00:00.000Z")
        assertEquals("2026-10-02T10:00:00.000Z", env.repository.observeOwnRefreshedAt().first())
    }
}
