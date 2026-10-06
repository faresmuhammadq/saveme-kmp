package com.fares.saveme.feature.account.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.Page
import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.data.dto.ExportDto
import com.fares.saveme.feature.account.data.dto.ExportRequestDto
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contract tests from the `exports.yaml` examples (backend spec 022). */
class ExportContractTest {

    private val queued = """{"id":"0192f9a0-6b1c-7c3d-8e4f-5a6b7c8d9e0f","scope":"account","wallet_id":null,"status":"queued","failure_code":null,"requested_at":"2026-10-06T10:00:00.000000Z","completed_at":null,"expires_at":null,"size_bytes":null,"download_url":null,"download_url_expires_at":null}"""
    private val running = queued.replace("queued", "running")
    private val ready = """{"id":"0192f9b2-0000-7000-8000-0000000000e2","scope":"wallet","wallet_id":"0192f79e-0000-7000-8000-000000000001","status":"ready","failure_code":null,"requested_at":"2026-10-06T10:00:00.000000Z","completed_at":"2026-10-06T10:00:41.120000Z","expires_at":"2026-10-07T10:00:41.120000Z","size_bytes":184320,"download_url":"https://api.example/exports/0192f9b2-0000-7000-8000-0000000000e2/file?expires=1791282341&signature=4b1c0e9a","download_url_expires_at":"2026-10-06T10:15:41.000000Z"}"""
    private val expired = """{"id":"0192f9a0-6b1c-7c3d-8e4f-5a6b7c8d9e0f","scope":"account","wallet_id":null,"status":"expired","failure_code":null,"requested_at":"2026-10-01T08:00:00.000000Z","completed_at":"2026-10-01T08:01:02.000000Z","expires_at":"2026-10-02T08:01:02.000000Z","size_bytes":201728,"download_url":null,"download_url_expires_at":null}"""
    private val failed = queued.replace("\"status\":\"queued\",\"failure_code\":null", "\"status\":\"failed\",\"failure_code\":\"generation_failed\"")
    private val unknownStatus = queued.replace("queued", "archiving").replace("\"size_bytes\"", "\"something_new\":1,\"size_bytes\"")

    private fun envelope(data: String) = """{"data":$data,"meta":{}}"""

    @Test
    fun requestSendsOnlyTheScope() = runTest {
        val env = AccountTestEnv { jsonResponse(202, envelope(queued)) }
        env.signedIn()
        val account = assertIs<ApiResult.Success<ExportDto>>(ExportApi(env.auth.client).requestExport(ExportRequestDto("account"))).value
        assertEquals("queued", account.status)
        val request = env.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/exports"))
        assertEquals(mapOf("scope" to JsonPrimitive("account")), request.jsonBody().toMap())
        assertNull(request.headers["Idempotency-Key"])
        assertTrue(request.headers[HttpHeaders.Authorization]!!.startsWith("Bearer "))
        assertEquals("android", request.headers["X-Client-Platform"])
        assertTrue(request.headers["X-Correlation-Id"] != null)
    }

    @Test
    fun walletRequestCarriesTheWalletId() = runTest {
        val env = AccountTestEnv { jsonResponse(202, envelope(queued)) }
        env.signedIn()
        ExportApi(env.auth.client).requestExport(ExportRequestDto("wallet", "w1"))
        assertEquals(
            mapOf("scope" to JsonPrimitive("wallet"), "wallet_id" to JsonPrimitive("w1")),
            env.requests.single().jsonBody().toMap(),
        )
    }

    @Test
    fun listAsksForTwentyAndFollowsTheCursor() = runTest {
        val env = AccountTestEnv {
            jsonResponse(200, """{"data":[$ready,$expired],"meta":{"next_cursor":"c2","has_more":true,"limit":20}}""")
        }
        env.signedIn()
        val api = ExportApi(env.auth.client)
        val page = assertIs<ApiResult.Success<Page<ExportDto>>>(api.listExports(null)).value
        assertEquals(listOf("ready", "expired"), page.items.map { it.status })
        assertEquals("c2", page.nextCursor)
        assertTrue(page.hasMore)
        api.listExports("c2")
        val (first, second) = env.requests
        assertEquals("20", first.url.parameters["limit"])
        assertNull(first.url.parameters["cursor"])
        assertEquals("c2", second.url.parameters["cursor"])
        assertEquals(HttpMethod.Get, first.method)
    }

    @Test
    fun everyExampleDecodesAndAnUnknownStatusAndMemberDoNot() = runTest {
        listOf(queued, running, ready, expired, failed, unknownStatus).forEach { body ->
            val env = AccountTestEnv { jsonResponse(200, envelope(body)) }
            env.signedIn()
            val export = assertIs<ApiResult.Success<ExportDto>>(ExportApi(env.auth.client).getExport("e1")).value
            assertTrue(env.requests.single().url.encodedPath.endsWith("/api/v1/exports/e1"))
            assertTrue(export.id.isNotEmpty())
        }
        assertEquals("archiving", AppJson.decodeFromString(ExportDto.serializer(), unknownStatus).status)
    }

    @Test
    fun theDownloadUrlNeverPrints() {
        val dto = AppJson.decodeFromString(ExportDto.serializer(), ready)
        assertTrue(dto.downloadUrl!!.contains("signature"))
        listOf(dto.toString(), "$dto").forEach { text ->
            assertFalse(text.contains("signature"), text)
            assertFalse(text.contains("https://"), text)
            assertTrue(text.contains("downloadUrl=***"))
        }
    }

    @Test
    fun conflictCarriesTheActiveExportAsCurrent() = runTest {
        val env = AccountTestEnv { jsonResponse(409, problemBody(409, "EXPORT_IN_PROGRESS", ""","current":$running""")) }
        env.signedIn()
        val failure = assertIs<ApiResult.Failure>(ExportApi(env.auth.client).requestExport(ExportRequestDto("account")))
        assertEquals(ApiErrorCode.EXPORT_IN_PROGRESS, failure.code)
        val current = AppJson.decodeFromJsonElement(ExportDto.serializer(), failure.problem.current!!)
        assertEquals("running", current.status)
    }

    @Test
    fun rateLimitCarriesTheWaitInTheBodyOrOnlyInTheHeader() = runTest {
        val body = AccountTestEnv { jsonResponse(429, problemBody(429, "RATE_LIMITED", ""","retry_after_seconds":3600"""), "Retry-After" to "3600") }
        body.signedIn()
        assertEquals(3600L, assertIs<ApiResult.Failure>(ExportApi(body.auth.client).requestExport(ExportRequestDto("account"))).retryAfterSeconds)
        val header = AccountTestEnv { jsonResponse(429, problemBody(429, "RATE_LIMITED"), "Retry-After" to "90") }
        header.signedIn()
        assertEquals(90L, assertIs<ApiResult.Failure>(ExportApi(header.auth.client).requestExport(ExportRequestDto("account"))).retryAfterSeconds)
    }
}
