package com.fares.saveme.feature.budgets.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.BatchHandler
import com.fares.saveme.core.sync.BatchItemResultDto
import com.fares.saveme.core.sync.FakeSyncApi
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetBody
import com.fares.saveme.feature.budgets.budgetJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Budget items ride the same batch as category and transaction items, without a wallet (spec 009 FR-019, spec 012 FR-019). */
class BudgetBatchTest {

    private val user = SyncScope(ScopeKind.User, "u1")
    private val wallet = SyncScope(ScopeKind.Wallet, "w1")

    private fun payload(category: String?, archive: Boolean = false) =
        """{"categoryId":${category?.let { "\"$it\"" } ?: "null"},"currency":"EGP","amountMinor":600000,"archive":$archive}"""

    private class Rig(env: BudgetTestEnv, val log: MutableList<String>) {
        val categories = BatchHandler("category", log = log)
        val transactions = BatchHandler("transaction", log = log)
        val api = FakeSyncApi()
        val engine = SyncEngine(
            env.pendingDao, env.scopeDao, listOf(env.syncHandler, categories, transactions), env.scheduler,
            currentAccountId = { "u1" }, clock = env.clock, syncApi = api,
        )

        init {
            api.batchAnswer = { items ->
                log += "batch: ${items.joinToString { "${it.type}/${it.id}" }}"
                ApiResult.Success(
                    items.map { item ->
                        // The server answers a budget with the whole record, as the real handler decodes it.
                        if (item.type == "budget") {
                            BatchItemResultDto(item.type, item.id, "applied", 200, AppJson.parseToJsonElement(budgetJson(item.id, version = 1)) as JsonObject, null)
                        } else {
                            FakeSyncApi.appliedResult(item, version = 1)
                        }
                    },
                )
            }
        }
    }

    @Test
    fun budgetItemsShareTheBatchWithCategoryAndTransactionItemsAndCarryNoWalletId() = runTest {
        val log = mutableListOf<String>()
        val env = BudgetTestEnv(backgroundScope)
        val rig = Rig(env, log)
        env.signedIn()
        env.repository.insertPlaceholder("b1", "u1", "c-new", "EGP", 600_000)
        rig.engine.enqueue("category", "c-new", user, 0, "{}")
        rig.engine.enqueue("budget", "b1", user, 0, payload("c-new"))
        rig.engine.enqueue("transaction", "t1", wallet, 0, "{}")
        rig.engine.drain()
        assertEquals(1, rig.api.batches.size, "one request carries all three")
        val items = rig.api.batches.single()
        assertEquals(listOf("category", "budget", "transaction"), items.map { it.type })
        assertNull(items.first { it.type == "budget" }.walletId, "a wallet_id on a budget item rejects the whole batch")
        assertEquals("w1", items.first { it.type == "transaction" }.walletId)
        assertEquals("upsert", items.first { it.type == "budget" }.op)
    }

    @Test
    fun aBudgetNamingACategoryNotYetAcceptedIsHeldWithoutCountingATry() = runTest {
        val log = mutableListOf<String>()
        val env = BudgetTestEnv(backgroundScope)
        val rig = Rig(env, log)
        env.signedIn()
        env.repository.insertPlaceholder("b1", "u1", "c-new", "EGP", 600_000)
        rig.engine.enqueue("category", "c-new", user, 0, "{}")
        // The person has not settled the category (a refusal waits for them), so its create is not in this run.
        env.pendingDao.upsert(env.pendingDao.get("category", "c-new")!!.copy(state = "Refused"))
        rig.engine.enqueue("budget", "b1", user, 0, payload("c-new"))
        rig.engine.drain()
        assertTrue(rig.api.batches.isEmpty(), "nothing is sent for the budget")
        val held = env.pendingDao.get("budget", "b1")!!
        assertEquals("Waiting", held.state)
        assertEquals(0, held.attempts)
    }

    @Test
    fun anArchiveGoesPerRecordAtItsPlaceInTheOrder() = runTest {
        val log = mutableListOf<String>()
        val env = BudgetTestEnv(backgroundScope) { request ->
            log += "${request.method.value} ${request.url.encodedPath.substringAfter("/api/v1").ifEmpty { request.url.encodedPath }}"
            jsonResponse(200, budgetBody(budgetJson("b2", version = 3, archivedAt = "2026-10-02T10:00:00Z")))
        }
        val rig = Rig(env, log)
        env.signedIn()
        env.seed(budgetJson("b1", version = 2))
        env.seed(budgetJson("b2", version = 2, amount = 600_000))
        rig.engine.enqueue("budget", "b1", user, 2, payload("c1"))
        rig.engine.enqueue("budget", "b2", user, 2, payload("c1", archive = true))
        rig.engine.enqueue("transaction", "t1", wallet, 0, "{}")
        rig.engine.drain()
        assertEquals(3, log.size, log.toString())
        assertEquals("batch: budget/b1", log[0])
        assertTrue(log[1].startsWith("POST") && log[1].endsWith("/budgets/b2/archive"), log[1])
        assertEquals("batch: transaction/t1", log[2])
    }
}
