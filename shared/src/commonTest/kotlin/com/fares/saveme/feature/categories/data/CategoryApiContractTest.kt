package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.categories.CategoryTestEnv
import com.fares.saveme.feature.categories.categoryBody
import com.fares.saveme.feature.categories.categoryJson
import com.fares.saveme.feature.categories.categoryPage
import com.fares.saveme.feature.categories.data.dto.CategoryWriteRequest
import com.fares.saveme.feature.categories.platformJson
import com.fares.saveme.feature.categories.referenceJson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every call and handled code of contracts/categories-api-client.md (P-14.3). */
class CategoryApiContractTest {

    private fun env(scope: kotlinx.coroutines.CoroutineScope, handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        CategoryTestEnv(scope, handler = handler)

    @Test
    fun putSendsExactlyTheSchemaMembersWithExplicitNulls() = runTest {
        val env = env(backgroundScope) { jsonResponse(201, categoryBody(categoryJson(version = 1))) }
        env.signedIn()
        assertIs<ApiResult.Success<*>>(env.api.put("c1", CategoryWriteRequest("expense", "Coffee", null, null, 0)))
        val request = env.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertTrue(request.url.encodedPath.endsWith("/categories/c1"))
        val body = request.jsonBody()
        assertEquals(setOf("kind", "name", "icon", "color", "version"), body.keys)
        assertEquals("null", body["icon"].toString())
        assertEquals("null", body["color"].toString())
        assertEquals("0", body["version"].toString())
    }

    @Test
    fun archiveAndDeleteCarryOnlyTheVersionAndDeleteSendsABodyWithAJsonContentType() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, categoryBody()) }
        env.signedIn()
        env.api.archive("c1", 4)
        env.api.delete("c1", 5)
        val (archive, delete) = env.requests
        assertEquals(HttpMethod.Post, archive.method)
        assertTrue(archive.url.encodedPath.endsWith("/categories/c1/archive"))
        assertEquals(setOf("version"), archive.jsonBody().keys)
        assertEquals("4", archive.jsonBody()["version"].toString())
        assertEquals(HttpMethod.Delete, delete.method)
        assertEquals("5", delete.jsonBody()["version"].toString())
        assertTrue(delete.body.contentType.toString().startsWith("application/json"), "${delete.body.contentType}")
    }

    @Test
    fun theDefaultSetIsReadWithoutExtraQuery() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, categoryPage(listOf(platformJson()), limit = 26)) }
        env.signedIn()
        val defaults = assertIs<ApiResult.Success<com.fares.saveme.core.network.Page<com.fares.saveme.feature.categories.data.dto.PlatformCategoryDto>>>(env.api.listDefaults())
        assertEquals("بقالة", defaults.value.items.single().names.ar)
        assertEquals(emptySet<String>(), env.requests[0].url.parameters.names())
    }

    @Test
    fun aResponseWithAnUnknownMemberStillDecodes() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, categoryBody(categoryJson(name = "Café", icon = "restaurant", color = "#1F7F8C"))) }
        env.signedIn()
        val dto = assertIs<ApiResult.Success<com.fares.saveme.feature.categories.data.dto.CategoryDto>>(env.api.put("c1", CategoryWriteRequest("expense", "Café", null, null, 1))).value
        assertEquals("Café", dto.name)
        assertEquals("restaurant", dto.icon)
    }

    @Test
    fun aVersionConflictCarriesTheLiveOrDeletedCurrentCopy() = runTest {
        var deleted = false
        val env = env(backgroundScope) {
            val current = if (deleted) categoryJson(version = 6, deletedAt = "2026-10-01T00:00:00Z") else categoryJson(version = 6, name = "Other")
            jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":$current"""))
        }
        env.signedIn()
        val live = assertIs<ApiResult.Failure>(env.api.put("c1", CategoryWriteRequest("expense", "Coffee", null, null, 3)))
        assertEquals(ApiErrorCode.VERSION_CONFLICT, live.code)
        assertNotNull(live.problem.current)
        deleted = true
        val tombstone = assertIs<ApiResult.Failure>(env.api.delete("c1", 3))
        assertTrue("deleted_at\":\"2026" in tombstone.problem.current.toString())
    }

    @Test
    fun aCategoryInUseConflictHasItsOwnCodeAndNoCurrentCopy() = runTest {
        val env = env(backgroundScope) { jsonResponse(409, problemBody(409, "CATEGORY_IN_USE")) }
        env.signedIn()
        val failure = assertIs<ApiResult.Failure>(env.api.delete("c1", 3))
        assertEquals(ApiErrorCode.CATEGORY_IN_USE, failure.code)
        assertNull(failure.problem.current)
    }

    @Test
    fun fieldRefusalsAndInvalidIdsKeepTheirPointersAndCodes() = runTest {
        val env = env(backgroundScope) {
            jsonResponse(
                422,
                problemBody(
                    422, "VALIDATION_FAILED",
                    ""","errors":[{"pointer":"/name","code":"OUT_OF_RANGE"},{"pointer":"/kind","code":"IMMUTABLE_FIELD"},{"pointer":"/kind","code":"INVALID_ENUM_VALUE"},{"pointer":"/parent_id","code":"UNKNOWN_FIELD"}]""",
                ),
            )
        }
        env.signedIn()
        val failure = assertIs<ApiResult.Failure>(env.api.put("c1", CategoryWriteRequest("expense", "x", null, null, 0)))
        assertEquals(
            listOf("/name" to "OUT_OF_RANGE", "/kind" to "IMMUTABLE_FIELD", "/kind" to "INVALID_ENUM_VALUE", "/parent_id" to "UNKNOWN_FIELD"),
            failure.problem.errors!!.map { it.pointer to it.code },
        )
        val invalidId = CategoryTestEnv(backgroundScope) { jsonResponse(422, problemBody(422, "INVALID_ID")) }
        invalidId.signedIn()
        val invalid = assertIs<ApiResult.Failure>(invalidId.api.archive("nope", 1))
        assertEquals("INVALID_ID", invalid.problem.code)
        assertEquals(ApiErrorCode.UNKNOWN, invalid.code)
    }

    @Test
    fun anUnverifiedAddressPublishesTheEventAndNotFoundIsReported() = runTest {
        var status = 403
        val env = env(backgroundScope) {
            if (status == 403) jsonResponse(403, problemBody(403, "EMAIL_NOT_VERIFIED")) else jsonResponse(404, problemBody(404, "NOT_FOUND"))
        }
        env.signedIn()
        val events = mutableListOf<ApiEvent>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        scope.launchCollect(env.auth.events.events, events)
        val forbidden = assertIs<ApiResult.Failure>(env.api.put("c1", CategoryWriteRequest("expense", "x", null, null, 0)))
        assertEquals(ApiErrorCode.EMAIL_NOT_VERIFIED, forbidden.code)
        assertTrue(events.any { it is ApiEvent.EmailNotVerified }, "$events")
        status = 404
        assertEquals(ApiErrorCode.NOT_FOUND, assertIs<ApiResult.Failure>(env.api.archive("c1", 1)).code)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun aBusyServerAnswersWithRetryAfterAndOfflineIsReported() = runTest {
        val busy = env(backgroundScope) { jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE", ""","retry_after_seconds":30"""), HttpHeaders.RetryAfter to "30") }
        busy.signedIn()
        val failure = assertIs<ApiResult.Failure>(busy.api.put("c1", CategoryWriteRequest("expense", "x", null, null, 0)))
        assertEquals(30L, failure.retryAfterSeconds)
        val offline = CategoryTestEnv(backgroundScope) { throw kotlinx.io.IOException("offline") }
        offline.signedIn()
        assertEquals(ApiResult.Offline, offline.api.put("c1", CategoryWriteRequest("expense", "x", null, null, 0)))
    }

    @Test
    fun anExpiredSessionEndsTheSession() = runTest {
        val env = env(backgroundScope) { jsonResponse(401, problemBody(401, "UNAUTHENTICATED")) }
        env.signedIn()
        val events = mutableListOf<ApiEvent>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        scope.launchCollect(env.auth.events.events, events)
        assertEquals(ApiErrorCode.UNAUTHENTICATED, assertIs<ApiResult.Failure>(env.api.put("c1", CategoryWriteRequest("expense", "x", null, null, 0))).code)
        assertTrue(events.any { it is ApiEvent.SessionEnded }, "$events")
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}

private fun <T> kotlinx.coroutines.CoroutineScope.launchCollect(flow: kotlinx.coroutines.flow.Flow<T>, into: MutableList<T>) {
    flow.onEach { into += it }.launchIn(this)
}
