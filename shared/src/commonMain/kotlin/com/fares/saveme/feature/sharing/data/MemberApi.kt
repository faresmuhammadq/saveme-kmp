package com.fares.saveme.feature.sharing.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.sharing.data.dto.MemberDto
import com.fares.saveme.feature.sharing.data.dto.MemberRoleRequest
import com.fares.saveme.feature.sharing.data.dto.VersionRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * The member endpoints of sharing.yaml v1.9 (A6-A8). Online-only actions, never retried automatically (P-02.6): each carries
 * the membership's last-seen version.
 */
class MemberApi(private val client: ApiClient) {

    suspend fun changeRole(walletId: String, userId: String, role: String, version: Long): ApiResult<MemberDto> = client.call {
        method = HttpMethod.Patch
        url("wallets/$walletId/members/$userId")
        contentType(ContentType.Application.Json)
        setBody(MemberRoleRequest(role, version))
    }

    /** The version travels in a JSON body on DELETE. */
    suspend fun remove(walletId: String, userId: String, version: Long): ApiResult<MemberDto> = client.call {
        method = HttpMethod.Delete
        url("wallets/$walletId/members/$userId")
        contentType(ContentType.Application.Json)
        setBody(VersionRequest(version))
    }

    suspend fun leave(walletId: String, version: Long): ApiResult<MemberDto> = client.call {
        method = HttpMethod.Post
        url("wallets/$walletId/leave")
        contentType(ContentType.Application.Json)
        setBody(VersionRequest(version))
    }
}
