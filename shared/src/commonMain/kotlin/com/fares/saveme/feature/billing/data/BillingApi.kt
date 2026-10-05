package com.fares.saveme.feature.billing.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.idempotencyKey
import com.fares.saveme.core.network.publicEndpoint
import com.fares.saveme.feature.billing.data.dto.MySubscriptionDto
import com.fares.saveme.feature.billing.data.dto.PlanDto
import com.fares.saveme.feature.billing.data.dto.RedeemRequestDto
import com.fares.saveme.feature.billing.data.dto.RedeemResultDto
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

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

    /**
     * Redeems a mailed code (spec 015). Plain strings: `data` never imports `domain` (P-10.6). Never retried;
     * [idempotencyKey] is one attempt's identifier. The claim endpoint is web-only and never called (P-12.4).
     */
    suspend fun redeem(code: String, idempotencyKey: String): ApiResult<RedeemResultDto> = client.call {
        method = HttpMethod.Post
        url("billing/redeem")
        idempotencyKey(idempotencyKey)
        contentType(ContentType.Application.Json)
        setBody(RedeemRequestDto(code))
    }
}
