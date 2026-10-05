package com.fares.saveme.feature.billing

import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.billing.domain.EntitlementSet
import com.fares.saveme.feature.billing.domain.Entitlements
import com.fares.saveme.feature.billing.domain.FlagFeature
import com.fares.saveme.feature.billing.domain.QuotaFeature
import com.fares.saveme.feature.billing.domain.QuotaLimit
import com.fares.saveme.feature.billing.domain.RedeemService
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Counts the post-redemption re-read and records what it found, so order can be asserted. */
class RecordingEntitlements(private val onRefresh: () -> Unit = {}) : Entitlements {
    var refreshes = 0
    var outcome = RefreshOutcome.Done
    override val state: StateFlow<EntitlementSet?> = MutableStateFlow(null)
    override fun can(flag: FlagFeature) = false
    override fun quota(quota: QuotaFeature): QuotaLimit = QuotaLimit.Limited(0)
    override fun allowsAnother(quota: QuotaFeature, currentCount: Int) = false
    override suspend fun refreshAfterBillingAction(): RefreshOutcome {
        refreshes++
        onRefresh()
        return outcome
    }
}

/** A signed-in account whose `POST billing/redeem` answers whatever [redeem] says. */
class RedeemTestEnv(scope: CoroutineScope, redeem: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { jsonResponse(200, redeemedGovernsBody()) }) {
    var redeemHandler = redeem
    private val routes = BillingRoutes()
    val env = BillingTestEnv { request ->
        if (request.endsWithPath("/billing/redeem")) redeemHandler(this, request) else with(routes) { answer(request) }
    }
    val sessions = env.sessionManager(scope)
    val entitlements = RecordingEntitlements()
    val service = RedeemService(env.api, sessions, env.subscriptions, entitlements, scope)

    suspend fun signIn() = env.signIn(sessions)

    val redeemRequests: List<HttpRequestData> get() = env.requests.filter { it.endsWithPath("/billing/redeem") }
}

fun HttpRequestData.bodyText(): String = (body as TextContent).text
