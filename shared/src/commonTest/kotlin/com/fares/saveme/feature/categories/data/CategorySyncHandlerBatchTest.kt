package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryBody
import com.fares.saveme.feature.categories.categoryJson
import com.fares.saveme.feature.categories.data.dto.CategoryPayload
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/** The batch item is exactly the per-record write, with the stored kind (spec 009 FR-017, FR-024). */
class CategorySyncHandlerBatchTest {
    private val payload = CategoryPayload("expense", "Coffee", null, "#1F7F8C")

    private fun change(base: Long, payload: CategoryPayload = this.payload, delete: Boolean = false) = PendingChange(
        recordType = "category", recordId = "c1", accountId = "u1", scope = SyncScope(ScopeKind.User, "u1"),
        baseVersion = base, payloadJson = AppJson.encodeToString(CategoryPayload.serializer(), payload), isDelete = delete,
        restore = false, revision = 0, createdOrder = 1, state = ChangeState.Waiting, attempts = 0, nextAttemptAt = null,
        clashJson = null, refusal = null, updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
    )

    @Test
    fun aCreateAndASaveCarryTheBodyOfThePutAndTheStoredKind() = runTest {
        for (base in listOf(0L, 2L)) {
            val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryBody(categoryJson(version = base + 1))) }
            env.signedIn()
            // The stored kind wins over a payload that says otherwise (FR-008 of spec 007).
            env.seed(categoryJson(kind = "income", version = 2))
            val sent = change(base, payload.copy(kind = "expense"))
            val item = assertNotNull(env.syncHandler.batchItem(sent))
            env.syncHandler.send(sent)
            assertEquals("category", item.type)
            assertEquals(BatchOp.Upsert, item.op)
            assertEquals("c1", item.id)
            assertNull(item.walletId)
            assertEquals(env.requests.single().jsonBody(), item.data)
            assertEquals("\"income\"", (item.data as JsonObject)["kind"].toString())
            assertEquals("null", (item.data as JsonObject)["icon"].toString(), "explicit null")
        }
    }

    @Test
    fun aDeleteCarriesOnlyTheVersionAndAnArchiveGoesPerRecord() = runTest {
        val env = CategoryTestEnv(backgroundScope) { jsonResponse(200, categoryBody(categoryJson(deletedAt = "2026-10-02T10:00:00Z"))) }
        env.signedIn()
        val item = assertNotNull(env.syncHandler.batchItem(change(5, delete = true)))
        env.syncHandler.send(change(5, delete = true))
        assertEquals(BatchOp.Delete, item.op)
        assertEquals(env.requests.single().jsonBody(), item.data)
        assertNull(env.syncHandler.batchItem(change(2, payload.copy(archive = true))))
    }
}
