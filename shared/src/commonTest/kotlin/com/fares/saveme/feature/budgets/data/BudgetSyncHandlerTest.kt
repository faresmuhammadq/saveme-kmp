package com.fares.saveme.feature.budgets.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.network.ProblemError
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.RecordKey
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetBody
import com.fares.saveme.feature.budgets.budgetJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** A budget's batch items, per-record sends, prerequisites and refusals (research R-04, spec 009 FR-017/FR-018). */
class BudgetSyncHandlerTest {

    private fun change(base: Long, payload: BudgetPayload, delete: Boolean = false, id: String = "b1") = PendingChange(
        recordType = "budget", recordId = id, accountId = "u1", scope = SyncScope(ScopeKind.User, "u1"),
        baseVersion = base, payloadJson = AppJson.encodeToString(BudgetPayload.serializer(), payload), isDelete = delete,
        restore = false, revision = 0, createdOrder = 1, state = ChangeState.Waiting, attempts = 0, nextAttemptAt = null,
        clashJson = null, refusal = null, updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
    )

    private val payload = BudgetPayload("c1", "EGP", 700_000)

    private fun env(scope: kotlinx.coroutines.CoroutineScope, version: Long = 2, archivedAt: String? = null) =
        BudgetTestEnv(scope) { jsonResponse(200, budgetBody(budgetJson(version = version, archivedAt = archivedAt))) }

    @Test
    fun theUpsertItemIsExactlyThePutBodyWithoutAWalletAndOfTypeBudget() = runTest {
        for (base in listOf(0L, 2L)) {
            val env = env(backgroundScope, version = base + 1)
            env.signedIn()
            if (base > 0) env.seed(budgetJson(version = 2))
            val sent = change(base, payload)
            val item = assertNotNull(env.syncHandler.batchItem(sent))
            env.syncHandler.send(sent)
            assertEquals("budget", item.type)
            assertEquals(BatchOp.Upsert, item.op)
            assertEquals("b1", item.id)
            assertNull(item.walletId, "a wallet_id on a budget item rejects the whole batch")
            assertEquals(env.requests.single().jsonBody(), item.data)
            assertEquals(setOf("category_id", "period_type", "amount_minor", "currency", "version"), (item.data as JsonObject).keys)
        }
    }

    @Test
    fun forABudgetTheServerHasCategoryCurrencyAndPeriodTypeComeFromTheStoredRow() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seed(budgetJson(categoryId = "c-stored", currency = "USD", version = 2))
        val item = assertNotNull(env.syncHandler.batchItem(change(2, BudgetPayload("c-other", "EGP", 700_000))))
        val data = item.data as JsonObject
        assertEquals("\"c-stored\"", data["category_id"].toString())
        assertEquals("\"USD\"", data["currency"].toString())
        assertEquals("\"monthly\"", data["period_type"].toString())
        assertEquals("700000", data["amount_minor"].toString())
        env.syncHandler.send(change(2, BudgetPayload("c-other", "EGP", 700_000)))
        assertEquals("\"c-stored\"", env.requests.single().jsonBody()["category_id"].toString())
    }

    @Test
    fun aCreateTakesCategoryAndCurrencyFromThePayloadAndAllExpensesIsAnExplicitNull() = runTest {
        val env = env(backgroundScope, version = 1)
        env.signedIn()
        env.repository.insertPlaceholder("b1", "u1", null, "EGP", 100)
        val item = assertNotNull(env.syncHandler.batchItem(change(0, BudgetPayload(null, "EGP", 600_000))))
        assertEquals("null", (item.data as JsonObject)["category_id"].toString())
        assertEquals("0", (item.data as JsonObject)["version"].toString())
    }

    @Test
    fun aDeleteCarriesOnlyTheVersionAndAnArchiveGoesPerRecord() = runTest {
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(200, budgetBody(budgetJson(deletedAt = "2026-10-02T10:00:00Z"))) }
        env.signedIn()
        val item = assertNotNull(env.syncHandler.batchItem(change(5, payload, delete = true)))
        env.syncHandler.send(change(5, payload, delete = true))
        assertEquals(BatchOp.Delete, item.op)
        assertNull(item.walletId)
        assertEquals(env.requests.single().jsonBody(), item.data)
        assertEquals(setOf("version"), (item.data as JsonObject).keys)
        assertNull(env.syncHandler.batchItem(change(2, payload.copy(archive = true))))
    }

    @Test
    fun anEditSendsOnePutAndAnArchiveWithAnEditSendsThePutThenTheArchiveOnTheNewVersion() = runTest {
        val env = BudgetTestEnv(backgroundScope) { request ->
            if (request.url.encodedPath.endsWith("/archive")) jsonResponse(200, budgetBody(budgetJson(version = 4, archivedAt = "2026-10-02T10:00:00Z")))
            else jsonResponse(200, budgetBody(budgetJson(version = 3, amount = 700_000)))
        }
        env.signedIn()
        env.seed(budgetJson(version = 2))
        val result = env.syncHandler.send(change(2, payload.copy(archive = true)))
        assertIs<ApiResult.Success<*>>(result)
        val (put, archive) = env.requests
        assertEquals("PUT", put.method.value)
        assertTrue(put.url.encodedPath.endsWith("/budgets/b1"))
        assertEquals("POST", archive.method.value)
        assertTrue(archive.url.encodedPath.endsWith("/budgets/b1/archive"))
        assertEquals("3", archive.jsonBody()["version"].toString(), "the archive rides the version the PUT answered")
    }

    @Test
    fun anArchiveOnlyChangeWithTheStoredLimitSkipsThePut() = runTest {
        val env = BudgetTestEnv(backgroundScope) { jsonResponse(200, budgetBody(budgetJson(version = 3, archivedAt = "2026-10-02T10:00:00Z"))) }
        env.signedIn()
        env.seed(budgetJson(version = 2, amount = 700_000))
        env.syncHandler.send(change(2, payload.copy(archive = true)))
        val request = env.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/budgets/b1/archive"))
        assertEquals("2", request.jsonBody()["version"].toString())
    }

    @Test
    fun anAlreadyArchivedAnswerToThePutIsNotArchivedAgain() = runTest {
        val env = env(backgroundScope, version = 3, archivedAt = "2026-10-01T00:00:00Z")
        env.signedIn()
        env.seed(budgetJson(version = 2))
        env.syncHandler.send(change(2, payload.copy(archive = true)))
        assertEquals(1, env.requests.size)
    }

    @Test
    fun aFailedPutStopsBeforeTheArchive() = runTest {
        val env = BudgetTestEnv(backgroundScope) { throw kotlinx.io.IOException("offline") }
        env.signedIn()
        env.seed(budgetJson(version = 2))
        assertEquals(ApiResult.Offline, env.syncHandler.send(change(2, payload.copy(archive = true))))
        assertEquals(1, env.requests.size)
    }

    @Test
    fun theCategoryOfABudgetIsAPrerequisiteAndAllExpensesHasNone() = runTest {
        val env = env(backgroundScope)
        assertEquals(listOf(RecordKey("category", "c1")), env.syncHandler.prerequisites(change(0, payload)))
        assertEquals(emptyList(), env.syncHandler.prerequisites(change(0, BudgetPayload(null, "EGP", 1))))
        assertEquals(emptyList(), env.syncHandler.prerequisites(change(2, payload, delete = true)))
    }

    @Test
    fun deletionIsFinalSoThereIsNoRestore() = runTest {
        assertFalse(env(backgroundScope).syncHandler.canRestore)
        assertEquals("budget", env(backgroundScope).syncHandler.recordType)
    }

    @Test
    fun discardingRemovesAPlaceholderOnly() = runTest {
        val env = env(backgroundScope)
        env.repository.insertPlaceholder("fresh", "u1", "c1", "EGP", 100)
        env.seed(budgetJson("synced", version = 2))
        env.syncHandler.onDiscarded("fresh")
        env.syncHandler.onDiscarded("synced")
        assertEquals(setOf("synced"), env.budgetDao.rows.value.keys)
    }

    @Test
    fun refusalsMapTheFourPointersToFieldNames() = runTest {
        val problem = ProblemDetails(
            status = 422, code = "VALIDATION_FAILED",
            errors = listOf(
                ProblemError("/category_id", "INVALID_ENUM_VALUE"), ProblemError("/amount_minor", "OUT_OF_RANGE"),
                ProblemError("/currency", "INVALID_CURRENCY"), ProblemError("/period_type", "IMMUTABLE_FIELD"), ProblemError("/other", "X"),
            ),
        )
        assertEquals(
            mapOf("category" to "INVALID_ENUM_VALUE", "limit" to "OUT_OF_RANGE", "currency" to "INVALID_CURRENCY", "period" to "IMMUTABLE_FIELD"),
            env(backgroundScope).syncHandler.refusalFields(problem),
        )
    }

    @Test
    fun theServerCopyIsStoredAndATombstoneRemovesTheRow() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.syncHandler.applyServerCopy(AppJson.parseToJsonElement(budgetJson("x", version = 3)))
        assertEquals(3L, env.budgetDao.rows.value.getValue("x").version)
        env.syncHandler.applyServerCopy(AppJson.parseToJsonElement(budgetJson("x", version = 4, deletedAt = "2026-10-02T10:00:00Z")))
        assertNull(env.budgetDao.rows.value["x"])
    }
}
