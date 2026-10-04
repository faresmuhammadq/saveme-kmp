package com.fares.saveme.core.sync

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.jsonResponse
import io.ktor.client.request.HttpRequestData
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

/**
 * The real wire client ([HttpSyncApi] over the app's `ApiClient`) answered with [CapturedSyncResponses]: each
 * request is routed by the scope it reads and the position it sends, so a test replays the live backend's
 * answers in the order the QA recorded them. No answer scripted → `NO_ANSWER` (a test defect, never silent).
 */
class CapturedSyncServer {
    /** The person's scope: the answer to a read from [cursor] (null = from the start). */
    var user: (cursor: String?) -> CapturedSyncResponses.Answer = { error("no user feed answer scripted for $it") }

    /** A wallet's feed: the answer to a read of [walletId] from [cursor]. */
    var wallet: (walletId: String, cursor: String?) -> CapturedSyncResponses.Answer = { id, c -> error("no wallet feed answer scripted for $id/$c") }

    /** The batch upload: the answer to the n-th upload (0-based) with these items; throw an IOException for "no answer". */
    var batch: (n: Int, items: JsonElement) -> CapturedSyncResponses.Answer = { n, _ -> error("no batch answer scripted for upload $n") }

    /** The heads: the answer to a read of the wallet heads from [cursor] (spec 011). */
    var heads: (cursor: String?) -> CapturedSyncResponses.Answer = { error("no heads answer scripted for $it") }

    val uploads = mutableListOf<JsonElement>()
    val requests = mutableListOf<String>()
    val feedReads = mutableListOf<String>()

    val auth = AuthTestEnv { request -> answer(request).let { jsonResponse(it.status, it.body) } }
    val api = HttpSyncApi(auth.client, auth.events, batchTimeoutMillis = null)

    suspend fun signedIn() = auth.secureStore.write(StoredSession(CREDENTIAL, "a", Instant.parse("2026-09-26T10:00:00Z")))

    private fun answer(request: HttpRequestData): CapturedSyncResponses.Answer {
        val path = request.url.encodedPath
        val cursor = request.url.parameters["cursor"]
        requests += path.substringAfter("/api/v1/") + (cursor?.let { "?cursor" } ?: "")
        return when {
            path.endsWith("/sync/wallets/heads") -> heads(cursor)
            path.endsWith("/sync/batch") -> {
                val items = request.jsonBody().jsonObject.getValue("items")
                uploads += items
                batch(uploads.size - 1, items)
            }
            path.endsWith("/sync/user/changes") -> {
                feedReads += "user${cursor?.let { "@" + it.takeLast(6) } ?: ""}"
                user(cursor)
            }
            else -> {
                val walletId = path.substringAfter("/sync/wallets/").substringBefore("/changes")
                feedReads += "wallet $walletId${cursor?.let { "@" + it.takeLast(6) } ?: ""}"
                wallet(walletId, cursor)
            }
        }
    }

    companion object {
        /** The position a captured page ends at (`meta.sync_cursor`). */
        fun syncCursorOf(answer: CapturedSyncResponses.Answer): String =
            AppJson.parseToJsonElement(answer.body).jsonObject.getValue("meta").jsonObject.getValue("sync_cursor").let { (it as kotlinx.serialization.json.JsonPrimitive).content }

        /** The `next_cursor` of a captured page. */
        fun nextCursorOf(answer: CapturedSyncResponses.Answer): String =
            AppJson.parseToJsonElement(answer.body).jsonObject.getValue("meta").jsonObject.getValue("next_cursor").let { (it as kotlinx.serialization.json.JsonPrimitive).content }

        /** The `items` of a captured request body. */
        fun itemsOf(request: String): JsonElement = AppJson.parseToJsonElement(request).jsonObject.getValue("items")
    }
}
