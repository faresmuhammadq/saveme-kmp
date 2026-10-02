package com.fares.saveme.feature.billing.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.publicEndpoint
import com.fares.saveme.feature.billing.data.dto.MySubscriptionDto
import com.fares.saveme.feature.billing.data.dto.PlanDto
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/** Plans & Billing reads (contracts/billing-api-client.md). No retry beyond the client's own policy (FR-020). */
class BillingApi(private val client: ApiClient) {

    /** Public: the catalogue needs no session. Names come in `Accept-Language`. */
    suspend fun listPlans(): ApiResult<List<PlanDto>> = client.call {
        method = HttpMethod.Get
        url("plans")
        publicEndpoint()
    }

    suspend fun mySubscription(): ApiResult<MySubscriptionDto> = client.call {
        method = HttpMethod.Get
        url("me/subscription")
    }
}
