package com.fares.saveme.core.network

import co.touchlab.kermit.Logger
import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.platform.ClientInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlin.uuid.Uuid

private val PublicEndpointKey = AttributeKey<Boolean>("PublicEndpoint")

/** Marks an operation declared `security: []`: no bearer credential is attached. */
fun HttpRequestBuilder.publicEndpoint() {
    attributes.put(PublicEndpointKey, true)
}

fun createHttpClient(
    engine: HttpClientEngine,
    config: AppConfig,
    clientInfo: ClientInfo,
    credentialProvider: suspend () -> String?,
    languageProvider: () -> String = { clientInfo.languageTag() },
    /** Null disables the whole-request timeout (tests: its timer would run on virtual time). */
    requestTimeoutMillis: Long? = 30_000,
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(AppJson)
        json(AppJson, ContentType("application", "problem+json"))
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        this.requestTimeoutMillis = requestTimeoutMillis
    }
    defaultRequest {
        url(config.baseUrl.trimEnd('/') + "/api/v1/")
    }
    install(
        createClientPlugin("ClientHeaders") {
            onRequest { request, _ ->
                request.headers[HttpHeaders.Accept] = ContentType.Application.Json.toString()
                request.headers["X-Client-Platform"] = clientInfo.platform
                request.headers["X-Client-Version"] = clientInfo.appVersion
                request.headers[HttpHeaders.AcceptLanguage] = languageProvider()
                request.headers["X-Correlation-Id"] = Uuid.generateV7().toString()
                if (request.attributes.getOrNull(PublicEndpointKey) != true) {
                    credentialProvider()?.let { request.headers[HttpHeaders.Authorization] = "Bearer $it" }
                }
                Logger.d(tag = "api") { "${request.method.value} ${request.url.buildString()}" }
            }
        },
    )
}

/**
 * Executes calls and turns every outcome into an [ApiResult]. Never retries: auth submissions
 * must not be resent automatically (research R-04, P-02.6).
 */
class ApiClient(
    @PublishedApi internal val http: HttpClient,
    private val events: ApiEvents,
) {

    suspend inline fun <reified T> call(noinline block: HttpRequestBuilder.() -> Unit): ApiResult<T> =
        execute(block) { response ->
            if (T::class == Unit::class) Unit as T else response.body<Envelope<T>>().data
        }

    /**
     * Like [call], but keeps the envelope's `meta` (a created link invitation answers `meta.share_url`, spec 013 R-04).
     * [call] is unchanged.
     */
    suspend inline fun <reified T> callWithMeta(noinline block: HttpRequestBuilder.() -> Unit): ApiResult<Envelope<T>> =
        execute(block) { response -> response.body<Envelope<T>>() }

    /** A collection page: `data` plus the cursor from `meta` (CollectionMeta). */
    suspend inline fun <reified T> callPage(noinline block: HttpRequestBuilder.() -> Unit): ApiResult<Page<T>> =
        execute(block) { response ->
            val envelope = response.body<Envelope<List<T>>>()
            val meta = AppJson.decodeFromJsonElement(CollectionMeta.serializer(), envelope.meta)
            Page(envelope.data, meta.nextCursor, meta.hasMore)
        }

    @PublishedApi
    internal suspend fun <T> execute(
        block: HttpRequestBuilder.() -> Unit,
        decode: suspend (HttpResponse) -> T,
    ): ApiResult<T> {
        val response = try {
            http.request(block)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Logger.w(e, tag = "api") { "request timed out: ${e.message}" }
            return ApiResult.Timeout
        } catch (e: IOException) {
            Logger.w(e, tag = "api") { "request failed: ${e.message}" }
            return if (e::class.simpleName?.contains("Timeout") == true) ApiResult.Timeout else ApiResult.Offline
        }

        if (response.status.isSuccess()) return ApiResult.Success(decode(response))

        val problem = runCatching { AppJson.decodeFromString(ProblemDetails.serializer(), response.bodyAsText()) }
            .getOrElse { ProblemDetails(status = response.status.value) }
        val code = ApiErrorCode.from(problem.code)
        publishEvents(response, problem, code)
        return ApiResult.Failure(
            problem = problem,
            code = code,
            retryAfterSeconds = problem.retryAfterSeconds ?: response.headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
        )
    }

    private fun publishEvents(response: HttpResponse, problem: ProblemDetails, code: ApiErrorCode) {
        val credentialed = response.call.request.headers[HttpHeaders.Authorization] != null
        when {
            // Branch on the code: a wrong current password is 401 INVALID_CREDENTIALS on a live session (R-04).
            credentialed && (code == ApiErrorCode.UNAUTHENTICATED ||
                (response.status == HttpStatusCode.Unauthorized && code == ApiErrorCode.UNKNOWN)) ->
                events.publish(ApiEvent.SessionEnded)
            response.status == HttpStatusCode.UpgradeRequired -> events.publish(ApiEvent.UpdateRequired(problem.minimumVersion))
            code == ApiErrorCode.EMAIL_NOT_VERIFIED -> events.publish(ApiEvent.EmailNotVerified)
            code == ApiErrorCode.FEATURE_NOT_ENTITLED -> events.publish(ApiEvent.FeatureNotEntitled)
        }
    }
}
