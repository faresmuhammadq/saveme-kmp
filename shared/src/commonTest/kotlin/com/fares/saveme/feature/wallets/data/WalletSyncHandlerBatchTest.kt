package com.fares.saveme.feature.wallets.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/** The batch item is exactly the per-record write (spec 009 FR-017, FR-024). */
class WalletSyncHandlerBatchTest {
    private val draft = WalletDraft("cash", "Pocket", "EGP", 25_000, null, "#4C6FD6")

    private fun change(base: Long, draft: WalletDraft = this.draft, delete: Boolean = false) = PendingChange(
        recordType = "wallet", recordId = "w1", accountId = "u1", scope = SyncScope(ScopeKind.Wallet, "w1"),
        baseVersion = base, payloadJson = AppJson.encodeToString(WalletDraft.serializer(), draft), isDelete = delete,
        restore = false, revision = 0, createdOrder = 1, state = ChangeState.Waiting, attempts = 0, nextAttemptAt = null,
        clashJson = null, refusal = null, updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
    )

    @Test
    fun aCreateAndASaveAreUpsertItemsWhoseDataIsTheBodyOfThePut() = runTest {
        for ((base, edited) in listOf(0L to draft, 3L to draft.copy(name = "Renamed", icon = "bank"))) {
            val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody(walletJson(version = base + 1))) }
            env.signedIn()
            env.seed(walletJson(version = base.coerceAtLeast(1)))
            val item = assertNotNull(env.syncHandler.batchItem(change(base, edited)))
            env.syncHandler.send(change(base, edited))
            val put = env.requests.single().jsonBody()

            assertEquals("wallet", item.type)
            assertEquals(BatchOp.Upsert, item.op)
            assertEquals("w1", item.id)
            assertNull(item.walletId, "a wallet item carries no wallet id")
            assertEquals(put, item.data, "byte-equal to the per-record body, explicit nulls included")
        }
    }

    @Test
    fun explicitNullsStayInTheData() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        val item = assertNotNull(env.syncHandler.batchItem(change(0)))
        assertEquals("null", (item.data as kotlinx.serialization.json.JsonObject)["icon"].toString())
        assertEquals("\"#4C6FD6\"", (item.data as kotlinx.serialization.json.JsonObject)["color"].toString())
    }

    @Test
    fun aDeleteIsADeleteItemWithOnlyTheVersionLikeTheDeleteBody() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody(walletJson(deletedAt = "2026-10-02T10:00:00Z"))) }
        env.signedIn()
        val item = assertNotNull(env.syncHandler.batchItem(change(4, delete = true)))
        env.syncHandler.send(change(4, delete = true))
        assertEquals(BatchOp.Delete, item.op)
        assertEquals(env.requests.single().jsonBody(), item.data)
        assertEquals(setOf("version"), (item.data as kotlinx.serialization.json.JsonObject).keys)
    }

    @Test
    fun anArchiveGoesPerRecord() = runTest {
        val env = WalletTestEnv(backgroundScope) { jsonResponse(200, walletBody()) }
        assertNull(env.syncHandler.batchItem(change(2, draft.copy(archive = true))))
        assertNull(env.syncHandler.batchItem(change(0, draft.copy(archive = true))), "even a create that is archived at once")
    }
}
