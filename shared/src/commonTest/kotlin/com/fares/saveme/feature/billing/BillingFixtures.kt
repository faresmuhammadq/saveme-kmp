package com.fares.saveme.feature.billing

import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.signInSuccessBody
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.billing.data.BillingApi
import com.fares.saveme.feature.billing.data.CatalogueRepository
import com.fares.saveme.feature.billing.data.SubscriptionRepository
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock
import kotlin.time.Instant

const val FREE_ID = "01a0e69a-962e-7239-9101-0671bf5529d6"
const val PLUS_ID = "01a0e69a-962e-7239-9101-0671bf5529d7"

/** The launch seed's Free plan features (billing.yaml Features): `wallets.max` is an explicit null. */
const val FREE_FEATURES = """{"sharing.enabled":false,"sharing.max_members_per_wallet":1,"export.enabled":false,"reports.advanced":false,"wallets.max":null}"""
const val PLUS_FEATURES = """{"sharing.enabled":true,"sharing.max_members_per_wallet":5,"export.enabled":true,"reports.advanced":true,"wallets.max":null}"""

fun entitlementsBody(id: String = PLUS_ID, name: String = "Plus · Monthly", features: String = PLUS_FEATURES) =
    """{"data":{"plan":{"id":"$id","name":"$name"},"features":$features},"meta":{}}"""

fun planJson(
    id: String,
    name: String,
    periodType: String,
    isDefault: Boolean = false,
    features: String = FREE_FEATURES,
    prices: String = "[]",
    periodDays: Int? = null,
) = """{"id":"$id","name":"$name","period_type":"$periodType","period_days":${periodDays ?: "null"},"is_default":$isDefault,"features":$features,"prices":$prices,"something_new":1}"""

const val INSTAPAY_PRICE = """{"channel":"instapay","product_id":null,"amount_minor":9900,"currency":"EGP"}"""
const val PLAY_PRICE = """{"channel":"google_play","product_id":"plus_monthly","amount_minor":14900,"currency":"EGP"}"""

fun plansBody(vararg plans: String) = """{"data":[${plans.joinToString(",")}],"meta":{"next_cursor":null,"has_more":false,"limit":50}}"""

/** The launch seed: Free, Plus · Monthly and Plus · Yearly with InstaPay prices only. */
fun seedPlansBody() = plansBody(
    planJson(FREE_ID, "Free", "lifetime", isDefault = true),
    planJson(PLUS_ID, "Plus · Monthly", "monthly", features = PLUS_FEATURES, prices = "[$INSTAPAY_PRICE]"),
    planJson("01a0e69a-962e-7239-9101-0671bf5529d8", "Plus · Yearly", "yearly", features = PLUS_FEATURES, prices = "[$INSTAPAY_PRICE]"),
)

fun freeSubscriptionBody() =
    """{"data":{"plan":{"id":"$FREE_ID","name":"Free"},"provider":null,"status":null,"current_period_start":null,"current_period_end":null,"grace_until":null,"auto_renew":null},"meta":{}}"""

fun plusSubscriptionBody(status: String = "active", end: String? = "2026-11-01T00:00:00Z", graceUntil: String? = null, autoRenew: Boolean = true) =
    """{"data":{"plan":{"id":"$PLUS_ID","name":"Plus · Monthly"},"provider":"manual","status":"$status","current_period_start":"2026-10-02T00:00:00Z",
        "current_period_end":${end?.let { "\"$it\"" } ?: "null"},"grace_until":${graceUntil?.let { "\"$it\"" } ?: "null"},"auto_renew":$autoRenew,"something_new":true},"meta":{}}"""

class MutableClock(var instant: Instant = Instant.parse("2026-10-02T10:00:00Z")) : Clock {
    override fun now(): Instant = instant
}

class BillingTestEnv(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
    val auth = AuthTestEnv(handler)
    val api = BillingApi(auth.client)
    val subscriptions = SubscriptionRepository(api, auth.keyValues)
    val catalogue = CatalogueRepository(api, auth.keyValues)
    val requests get() = auth.requests
    val events get() = auth.events

    suspend fun signedIn() = auth.secureStore.write(StoredSession(CREDENTIAL, "a", Instant.parse("2026-09-26T10:00:00Z")))

    fun sessionManager(scope: CoroutineScope) = SessionManager(auth.repository, auth.entitlementsApi, auth.events, scope)
}

const val USER_ID = "0192f7a0-0000-7000-8000-000000000001"
const val OTHER_USER_ID = "0192f7a0-0000-7000-8000-000000000002"

/** Routes the three billing reads and sign-in; [gate] holds a path's answer until the test opens it. */
class BillingRoutes(
    var entitlements: String = entitlementsBody(),
    var subscription: String = plusSubscriptionBody(),
    var plans: String = seedPlansBody(),
    var gatedPaths: Set<String> = emptySet(),
) {
    val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
    val started = kotlinx.coroutines.CompletableDeferred<Unit>()

    suspend fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath
        if (gatedPaths.any { path.endsWith(it) }) {
            started.complete(Unit)
            gate.await()
        }
        return when {
            path.endsWith("/auth/login") -> jsonResponse(200, signInSuccessBody())
            path.endsWith("/me/entitlements") -> jsonResponse(200, entitlements)
            path.endsWith("/me/subscription") -> jsonResponse(200, subscription)
            path.endsWith("/plans") -> jsonResponse(200, plans)
            else -> error("unexpected $path")
        }
    }
}

fun HttpRequestData.endsWithPath(suffix: String) = url.encodedPath.endsWith(suffix)

fun List<HttpRequestData>.count(suffix: String) = count { it.endsWithPath(suffix) }

/** Real-time wait for a mock engine that answers on its own threads. */
suspend fun waitUntil(condition: () -> Boolean) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        kotlinx.coroutines.withTimeout(10_000) { while (!condition()) kotlinx.coroutines.delay(5) }
    }
}

suspend fun BillingTestEnv.signIn(manager: SessionManager) {
    com.fares.saveme.feature.auth.domain.usecase.SignInUseCase(
        auth.clientInfo,
        com.fares.saveme.core.storage.DeviceIdentityStore(auth.keyValues),
        auth.repository,
        manager,
    )("sara@example.com", "pw", "0192f7a0-1c3d-7abc-9f01-2b3c4d5e6f70")
}

object EntitlementSetFixtures {
    val free = com.fares.saveme.feature.billing.domain.EntitlementSet(
        mapOf(
            "sharing.enabled" to com.fares.saveme.feature.auth.domain.model.FeatureValue.Flag(false),
            "sharing.max_members_per_wallet" to com.fares.saveme.feature.auth.domain.model.FeatureValue.Quota(1),
            "wallets.max" to com.fares.saveme.feature.auth.domain.model.FeatureValue.Quota(null),
        ),
    )
    val plus = com.fares.saveme.feature.billing.domain.EntitlementSet(
        mapOf(
            "sharing.enabled" to com.fares.saveme.feature.auth.domain.model.FeatureValue.Flag(true),
            "sharing.max_members_per_wallet" to com.fares.saveme.feature.auth.domain.model.FeatureValue.Quota(5),
            "export.enabled" to com.fares.saveme.feature.auth.domain.model.FeatureValue.Flag(true),
            "reports.advanced" to com.fares.saveme.feature.auth.domain.model.FeatureValue.Flag(true),
            "wallets.max" to com.fares.saveme.feature.auth.domain.model.FeatureValue.Quota(null),
        ),
    )
}
