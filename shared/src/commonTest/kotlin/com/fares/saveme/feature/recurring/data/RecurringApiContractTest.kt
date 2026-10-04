package com.fares.saveme.feature.recurring.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.recurring.RecurringTestEnv
import com.fares.saveme.feature.recurring.data.dto.OccurrenceDto
import com.fares.saveme.feature.recurring.data.dto.OccurrenceWriteRequest
import com.fares.saveme.feature.recurring.data.dto.RecurringRuleDto
import com.fares.saveme.feature.recurring.data.dto.RecurringRuleWriteRequest
import com.fares.saveme.feature.recurring.occurrenceBody
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleBody
import com.fares.saveme.feature.recurring.ruleJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
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

/** Every call and handled code of contracts/recurring-api-client.md (P-14.3), from the recurring.yaml examples. */
class RecurringApiContractTest {

    private fun env(scope: CoroutineScope, handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        RecurringTestEnv(scope, handler)

    private val ruleMembers = setOf(
        "preset", "status", "kind", "amount_minor", "amount_basis", "currency", "category_id", "counter_wallet_id", "description",
        "memo", "counterparty", "auto_confirm", "frequency", "interval", "anchor_day", "start_date", "end_date", "occurrence_count",
        "payout_round", "payout_category_id", "version",
    )

    private val occurrenceMembers = setOf(
        "status", "date", "amount_minor", "category_id", "description", "memo", "counterparty", "transaction_id", "version",
    )

    private fun rent(version: Long = 0, endDate: String? = null, status: String = "active") = RecurringRuleWriteRequest(
        preset = "custom", status = status, kind = "expense", amountMinor = 450_000, amountBasis = "per_occurrence", currency = "EGP",
        categoryId = "0192f7a1-0000-7000-8000-0000000000c5", counterWalletId = null, description = "Rent", memo = null, counterparty = null,
        autoConfirm = true, frequency = "monthly", interval = 1, anchorDay = 1, startDate = "2026-11-01", endDate = endDate,
        occurrenceCount = null, payoutRound = null, payoutCategoryId = null, version = version,
    )

    private val installment = RecurringRuleWriteRequest(
        preset = "installment", status = "active", kind = "expense", amountMinor = 1_000_000, amountBasis = "total", currency = "EGP",
        categoryId = "0192f7a1-0000-7000-8000-0000000000c8", counterWalletId = null, description = "Fridge", memo = null,
        counterparty = "B.TECH", autoConfirm = true, frequency = "monthly", interval = 1, anchorDay = 25, startDate = "2026-10-25",
        endDate = null, occurrenceCount = 12, payoutRound = null, payoutCategoryId = null, version = 0,
    )

    private fun confirm(version: Long = 1) = OccurrenceWriteRequest(
        status = "confirmed", date = "2026-11-03", amountMinor = 61_200, categoryId = "0192f7a1-0000-7000-8000-0000000000c9",
        description = "Electricity", memo = null, counterparty = null, transactionId = "0192f7e0-0000-7000-8000-0000000000f1", version = version,
    )

    @Test
    fun createRentSendsExactlyTheTwentyOneWritableMembersWithExplicitNulls() = runTest {
        val env = env(backgroundScope) { jsonResponse(201, ruleBody()) }
        env.signedIn()
        assertIs<ApiResult.Success<*>>(env.api.putRule("w1", "r1", rent()))
        val request = env.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/recurring-rules/r1"))
        val body = request.jsonBody()
        assertEquals(ruleMembers, body.keys)
        listOf("counter_wallet_id", "memo", "counterparty", "end_date", "occurrence_count", "payout_round", "payout_category_id").forEach {
            assertEquals("null", body[it].toString(), it)
        }
        assertEquals("450000", body["amount_minor"].toString())
        assertEquals("0", body["version"].toString())
        assertEquals("true", body["auto_confirm"].toString())
        listOf("timezone", "created_by", "wallet_id", "deleted_at", "change_seq", "created_at", "updated_at").forEach { assertFalse(it in body.keys, it) }
    }

    @Test
    fun installmentTotalAndTheEndBeforeRaiseSplitCarryTheirMembers() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, ruleBody()) }
        env.signedIn()
        env.api.putRule("w1", "r2", installment)
        val total = env.requests[0].jsonBody()
        assertEquals(ruleMembers, total.keys)
        assertEquals("\"total\"", total["amount_basis"].toString())
        assertEquals("12", total["occurrence_count"].toString())
        assertEquals("\"B.TECH\"", total["counterparty"].toString())
        env.api.putRule("w1", "r1", rent(version = 1, endDate = "2026-12-31"))
        val end = env.requests[1].jsonBody()
        assertEquals("\"2026-12-31\"", end["end_date"].toString())
        assertEquals("1", end["version"].toString())
    }

    @Test
    fun responsesIgnoreUnknownMembersAndKeepUnknownEnumStrings() = runTest {
        val env = env(backgroundScope) { jsonResponse(201, ruleBody(ruleJson(preset = "payroll", frequency = "fortnightly", status = "frozen"))) }
        env.signedIn()
        val dto = assertIs<ApiResult.Success<RecurringRuleDto>>(env.api.putRule("w1", "r1", rent())).value
        assertEquals("payroll", dto.preset)
        assertEquals("fortnightly", dto.frequency)
        assertEquals("frozen", dto.status)
        assertEquals("Africa/Cairo", dto.timezone)
        assertEquals(450_000L, dto.amountMinor)
    }

    @Test
    fun aRuleVersionConflictCarriesTheLiveOrTombstonedCurrentCopy() = runTest {
        var deleted = false
        val env = env(backgroundScope) {
            val current = if (deleted) ruleJson(version = 4, deletedAt = "2026-10-01T00:00:00Z") else ruleJson(version = 4, description = "Other")
            jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":$current"""))
        }
        env.signedIn()
        val live = assertIs<ApiResult.Failure>(env.api.putRule("w1", "r1", rent(2)))
        assertEquals(ApiErrorCode.VERSION_CONFLICT, live.code)
        assertNotNull(live.problem.current)
        deleted = true
        val tombstone = assertIs<ApiResult.Failure>(env.api.putRule("w1", "r1", rent(2)))
        assertTrue("\"deleted_at\":\"2026" in tombstone.problem.current.toString())
    }

    @Test
    fun everyRuleFieldCodeKeepsItsPointerAndAMalformedIdIsInvalidId() = runTest {
        val errors = listOf(
            "/preset" to "IMMUTABLE_FIELD", "/kind" to "IMMUTABLE_FIELD", "/currency" to "IMMUTABLE_FIELD", "/frequency" to "IMMUTABLE_FIELD",
            "/currency" to "CURRENCY_MISMATCH", "/counter_wallet_id" to "CURRENCY_MISMATCH", "/status" to "INVALID_ENUM_VALUE",
            "/category_id" to "REQUIRED", "/anchor_day" to "REQUIRED", "/end_date" to "OUT_OF_RANGE", "/occurrence_count" to "OUT_OF_RANGE",
            "/timezone" to "UNKNOWN_FIELD",
        )
        val body = errors.joinToString(",") { (p, c) -> """{"pointer":"$p","code":"$c"}""" }
        val env = env(backgroundScope) { jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[$body]""")) }
        env.signedIn()
        val failure = assertIs<ApiResult.Failure>(env.api.putRule("w1", "r1", rent()))
        assertEquals(ApiErrorCode.VALIDATION_FAILED, failure.code)
        assertEquals(errors, failure.problem.errors!!.map { it.pointer to it.code })
        val invalidId = env(backgroundScope) { jsonResponse(422, problemBody(422, "INVALID_ID")) }
        invalidId.signedIn()
        assertEquals("INVALID_ID", assertIs<ApiResult.Failure>(invalidId.api.putRule("w1", "nope", rent())).problem.code)
    }

    @Test
    fun deleteCarriesAJsonBodyWithTheVersionAndReturnsTheTombstone() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, ruleBody(ruleJson(version = 3, deletedAt = "2026-10-02T10:00:00Z"))) }
        env.signedIn()
        val dto = assertIs<ApiResult.Success<RecurringRuleDto>>(env.api.deleteRule("w1", "r1", 2)).value
        assertEquals("2026-10-02T10:00:00Z", dto.deletedAt)
        val request = env.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/recurring-rules/r1"))
        assertEquals(setOf("version"), request.jsonBody().keys)
        assertEquals("2", request.jsonBody()["version"].toString())
        assertTrue(request.body.contentType.toString().startsWith("application/json"), "${request.body.contentType}")
    }

    @Test
    fun ruleDeleteConflictsAreReported() = runTest {
        val env = env(backgroundScope) { jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${ruleJson(version = 5)}""")) }
        env.signedIn()
        assertEquals(ApiErrorCode.VERSION_CONFLICT, assertIs<ApiResult.Failure>(env.api.deleteRule("w1", "r1", 2)).code)
    }

    @Test
    fun confirmingWithTheActualAmountSendsTheNineWritableMembersAndTheClientsTransactionId() = runTest {
        val env = env(backgroundScope) {
            jsonResponse(200, occurrenceBody(occurrenceJson(version = 2, status = "confirmed", transactionId = "0192f7e0-0000-7000-8000-0000000000f1", edited = true)))
        }
        env.signedIn()
        val dto = assertIs<ApiResult.Success<OccurrenceDto>>(env.api.putOccurrence("w1", "o1", confirm())).value
        assertEquals("confirmed", dto.status)
        assertEquals("0192f7e0-0000-7000-8000-0000000000f1", dto.transactionId)
        val request = env.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/occurrences/o1"))
        val body = request.jsonBody()
        assertEquals(occurrenceMembers, body.keys)
        assertEquals("\"confirmed\"", body["status"].toString())
        assertEquals("61200", body["amount_minor"].toString())
        assertEquals("null", body["memo"].toString())
        listOf("wallet_id", "recurring_rule_id", "ordinal", "scheduled_date", "edited", "auto_confirm_refusal", "kind", "currency").forEach {
            assertFalse(it in body.keys, it)
        }
    }

    @Test
    fun skipAndUnskipSendANullTransactionId() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, occurrenceBody(occurrenceJson(status = "skipped", version = 2))) }
        env.signedIn()
        val skip = OccurrenceWriteRequest("skipped", "2026-08-01", 450_000, "c1", "Rent", null, null, null, 1)
        env.api.putOccurrence("w1", "o1", skip)
        env.api.putOccurrence("w1", "o1", skip.copy(status = "scheduled", version = 2))
        env.requests.forEach {
            assertEquals(occurrenceMembers, it.jsonBody().keys)
            assertEquals("null", it.jsonBody()["transaction_id"].toString())
        }
        assertEquals("\"skipped\"", env.requests[0].jsonBody()["status"].toString())
        assertEquals("\"scheduled\"", env.requests[1].jsonBody()["status"].toString())
    }

    @Test
    fun anOccurrenceConflictCarriesTheConfirmedScheduledOrTombstonedCurrentCopy() = runTest {
        var current = occurrenceJson(version = 3, status = "confirmed", transactionId = "other")
        val env = env(backgroundScope) { jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":$current""")) }
        env.signedIn()
        for (copy in listOf(
            occurrenceJson(version = 3, status = "confirmed", transactionId = "other"),
            occurrenceJson(version = 3, status = "scheduled"),
            occurrenceJson(version = 3, deletedAt = "2026-10-01T00:00:00Z"),
        )) {
            current = copy
            val failure = assertIs<ApiResult.Failure>(env.api.putOccurrence("w1", "o1", confirm()))
            assertEquals(ApiErrorCode.VERSION_CONFLICT, failure.code)
            assertNotNull(failure.problem.current)
        }
    }

    @Test
    fun occurrenceRefusalsKeepTheirPointers() = runTest {
        val errors = listOf(
            "/status" to "INVALID_ENUM_VALUE", "/status" to "CURRENCY_MISMATCH", "/transaction_id" to "REQUIRED",
            "/transaction_id" to "INVALID_ENUM_VALUE", "/category_id" to "INVALID_ENUM_VALUE", "/date" to "OUT_OF_RANGE",
            "/amount_minor" to "IMMUTABLE_FIELD",
        )
        val body = errors.joinToString(",") { (p, c) -> """{"pointer":"$p","code":"$c"}""" }
        val env = env(backgroundScope) { jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[$body]""")) }
        env.signedIn()
        val failure = assertIs<ApiResult.Failure>(env.api.putOccurrence("w1", "o1", confirm()))
        assertEquals(errors, failure.problem.errors!!.map { it.pointer to it.code })
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
        assertEquals(ApiErrorCode.EMAIL_NOT_VERIFIED, assertIs<ApiResult.Failure>(env.api.putRule("w1", "r1", rent())).code)
        assertTrue(events.any { it is ApiEvent.EmailNotVerified }, "$events")
        code = "FORBIDDEN"
        assertEquals(ApiErrorCode.FORBIDDEN, assertIs<ApiResult.Failure>(env.api.putOccurrence("w1", "o1", confirm())).code)
        assertEquals(ApiErrorCode.FORBIDDEN, assertIs<ApiResult.Failure>(env.api.materialise("w1", "r1")).code)
        status = 404
        code = "NOT_FOUND"
        assertEquals(ApiErrorCode.NOT_FOUND, assertIs<ApiResult.Failure>(env.api.putOccurrence("w1", "o1", confirm())).code)
        assertEquals(ApiErrorCode.NOT_FOUND, assertIs<ApiResult.Failure>(env.api.materialise("w1", "r1")).code)
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun materialiseIsAPostWithoutABodyAndAnswersTheRuleOrAConflict() = runTest {
        var status = 200
        val env = env(backgroundScope) {
            if (status == 200) jsonResponse(200, ruleBody()) else jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${ruleJson(version = 7, deletedAt = "2026-10-01T00:00:00Z")}"""))
        }
        env.signedIn()
        assertIs<ApiResult.Success<RecurringRuleDto>>(env.api.materialise("w1", "r1"))
        val request = env.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/recurring-rules/r1/materialise"))
        assertNull(request.body.contentType)
        status = 409
        assertEquals(ApiErrorCode.VERSION_CONFLICT, assertIs<ApiResult.Failure>(env.api.materialise("w1", "r1")).code)
    }

    @Test
    fun aBusyServerAnswersWithRetryAfterAndOfflineIsReported() = runTest {
        val busy = env(backgroundScope) {
            jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE", ""","retry_after_seconds":30"""), io.ktor.http.HttpHeaders.RetryAfter to "30")
        }
        busy.signedIn()
        assertEquals(30L, assertIs<ApiResult.Failure>(busy.api.putRule("w1", "r1", rent())).retryAfterSeconds)
        val offline = env(backgroundScope) { throw kotlinx.io.IOException("offline") }
        offline.signedIn()
        assertEquals(ApiResult.Offline, offline.api.putRule("w1", "r1", rent()))
        assertEquals(ApiResult.Offline, offline.api.materialise("w1", "r1"))
    }

    @Test
    fun anExpiredSessionEndsTheSession() = runTest {
        val env = env(backgroundScope) { jsonResponse(401, problemBody(401, "UNAUTHENTICATED")) }
        env.signedIn()
        val events = mutableListOf<ApiEvent>()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        env.auth.events.events.onEach { events += it }.launchIn(scope)
        assertEquals(ApiErrorCode.UNAUTHENTICATED, assertIs<ApiResult.Failure>(env.api.putRule("w1", "r1", rent())).code)
        assertTrue(events.any { it is ApiEvent.SessionEnded }, "$events")
        scope.coroutineContext[Job]?.cancel()
    }
}
