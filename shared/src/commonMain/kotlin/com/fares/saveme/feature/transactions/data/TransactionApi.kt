package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.transactions.data.dto.TransactionDto
import com.fares.saveme.feature.transactions.data.dto.TransactionRestoreRequest
import com.fares.saveme.feature.transactions.data.dto.TransactionWriteRequest
import com.fares.saveme.feature.transactions.data.dto.VersionOnlyRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * The transaction endpoints of transactions.yaml (contracts/transactions-api-client.md). All
 * credentialed. `GET /wallets/{id}/transactions/{tid}` is never called (research R-05).
 */
class TransactionApi(private val client: ApiClient) {

    /** Creates (version 0) or saves; the server answers an identical replay with 200. */
    suspend fun put(walletId: String, id: String, request: TransactionWriteRequest): ApiResult<TransactionDto> = client.call {
        method = HttpMethod.Put
        url("wallets/$walletId/transactions/$id")
        contentType(ContentType.Application.Json)
        setBody(request.toJson())
    }

    /** Brings a deleted transaction back (`PATCH {"deleted_at": null, "version": n}`). */
    suspend fun restore(walletId: String, id: String, version: Long): ApiResult<TransactionDto> = client.call {
        method = HttpMethod.Patch
        url("wallets/$walletId/transactions/$id")
        contentType(ContentType.Application.Json)
        setBody(TransactionRestoreRequest(version).toJson())
    }

    /** The version travels in a JSON body on DELETE (backend spec 012). */
    suspend fun delete(walletId: String, id: String, version: Long): ApiResult<TransactionDto> = client.call {
        method = HttpMethod.Delete
        url("wallets/$walletId/transactions/$id")
        contentType(ContentType.Application.Json)
        setBody(VersionOnlyRequest(version))
    }
}
