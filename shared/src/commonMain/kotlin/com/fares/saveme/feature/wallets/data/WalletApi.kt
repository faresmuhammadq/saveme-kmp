package com.fares.saveme.feature.wallets.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.wallets.data.dto.OwnershipTransferRequest
import com.fares.saveme.feature.wallets.data.dto.VersionOnlyRequest
import com.fares.saveme.feature.wallets.data.dto.WalletDto
import com.fares.saveme.feature.wallets.data.dto.WalletWriteRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * The wallet endpoints of wallets.yaml (contracts/wallets-api-client.md). All credentialed.
 * `PATCH /wallets/{id}` is never called (research R-05).
 */
class WalletApi(private val client: ApiClient) {

    /** Creates (version 0) or saves; the server answers an identical replay with 200. */
    suspend fun put(id: String, request: WalletWriteRequest): ApiResult<WalletDto> = client.call {
        method = HttpMethod.Put
        url("wallets/$id")
        contentType(ContentType.Application.Json)
        setBody(request.toJson())
    }

    suspend fun archive(id: String, version: Long): ApiResult<WalletDto> = client.call {
        method = HttpMethod.Post
        url("wallets/$id/archive")
        contentType(ContentType.Application.Json)
        setBody(VersionOnlyRequest(version))
    }

    /** The version travels in a JSON body on DELETE (backend spec 010 assumed default 16). */
    suspend fun delete(id: String, version: Long): ApiResult<WalletDto> = client.call {
        method = HttpMethod.Delete
        url("wallets/$id")
        contentType(ContentType.Application.Json)
        setBody(VersionOnlyRequest(version))
    }

    /** Never retried automatically (research R-15). */
    suspend fun transferOwnership(id: String, userId: String, version: Long): ApiResult<WalletDto> = client.call {
        method = HttpMethod.Post
        url("wallets/$id/transfer-ownership")
        contentType(ContentType.Application.Json)
        setBody(OwnershipTransferRequest(userId, version))
    }
}
