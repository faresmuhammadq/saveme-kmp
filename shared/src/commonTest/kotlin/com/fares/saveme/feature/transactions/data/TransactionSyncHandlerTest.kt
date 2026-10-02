package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.network.ProblemError
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.dto
import com.fares.saveme.feature.transactions.transactionBody
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TransactionSyncHandlerTest {
    private val payload = TransactionPayload("expense", "2026-10-02", 6_000, "EGP", categoryId = "c2", description = "Taxi")

    private fun change(
        base: Long,
        payload: TransactionPayload = this.payload,
        delete: Boolean = false,
        restore: Boolean = false,
        source: String = "w1",
    ) = PendingChange(
        recordType = "transaction", recordId = "t1", accountId = "u1", scope = SyncScope(ScopeKind.Wallet, source),
        baseVersion = base, payloadJson = AppJson.encodeToString(TransactionPayload.serializer(), payload), isDelete = delete,
        restore = restore, revision = 0, createdOrder = 1, state = ChangeState.Waiting, attempts = 0, nextAttemptAt = null,
        clashJson = null, refusal = null, updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
    )

    private fun versionOf(result: ApiResult<JsonElement>) =
        assertIs<ApiResult.Success<JsonElement>>(result).value.jsonObject["version"]!!.jsonPrimitive.content.toLong()

    @Test
    fun recordTypeAndRestoreFlag() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        assertEquals("transaction", env.syncHandler.recordType)
        assertTrue(env.syncHandler.canRestore)
    }

    @Test
    fun createSendsPutWithVersionZeroUnderTheSourceWalletAndTheSavedCurrency() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(201, transactionBody(transactionJson(version = 1))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        assertEquals(1L, versionOf(env.syncHandler.send(change(base = 0))))
        val request = env.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/transactions/t1"))
        assertEquals("0", request.jsonBody()["version"].toString())
        assertEquals("\"EGP\"", request.jsonBody()["currency"].toString())
        assertEquals("6000", request.jsonBody()["amount_minor"].toString())
    }

    @Test
    fun editSendsTheBaseAndTheStoredKindAndCounterWallet() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, transactionBody(transactionJson(version = 4))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 3, kind = "expense", categoryId = "c1"))
        // The payload says income and names a destination; neither is ever sent changed (FR-012).
        val tampered = payload.copy(kind = "income", counterWalletId = "w2")
        assertEquals(4L, versionOf(env.syncHandler.send(change(base = 3, payload = tampered))))
        val body = env.requests.single().jsonBody()
        assertEquals("3", body["version"].toString())
        assertEquals("\"expense\"", body["kind"].toString())
        assertEquals("null", body["counter_wallet_id"].toString())
    }

    @Test
    fun aTransferKeepsItsStoredDestination() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, transactionBody(transactionJson(version = 4, kind = "transfer", categoryId = null, counterWalletId = "w2"))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 3, kind = "transfer", categoryId = null, counterWalletId = "w2"))
        env.syncHandler.send(change(base = 3, payload = payload.copy(kind = "transfer", categoryId = null, counterWalletId = "w3")))
        assertEquals("\"w2\"", env.requests.single().jsonBody()["counter_wallet_id"].toString())
    }

    @Test
    fun deleteSendsTheBaseVersionInTheBody() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, transactionBody(transactionJson(version = 6, deletedAt = "2026-10-02T10:00:00Z"))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        assertEquals(6L, versionOf(env.syncHandler.send(change(base = 5, delete = true))))
        val request = env.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("5", request.jsonBody()["version"].toString())
    }

    @Test
    fun restoreSendsThePatchThenAPutWithThePatchsVersionWhenThePayloadDiffers() = runTest {
        val env = TransactionTestEnv(backgroundScope) { request ->
            if (request.method == HttpMethod.Patch) jsonResponse(200, transactionBody(transactionJson(version = 7, amount = 4_550)))
            else jsonResponse(200, transactionBody(transactionJson(version = 8, amount = 6_000)))
        }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        assertEquals(8L, versionOf(env.syncHandler.send(change(base = 6, restore = true))))
        val (patch, put) = env.requests
        assertEquals(HttpMethod.Patch, patch.method)
        assertEquals("6", patch.jsonBody()["version"].toString())
        assertEquals("null", patch.jsonBody()["deleted_at"].toString())
        assertEquals(HttpMethod.Put, put.method)
        assertEquals("7", put.jsonBody()["version"].toString())
        assertEquals("6000", put.jsonBody()["amount_minor"].toString())
    }

    @Test
    fun restoreSendsOnlyThePatchWhenThePayloadEqualsTheRestoredTransaction() = runTest {
        val same = transactionJson(version = 7, amount = 6_000, categoryId = "c2", description = "Taxi", date = "2026-10-02")
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, transactionBody(same)) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        assertEquals(7L, versionOf(env.syncHandler.send(change(base = 6, restore = true))))
        assertEquals(1, env.requests.size)
    }

    @Test
    fun aFailureOfThePutAfterAPatchIsReturnedAsThatFailure() = runTest {
        val env = TransactionTestEnv(backgroundScope) { request ->
            if (request.method == HttpMethod.Patch) jsonResponse(200, transactionBody(transactionJson(version = 7)))
            else jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${transactionJson(version = 9)}"""))
        }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        val failure = assertIs<ApiResult.Failure>(env.syncHandler.send(change(base = 6, restore = true)))
        assertEquals("VERSION_CONFLICT", failure.problem.code)
        assertEquals(2, env.requests.size)
    }

    @Test
    fun thePathIsTheDestinationWhenTheSourceIsNotOnThePhone() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, transactionBody(transactionJson(version = 6, deletedAt = "2026-10-02T10:00:00Z"))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w2"))
        env.seed(transactionJson(walletId = "w9", kind = "transfer", categoryId = null, counterWalletId = "w2", version = 5))
        env.syncHandler.send(change(base = 5, delete = true, source = "w9"))
        assertTrue(env.requests.single().url.encodedPath.endsWith("/wallets/w2/transactions/t1"), env.requests.single().url.encodedPath)
    }

    @Test
    fun anAnswerToTheHandlersOwnSendAddsTheCarryAndATombstoneStaysHiddenUntilItSettles() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, transactionBody(transactionJson(version = 6, amount = 4_550, deletedAt = "2026-10-02T10:00:00Z"))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 5, amount = 4_550))
        val result = env.syncHandler.send(change(base = 5, delete = true))
        env.syncHandler.applySentAnswer(assertIs<ApiResult.Success<JsonElement>>(result).value)
        val row = env.transactionDao.get("t1")!!
        assertEquals("2026-10-02T10:00:00Z", row.deletedAt)
        // The expense had taken 45.50 out of the wallet; its deletion puts it back until a wallet read includes that.
        assertEquals(4_550, row.carrySourceMinor)
        assertNotNull(row.carryConfirmedAt)
    }

    @Test
    fun aCopyFromAnotherDeviceAddsNoCarryAndATombstoneRemovesTheRowAtOnce() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seed(transactionJson(version = 5))
        env.syncHandler.applyServerCopy(AppJson.parseToJsonElement(transactionJson(version = 6, amount = 9_000)))
        val live = env.transactionDao.get("t1")!!
        assertEquals(9_000, live.amountMinor)
        assertEquals(0, live.carrySourceMinor)
        assertNull(live.carryConfirmedAt)
        env.syncHandler.applyServerCopy(AppJson.parseToJsonElement(transactionJson(version = 7, deletedAt = "2026-10-02T10:00:00Z")))
        assertNull(env.transactionDao.get("t1"))
    }

    @Test
    fun aSendAloneNoLongerMarksTheNextCopyAsItsOwn() = runTest {
        // spec 009 R-14: the engine says "this is my own answer" through applySentAnswer; applyServerCopy never adds a carry.
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, transactionBody(transactionJson(version = 6, amount = 4_550))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 5, amount = 4_550))
        env.syncHandler.applyServerCopy(assertIs<ApiResult.Success<JsonElement>>(env.syncHandler.send(change(base = 5))).value)
        assertEquals(0, env.transactionDao.get("t1")!!.carrySourceMinor)
        assertNull(env.transactionDao.get("t1")!!.carryConfirmedAt)
    }

    @Test
    fun aNewTransactionsFirstAnswerCarriesItsWholeEffect() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(201, transactionBody(transactionJson(version = 1, amount = 6_000))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.repository.insertPlaceholder(NewTransaction("t1", "u1", "w1", null, "expense", "2026-10-02", 6_000, "EGP", "c2", null, null, null, false, "u1"))
        env.syncHandler.applySentAnswer(assertIs<ApiResult.Success<JsonElement>>(env.syncHandler.send(change(base = 0))).value)
        assertEquals(-6_000, env.transactionDao.get("t1")!!.carrySourceMinor)
    }

    @Test
    fun discardingRemovesAPlaceholderOnly() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.repository.insertPlaceholder(NewTransaction("p", "u1", "w1", null, "expense", "2026-10-02", 100, "EGP", "c1", null, null, null, false, "u1"))
        env.seed(transactionJson(id = "s", version = 2))
        env.syncHandler.onDiscarded("p")
        env.syncHandler.onDiscarded("s")
        assertNull(env.transactionDao.get("p"))
        assertNotNull(env.transactionDao.get("s"))
    }

    @Test
    fun allTenPointersMapToFieldNames() {
        val pointers = listOf(
            "/amount_minor" to "amount", "/date" to "date", "/category_id" to "category", "/counter_wallet_id" to "destination",
            "/currency" to "currency", "/kind" to "kind", "/description" to "note", "/memo" to "details", "/counterparty" to "person", "/cleared" to "cleared",
        )
        val handler = TransactionSyncHandler(
            repository = TransactionRepository(
                com.fares.saveme.feature.transactions.FakeTransactionDao(), com.fares.saveme.feature.account.FakeSyncScopeDao(), { "u1" }, { false },
            ),
            api = TransactionApi(com.fares.saveme.feature.auth.AuthTestEnv { error("unused") }.client),
            walletOnPhone = { true },
        )
        val problem = ProblemDetails(code = "VALIDATION_FAILED", errors = pointers.map { ProblemError(it.first, "REQUIRED") } + ProblemError("/unknown", "X"))
        assertEquals(pointers.map { it.second }.toSet(), handler.refusalFields(problem).keys)
        assertFalse("unknown" in handler.refusalFields(problem).keys)
        assertEquals("REQUIRED", handler.refusalFields(problem)["amount"])
    }

    @Test
    fun nothingTheHandlerLogsContainsTheAmountOrTheNote() = runTest {
        // The engine's change description and the entity print ids and versions only (P-09.2).
        val env = TransactionTestEnv(backgroundScope)
        env.seed(transactionJson(description = "Secret note", amount = 123_456))
        val text = env.transactionDao.get("t1").toString() + change(base = 1, payload = payload.copy(description = "Secret note", amountMinor = 123_456)).toString()
        assertFalse("Secret note" in text)
        assertFalse("123456" in text)
        assertEquals(dto(transactionJson()).id, "t1")
    }
}
