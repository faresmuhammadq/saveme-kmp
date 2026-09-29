package com.fares.saveme.core.network

import app.cash.turbine.test
import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.Environment
import com.fares.saveme.core.platform.ClientInfo
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val uuidV7 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

class FakeClientInfo(
    override val platform: String = "android",
    override val appVersion: String = "0.1.0",
    override val deviceName: String = "Pixel 8",
    override val liveApiSupported: Boolean = true,
    private val language: String = "ar",
) : ClientInfo {
    override fun timezoneId() = "Africa/Cairo"
    override fun languageTag() = language
}

@Serializable
private data class Ping(val ok: Boolean)

private fun problem(status: HttpStatusCode, code: String, extra: String = "") =
    """{"type":"t","title":"x","status":${status.value},"detail":"d","code":"$code","request_id":"req-1"$extra}"""

class HttpClientFactoryTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val events = ApiEvents()

    private fun client(
        credential: String? = null,
        language: (() -> String)? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ApiClient {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val http = createHttpClient(
            engine = engine,
            config = AppConfig("http://192.168.1.20:8080", "192.168.1.20", Environment.Dev, isDebugTools = true),
            clientInfo = FakeClientInfo(),
            credentialProvider = { credential },
            // The timeout's timer would run on runTest's virtual time and fire before the mock answers.
            requestTimeoutMillis = null,
            languageProvider = language ?: { "ar" },
        )
        return ApiClient(http, events)
    }

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    @Test
    fun everyRequestCarriesTheRequiredHeaders() = runTest {
        val api = client { json(HttpStatusCode.OK, """{"data":{"ok":true},"meta":{}}""") }

        api.call<Ping> { url("ping"); method = HttpMethod.Get }
        api.call<Ping> { url("ping"); method = HttpMethod.Get }

        val (first, second) = requests
        assertEquals("http://192.168.1.20:8080/api/v1/ping", first.url.toString())
        assertEquals("application/json", first.headers[HttpHeaders.Accept])
        assertEquals("android", first.headers["X-Client-Platform"])
        assertTrue(Regex("^\\d+\\.\\d+\\.\\d+$").matches(first.headers["X-Client-Version"]!!))
        assertEquals("ar", first.headers[HttpHeaders.AcceptLanguage])
        assertTrue(uuidV7.matches(first.headers["X-Correlation-Id"]!!))
        assertNotEquals(first.headers["X-Correlation-Id"], second.headers["X-Correlation-Id"])
        assertNull(first.headers[HttpHeaders.Authorization])
    }

    @Test
    fun acceptLanguageFollowsTheAppLanguageNotTheDevice() = runTest {
        var language = "en"
        val api = client(language = { language }) { json(HttpStatusCode.OK, "{\"data\":{\"ok\":true},\"meta\":{}}") }
        api.call<Ping> { url("ping") }
        language = "ar"
        api.call<Ping> { url("ping") }
        assertEquals(listOf("en", "ar"), requests.map { it.headers[HttpHeaders.AcceptLanguage] })
    }

    @Test
    fun bearerIsSentOnlyOnCredentialedRequests() = runTest {
        val api = client(credential = "cred") { json(HttpStatusCode.OK, """{"data":{"ok":true},"meta":{}}""") }

        api.call<Ping> { url("ping") }
        api.call<Ping> { url("ping"); publicEndpoint() }

        assertEquals("Bearer cred", requests[0].headers[HttpHeaders.Authorization])
        assertNull(requests[1].headers[HttpHeaders.Authorization])
    }

    @Test
    fun successDecodesEnvelopeAndNoContentDecodesUnit() = runTest {
        val api = client { request ->
            if (request.url.encodedPath.endsWith("empty")) respond("", HttpStatusCode.NoContent)
            else json(HttpStatusCode.OK, """{"data":{"ok":true,"new_member":1},"meta":{}}""")
        }

        assertEquals(ApiResult.Success(Ping(true)), api.call<Ping> { url("ping") })
        assertEquals(ApiResult.Success(Unit), api.call<Unit> { url("empty"); method = HttpMethod.Post })
    }

    @Test
    fun failureCarriesProblemCodeAndRetryAfter() = runTest {
        val api = client {
            respond(
                problem(HttpStatusCode.fromValue(423), "ACCOUNT_LOCKED"),
                HttpStatusCode.fromValue(423),
                headersOf(HttpHeaders.ContentType to listOf("application/problem+json"), HttpHeaders.RetryAfter to listOf("900")),
            )
        }

        val result = api.call<Ping> { url("auth/login"); publicEndpoint() }

        assertIs<ApiResult.Failure>(result)
        assertEquals(ApiErrorCode.ACCOUNT_LOCKED, result.code)
        assertEquals(900L, result.retryAfterSeconds)
        assertEquals("req-1", result.problem.requestId)
    }

    @Test
    fun transportFailureIsOffline() = runTest {
        val api = client { throw IOException("no route") }
        assertEquals(ApiResult.Offline, api.call<Ping> { url("ping") })
    }

    @Test
    fun unauthenticatedOnCredentialedRequestPublishesSessionEnded() = runTest {
        val api = client(credential = "cred") {
            json(HttpStatusCode.Unauthorized, problem(HttpStatusCode.Unauthorized, "UNAUTHENTICATED"))
        }
        events.events.test {
            api.call<Ping> { url("auth/resend-verification") }
            assertEquals(ApiEvent.SessionEnded, awaitItem())
        }
    }

    @Test
    fun unauthenticatedOnPublicRequestPublishesNothing() = runTest {
        val api = client(credential = "cred") {
            json(HttpStatusCode.Unauthorized, problem(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS"))
        }
        events.events.test {
            api.call<Ping> { url("auth/login"); publicEndpoint() }
            expectNoEvents()
        }
    }

    @Test
    fun upgradeRequiredAndEmailNotVerifiedArePublished() = runTest {
        val api = client { request ->
            if (request.url.encodedPath.endsWith("old")) {
                json(HttpStatusCode.UpgradeRequired, problem(HttpStatusCode.UpgradeRequired, "CLIENT_VERSION_UNSUPPORTED", ""","minimum_version":"0.3.0""""))
            } else {
                json(HttpStatusCode.Forbidden, problem(HttpStatusCode.Forbidden, "EMAIL_NOT_VERIFIED"))
            }
        }
        events.events.test {
            api.call<Ping> { url("old") }
            assertEquals(ApiEvent.UpdateRequired("0.3.0"), awaitItem())
            api.call<Ping> { url("write") }
            assertEquals(ApiEvent.EmailNotVerified, awaitItem())
        }
    }
}
