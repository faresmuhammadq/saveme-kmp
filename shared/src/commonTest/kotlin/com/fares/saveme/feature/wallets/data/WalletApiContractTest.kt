package com.fares.saveme.feature.wallets.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.wallets.WalletTestEnv
import com.fares.saveme.feature.wallets.data.dto.WalletDto
import com.fares.saveme.feature.wallets.data.dto.WalletWriteRequest
import com.fares.saveme.feature.wallets.memberJson
import com.fares.saveme.feature.wallets.pageOf
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Every call of contracts/wallets-api-client.md and every code it handles (P-14.3). */
class WalletApiContractTest {
    private val write = WalletWriteRequest("cash", "Pocket", "EGP", 25_000, null, null, version = 0)

    private suspend fun env(scope: kotlinx.coroutines.CoroutineScope, handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): WalletTestEnv =
        WalletTestEnv(scope, handler = handler).also { it.signedIn() }

    @Test
    fun putSendsExactlyTheWritableMembersWithExplicitNulls() = runTest {
        val env = env(backgroundScope) { jsonResponse(201, walletBody(walletJson(version = 1))) }
        val created = assertIs<ApiResult.Success<WalletDto>>(env.api.put("w1", write)).value
        assertEquals(1L, created.version)
        val request = env.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/wallets/w1"))
        val body = request.jsonBody()
        assertEquals(setOf("type", "name", "currency", "opening_balance_minor", "icon", "color", "version"), body.keys)
        assertEquals(JsonNull, body["icon"])
        assertEquals(JsonNull, body["color"])
        assertEquals("0", body["version"].toString())
        assertEquals("25000", body["opening_balance_minor"].toString())
    }

    @Test
    fun putSavesAndAnIdenticalReplayIsA200() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, walletBody(walletJson(version = 4))) }
        assertEquals(4L, assertIs<ApiResult.Success<WalletDto>>(env.api.put("w1", write.copy(version = 3))).value.version)
    }

    @Test
    fun aStaleVersionCarriesTheLiveOrTheDeletedCurrentCopy() = runTest {
        var answer = 0
        val env = env(backgroundScope) {
            val current = if (answer++ == 0) walletJson(version = 6, name = "Web") else walletJson(version = 7, deletedAt = "2026-10-01T00:00:00Z")
            jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":$current"""))
        }
        val live = assertIs<ApiResult.Failure>(env.api.put("w1", write.copy(version = 3)))
        assertEquals(ApiErrorCode.VERSION_CONFLICT, live.code)
        assertEquals("Web", AppJson.decodeFromJsonElement(WalletDto.serializer(), assertNotNull(live.problem.current)).name)
        val gone = assertIs<ApiResult.Failure>(env.api.put("w1", write.copy(version = 3)))
        assertNotNull(AppJson.decodeFromJsonElement(WalletDto.serializer(), assertNotNull(gone.problem.current)).deletedAt)
    }

    @Test
    fun validationErrorsCarryTheirPointersAndCodes() = runTest {
        val errors = listOf(
            "IMMUTABLE_FIELD" to "/currency", "IMMUTABLE_FIELD" to "/type", "OUT_OF_RANGE" to "/name", "INVALID_ENUM_VALUE" to "/user_id",
        )
        var answer = 0
        val env = env(backgroundScope) {
            val (code, pointer) = errors[answer++]
            jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"$pointer","code":"$code"}]"""))
        }
        errors.forEach { (code, pointer) ->
            val failure = assertIs<ApiResult.Failure>(env.api.put("w1", write))
            assertEquals(ApiErrorCode.VALIDATION_FAILED, failure.code)
            assertEquals(pointer, failure.problem.errors!!.single().pointer)
            assertEquals(code, failure.problem.errors!!.single().code)
        }
    }

    @Test
    fun forbiddenVariantsPublishTheirEvents() = runTest {
        var answer = 0
        val env = env(backgroundScope) {
            jsonResponse(403, problemBody(403, listOf("FORBIDDEN", "EMAIL_NOT_VERIFIED", "FEATURE_NOT_ENTITLED")[answer++]))
        }
        val events = mutableListOf<ApiEvent>()
        val job = collectEvents(env, events)
        assertEquals(ApiErrorCode.FORBIDDEN, assertIs<ApiResult.Failure>(env.api.put("w1", write)).code)
        assertEquals(ApiErrorCode.EMAIL_NOT_VERIFIED, assertIs<ApiResult.Failure>(env.api.put("w1", write)).code)
        assertEquals(ApiErrorCode.FEATURE_NOT_ENTITLED, assertIs<ApiResult.Failure>(env.api.put("w1", write)).code)
        job.cancel()
        assertEquals(listOf<ApiEvent>(ApiEvent.EmailNotVerified, ApiEvent.FeatureNotEntitled), events)
    }

    private fun kotlinx.coroutines.test.TestScope.collectEvents(env: WalletTestEnv, into: MutableList<ApiEvent>) =
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { env.auth.events.events.collect { into += it } }

    @Test
    fun serviceUnavailableKeepsRetryAfter() = runTest {
        val env = env(backgroundScope) { jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE", ""","retry_after_seconds":12""")) }
        val failure = assertIs<ApiResult.Failure>(env.api.put("w1", write))
        assertEquals(12L, failure.retryAfterSeconds)
        assertEquals(ApiErrorCode.SERVICE_UNAVAILABLE.retryClass, failure.code.retryClass)
    }

    @Test
    fun unauthenticatedEndsTheSession() = runTest {
        val env = env(backgroundScope) { jsonResponse(401, problemBody(401, "UNAUTHENTICATED")) }
        val events = mutableListOf<ApiEvent>()
        val job = collectEvents(env, events)
        env.api.put("w1", write)
        job.cancel()
        assertEquals(listOf<ApiEvent>(ApiEvent.SessionEnded), events)
    }

    @Test
    fun offlineIsOffline() = runTest {
        val env = env(backgroundScope) { throw kotlinx.io.IOException("no route") }
        assertEquals(ApiResult.Offline, env.api.put("w1", write))
    }

    @Test
    fun archivePostsTheVersionBody() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, walletBody(walletJson(archivedAt = "2026-10-02T00:00:00Z", version = 5))) }
        val archived = assertIs<ApiResult.Success<WalletDto>>(env.api.archive("w1", 4)).value
        assertNotNull(archived.archivedAt)
        val request = env.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/wallets/w1/archive"))
        assertEquals(setOf("version"), request.jsonBody().keys)
        assertEquals("4", request.jsonBody()["version"].toString())
    }

    @Test
    fun deleteSendsAJsonBodyOnDelete() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, walletBody(walletJson(deletedAt = "2026-10-02T00:00:00Z", version = 5))) }
        val tombstone = assertIs<ApiResult.Success<WalletDto>>(env.api.delete("w1", 4)).value
        assertNotNull(tombstone.deletedAt)
        val request = env.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("4", request.jsonBody()["version"].toString())
        assertTrue((request.body as OutgoingContent).contentType.toString().startsWith("application/json"))
    }

    @Test
    fun transferOwnershipPostsTheUserAndVersion() = runTest {
        val env = env(backgroundScope) { jsonResponse(200, walletBody(walletJson(role = "editor", version = 6))) }
        val wallet = assertIs<ApiResult.Success<WalletDto>>(env.api.transferOwnership("w1", "u2", 5)).value
        assertEquals("editor", wallet.myRole)
        val request = env.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/wallets/w1/transfer-ownership"))
        assertEquals(setOf("user_id", "version"), request.jsonBody().keys)
        assertEquals("\"u2\"", request.jsonBody()["user_id"].toString())
    }

    @Test
    fun transferFailuresKeepTheirCodes() = runTest {
        var answer = 0
        val env = env(backgroundScope) {
            when (answer++) {
                0 -> jsonResponse(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/user_id","code":"INVALID_ENUM_VALUE"}]"""))
                1 -> jsonResponse(409, problemBody(409, "VERSION_CONFLICT", ""","current":${walletJson(version = 9)}"""))
                else -> jsonResponse(403, problemBody(403, "FORBIDDEN"))
            }
        }
        assertEquals(ApiErrorCode.VALIDATION_FAILED, assertIs<ApiResult.Failure>(env.api.transferOwnership("w1", "u9", 5)).code)
        assertEquals(ApiErrorCode.VERSION_CONFLICT, assertIs<ApiResult.Failure>(env.api.transferOwnership("w1", "u2", 5)).code)
        assertEquals(ApiErrorCode.FORBIDDEN, assertIs<ApiResult.Failure>(env.api.transferOwnership("w1", "u2", 5)).code)
    }
}
