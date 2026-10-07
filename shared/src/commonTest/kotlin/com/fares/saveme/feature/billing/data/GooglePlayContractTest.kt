package com.fares.saveme.feature.billing.data

import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.billing.BillingTestEnv
import com.fares.saveme.feature.billing.bodyText
import com.fares.saveme.feature.billing.data.dto.PlayPurchaseResultDto
import com.fares.saveme.feature.billing.data.dto.PlayRestoreResultDto
import com.fares.saveme.feature.billing.data.play.PlayPurchaseState
import com.fares.saveme.feature.billing.data.play.PurchaseToken
import com.fares.saveme.feature.billing.domain.play.PlayKeys
import com.fares.saveme.feature.billing.play.GooglePlayFixtures
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spec 018 contracts/google-play-api-client.md: requests and answers of `google-play.yaml` v1.15 through the real client (P-14.3). */
class GooglePlayContractTest {

    private val key = PlayKeys.verify("user-1", PurchaseToken(GooglePlayFixtures.TOKEN), PlayPurchaseState.Purchased)

    private suspend fun env(answer: suspend MockRequestHandleScope.() -> HttpResponseData): BillingTestEnv =
        BillingTestEnv { answer() }.also { it.signedIn() }

    private suspend fun verify(answer: suspend MockRequestHandleScope.() -> HttpResponseData): Pair<BillingTestEnv, ApiResult<PlayPurchaseResultDto>> {
        val env = env(answer)
        return env to env.api.verifyPlayPurchase(GooglePlayFixtures.TOKEN, "plus_monthly", key)
    }

    private suspend fun failure(status: Int, body: String, vararg headers: Pair<String, String>): ApiResult.Failure =
        assertIs<ApiResult.Failure>(verify { jsonResponse(status, body, *headers) }.second)

    @Test
    fun verifyPostsTheTwoMembersWithAnIdempotencyKeyAndTheStandardHeaders() = runTest {
        val (env, _) = verify { jsonResponse(200, GooglePlayFixtures.active()) }
        val request = env.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/billing/google/purchases"))
        assertNotNull(request.headers["Authorization"])
        val sent = request.headers.getAll("Idempotency-Key")!!.single()
        assertEquals(key, sent)
        assertTrue(Regex("^gp1:[0-9a-f]{64}$").matches(sent) && sent.length == 68, sent)
        val body = Json.parseToJsonElement(request.bodyText()).jsonObject
        assertEquals(setOf("purchase_token", "product_id"), body.keys)
        assertTrue(request.body.contentType.toString().startsWith("application/json"))
        assertNotNull(request.headers["X-Correlation-Id"])
        assertFalse(request.url.toString().contains(GooglePlayFixtures.TOKEN), "the token is in the body only")
        request.headers.entries().forEach { (_, values) -> values.forEach { assertFalse(it.contains(GooglePlayFixtures.TOKEN)) } }
    }

    @Test
    fun theActiveAndAckPendingExamplesDecode() = runTest {
        val active = assertIs<ApiResult.Success<PlayPurchaseResultDto>>(verify { jsonResponse(200, GooglePlayFixtures.active()) }.second).value
        assertEquals("active", active.purchase.state)
        assertTrue(active.purchase.acknowledged)
        assertEquals("google_play", active.subscription.provider)
        assertEquals("Plus · Monthly", active.entitlements.plan.name)
        val ackPending = assertIs<ApiResult.Success<PlayPurchaseResultDto>>(verify { jsonResponse(200, GooglePlayFixtures.ackPending()) }.second).value
        assertFalse(ackPending.purchase.acknowledged)
        assertEquals("plus_yearly", ackPending.purchase.productId)
    }

    @Test
    fun theAcceptedPendingExampleDecodesAsASuccess() = runTest {
        val pending = assertIs<ApiResult.Success<PlayPurchaseResultDto>>(verify { jsonResponse(202, GooglePlayFixtures.pending()) }.second).value
        assertEquals("pending", pending.purchase.state)
        assertNull(pending.purchase.currentPeriodEnd)
        assertNull(pending.subscription.provider)
    }

    @Test
    fun anUnknownStateOrMemberDecodesWithoutError() = runTest {
        val body = GooglePlayFixtures.active(state = "something_new")
        val result = assertIs<ApiResult.Success<PlayPurchaseResultDto>>(verify { jsonResponse(200, body) }.second).value
        assertEquals("something_new", result.purchase.state)
    }

    @Test
    fun purchaseAlreadyLinkedIs409AndNeverRetried() = runTest {
        val refused = failure(409, GooglePlayFixtures.alreadyLinked())
        assertEquals(ApiErrorCode.PURCHASE_ALREADY_LINKED, refused.code)
        assertEquals("req-42", refused.problem.requestId)
    }

    @Test
    fun purchaseNotVerifiedIs422() = runTest {
        assertEquals(ApiErrorCode.PURCHASE_NOT_VERIFIED, failure(422, GooglePlayFixtures.notVerified()).code)
    }

    @Test
    fun anUnknownProductIsValidationFailedAtProductId() = runTest {
        val refused = failure(422, GooglePlayFixtures.unknownProduct())
        assertEquals(ApiErrorCode.VALIDATION_FAILED, refused.code)
        assertEquals("/product_id", refused.problem.errors!!.single().pointer)
    }

    @Test
    fun inProgressRateLimitedAndUnavailableCarryTheirWait() = runTest {
        val inProgress = failure(409, GooglePlayFixtures.inProgress(), "Retry-After" to "3")
        assertEquals(ApiErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS, inProgress.code)
        assertEquals(3L, inProgress.retryAfterSeconds)
        val limited = failure(429, GooglePlayFixtures.rateLimited())
        assertEquals(ApiErrorCode.RATE_LIMITED, limited.code)
        assertEquals(120L, limited.retryAfterSeconds)
        val down = failure(503, GooglePlayFixtures.unavailable())
        assertEquals(ApiErrorCode.SERVICE_UNAVAILABLE, down.code)
        assertEquals(45L, down.retryAfterSeconds)
    }

    @Test
    fun aReusedKeyIs409AndTheSessionAndUpdateAnswersAreRecognised() = runTest {
        assertEquals(ApiErrorCode.IDEMPOTENCY_KEY_REUSED, failure(409, GooglePlayFixtures.keyReused()).code)
        assertEquals(ApiErrorCode.UNAUTHENTICATED, failure(401, GooglePlayFixtures.unauthenticated()).code)
        val update = failure(426, GooglePlayFixtures.upgradeRequired())
        assertEquals(426, update.problem.status)
    }

    @Test
    fun restorePostsTheItemsWithOneKeyAndDecodesTheRestoredExample() = runTest {
        val env = env { jsonResponse(200, GooglePlayFixtures.restored()) }
        val items = listOf(GooglePlayFixtures.RESTORE_TOKEN_ONE to "plus_monthly", GooglePlayFixtures.RESTORE_TOKEN_TWO to "plus_yearly")
        val result = assertIs<ApiResult.Success<PlayRestoreResultDto>>(env.api.restorePlayPurchases(items, "attempt-key-0001-abcdef")).value
        val request = env.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/api/v1/billing/google/restore"))
        assertEquals("attempt-key-0001-abcdef", request.headers["Idempotency-Key"])
        val purchases = Json.parseToJsonElement(request.bodyText()).jsonObject["purchases"]!!.toString()
        assertTrue(purchases.contains(GooglePlayFixtures.RESTORE_TOKEN_TWO))
        assertEquals(listOf("linked", "linked_elsewhere"), result.results.map { it.outcome })
        assertEquals(1, result.results[1].index)
        assertEquals("google_play", result.subscription.provider)
    }

    @Test
    fun restoreSendsZeroOneAndTwentyFiveItems() = runTest {
        listOf(0, 1, 25).forEach { count ->
            val env = env { jsonResponse(200, GooglePlayFixtures.restoreOf()) }
            env.api.restorePlayPurchases(List(count) { "t-$it" to "plus_monthly" }, "k".repeat(20))
            val body = Json.parseToJsonElement(env.requests.single().bodyText()).jsonObject
            assertEquals(setOf("purchases"), body.keys)
            assertEquals(count, (body["purchases"] as kotlinx.serialization.json.JsonArray).size)
        }
    }

    @Test
    fun anUnknownRestoreOutcomeAndMemberDecodeWithoutError() = runTest {
        val env = env { jsonResponse(200, GooglePlayFixtures.restoreOf("linked", "brand_new_outcome")) }
        val result = assertIs<ApiResult.Success<PlayRestoreResultDto>>(env.api.restorePlayPurchases(listOf("a" to "p", "b" to "p"), "k".repeat(20))).value
        assertEquals("brand_new_outcome", result.results[1].outcome)
    }

    @Test
    fun theAccountReferenceExampleDecodesAndOnlyAWellFormedValueIsAReference() = runTest {
        val env = env { jsonResponse(200, GooglePlayFixtures.reference()) }
        val read = assertIs<ApiResult.Success<com.fares.saveme.feature.billing.data.dto.PlayAccountReferenceDto>>(env.api.playAccountReference()).value
        assertTrue(read.isWellFormed)
        assertEquals("GET", env.requests.single().method.value)
        val short = env { jsonResponse(200, GooglePlayFixtures.reference(GooglePlayFixtures.REFERENCE.dropLast(1))) }
        assertFalse(assertIs<ApiResult.Success<com.fares.saveme.feature.billing.data.dto.PlayAccountReferenceDto>>(short.api.playAccountReference()).value.isWellFormed)
        val upper = env { jsonResponse(200, GooglePlayFixtures.reference(GooglePlayFixtures.REFERENCE.uppercase())) }
        assertFalse(assertIs<ApiResult.Success<com.fares.saveme.feature.billing.data.dto.PlayAccountReferenceDto>>(upper.api.playAccountReference()).value.isWellFormed)
        assertFalse(read.toString().contains(GooglePlayFixtures.REFERENCE))
    }
}
