package com.fares.saveme.feature.sharing.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.Page
import com.fares.saveme.feature.sharing.data.dto.ActivityEntryDto
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/** `GET /wallets/{id}/activity` (A9): online-only, not stored; the cursor is opaque. */
class ActivityApi(private val client: ApiClient) {

    suspend fun page(walletId: String, cursor: String?): ApiResult<Page<ActivityEntryDto>> = client.callPage {
        method = HttpMethod.Get
        url("wallets/$walletId/activity")
        parameter("limit", PAGE_LIMIT)
        if (cursor != null) parameter("cursor", cursor)
    }

    companion object {
        const val PAGE_LIMIT = 50
    }
}
