package com.fares.saveme.feature.auth.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.data.dto.EntitlementSummaryDto
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/** `GET /me/entitlements` (billing.yaml): the same object sign-in returns. Auth owns the cached snapshot (research R-02). */
class EntitlementsApi(private val client: ApiClient) {

    suspend fun fetch(): ApiResult<EntitlementSummaryDto> = client.call {
        method = HttpMethod.Get
        url("me/entitlements")
    }
}
