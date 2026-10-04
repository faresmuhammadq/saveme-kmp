package com.fares.saveme.feature.budgets.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.Page
import com.fares.saveme.feature.budgets.data.dto.BudgetDto
import com.fares.saveme.feature.budgets.data.dto.BudgetProgressDto
import com.fares.saveme.feature.budgets.data.dto.BudgetVersionRequest
import com.fares.saveme.feature.budgets.data.dto.BudgetWriteRequest
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * The budget endpoints of budgets.yaml v1.8 (contracts/budgets-api-client.md). All credentialed. `GET /budgets`,
 * `GET /budgets/{id}` (the user feed carries budgets), `PATCH /budgets/{id}` (a limit edit is a whole save) and the
 * `date` query parameter (current period only, FR-011) are never used.
 */
class BudgetApi(private val client: ApiClient) {

    /** Creates (version 0) or saves; the server answers an identical replay with 200. */
    suspend fun put(id: String, request: BudgetWriteRequest): ApiResult<BudgetDto> = client.call {
        method = HttpMethod.Put
        url("budgets/$id")
        contentType(ContentType.Application.Json)
        setBody(request.toJson())
    }

    /** The version travels in a JSON body on DELETE. */
    suspend fun delete(id: String, version: Long): ApiResult<BudgetDto> = client.call {
        method = HttpMethod.Delete
        url("budgets/$id")
        contentType(ContentType.Application.Json)
        setBody(BudgetVersionRequest(version))
    }

    /** Per record only: archive is not a batch operation. */
    suspend fun archive(id: String, version: Long): ApiResult<BudgetDto> = client.call {
        method = HttpMethod.Post
        url("budgets/$id/archive")
        contentType(ContentType.Application.Json)
        setBody(BudgetVersionRequest(version))
    }

    /** One page of the active budgets' progress for today's period: no `date`, `filter` or `sort`. */
    suspend fun progressPage(cursor: String?): ApiResult<Page<BudgetProgressDto>> = client.callPage {
        method = HttpMethod.Get
        url("budget-progress")
        parameter("limit", PAGE_LIMIT)
        if (cursor != null) parameter("cursor", cursor)
    }

    /** One budget's progress for today's period (archived ones included). */
    suspend fun progressOne(id: String): ApiResult<BudgetProgressDto> = client.call {
        method = HttpMethod.Get
        url("budgets/$id/progress")
    }

    companion object {
        const val PAGE_LIMIT = 200
    }
}
