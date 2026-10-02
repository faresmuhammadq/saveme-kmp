package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.Page
import com.fares.saveme.feature.categories.data.dto.CategoryReferenceDto
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpMethod

/** `GET /wallets/{id}/category-references` of categories.yaml: a wallet's partner categories, read-only. */
class CategoryReferenceApi(private val client: ApiClient) {

    suspend fun listReferences(walletId: String, cursor: String?): ApiResult<Page<CategoryReferenceDto>> = client.callPage {
        method = HttpMethod.Get
        url("wallets/$walletId/category-references")
        parameter("limit", CategoryApi.PAGE_SIZE)
        if (cursor != null) parameter("cursor", cursor)
    }
}
