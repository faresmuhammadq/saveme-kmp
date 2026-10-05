package com.fares.saveme.feature.billing.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.billing.BillingTestEnv
import com.fares.saveme.feature.billing.GROUPED_CODE
import com.fares.saveme.feature.billing.YEARLY_ID
import com.fares.saveme.feature.billing.bodyText
import com.fares.saveme.feature.billing.lifetimeStillGovernsBody
import com.fares.saveme.feature.billing.playStillGovernsBody
import com.fares.saveme.feature.billing.redeemedGovernsBody
import com.fares.saveme.feature.billing.data.dto.RedeemResultDto
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedeemContractTest {

    private val key = "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70"

    private suspend fun redeem(body: String): Pair<BillingTestEnv, ApiResult<RedeemResultDto>> {
        val env = BillingTestEnv { jsonResponse(200, body) }
        env.signedIn()
        return env to env.api.redeem(GROUPED_CODE, key)
    }

    @Test
    fun postsTheGroupedCodeWithTheAttemptKeyAndTheStandardHeaders() = runTest {
        val (env, _) = redeem(redeemedGovernsBody())
        val request = env.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/billing/redeem"))
        assertNotNull(request.headers["Authorization"])
        assertEquals(key, request.headers.getAll("Idempotency-Key")?.single())
        assertEquals("""{"code":"$GROUPED_CODE"}""", request.bodyText())
        assertTrue(request.body.contentType.toString().startsWith("application/json"))
        assertEquals(env.auth.clientInfo.languageTag(), request.headers["Accept-Language"])
        assertNotNull(request.headers["X-Correlation-Id"])
    }

    @Test
    fun redeemedGovernsDecodesAndIgnoresUnknownMembers() = runTest {
        val result = assertIs<ApiResult.Success<RedeemResultDto>>(redeem(redeemedGovernsBody()).second).value
        assertEquals("instapay", result.subscription.provider)
        assertEquals(YEARLY_ID, result.governing.plan.id)
        assertEquals(false, result.governing.autoRenew)
        assertEquals(JsonPrimitive(true), result.entitlements.features["sharing.enabled"])
        assertEquals(JsonNull, result.entitlements.features["wallets.max"])
    }

    @Test
    fun playStillGovernsDecodes() = runTest {
        val result = assertIs<ApiResult.Success<RedeemResultDto>>(redeem(playStillGovernsBody()).second).value
        assertEquals("google_play", result.governing.provider)
        assertEquals(true, result.governing.autoRenew)
        assertEquals("2026-11-05T12:00:00.000000Z", result.subscription.currentPeriodEnd)
    }

    @Test
    fun aLifetimeGoverningHasNoEnd() = runTest {
        val result = assertIs<ApiResult.Success<RedeemResultDto>>(redeem(lifetimeStillGovernsBody()).second).value
        assertNull(result.governing.currentPeriodEnd)
    }

    @Test
    fun theRequestDtoNeverPrintsTheCode() {
        assertTrue(!com.fares.saveme.feature.billing.data.dto.RedeemRequestDto(GROUPED_CODE).toString().contains("7KQ2"))
    }
}
