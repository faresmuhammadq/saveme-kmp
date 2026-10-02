package com.fares.saveme.feature.billing.data

import app.cash.turbine.test
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.core.network.ApiEvent
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.data.dto.EntitlementSummaryDto
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.billing.BillingTestEnv
import com.fares.saveme.feature.billing.FREE_ID
import com.fares.saveme.feature.billing.PLAY_PRICE
import com.fares.saveme.feature.billing.PLUS_ID
import com.fares.saveme.feature.billing.entitlementsBody
import com.fares.saveme.feature.billing.freeSubscriptionBody
import com.fares.saveme.feature.billing.planJson
import com.fares.saveme.feature.billing.plansBody
import com.fares.saveme.feature.billing.plusSubscriptionBody
import com.fares.saveme.feature.billing.seedPlansBody
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillingContractTest {

    @Test
    fun listPlansIsPublicAndDecodesInOrder() = runTest {
        val env = BillingTestEnv { jsonResponse(200, seedPlansBody()) }
        env.signedIn()

        val plans = assertIs<ApiResult.Success<List<com.fares.saveme.feature.billing.data.dto.PlanDto>>>(env.api.listPlans()).value

        val request = env.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertTrue(request.url.encodedPath.endsWith("/api/v1/plans"))
        assertNull(request.headers["Authorization"], "the catalogue is public")
        assertEquals(env.auth.clientInfo.languageTag(), request.headers["Accept-Language"])
        assertEquals(listOf("Free", "Plus · Monthly", "Plus · Yearly"), plans.map { it.name })
        assertEquals(JsonNull, plans.first().features["wallets.max"])
        assertEquals(listOf("instapay"), plans[1].prices.map { it.channel })
    }

    @Test
    fun anUnknownMemberAndAnUnknownCapabilityAreIgnored() = runTest {
        val body = plansBody(planJson(FREE_ID, "Free", "lifetime", features = """{"future.thing":true,"wallets.max":3}""", prices = "[$PLAY_PRICE]"))
        val env = BillingTestEnv { jsonResponse(200, body) }
        val plan = assertIs<ApiResult.Success<List<com.fares.saveme.feature.billing.data.dto.PlanDto>>>(env.api.listPlans()).value.single()
        assertEquals(14_900L, plan.prices.single().amountMinor)
        assertEquals(JsonPrimitive(3), plan.features["wallets.max"])
    }

    @Test
    fun listPlansOfflineIsOffline() = runTest {
        val env = BillingTestEnv { throw kotlinx.io.IOException("no route") }
        assertEquals(ApiResult.Offline, env.api.listPlans())
    }

    @Test
    fun mySubscriptionIsCredentialedAndDecodesPaidAndFree() = runTest {
        var body = plusSubscriptionBody()
        val env = BillingTestEnv { jsonResponse(200, body) }
        env.signedIn()

        val paid = assertIs<ApiResult.Success<com.fares.saveme.feature.billing.data.dto.MySubscriptionDto>>(env.api.mySubscription()).value
        body = freeSubscriptionBody()
        val free = assertIs<ApiResult.Success<com.fares.saveme.feature.billing.data.dto.MySubscriptionDto>>(env.api.mySubscription()).value

        val request = env.requests.first()
        assertTrue(request.url.encodedPath.endsWith("/api/v1/me/subscription"))
        assertTrue(request.headers["Authorization"]!!.startsWith("Bearer "))
        assertEquals("active", paid.status)
        assertEquals(true, paid.autoRenew)
        assertEquals(PLUS_ID, paid.plan.id)
        assertNull(free.status)
        assertNull(free.currentPeriodEnd)
        assertNull(free.autoRenew)
    }

    @Test
    fun entitlementsAreCredentialedAndANullQuotaSurvives() = runTest {
        val env = BillingTestEnv { jsonResponse(200, entitlementsBody()) }
        env.signedIn()

        val summary = assertIs<ApiResult.Success<EntitlementSummaryDto>>(env.auth.entitlementsApi.fetch()).value

        val request = env.requests.single()
        assertTrue(request.url.encodedPath.endsWith("/api/v1/me/entitlements"))
        assertTrue(request.headers["Authorization"]!!.startsWith("Bearer "))
        assertEquals(JsonNull, summary.features["wallets.max"])
        assertEquals(JsonPrimitive(5), summary.features["sharing.max_members_per_wallet"])
    }

    @Test
    fun unauthenticatedEndsTheSessionOnBothCredentialedCalls() = runTest {
        val env = BillingTestEnv { jsonResponse(401, problemBody(401, "UNAUTHENTICATED")) }
        env.signedIn()
        env.events.events.test {
            env.api.mySubscription()
            assertEquals(ApiEvent.SessionEnded, awaitItem())
            env.auth.entitlementsApi.fetch()
            assertEquals(ApiEvent.SessionEnded, awaitItem())
        }
    }

    @Test
    fun featureNotEntitledPublishesTheEventAndKeepsItsCode() = runTest {
        val env = BillingTestEnv { jsonResponse(403, problemBody(403, "FEATURE_NOT_ENTITLED")) }
        env.signedIn()
        env.events.events.test {
            val failure = assertIs<ApiResult.Failure>(env.api.mySubscription())
            assertEquals(ApiErrorCode.FEATURE_NOT_ENTITLED, failure.code)
            assertEquals(ApiEvent.FeatureNotEntitled, awaitItem())
        }
    }
}
