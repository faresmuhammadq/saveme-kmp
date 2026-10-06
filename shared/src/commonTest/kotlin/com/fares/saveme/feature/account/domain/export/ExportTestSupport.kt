package com.fares.saveme.feature.account.domain.export

import com.fares.saveme.feature.account.AccountTestEnv
import com.fares.saveme.feature.account.data.ExportApi
import com.fares.saveme.feature.auth.jsonResponse
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlin.time.Instant

const val REQUESTED_AT = "2026-10-06T10:00:00.000000Z"
const val SECRET_URL = "https://api.example/exports/e1/file?expires=1791282341&signature=4b1c0e9a"

/** One export as the wire carries it. */
fun exportJson(
    id: String = "e1",
    status: String = "queued",
    scope: String = "account",
    walletId: String? = null,
    failure: String? = null,
    requestedAt: String = REQUESTED_AT,
    size: Long? = null,
    url: String? = null,
) = """{"id":"$id","scope":"$scope","wallet_id":${walletId?.let { "\"$it\"" } ?: "null"},"status":"$status",
    "failure_code":${failure?.let { "\"$it\"" } ?: "null"},"requested_at":"$requestedAt","completed_at":null,"expires_at":null,
    "size_bytes":${size ?: "null"},"download_url":${url?.let { "\"$it\"" } ?: "null"},"download_url_expires_at":null}"""

fun envelope(data: String) = """{"data":$data,"meta":{}}"""

/** A signed-in environment answering through [handler]; [api] is the real [ExportApi] over the mock engine. */
class ExportTestEnv(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
    val env = AccountTestEnv(handler)
    val api = ExportApi(env.auth.client)
    val requests get() = env.requests
    suspend fun signedIn() = env.signedIn()
}

/** Answers [bodies] in order and then the last one again, all `200`. */
fun scripted(bodies: List<String>): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData {
    var index = 0
    return { jsonResponse(200, envelope(bodies[minOf(index++, bodies.lastIndex)])) }
}

val Base: Instant = Instant.parse("2026-10-06T10:00:00Z")
