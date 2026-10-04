package com.fares.saveme.feature.budgets.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.Page
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.budgets.BudgetTestEnv
import com.fares.saveme.feature.budgets.budgetBody
import com.fares.saveme.feature.budgets.budgetJson
import com.fares.saveme.feature.budgets.data.dto.BudgetDto
import com.fares.saveme.feature.budgets.data.dto.BudgetProgressDto
import com.fares.saveme.feature.budgets.data.dto.BudgetWriteRequest
import com.fares.saveme.feature.budgets.progressBody
import com.fares.saveme.feature.budgets.progressPage
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every call and handled code of contracts/budgets-api-client.md, with the examples of budgets.yaml v1.8 (P-14.3). */
class BudgetApiContractTest {

    private val groceries = "0192f7e0-0000-7000-8000-0000000000b1"

    /** budgets.yaml `currentPeriod`. */
    private val currentPeriod = """
        {"budget_id":"$groceries","period":{"start":"2026-09-25","end":"2026-10-24","days_in_period":30,"days_elapsed":8},"is_current":true,
         "limit":{"amount_minor":600000,"currency":"EGP"},"spent":{"amount_minor":350000,"currency":"EGP"},
         "remaining":{"amount_minor":250000,"currency":"EGP"},"projected_end":{"amount_minor":1312500,"currency":"EGP"},
         "over_limit":false,"other_currencies":[]}
    """.trimIndent()

    /** budgets.yaml `pastPeriodOver`. */
    private val pastPeriodOver = """
        {"budget_id":"$groceries","period":{"start":"2026-08-25","end":"2026-09-24","days_in_period":31,"days_elapsed":null},"is_current":false,
         "limit":{"amount_minor":600000,"currency":"EGP"},"spent":{"amount_minor":650000,"currency":"EGP"},
         "remaining":{"amount_minor":-50000,"currency":"EGP"},"projected_end":null,"over_limit":true,"other_currencies":["USD"]}
    """.trimIndent()

    private fun env(scope: kotlinx.coroutines.CoroutineScope, handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        BudgetTestEnv(scope, handler)

    @Test
    fun putSendsExactlyTheSchemaMembersWithAnExplicitNullForAllExpenses() = runTest {
        val env = env(backgroundScope) { jsonResponse(201, budgetBody(budgetJson(categoryId = null, version = 1))) }
        env.signedIn()
        assertIs<ApiResult.Success<*>>(env.api.put("b1", BudgetWriteRequest(null, "monthly", 600_000, "EGP", 0)))
        val request = env.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertTrue(request.url.encodedPath.endsWith("/budgets/b1"))
        val body = request.jsonBody()
        assertEquals(setOf("category_id", "period_type", "amount_minor", "currency", "version"), body.keys)
        assertEquals("null", body["category_id"].toString())
        assertEquals("\"monthly\"", body["period_type"].toString())
        assertEquals("600000", body["amount_minor"].toString())
        assertEquals("\"EGP\"", body["currency"].toString())
        assertEquals("0", body["version"].toString())
    }

    @Test
    fun putWithACategoryAndASaveCarryTheCategoryAndTheLastSeenVersion() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, budgetBody(budgetJson(version = 4))) }
        env.signedIn()
        val saved = assertIs<ApiResult.Success<BudgetDto>>(env.api.put("b1", BudgetWriteRequest("c1", "monthly", 700_000, "EGP", 3)))
        assertEquals(4L, saved.value.version)
        val body = env.requests.single().jsonBody()
        assertEquals("\"c1\"", body["category_id"].toString())
        assertEquals("3", body["version"].toString())
        assertTrue("user_id" !in body && "archived_at" !in body && "id" !in body, "read-only members are never sent (FR-008)")
    }

    @Test
    fun deleteAndArchiveCarryOnlyTheVersionAndDeleteSendsAJsonBody() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, budgetBody()) }
        env.signedIn()
        env.api.delete("b1", 5)
        env.api.archive("b1", 6)
        val (delete, archive) = env.requests
        assertEquals(HttpMethod.Delete, delete.method)
        assertTrue(delete.url.encodedPath.endsWith("/budgets/b1"))
        assertEquals(setOf("version"), delete.jsonBody().keys)
        assertEquals("5", delete.jsonBody()["version"].toString())
        assertTrue(delete.body.contentType.toString().startsWith("application/json"), "${delete.body.contentType}")
        assertEquals(HttpMethod.Post, archive.method)
        assertTrue(archive.url.encodedPath.endsWith("/budgets/b1/archive"))
        assertEquals(setOf("version"), archive.jsonBody().keys)
        assertEquals("6", archive.jsonBody()["version"].toString())
    }

    @Test
    fun theProgressListAsksForTodaysPeriodOnlyAndFollowsTheCursor() = runTest {
        val env = env(backgroundScope) { request ->
            if (request.url.parameters["cursor"] == null) jsonResponse(200, progressPage(listOf(currentPeriod), nextCursor = "next-1", limit = 1))
            else jsonResponse(200, progressPage(emptyList()))
        }
        env.signedIn()
        val first = assertIs<ApiResult.Success<Page<BudgetProgressDto>>>(env.api.progressPage(null)).value
        assertEquals("next-1", first.nextCursor)
        assertTrue(first.hasMore)
        val second = assertIs<ApiResult.Success<Page<BudgetProgressDto>>>(env.api.progressPage("next-1")).value
        assertTrue(!second.hasMore && second.items.isEmpty())
        val (one, two) = env.requests
        assertTrue(one.url.encodedPath.endsWith("/budget-progress"))
        assertEquals(setOf("limit"), one.url.parameters.names(), "no date, filter or sort (FR-011)")
        assertEquals("200", one.url.parameters["limit"])
        assertEquals(setOf("limit", "cursor"), two.url.parameters.names())
        assertEquals("next-1", two.url.parameters["cursor"])
    }

    @Test
    fun theCurrentAndThePastPeriodExamplesDecodeWithNullsAndAnUnknownMemberIgnored() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, progressPage(listOf(currentPeriod, pastPeriodOver.replace("\"over_limit\"", "\"an_unknown_member\":1,\"over_limit\"")))) }
        env.signedIn()
        val items = assertIs<ApiResult.Success<Page<BudgetProgressDto>>>(env.api.progressPage(null)).value.items
        val current = items[0]
        assertEquals(8, current.period.daysElapsed)
        assertEquals(350_000, current.spent.amountMinor)
        assertEquals(1_312_500, current.projectedEnd!!.amountMinor)
        assertEquals(emptyList(), current.otherCurrencies)
        val past = items[1]
        assertNull(past.period.daysElapsed)
        assertNull(past.projectedEnd)
        assertEquals(true, past.overLimit)
        assertEquals(-50_000, past.remaining!!.amountMinor)
        assertEquals(listOf("USD"), past.otherCurrencies)
    }

    @Test
    fun theProgressOfOneBudgetIsReadWithoutAQueryAndANotFoundIsReported() = runTest {
        var found = true
        val env = env(backgroundScope) { if (found) jsonResponse(200, progressBody(currentPeriod)) else jsonResponse(404, problemBody(404, "NOT_FOUND")) }
        env.signedIn()
        val ok = assertIs<ApiResult.Success<BudgetProgressDto>>(env.api.progressOne("b1"))
        assertEquals(groceries, ok.value.budgetId)
        assertTrue(env.requests[0].url.encodedPath.endsWith("/budgets/b1/progress"))
        assertEquals(emptySet<String>(), env.requests[0].url.parameters.names())
        found = false
        val failure = assertIs<ApiResult.Failure>(env.api.progressOne("b1"))
        assertEquals(ApiErrorCode.NOT_FOUND, failure.code)
    }

    @Test
    fun aResponseWithAnUnknownMemberAndAnUnknownPeriodTypeStillDecodes() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, budgetBody(budgetJson(periodType = "weekly"))) }
        env.signedIn()
        val dto = assertIs<ApiResult.Success<BudgetDto>>(env.api.put("b1", BudgetWriteRequest("c1", "monthly", 1, "EGP", 1))).value
        assertEquals("weekly", dto.periodType)
    }

    @Test
    fun aVersionConflictCarriesTheLiveOrDeletedCurrentCopy() = runTest {
        var deleted = false
        val env = env(backgroundScope) {
            val current = if (deleted) budgetJson(version = 6, deletedAt = "2026-10-01T00:00:00Z") else budgetJson(version = 6, amount = 900_000)
            jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":$current"""))
        }
        env.signedIn()
        val live = assertIs<ApiResult.Failure>(env.api.put("b1", BudgetWriteRequest("c1", "monthly", 700_000, "EGP", 3)))
        assertEquals(ApiErrorCode.VERSION_CONFLICT, live.code)
        assertNotNull(live.problem.current)
        deleted = true
        val tombstone = assertIs<ApiResult.Failure>(env.api.archive("b1", 3))
        assertTrue("deleted_at\":\"2026" in tombstone.problem.current.toString())
        assertIs<ApiResult.Failure>(env.api.delete("b1", 3))
    }

    @Test
    fun everyValidationCodeKeepsItsPointerAndCode() = runTest {
        val env = env(backgroundScope) {
            jsonResponse(
                422,
                problemBody(
                    422, "VALIDATION_FAILED",
                    ""","errors":[{"pointer":"/category_id","code":"INVALID_ENUM_VALUE"},{"pointer":"/amount_minor","code":"OUT_OF_RANGE"},{"pointer":"/amount_minor","code":"AMOUNT_NOT_INTEGER"},{"pointer":"/currency","code":"INVALID_CURRENCY"},{"pointer":"/period_type","code":"IMMUTABLE_FIELD"},{"pointer":"/category_id","code":"REQUIRED"},{"pointer":"/rollover","code":"UNKNOWN_FIELD"},{"pointer":"/version","code":"INVALID_TYPE"}]""",
                ),
            )
        }
        env.signedIn()
        val failure = assertIs<ApiResult.Failure>(env.api.put("b1", BudgetWriteRequest("c1", "monthly", 0, "EGP", 0)))
        assertEquals(
            listOf(
                "/category_id" to "INVALID_ENUM_VALUE", "/amount_minor" to "OUT_OF_RANGE", "/amount_minor" to "AMOUNT_NOT_INTEGER",
                "/currency" to "INVALID_CURRENCY", "/period_type" to "IMMUTABLE_FIELD", "/category_id" to "REQUIRED",
                "/rollover" to "UNKNOWN_FIELD", "/version" to "INVALID_TYPE",
            ),
            failure.problem.errors!!.map { it.pointer to it.code },
        )
        val invalidId = BudgetTestEnv(backgroundScope) { jsonResponse(422, problemBody(422, "INVALID_ID")) }
        invalidId.signedIn()
        assertEquals("INVALID_ID", assertIs<ApiResult.Failure>(invalidId.api.archive("nope", 1)).problem.code)
    }

    @Test
    fun anUnverifiedAddressAndANotFoundAreReportedByCode() = runTest {
        var status = 403
        val env = env(backgroundScope) {
            if (status == 403) jsonResponse(403, problemBody(403, "EMAIL_NOT_VERIFIED")) else jsonResponse(404, problemBody(404, "NOT_FOUND"))
        }
        env.signedIn()
        assertEquals(ApiErrorCode.EMAIL_NOT_VERIFIED, assertIs<ApiResult.Failure>(env.api.put("b1", BudgetWriteRequest(null, "monthly", 1, "EGP", 0))).code)
        status = 404
        assertEquals(ApiErrorCode.NOT_FOUND, assertIs<ApiResult.Failure>(env.api.delete("b1", 1)).code)
    }

    @Test
    fun anInvalidCursorIsReportedSoTheWalkCanRestart() = runTest {
        val env = env(backgroundScope) { jsonResponse(400, problemBody(400, "INVALID_CURSOR")) }
        env.signedIn()
        assertEquals(ApiErrorCode.INVALID_CURSOR, assertIs<ApiResult.Failure>(env.api.progressPage("stale")).code)
    }

    @Test
    fun retryClassesFollowTheCatalogue() = runTest {
        val env = env(backgroundScope) { jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE")) }
        env.signedIn()
        val failure = assertIs<ApiResult.Failure>(env.api.progressOne("b1"))
        assertEquals(ApiErrorCode.SERVICE_UNAVAILABLE, failure.code)
    }
}
