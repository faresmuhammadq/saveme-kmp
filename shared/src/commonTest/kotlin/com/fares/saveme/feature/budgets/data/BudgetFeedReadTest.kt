package com.fares.saveme.feature.budgets.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FakeSyncApi
import com.fares.saveme.core.sync.FakeSyncApi.Companion.page
import com.fares.saveme.core.sync.FakeSyncApi.Companion.row
import com.fares.saveme.core.sync.FeedPuller
import com.fares.saveme.core.sync.RecordingApplier
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.ScopeRead
import com.fares.saveme.core.sync.SyncLock
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** After the 8 to 9 reset the user scope is read from the start; budget rows are applied and swept like the rest (US6-S1, FR-020). */
class BudgetFeedReadTest {

    private val user = SyncScope(ScopeKind.User, "u1")

    private fun fields(json: String) = (AppJson.parseToJsonElement(json) as JsonObject).filterKeys { it != "id" && it != "version" && it != "deleted_at" }

    @Test
    fun anUpdatedPhoneReadsThePersonsScopeFromTheStartAndAppliesBudgetsBesideTheOtherTypes() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        env.signedIn()
        // The phone after the migration: budgets skipped until now, cursor and position forgotten, data kept.
        env.scopeDao.clearFeed("user:u1")
        env.seed(budgetJson("gone", version = 2))
        env.seed(budgetJson("queued", version = 2))
        env.repository.insertPlaceholder("fresh", "u1", "c1", "EGP", 100)
        env.engine.enqueue("budget", "queued", user, 2, "{}")

        val api = FakeSyncApi()
        api.user = { cursor ->
            assertNull(cursor, "read from the start")
            ApiResult.Success(
                page(
                    listOf(
                        row("category", "c1", version = 3),
                        row("budget", "new", version = 1, extra = fields(budgetJson("new", amount = 123_456, currency = "KWD"))),
                        row("budget", "queued", version = 3, extra = fields(budgetJson("queued"))),
                    ),
                    sync = "u-pos",
                    position = 12,
                ),
            )
        }
        val category = RecordingApplier(ScopeKind.User, "category")
        val puller = FeedPuller(
            api, env.scopeDao, { listOf(BudgetFeedApplier(env.repository), category) }, { null }, SyncLock(), { "u1" }, env.clock,
        )

        assertIs<ScopeRead.Done>(puller.read(user))
        assertEquals(listOf<String?>(null), api.userCalls)
        val stored = env.budgetDao.rows.value
        assertEquals(123_456L, stored.getValue("new").amountMinor)
        assertEquals("KWD", stored.getValue("new").currency)
        assertNull(stored["gone"], "a synced budget the server did not return is swept")
        assertNotNull(stored["queued"], "a budget with a waiting change is kept")
        assertNotNull(stored["fresh"], "a placeholder is kept")
        assertEquals(1, category.applied.single().second.size, "other user-scope types are still applied")
        assertEquals(setOf("c1"), category.swept.single().second, "and swept")
        assertEquals("u-pos", env.scopeDao.rows.value["user:u1"]!!.feedCursor)
        assertEquals(12L, env.scopeDao.rows.value["user:u1"]!!.feedPosition)
    }
}
