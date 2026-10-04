package com.fares.saveme.core.sync

import app.cash.turbine.test
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every call of contracts/sync-api-client.md and every refusal it handles, over the OpenAPI examples (P-14.3). */
class SyncApiContractTest {

    private class Env(batchTimeoutMillis: Long? = null, handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val auth = AuthTestEnv(handler)
        val api = HttpSyncApi(auth.client, auth.events, batchTimeoutMillis)
        val requests get() = auth.requests

        suspend fun signedIn() = auth.secureStore.write(
            com.fares.saveme.core.storage.StoredSession(CREDENTIAL, "a", kotlin.time.Instant.parse("2026-09-26T10:00:00Z")),
        )
    }

    private fun item(type: String = "wallet", op: BatchOp = BatchOp.Upsert, id: String = "w1", wallet: String? = null) =
        BatchItemDto(type, op.wire, id, wallet, buildJsonObject { put("version", JsonPrimitive(0)) })

    @Test
    fun theUserFeedAsksForTwoHundredAndOnlySendsTheCursorWhenOneIsStored() = runTest {
        val env = Env { jsonResponse(200, OpenApiSyncExamples.USER_FEED_PAGE) }
        env.signedIn()
        env.api.userChanges(null)
        env.api.userChanges("stored-position")

        val (first, second) = env.requests
        assertEquals(HttpMethod.Get, first.method)
        assertTrue(first.url.encodedPath.endsWith("/api/v1/sync/user/changes"), first.url.encodedPath)
        assertEquals("200", first.url.parameters["limit"])
        assertFalse(first.url.parameters.contains("cursor"), "never cursor= without a stored position")
        assertEquals(setOf("limit"), first.url.parameters.names())
        assertEquals("stored-position", second.url.parameters["cursor"])
        assertEquals(setOf("limit", "cursor"), second.url.parameters.names())
        assertTrue(first.headers["Authorization"]!!.startsWith("Bearer "))
        listOf("X-Client-Platform", "X-Client-Version", "Accept-Language", "X-Correlation-Id").forEach { assertNotNull(first.headers[it], it) }
    }

    @Test
    fun aWalletFeedIsAddressedByTheWalletId() = runTest {
        val env = Env { jsonResponse(200, OpenApiSyncExamples.WALLET_FEED_PAGE) }
        env.signedIn()
        env.api.walletChanges("0192f79e-0000-7000-8000-000000000001", "c1")
        val request = env.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/api/v1/sync/wallets/0192f79e-0000-7000-8000-000000000001/changes"), request.url.encodedPath)
        assertEquals("c1", request.url.parameters["cursor"])
        assertEquals("200", request.url.parameters["limit"])
    }

    @Test
    fun theUserFeedExampleDecodesWithRecordsAsRawJsonAndAnUnknownMetaMemberIsIgnored() = runTest {
        val env = Env { jsonResponse(200, OpenApiSyncExamples.USER_FEED_PAGE) }
        env.signedIn()
        val page = assertIs<ApiResult.Success<FeedPageDto>>(env.api.userChanges(null)).value
        assertEquals(listOf("wallet_member", "category"), page.data.map { it.type })
        assertEquals("editor", page.data[0].record.jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, page.data[0].record.jsonObject["deleted_at"])
        assertFalse(page.meta.hasMore)
        assertNull(page.meta.nextCursor)
        assertTrue(page.meta.syncCursor.startsWith("eyJ2IjoxLCJmIjoidSIs"))
    }

    @Test
    fun theWalletFeedExampleDecodesAndReportsMorePages() = runTest {
        val env = Env { jsonResponse(200, OpenApiSyncExamples.WALLET_FEED_PAGE) }
        env.signedIn()
        val page = assertIs<ApiResult.Success<FeedPageDto>>(env.api.walletChanges("w1", null)).value
        assertEquals(2, page.data.size)
        assertTrue(page.meta.hasMore)
        assertEquals(page.meta.nextCursor, page.meta.syncCursor, "next_cursor equals sync_cursor while has_more")
        assertNotNull(page.data[1].record.jsonObject["deleted_at"]!!.jsonPrimitive.content)
        assertEquals(2, page.meta.limit)
    }

    @Test
    fun everyFeedRefusalTheFeatureHandlesDecodesToItsCode() = runTest {
        val cases = listOf(
            Triple(400, "INVALID_CURSOR", ApiErrorCode.INVALID_CURSOR),
            Triple(400, "INVALID_QUERY_PARAMETER", ApiErrorCode.UNKNOWN),
            Triple(410, "SYNC_CURSOR_EXPIRED", ApiErrorCode.SYNC_CURSOR_EXPIRED),
            Triple(404, "NOT_FOUND", ApiErrorCode.NOT_FOUND),
            Triple(422, "INVALID_ID", ApiErrorCode.UNKNOWN),
            Triple(500, "INTERNAL_ERROR", ApiErrorCode.INTERNAL_ERROR),
        )
        for ((status, code, expected) in cases) {
            val env = Env { jsonResponse(status, problemBody(status, code)) }
            env.signedIn()
            val failure = assertIs<ApiResult.Failure>(env.api.walletChanges("w1", "c"), "$status $code")
            assertEquals(expected, failure.code, "$status $code")
            assertEquals(code, failure.problem.code)
            assertEquals("req-42", failure.problem.requestId)
        }
    }

    @Test
    fun aRateLimitedFeedKeepsRetryAfterAndAnExpiredSessionEndsTheSession() = runTest {
        val limited = Env { jsonResponse(429, problemBody(429, "RATE_LIMITED"), "Retry-After" to "12") }
        limited.signedIn()
        assertEquals(12L, assertIs<ApiResult.Failure>(limited.api.userChanges(null)).retryAfterSeconds)

        val ended = Env { jsonResponse(401, problemBody(401, "UNAUTHENTICATED")) }
        ended.signedIn()
        ended.auth.events.events.test {
            assertEquals(ApiErrorCode.UNAUTHENTICATED, assertIs<ApiResult.Failure>(ended.api.userChanges(null)).code)
            assertEquals(ApiEvent.SessionEnded, awaitItem())
        }

        val old = Env { jsonResponse(426, problemBody(426, "CLIENT_VERSION_UNSUPPORTED", ""","minimum_version":"2.0.0"""")) }
        old.signedIn()
        old.auth.events.events.test {
            assertEquals(ApiErrorCode.CLIENT_VERSION_UNSUPPORTED, assertIs<ApiResult.Failure>(old.api.userChanges(null)).code)
            assertEquals(ApiEvent.UpdateRequired("2.0.0"), awaitItem())
        }
    }

    @Test
    fun offlineFeedsAreOffline() = runTest {
        val env = Env { throw kotlinx.io.IOException("no route") }
        env.signedIn()
        assertEquals(ApiResult.Offline, env.api.userChanges(null))
    }

    @Test
    fun aFeedThatIsNotTheDocumentedShapeIsReportedAsUnreadable() = runTest {
        val env = Env { jsonResponse(200, """{"data":"nope"}""") }
        env.signedIn()
        val failure = assertIs<ApiResult.Failure>(env.api.userChanges(null))
        assertEquals(HttpSyncApi.UNREADABLE, failure.problem.code)
        assertEquals(200, failure.problem.status)
    }

    @Test
    fun theBatchBodyHasOnlyItemsAndOmitsAWalletIdThatIsNull() = runTest {
        val env = Env { jsonResponse(200, OpenApiSyncExamples.VALIDATION_REFUSAL) }
        env.signedIn()
        env.api.batch(listOf(item(), item("transaction", BatchOp.Delete, "t1", wallet = "w1")))

        val request = env.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/sync/batch"), request.url.encodedPath)
        assertNull(request.headers["Idempotency-Key"], "the batch is idempotent because every item is")
        val body = request.jsonBody()
        assertEquals(setOf("items"), body.keys)
        val items = (body["items"] as kotlinx.serialization.json.JsonArray).map { it.jsonObject }
        assertEquals(setOf("type", "op", "id", "data"), items[0].keys)
        assertEquals(setOf("type", "op", "id", "wallet_id", "data"), items[1].keys)
        assertEquals("delete", items[1]["op"]!!.jsonPrimitive.content)
        assertEquals("w1", items[1]["wallet_id"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(0), items[0]["data"]!!.jsonObject["version"])
    }

    @Test
    fun theMixedResultsExampleDecodesEveryOutcome() = runTest {
        val env = Env { jsonResponse(200, OpenApiSyncExamples.MIXED_RESULTS) }
        env.signedIn()
        val results = assertIs<ApiResult.Success<List<BatchItemResultDto>>>(env.api.batch(listOf(item()))).value
        assertEquals(listOf("applied", "applied", "conflict", "rejected"), results.map { it.outcome })
        assertEquals(listOf(201, 201, 409, 404), results.map { it.status })
        assertEquals(1L, results[0].record!!.jsonObject["version"]!!.jsonPrimitive.content.toLong())
        assertNull(results[0].problem)
        val clash = results[2]
        assertNull(clash.record)
        assertEquals("VERSION_CONFLICT", clash.problem!!.code)
        assertEquals(3L, clash.problem.current!!.jsonObject["version"]!!.jsonPrimitive.content.toLong())
        assertEquals(ApiErrorCode.NOT_FOUND, ApiErrorCode.from(results[3].problem!!.code))
    }

    @Test
    fun theValidationRefusalKeepsItsPointerRootedAtTheItemsData() = runTest {
        val env = Env { jsonResponse(200, OpenApiSyncExamples.VALIDATION_REFUSAL) }
        env.signedIn()
        val result = assertIs<ApiResult.Success<List<BatchItemResultDto>>>(env.api.batch(listOf(item()))).value.single()
        assertEquals("/amount_minor", result.problem!!.errors!!.single().pointer)
        assertEquals("AMOUNT_NOT_INTEGER", result.problem.errors!!.single().code)
    }

    @Test
    fun everyRequestLevelRefusalOfTheBatchDecodes() = runTest {
        val cases = listOf(
            Triple(422, problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/items","code":"OUT_OF_RANGE"}]"""), ApiErrorCode.VALIDATION_FAILED),
            Triple(400, problemBody(400, "MALFORMED_JSON"), ApiErrorCode.MALFORMED_JSON),
            Triple(415, problemBody(415, "UNSUPPORTED_MEDIA_TYPE"), ApiErrorCode.UNSUPPORTED_MEDIA_TYPE),
            Triple(404, problemBody(404, "NOT_FOUND"), ApiErrorCode.NOT_FOUND),
            Triple(429, problemBody(429, "RATE_LIMITED"), ApiErrorCode.RATE_LIMITED),
            Triple(503, problemBody(503, "SERVICE_UNAVAILABLE"), ApiErrorCode.SERVICE_UNAVAILABLE),
        )
        for ((status, body, code) in cases) {
            val env = Env { jsonResponse(status, body) }
            env.signedIn()
            assertEquals(code, assertIs<ApiResult.Failure>(env.api.batch(listOf(item())), "$status").code, "$status")
        }
        val tooMany = Env { jsonResponse(422, cases[0].second) }
        tooMany.signedIn()
        assertEquals("/items", assertIs<ApiResult.Failure>(tooMany.api.batch(listOf(item()))).problem.errors!!.single().pointer)
    }

    @Test
    fun offlineAndUnreadableBatchesAreTold() = runTest {
        val offline = Env { throw kotlinx.io.IOException("no route") }
        offline.signedIn()
        assertEquals(ApiResult.Offline, offline.api.batch(listOf(item())))
        val unreadable = Env { jsonResponse(200, """{"data":{"not":"a list"},"meta":{}}""") }
        unreadable.signedIn()
        assertEquals(HttpSyncApi.UNREADABLE, assertIs<ApiResult.Failure>(unreadable.api.batch(listOf(item()))).problem.code)
    }

    @Test
    fun itemsRefusedForAnUnverifiedAddressOrAPlanLimitRaiseTheSameAppEventsAsTheSingleWrite() = runTest {
        fun refused(code: String) = """{"data":[{"type":"wallet","id":"w1","outcome":"rejected","status":403,"record":null,"problem":{"status":403,"code":"$code","request_id":"r"}}],"meta":{}}"""
        val env = Env { request -> jsonResponse(200, refused(if (request.headers["X-Test"] == "plan") "FEATURE_NOT_ENTITLED" else "EMAIL_NOT_VERIFIED")) }
        env.signedIn()
        env.auth.events.events.test {
            env.api.batch(listOf(item()))
            assertEquals(ApiEvent.EmailNotVerified, awaitItem())
        }
        val plan = Env { jsonResponse(200, refused("FEATURE_NOT_ENTITLED")) }
        plan.signedIn()
        plan.auth.events.events.test {
            plan.api.batch(listOf(item()))
            assertEquals(ApiEvent.FeatureNotEntitled, awaitItem())
        }
    }

    @Test
    fun theBatchGetsSixtySecondsAndTheFeedsKeepTheDefault() = runTest {
        // Real time: a 60 second timer on the test scheduler's virtual clock would fire before the mock answers.
        withContext(Dispatchers.Default) {
            var batchTimeout: Long? = null
            var feedTimeout: Long? = null
            val env = Env(batchTimeoutMillis = HttpSyncApi.BATCH_TIMEOUT_MILLIS, handler = { request ->
                val millis = request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
                if (request.method == HttpMethod.Post) batchTimeout = millis else feedTimeout = millis
                jsonResponse(200, if (request.method == HttpMethod.Post) OpenApiSyncExamples.VALIDATION_REFUSAL else OpenApiSyncExamples.USER_FEED_PAGE)
            })
            env.signedIn()
            env.api.batch(listOf(item()))
            env.api.userChanges(null)
            assertEquals(60_000L, batchTimeout)
            assertNull(feedTimeout, "the 30 second default applies to every other call")
        }
    }

    @Test
    fun theWireJsonOfTheItemIsTheBodyOfTheSingleWrite() {
        val dto = item("category", BatchOp.Upsert, "c1")
        val json = AppJson.encodeToString(BatchItemDto.serializer(), dto)
        assertEquals("""{"type":"category","op":"upsert","id":"c1","data":{"version":0}}""", json)
        assertTrue(JsonObject(emptyMap()).isEmpty())
    }

    // ---- Spec 011: heads, positions and the restore pair (contracts/sync-heads-client.md) ----

    @Test
    fun theHeadsAskForTwoHundredAndNothingElseOnTheFirstPage() = runTest {
        val env = Env { jsonResponse(200, OpenApiSyncExamples.HEADS_TWO_WALLETS) }
        env.signedIn()
        env.api.walletHeads(null)
        env.api.walletHeads("c2")

        val (first, second) = env.requests
        assertEquals(HttpMethod.Get, first.method)
        assertTrue(first.url.encodedPath.endsWith("/api/v1/sync/wallets/heads"), first.url.encodedPath)
        assertEquals(setOf("limit"), first.url.parameters.names(), "no cursor, filter or sort on the first page")
        assertEquals("200", first.url.parameters["limit"])
        assertEquals(setOf("limit", "cursor"), second.url.parameters.names())
        assertEquals("c2", second.url.parameters["cursor"])
        assertTrue(first.headers["Authorization"]!!.startsWith("Bearer "))
        listOf("X-Client-Platform", "X-Client-Version", "Accept-Language", "X-Correlation-Id").forEach { assertNotNull(first.headers[it], it) }
    }

    @Test
    fun theTwoWalletsExampleDecodesAndAnUnknownMemberIsIgnored() = runTest {
        val env = Env { jsonResponse(200, OpenApiSyncExamples.HEADS_TWO_WALLETS) }
        env.signedIn()
        val page = assertIs<ApiResult.Success<HeadsPageDto>>(env.api.walletHeads(null)).value
        assertEquals(listOf(812L, 133L), page.data.map { it.changeSeq })
        assertEquals(listOf(140L, 0L), page.data.map { it.purgedChangeSeq })
        assertEquals("0192f79e-0000-7000-8000-000000000001", page.data[0].walletId)
        assertFalse(page.meta.hasMore)
        assertNull(page.meta.nextCursor)
    }

    @Test
    fun aCallerInNoWalletGetsAnEmptyPage() = runTest {
        val env = Env { jsonResponse(200, """{"data":[],"meta":{"next_cursor":null,"has_more":false,"limit":200}}""") }
        env.signedIn()
        assertTrue(assertIs<ApiResult.Success<HeadsPageDto>>(env.api.walletHeads(null)).value.data.isEmpty())
    }

    @Test
    fun everyHeadsRefusalDecodesToItsCode() = runTest {
        val cases = listOf(
            Triple(400, "INVALID_CURSOR", ApiErrorCode.INVALID_CURSOR),
            Triple(400, "INVALID_QUERY_PARAMETER", ApiErrorCode.UNKNOWN),
            Triple(400, "FILTER_FIELD_NOT_SUPPORTED", ApiErrorCode.UNKNOWN),
            Triple(400, "SORT_FIELD_NOT_SUPPORTED", ApiErrorCode.UNKNOWN),
            Triple(401, "UNAUTHENTICATED", ApiErrorCode.UNAUTHENTICATED),
            Triple(406, "NOT_ACCEPTABLE", ApiErrorCode.NOT_ACCEPTABLE),
            Triple(426, "CLIENT_VERSION_UNSUPPORTED", ApiErrorCode.CLIENT_VERSION_UNSUPPORTED),
            Triple(429, "RATE_LIMITED", ApiErrorCode.RATE_LIMITED),
            Triple(500, "INTERNAL_ERROR", ApiErrorCode.INTERNAL_ERROR),
        )
        for ((status, code, expected) in cases) {
            val env = Env { jsonResponse(status, problemBody(status, code, if (status == 426) ""","minimum_version":"2.0.0"""" else ""), *(if (status == 429) arrayOf("Retry-After" to "7") else emptyArray())) }
            env.signedIn()
            val failure = assertIs<ApiResult.Failure>(env.api.walletHeads(null), "$status $code")
            assertEquals(expected, failure.code, "$status $code")
            assertEquals(code, failure.problem.code)
            if (status == 429) assertEquals(7L, failure.retryAfterSeconds)
        }
    }

    @Test
    fun offlineAndUnreadableHeadsAreTold() = runTest {
        val offline = Env { throw kotlinx.io.IOException("no route") }
        offline.signedIn()
        assertEquals(ApiResult.Offline, offline.api.walletHeads(null))
        val unreadable = Env { jsonResponse(200, """{"data":"nope","meta":{}}""") }
        unreadable.signedIn()
        assertEquals(HttpSyncApi.UNREADABLE, assertIs<ApiResult.Failure>(unreadable.api.walletHeads(null)).problem.code)
    }

    @Test
    fun feedPagesDecodeTheirIntegerPositionAndAPageWithoutOneDecodesToNull() = runTest {
        val user = Env { jsonResponse(200, OpenApiSyncExamples.USER_FEED_PAGE) }
        user.signedIn()
        assertEquals(59L, assertIs<ApiResult.Success<FeedPageDto>>(user.api.userChanges(null)).value.meta.syncPosition)
        val wallet = Env { jsonResponse(200, OpenApiSyncExamples.WALLET_FEED_PAGE) }
        wallet.signedIn()
        assertEquals(133L, assertIs<ApiResult.Success<FeedPageDto>>(wallet.api.walletChanges("w1", null)).value.meta.syncPosition)
        val older = Env { jsonResponse(200, """{"data":[],"meta":{"next_cursor":null,"has_more":false,"limit":200,"sync_cursor":"c"}}""") }
        older.signedIn()
        assertNull(assertIs<ApiResult.Success<FeedPageDto>>(older.api.userChanges(null)).value.meta.syncPosition)
    }

    @Test
    fun noFeedOrHeadsRequestEverCarriesAPosition() = runTest {
        val env = Env { request ->
            jsonResponse(200, if (request.url.encodedPath.endsWith("heads")) OpenApiSyncExamples.HEADS_TWO_WALLETS else OpenApiSyncExamples.USER_FEED_PAGE)
        }
        env.signedIn()
        env.api.userChanges("c")
        env.api.walletChanges("w1", "c")
        env.api.walletHeads("c")
        env.requests.forEach { request ->
            assertTrue(request.url.parameters.names().all { it in setOf("limit", "cursor") }, request.url.toString())
        }
    }

    @Test
    fun aBatchBodyCarryingARestorePairHasRestoreThenUpsertWithTheirVersions() = runTest {
        val env = Env { jsonResponse(200, OpenApiSyncExamples.VALIDATION_REFUSAL) }
        env.signedIn()
        val restore = BatchItemDto("transaction", BatchOp.Restore.wire, "t1", "w1", buildJsonObject { put("deleted_at", JsonNull); put("version", JsonPrimitive(4)) })
        val upsert = BatchItemDto("transaction", BatchOp.Upsert.wire, "t1", "w1", buildJsonObject { put("amount_minor", JsonPrimitive(900)); put("version", JsonPrimitive(5)) })
        env.api.batch(listOf(restore, upsert))

        val items = (env.requests.single().jsonBody()["items"] as kotlinx.serialization.json.JsonArray).map { it.jsonObject }
        assertEquals(listOf("restore", "upsert"), items.map { it["op"]!!.jsonPrimitive.content })
        assertEquals(listOf("w1", "w1"), items.map { it["wallet_id"]!!.jsonPrimitive.content })
        assertEquals(JsonNull, items[0]["data"]!!.jsonObject["deleted_at"])
        assertEquals(JsonPrimitive(4), items[0]["data"]!!.jsonObject["version"])
        assertEquals(JsonPrimitive(5), items[1]["data"]!!.jsonObject["version"])
    }
}
