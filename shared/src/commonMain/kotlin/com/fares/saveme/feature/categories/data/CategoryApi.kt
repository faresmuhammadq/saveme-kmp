package com.fares.saveme.feature.categories.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.Page
import com.fares.saveme.feature.categories.data.dto.CategoryDto
import com.fares.saveme.feature.categories.data.dto.CategoryVersionRequest
import com.fares.saveme.feature.categories.data.dto.CategoryWriteRequest
import com.fares.saveme.feature.categories.data.dto.PlatformCategoryDto
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * The category endpoints of categories.yaml (contracts/categories-api-client.md). All credentialed.
 * `PATCH /categories/{id}` and `GET /categories/{id}` are never called (research R-05).
 */
class CategoryApi(private val client: ApiClient) {

    suspend fun list(cursor: String?): ApiResult<Page<CategoryDto>> = client.callPage {
        method = HttpMethod.Get
        url("categories")
        parameter("limit", PAGE_SIZE)
        if (cursor != null) parameter("cursor", cursor)
    }

    /** Creates (version 0) or saves; the server answers an identical replay with 200. */
    suspend fun put(id: String, request: CategoryWriteRequest): ApiResult<CategoryDto> = client.call {
        method = HttpMethod.Put
        url("categories/$id")
        contentType(ContentType.Application.Json)
        setBody(request.toJson())
    }

    suspend fun archive(id: String, version: Long): ApiResult<CategoryDto> = client.call {
        method = HttpMethod.Post
        url("categories/$id/archive")
        contentType(ContentType.Application.Json)
        setBody(CategoryVersionRequest(version))
    }

    /** The version travels in a JSON body on DELETE (backend spec 011). */
    suspend fun delete(id: String, version: Long): ApiResult<CategoryDto> = client.call {
        method = HttpMethod.Delete
        url("categories/$id")
        contentType(ContentType.Application.Json)
        setBody(CategoryVersionRequest(version))
    }

    /** One page, no query parameters: the whole default set. */
    suspend fun listDefaults(): ApiResult<Page<PlatformCategoryDto>> = client.callPage {
        method = HttpMethod.Get
        url("platform-categories")
    }

    companion object {
        const val PAGE_SIZE = 200
    }
}
