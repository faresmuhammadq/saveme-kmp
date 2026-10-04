package com.fares.saveme.feature.recurring.data

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
import com.fares.saveme.feature.recurring.RecurringTestEnv
import com.fares.saveme.feature.recurring.data.dto.OccurrencePayload
import com.fares.saveme.feature.recurring.occurrenceBody
import com.fares.saveme.feature.recurring.occurrenceJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class OccurrenceSyncHandlerTest {
    private val confirmPayload = OccurrencePayload("confirmed", "2026-11-03", 61_200, "c1", "Electricity", transactionId = "tx-1")
    private val skipPayload = OccurrencePayload("skipped", "2026-11-01", 450_000, "c1", "Rent")

    private fun change(base: Long = 1, payload: OccurrencePayload = confirmPayload, id: String = "o1") = PendingChange(
        recordType = "occurrence", recordId = id, accountId = "u1", scope = SyncScope(ScopeKind.Wallet, "w1"),
        baseVersion = base, payloadJson = AppJson.encodeToString(OccurrencePayload.serializer(), payload), isDelete = false,
        restore = false, revision = 0, createdOrder = 1, state = ChangeState.Waiting, attempts = 0, nextAttemptAt = null,
        clashJson = null, refusal = null, updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
    )

    private fun json(text: String): JsonElement = AppJson.parseToJsonElement(text)

    @Test
    fun recordTypeAndNoRestore() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        assertEquals("occurrence", env.occurrenceHandler.recordType)
        assertFalse(env.occurrenceHandler.canRestore)
    }

    @Test
    fun aConfirmIsAPutWithTheBaseVersionAndThePayloadsTransactionId() = runTest {
        val env = RecurringTestEnv(backgroundScope) { jsonResponse(200, occurrenceBody(occurrenceJson(version = 2, status = "confirmed", transactionId = "tx-1"))) }
        env.signedIn()
        assertIs<ApiResult.Success<*>>(env.occurrenceHandler.send(change(base = 1)))
        assertIs<ApiResult.Success<*>>(env.occurrenceHandler.send(change(base = 1)))
        val first = env.requests[0]
        assertEquals(HttpMethod.Put, first.method)
        assertTrue(first.url.encodedPath.endsWith("/wallets/w1/occurrences/o1"))
        assertEquals("1", first.jsonBody()["version"].toString())
        assertEquals("\"tx-1\"", first.jsonBody()["transaction_id"].toString())
        assertEquals("\"tx-1\"", env.requests[1].jsonBody()["transaction_id"].toString(), "reused on every resend")
    }

    @Test
    fun skipAndUnskipSendANullTransactionId() = runTest {
        val env = RecurringTestEnv(backgroundScope) { jsonResponse(200, occurrenceBody(occurrenceJson(version = 2, status = "skipped"))) }
        env.signedIn()
        env.occurrenceHandler.send(change(payload = skipPayload))
        env.occurrenceHandler.send(change(2, skipPayload.copy(status = "scheduled")))
        assertEquals("null", env.requests[0].jsonBody()["transaction_id"].toString())
        assertEquals("\"scheduled\"", env.requests[1].jsonBody()["status"].toString())
        assertEquals("2", env.requests[1].jsonBody()["version"].toString())
    }

    @Test
    fun batchItemsAreAlwaysUpsertsWithTheWallet() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        val item = env.occurrenceHandler.batchItem(change())
        assertEquals(BatchOp.Upsert, item.op)
        assertEquals("w1", item.walletId)
        assertEquals("occurrence", item.type)
        assertEquals(setOf("status", "date", "amount_minor", "category_id", "description", "memo", "counterparty", "transaction_id", "version"), (item.data as kotlinx.serialization.json.JsonObject).keys)
    }

    @Test
    fun anEditedCategoryMadeOnThePhoneIsAPrerequisite() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        assertEquals(listOf(RecordKey("category", "c1")), env.occurrenceHandler.prerequisites(change()))
        assertEquals(emptyList(), env.occurrenceHandler.prerequisites(change(payload = skipPayload.copy(categoryId = null))))
    }

    @Test
    fun onlyAConfirmedCurrentIsAcceptedWithoutAsking() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        assertTrue(env.occurrenceHandler.acceptsClash(change(), json(occurrenceJson(version = 3, status = "confirmed", transactionId = "other"))))
        assertTrue(env.occurrenceHandler.acceptsClash(change(payload = skipPayload), json(occurrenceJson(version = 3, status = "confirmed", transactionId = "other"))))
        assertFalse(env.occurrenceHandler.acceptsClash(change(), json(occurrenceJson(version = 3, status = "scheduled"))))
        assertFalse(env.occurrenceHandler.acceptsClash(change(), json(occurrenceJson(version = 3, status = "skipped"))))
        assertFalse(env.occurrenceHandler.acceptsClash(change(), json(occurrenceJson(version = 3, deletedAt = "2026-10-01T00:00:00Z"))))
    }

    @Test
    fun anAcceptedClashStoresTheCopyWithTheNoticeAndDropsThePendingTransaction() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        env.seedOccurrence()
        env.engine.enqueue("occurrence", "o1", SyncScope(ScopeKind.Wallet, "w1"), 1, AppJson.encodeToString(OccurrencePayload.serializer(), confirmPayload))
        env.occurrenceHandler.applyServerCopy(json(occurrenceJson(version = 3, status = "confirmed", transactionId = "other")))
        val row = env.occurrenceDao.get("o1")!!
        assertEquals("confirmed", row.status)
        assertEquals("already_confirmed", row.localNotice)
        assertEquals(listOf<Pair<String, String?>>("o1" to "other"), env.dropped.calls)
        env.occurrences.clearNotice("o1")
        assertNull(env.occurrenceDao.get("o1")!!.localNotice)
    }

    @Test
    fun ourOwnAnswerNeverSetsTheNoticeOrDropsAnything() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        env.seedOccurrence()
        env.occurrenceHandler.applySentAnswer(json(occurrenceJson(version = 2, status = "confirmed", transactionId = "tx-1")))
        assertNull(env.occurrenceDao.get("o1")!!.localNotice)
        assertEquals(emptyList(), env.dropped.calls)
    }

    @Test
    fun discardingAChangeDropsThePhoneOnlyTransactionOfThatOccurrence() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        env.occurrenceHandler.onDiscarded("o1")
        assertEquals(listOf<Pair<String, String?>>("o1" to null), env.dropped.calls)
    }

    @Test
    fun refusalFieldsMapEveryPointerIncludingTheTransactionIdAsStatus() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        val problem = ProblemDetails(
            type = "t", title = "x", status = 422, code = "VALIDATION_FAILED",
            errors = listOf("/status", "/date", "/amount_minor", "/category_id", "/transaction_id", "/description", "/memo", "/counterparty", "/timezone")
                .map { ProblemError(pointer = it, code = "INVALID_ENUM_VALUE") },
        )
        assertEquals(
            setOf("status", "date", "amount", "category", "note", "details", "person"),
            env.occurrenceHandler.refusalFields(problem).keys,
        )
    }
}
