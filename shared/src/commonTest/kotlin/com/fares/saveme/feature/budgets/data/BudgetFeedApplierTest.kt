package com.fares.saveme.feature.budgets.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The person's own budgets from their feed: version guard, deletion rule and sweep of spec 009 (FR-019). */
class BudgetFeedApplierTest {

    private val user = SyncScope(ScopeKind.User, "u1")

    private fun json(text: String): JsonElement = AppJson.parseToJsonElement(text)

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = BudgetTestEnv(scope)

    @Test
    fun itConsumesBudgetRowsOfThePersonsOwnFeed() = runTest {
        val applier = BudgetFeedApplier(env(backgroundScope).repository)
        assertEquals(ScopeKind.User, applier.scopeKind)
        assertEquals("budget", applier.rowType)
    }

    @Test
    fun aBudgetIsAppliedWhenItsVersionIsTheSameOrNewerAndIgnoredWhenLower() = runTest {
        val env = env(backgroundScope)
        val applier = BudgetFeedApplier(env.repository)
        applier.apply(user, listOf(json(budgetJson("a", amount = 100, version = 3))))
        applier.apply(user, listOf(json(budgetJson("a", amount = 999, version = 2))))
        assertEquals(100L, env.budgetDao.rows.value.getValue("a").amountMinor)
        applier.apply(user, listOf(json(budgetJson("a", amount = 200, version = 3))))
        assertEquals(200L, env.budgetDao.rows.value.getValue("a").amountMinor)
        applier.apply(user, listOf(json(budgetJson("a", amount = 300, version = 4, archivedAt = "2026-10-01T00:00:00Z"))))
        assertEquals(300L, env.budgetDao.rows.value.getValue("a").amountMinor)
        assertEquals("2026-10-01T00:00:00Z", env.budgetDao.rows.value.getValue("a").archivedAt)
        assertEquals("u1", env.budgetDao.rows.value.getValue("a").accountId)
    }

    @Test
    fun unknownMembersAndAnUnknownPeriodTypeAreKept() = runTest {
        val env = env(backgroundScope)
        BudgetFeedApplier(env.repository).apply(user, listOf(json(budgetJson("a", periodType = "weekly"))))
        assertEquals("weekly", env.budgetDao.rows.value.getValue("a").periodType)
    }

    @Test
    fun aTombstoneRemovesTheBudgetAndItsProgressUnlessAChangeWaitsForIt() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(budgetJson("gone", version = 2))
        env.seed(budgetJson("queued", version = 2))
        env.seedProgress("gone")
        env.seedProgress("queued")
        env.engine.enqueue("budget", "queued", user, 2, "{}")
        BudgetFeedApplier(env.repository).apply(
            user,
            listOf(
                json(budgetJson("gone", version = 3, deletedAt = "2026-10-02T00:00:00Z")),
                json(budgetJson("queued", version = 3, deletedAt = "2026-10-02T00:00:00Z")),
            ),
        )
        assertNull(env.budgetDao.rows.value["gone"])
        assertNull(env.progressDao.rows.value["gone"], "its stored progress goes with it")
        assertNotNull(env.budgetDao.rows.value["queued"], "it stays until the person settles the clash")
        assertNotNull(env.progressDao.rows.value["queued"])
        assertEquals(2L, env.budgetDao.rows.value.getValue("queued").version)
    }

    @Test
    fun aFeedCopyNeverTouchesTheWaitingChange() = runTest {
        val env = env(backgroundScope)
        env.seed(budgetJson("a", version = 2))
        env.engine.enqueue("budget", "a", user, 2, """{"currency":"EGP","amountMinor":1}""")
        BudgetFeedApplier(env.repository).apply(user, listOf(json(budgetJson("a", amount = 777, version = 3))))
        assertEquals(777L, env.budgetDao.rows.value.getValue("a").amountMinor)
        assertEquals(2L, env.engine.get("budget", "a")!!.baseVersion)
        assertEquals("""{"currency":"EGP","amountMinor":1}""", env.engine.get("budget", "a")!!.payloadJson)
    }

    @Test
    fun aSweepRemovesUnseenSyncedBudgetsButNeverPlaceholdersOrQueuedOnes() = runTest {
        val env = env(backgroundScope)
        env.seed(budgetJson("seen", version = 2))
        env.seed(budgetJson("gone", version = 2))
        env.seed(budgetJson("queued", version = 2))
        env.seedProgress("gone")
        env.repository.insertPlaceholder("fresh", "u1", "c1", "EGP", 100)
        env.engine.enqueue("budget", "queued", user, 2, "{}")
        BudgetFeedApplier(env.repository).sweep(user, setOf("seen"))
        assertEquals(setOf("seen", "queued", "fresh"), env.budgetDao.rows.value.keys)
        assertNull(env.progressDao.rows.value["gone"])
    }
}
