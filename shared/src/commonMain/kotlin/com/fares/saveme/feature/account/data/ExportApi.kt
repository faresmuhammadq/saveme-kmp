package com.fares.saveme.feature.account.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.Page
import com.fares.saveme.feature.account.data.dto.ExportDto
import com.fares.saveme.feature.account.data.dto.ExportRequestDto
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * The export endpoints of exports.yaml (backend spec 022). All credentialed, none carries an `Idempotency-Key`: a retried
 * request meets the export it created as `current`. The signed file route in `download_url` is opened by the system browser
 * only; this client never requests it.
 */
class ExportApi(private val client: ApiClient) {

    suspend fun requestExport(body: ExportRequestDto): ApiResult<ExportDto> = client.call {
        method = HttpMethod.Post
        url("exports")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    suspend fun listExports(cursor: String?, limit: Int = PAGE_SIZE): ApiResult<Page<ExportDto>> = client.callPage {
        method = HttpMethod.Get
        url("exports")
        parameter("limit", limit)
        if (cursor != null) parameter("cursor", cursor)
    }

    suspend fun getExport(id: String): ApiResult<ExportDto> = client.call {
        method = HttpMethod.Get
        url("exports/$id")
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
