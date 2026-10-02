package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.RecordKey
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.transactionBody
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/** The batch item is exactly the per-record write; the wallet id is the one the path would use (spec 009 FR-017, FR-022, FR-024). */
class TransactionSyncHandlerBatchTest {
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

    private fun env(scope: kotlinx.coroutines.CoroutineScope) =
        TransactionTestEnv(scope) { jsonResponse(200, transactionBody(transactionJson(version = 4))) }

    @Test
    fun aCreateCarriesTheWalletIdAndAPayloadsCurrencyAndTheBodyOfThePut() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        val item = assertNotNull(env.syncHandler.batchItem(change(0)))
        env.syncHandler.send(change(0))
        assertEquals("transaction", item.type)
        assertEquals(BatchOp.Upsert, item.op)
        assertEquals("t1", item.id)
        assertEquals("w1", item.walletId)
        assertEquals(env.requests.single().jsonBody(), item.data)
        assertEquals("null", (item.data as JsonObject)["counter_wallet_id"].toString())
    }

    @Test
    fun anExistingTransactionKeepsItsStoredKindDestinationAndCurrency() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 3, kind = "transfer", categoryId = null, counterWalletId = "w2", currency = "USD"))
        val edited = payload.copy(kind = "expense", currency = "EGP", counterWalletId = null, categoryId = null)
        val item = assertNotNull(env.syncHandler.batchItem(change(3, edited)))
        env.syncHandler.send(change(3, edited))
        val body = item.data as JsonObject
        assertEquals("\"transfer\"", body["kind"].toString())
        assertEquals("\"USD\"", body["currency"].toString())
        assertEquals("\"w2\"", body["counter_wallet_id"].toString())
        assertEquals(env.requests.single().jsonBody(), item.data)
    }

    @Test
    fun theWalletIdIsTheDestinationWhenTheSourceIsGoneFromThePhone() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seedWallet(walletJson(id = "w2")) // only the destination is on the phone
        env.seed(transactionJson(version = 3, walletId = "w9", kind = "transfer", categoryId = null, counterWalletId = "w2"))
        val partner = change(3, payload.copy(kind = "transfer", categoryId = null, counterWalletId = "w2"), source = "w9")
        val item = assertNotNull(env.syncHandler.batchItem(partner))
        env.syncHandler.send(partner)
        assertEquals("w2", item.walletId)
        assertEquals(true, env.requests.single().url.encodedPath.contains("/wallets/w2/transactions/t1"))
    }

    @Test
    fun aDeleteCarriesOnlyTheVersionAndTheWalletId() = runTest {
        val env = env(backgroundScope)
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        val item = assertNotNull(env.syncHandler.batchItem(change(5, delete = true)))
        env.syncHandler.send(change(5, delete = true))
        assertEquals(BatchOp.Delete, item.op)
        assertEquals("w1", item.walletId)
        assertEquals(env.requests.single().jsonBody(), item.data)
    }

    @Test
    fun aRestoreWithMyChangeGoesPerRecordBecauseItIsAPatchThenAPut() = runTest {
        val env = env(backgroundScope)
        assertNull(env.syncHandler.batchItem(change(5, restore = true)))
    }

    @Test
    fun prerequisitesAreTheSourceWalletTheDestinationAndTheCategory() = runTest {
        val env = env(backgroundScope)
        val expense = env.syncHandler.prerequisites(change(0))
        assertEquals(listOf(RecordKey("wallet", "w1"), RecordKey("category", "c2")), expense)

        val transfer = env.syncHandler.prerequisites(change(0, payload.copy(kind = "transfer", categoryId = null, counterWalletId = "w2")))
        assertEquals(listOf(RecordKey("wallet", "w1"), RecordKey("wallet", "w2")), transfer)

        assertEquals(emptyList(), env.syncHandler.prerequisites(change(4, delete = true)), "a delete needs nothing created first")
    }
}
