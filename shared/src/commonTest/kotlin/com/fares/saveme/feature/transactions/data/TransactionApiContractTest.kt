package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.Page
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.dto.TransactionDto
import com.fares.saveme.feature.transactions.data.dto.TransactionWriteRequest
import com.fares.saveme.feature.transactions.transactionBody
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.transactions.transactionPage
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every call and handled code of contracts/transactions-api-client.md (P-14.3). */
class TransactionApiContractTest {

    private fun env(scope: CoroutineScope, handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        TransactionTestEnv(scope, handler = handler)

    private fun write(version: Long = 0, kind: String = "expense") = TransactionWriteRequest(
        kind = kind, date = "2026-10-01", amountMinor = 4_550, currency = "EGP", categoryId = "c1", counterWalletId = null,
        description = null, memo = null, counterparty = null, cleared = false, version = version,
    )

    @Test
    fun putSendsExactlyTheElevenWritableMembersWithExplicitNulls() = runTest {
        val env = env(backgroundScope) { jsonResponse(201, transactionBody()) }
        env.signedIn()
        assertIs<ApiResult.Success<*>>(env.api.put("w1", "t1", write()))
        val request = env.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/transactions/t1"))
        val body = request.jsonBody()
        assertEquals(
            setOf("kind", "date", "amount_minor", "currency", "category_id", "counter_wallet_id", "description", "memo", "counterparty", "cleared", "version"),
            body.keys,
        )
        listOf("counter_wallet_id", "description", "memo", "counterparty").forEach { assertEquals("null", body[it].toString(), it) }
        assertEquals("4550", body["amount_minor"].toString())
        assertEquals("0", body["version"].toString())
        listOf("wallet_id", "created_by", "recurring_rule_id", "occurrence_id", "deleted_at").forEach { assertFalse(it in body.keys, it) }
    }

    @Test
    fun restoreSendsDeletedAtNullAndTheVersion() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, transactionBody()) }
        env.signedIn()
        assertIs<ApiResult.Success<*>>(env.api.restore("w1", "t1", 4))
        val request = env.requests.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals(setOf("deleted_at", "version"), request.jsonBody().keys)
        assertEquals("null", request.jsonBody()["deleted_at"].toString())
        assertEquals("4", request.jsonBody()["version"].toString())
    }

    @Test
    fun deleteCarriesAJsonBodyWithTheVersionAndReturnsTheTombstone() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, transactionBody(transactionJson(version = 6, deletedAt = "2026-10-02T10:00:00Z"))) }
        env.signedIn()
        val dto = assertIs<ApiResult.Success<TransactionDto>>(env.api.delete("w1", "t1", 5)).value
        assertEquals("2026-10-02T10:00:00Z", dto.deletedAt)
        val request = env.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals(setOf("version"), request.jsonBody().keys)
        assertEquals("5", request.jsonBody()["version"].toString())
        assertTrue(request.body.contentType.toString().startsWith("application/json"), "${request.body.contentType}")
    }

    @Test
    fun responsesIgnoreUnknownMembersAndKeepTheRecurringLinks() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, transactionBody(transactionJson(recurringRuleId = "r1", occurrenceId = "o1", memo = "details"))) }
        env.signedIn()
        val dto = assertIs<ApiResult.Success<TransactionDto>>(env.api.put("w1", "t1", write(1))).value
        assertEquals("r1", dto.recurringRuleId)
        assertEquals("o1", dto.occurrenceId)
        assertEquals("details", dto.memo)
        assertEquals("u1", dto.createdBy)
    }

    @Test
    fun aVersionConflictCarriesTheLiveOrDeletedCurrentCopy() = runTest {
        var deleted = false
        val env = env(backgroundScope) {
            val current = if (deleted) transactionJson(version = 6, deletedAt = "2026-10-01T00:00:00Z") else transactionJson(version = 6, description = "Other")
            jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":$current"""))
        }
        env.signedIn()
        val live = assertIs<ApiResult.Failure>(env.api.put("w1", "t1", write(3)))
        assertEquals(ApiErrorCode.VERSION_CONFLICT, live.code)
        assertNotNull(live.problem.current)
        deleted = true
        val tombstone = assertIs<ApiResult.Failure>(env.api.restore("w1", "t1", 3))
        assertTrue("\"deleted_at\":\"2026" in tombstone.problem.current.toString())
    }

    @Test
    fun everyFieldCodeKeepsItsPointer() = runTest {
        val errors = listOf(
            "/amount_minor" to "OUT_OF_RANGE", "/amount_minor" to "REQUIRED", "/category_id" to "INVALID_ENUM_VALUE",
            "/counter_wallet_id" to "INVALID_ENUM_VALUE", "/currency" to "CURRENCY_MISMATCH", "/counter_wallet_id" to "CURRENCY_MISMATCH",
            "/kind" to "IMMUTABLE_FIELD", "/counter_wallet_id" to "IMMUTABLE_FIELD", "/recurring_rule_id" to "UNKNOWN_FIELD",
            "/date" to "DATE_NOT_CALENDAR_DATE",
        )
        val body = errors.joinToString(",") { (p, c) -> """{"pointer":"$p","code":"$c"}""" }
        val env = env(backgroundScope) { jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[$body]""")) }
        env.signedIn()
        val failure = assertIs<ApiResult.Failure>(env.api.put("w1", "t1", write()))
        assertEquals(ApiErrorCode.VALIDATION_FAILED, failure.code)
        assertEquals(errors, failure.problem.errors!!.map { it.pointer to it.code })
        val invalidId = env(backgroundScope) { jsonResponse(422, problemBody(422, "INVALID_ID")) }
        invalidId.signedIn()
        val invalid = assertIs<ApiResult.Failure>(invalidId.api.delete("w1", "nope", 1))
        assertEquals("INVALID_ID", invalid.problem.code)
    }

    @Test
    fun anUnverifiedAddressPublishesTheEventForbiddenAndNotFoundAreReported() = runTest {
        var status = 403
        var code = "EMAIL_NOT_VERIFIED"
        val env = env(backgroundScope) { jsonResponse(status, problemBody(status, code)) }
        env.signedIn()
        val events = mutableListOf<ApiEvent>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        env.auth.events.events.onEach { events += it }.launchIn(scope)
        assertEquals(ApiErrorCode.EMAIL_NOT_VERIFIED, assertIs<ApiResult.Failure>(env.api.put("w1", "t1", write())).code)
        assertTrue(events.any { it is ApiEvent.EmailNotVerified }, "$events")
        code = "FORBIDDEN"
        assertEquals(ApiErrorCode.FORBIDDEN, assertIs<ApiResult.Failure>(env.api.put("w1", "t1", write())).code)
        status = 404
        code = "NOT_FOUND"
        assertEquals(ApiErrorCode.NOT_FOUND, assertIs<ApiResult.Failure>(env.api.delete("w1", "t1", 1)).code)
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun aBusyServerAnswersWithRetryAfterAndOfflineIsReported() = runTest {
        val busy = env(backgroundScope) {
            jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE", ""","retry_after_seconds":30"""), HttpHeaders.RetryAfter to "30")
        }
        busy.signedIn()
        assertEquals(30L, assertIs<ApiResult.Failure>(busy.api.put("w1", "t1", write())).retryAfterSeconds)
        val offline = env(backgroundScope) { throw kotlinx.io.IOException("offline") }
        offline.signedIn()
        assertEquals(ApiResult.Offline, offline.api.put("w1", "t1", write()))
    }

    @Test
    fun anExpiredSessionEndsTheSession() = runTest {
        val env = env(backgroundScope) { jsonResponse(401, problemBody(401, "UNAUTHENTICATED")) }
        env.signedIn()
        val events = mutableListOf<ApiEvent>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        env.auth.events.events.onEach { events += it }.launchIn(scope)
        assertEquals(ApiErrorCode.UNAUTHENTICATED, assertIs<ApiResult.Failure>(env.api.put("w1", "t1", write())).code)
        assertTrue(events.any { it is ApiEvent.SessionEnded }, "$events")
        assertNull(null)
        scope.coroutineContext[Job]?.cancel()
    }
}
