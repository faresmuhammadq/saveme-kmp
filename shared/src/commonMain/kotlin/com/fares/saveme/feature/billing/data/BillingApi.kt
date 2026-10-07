package com.fares.saveme.feature.billing.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.idempotencyKey
import com.fares.saveme.core.network.publicEndpoint
import com.fares.saveme.feature.billing.data.dto.MySubscriptionDto
import com.fares.saveme.feature.billing.data.dto.PlanDto
import com.fares.saveme.feature.billing.data.dto.PlayAccountReferenceDto
import com.fares.saveme.feature.billing.data.dto.PlayPurchaseRequestDto
import com.fares.saveme.feature.billing.data.dto.PlayPurchaseResultDto
import com.fares.saveme.feature.billing.data.dto.PlayRestoreRequestDto
import com.fares.saveme.feature.billing.data.dto.PlayRestoreResultDto
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

    /**
     * Sends one Google Play purchase (spec 018, `google-play.yaml` v1.15). Plain strings: `data` never imports `domain` (P-10.6).
     * A 200 and a 202 share one body; the caller tells them apart by `purchase.state` (P-02.4). [idempotencyKey] is derived by the
     * caller and reused across retries.
     */
    suspend fun verifyPlayPurchase(token: String, productId: String, idempotencyKey: String): ApiResult<PlayPurchaseResultDto> =
        client.call {
            method = HttpMethod.Post
            url("billing/google/purchases")
            idempotencyKey(idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(PlayPurchaseRequestDto(token, productId))
        }

    /** Sends up to 25 owned purchases, `(token, productId)` pairs with distinct tokens (spec 018 FR-022). */
    suspend fun restorePlayPurchases(items: List<Pair<String, String>>, idempotencyKey: String): ApiResult<PlayRestoreResultDto> =
        client.call {
            method = HttpMethod.Post
            url("billing/google/restore")
            idempotencyKey(idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(PlayRestoreRequestDto(items.map { (token, productId) -> PlayPurchaseRequestDto(token, productId) }))
        }

    /** The value US6 passes to Google Play as the obfuscated account id. No reachable code calls it while the switch is off (FR-040). */
    suspend fun playAccountReference(): ApiResult<PlayAccountReferenceDto> = client.call {
        method = HttpMethod.Get
        url("billing/google/account-reference")
    }
}
