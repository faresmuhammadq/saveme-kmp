package com.fares.saveme.feature.budgets.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.CapturedSyncResponses
import com.fares.saveme.core.sync.CapturedSyncServer
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.data.CapturedBudgetResponses as C
import com.fares.saveme.feature.budgets.data.dto.BudgetDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The live backend's batch answer for budget items (spec 012 contract QA) through the real wire client, the real engine
 * and the budget handler: the items the app builds equal the ones the server answered (upsert, limit edit, delete, a
 * stale save, an already-deleted delete, an invalid limit), and every answer settles as the same single write's would.
 */
class BudgetCapturedBatchTest {

    private val user = SyncScope(ScopeKind.User, "u1")

    private fun payload(category: String?, currency: String, amount: Long) =
        AppJson.encodeToString(BudgetPayload.serializer(), BudgetPayload(category, currency, amount))

    @Test
    fun theLiveBatchOfBudgetItemsLeavesAsTheItemsTheServerAnsweredAndEveryAnswerSettles() = runTest {
        val env = BudgetTestEnv(backgroundScope)
        val server = CapturedSyncServer()
        server.signedIn()
        server.batch = { _, _ -> CapturedSyncResponses.Answer(200, C.BATCH_BUDGETS_200) }
        val engine = SyncEngine(
            env.pendingDao, env.scopeDao, listOf(env.syncHandler), env.scheduler, currentAccountId = { "u1" }, clock = env.clock, syncApi = server.api,
        )
        env.repository.storeServerCopy(AppJson.decodeFromString(BudgetDto.serializer(), AppJson.parseToJsonElement(C.PUT_CREATED_TRANSPORT_201).jsonObject.getValue("data").toString()))
        env.repository.storeServerCopy(AppJson.decodeFromString(BudgetDto.serializer(), AppJson.parseToJsonElement(C.PUT_CREATED_CATEGORY_201).jsonObject.getValue("data").toString()))

        engine.enqueue("budget", C.BUDGET_KWD, user, 0, payload(C.CATEGORY_GROCERIES, "KWD", 123_400))
        engine.enqueue("budget", C.BUDGET_TRANSPORT, user, 1, payload(C.CATEGORY_TRANSPORT, "EGP", 55_000))
        engine.enqueueDelete("budget", C.BUDGET_BATCH_DELETED, user, 1)
        engine.enqueue("budget", C.BUDGET_GROCERIES, user, 1, payload(C.CATEGORY_GROCERIES, "EGP", 999_999))
        engine.enqueueDelete("budget", C.BUDGET_DELETED, user, 1)
        engine.enqueue("budget", C.BUDGET_BATCH_INVALID, user, 0, payload(null, "EGP", 0))

        engine.drain()

        val sent = server.uploads.single().jsonArray
        val live = CapturedSyncServer.itemsOf(C.BATCH_BUDGETS_200_REQUEST).jsonArray
        assertEquals(live.map { it.jsonObject.getValue("id") }.toSet(), sent.map { it.jsonObject.getValue("id") }.toSet())
        for (item in live) {
            val ours = sent.single { it.jsonObject["id"] == item.jsonObject["id"] }.jsonObject
            assertEquals(item.jsonObject, ours, "item ${item.jsonObject["id"]}")
            assertTrue("wallet_id" !in ours, "a budget item never names a wallet")
        }

        // Applied: stored with the server's copy, nothing waits.
        assertNull(engine.get("budget", C.BUDGET_KWD))
        assertEquals("KWD", env.repository.get(C.BUDGET_KWD)!!.currency)
        assertEquals(1L, env.repository.get(C.BUDGET_KWD)!!.version)
        assertNull(engine.get("budget", C.BUDGET_TRANSPORT))
        assertEquals(55_000L, env.repository.get(C.BUDGET_TRANSPORT)!!.amountMinor)
        assertNull(engine.get("budget", C.BUDGET_BATCH_DELETED))
        assertNull(engine.get("budget", C.BUDGET_DELETED), "deleting an already deleted budget is accepted")
        // The stale save is a clash carrying the server's live copy; the invalid limit is refused with its field.
        val clash = assertNotNull(engine.get("budget", C.BUDGET_GROCERIES))
        assertEquals(ChangeState.Clash, clash.state)
        assertEquals(2L, AppJson.decodeFromString(BudgetDto.serializer(), clash.clashJson!!).version)
        val refused = assertNotNull(engine.get("budget", C.BUDGET_BATCH_INVALID))
        assertEquals(ChangeState.Refused, refused.state)
        assertEquals("limit", refused.refusal!!.fields.entries.single().key)
        assertEquals("OUT_OF_RANGE", refused.refusal!!.fields.getValue("limit"))
    }

    @Test
    fun aBatchWithAWalletIdOrAnArchiveOperationIsRefusedWholeSoTheClientNeverSendsOne() {
        for (body in listOf(C.BATCH_WALLET_ID_422, C.BATCH_ARCHIVE_OP_422)) {
            val problem = AppJson.parseToJsonElement(body).jsonObject
            assertEquals("\"VALIDATION_FAILED\"", problem.getValue("code").toString())
        }
        assertTrue("wallet_id" in C.BATCH_WALLET_ID_422_REQUEST && "\"archive\"" in C.BATCH_ARCHIVE_OP_422_REQUEST)
        // The handler's items: the first carries no wallet_id, and an archive is not an item at all (BudgetBatchTest).
        assertTrue("wallet_id" !in C.BATCH_BUDGETS_200_REQUEST)
    }

    @Test
    fun anUnverifiedPersonsBatchItemIsRefusedPerItemWithTheVerificationCode() {
        val result = AppJson.parseToJsonElement(C.BATCH_UNVERIFIED_200).jsonObject.getValue("data").jsonArray.single().jsonObject
        assertEquals("\"rejected\"", result.getValue("outcome").toString())
        assertEquals("403", result.getValue("status").toString())
        assertEquals("\"EMAIL_NOT_VERIFIED\"", (result.getValue("problem") as JsonObject).getValue("code").toString())
    }
}
