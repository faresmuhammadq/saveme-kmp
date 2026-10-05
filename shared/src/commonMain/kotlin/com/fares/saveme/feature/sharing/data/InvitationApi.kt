package com.fares.saveme.feature.sharing.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.sharing.data.dto.InvitationCreateMetaDto
import com.fares.saveme.feature.sharing.data.dto.InvitationDto
import com.fares.saveme.feature.sharing.data.dto.InvitationPreviewDto
import com.fares.saveme.feature.sharing.data.dto.InvitationWriteRequest
import com.fares.saveme.feature.sharing.data.dto.MemberDto
import com.fares.saveme.feature.sharing.data.dto.TokenRequest
import com.fares.saveme.feature.sharing.data.dto.VersionRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * The invitation's answer to a create: the record and, for a new link invitation, its one-time link. Never printed
 * (P-09.2): the link is in [shareUrl].
 */
class InvitationPut(val invitation: InvitationDto, val shareUrl: String?) {
    override fun toString() = "InvitationPut($invitation, link=${if (shareUrl == null) "none" else "<redacted>"})"
}

/**
 * The invitation endpoints of sharing.yaml v1.9 (contracts/sharing-api-client.md A1-A5). All credentialed. The secret travels
 * only in request bodies, never in a path or query (FR-010). `GET /wallets/{id}/invitations[/{iid}]` are never used: the wallet
 * feed carries invitations.
 */
class InvitationApi(private val client: ApiClient) {

    /** Creates (version 0); the server answers an identical replay with 200 and no link. */
    suspend fun put(walletId: String, id: String, request: InvitationWriteRequest): ApiResult<InvitationPut> {
        val result = client.callWithMeta<InvitationDto> {
            method = HttpMethod.Put
            url("wallets/$walletId/invitations/$id")
            contentType(ContentType.Application.Json)
            setBody(request.toJson())
        }
        return when (result) {
            is ApiResult.Success -> {
                val link = runCatching { AppJson.decodeFromJsonElement(InvitationCreateMetaDto.serializer(), result.value.meta).shareUrl }.getOrNull()
                ApiResult.Success(InvitationPut(result.value.data, link))
            }
            is ApiResult.Failure -> result
            ApiResult.Offline -> ApiResult.Offline
            ApiResult.Timeout -> ApiResult.Timeout
        }
    }

    /** The version travels in a JSON body on DELETE. */
    suspend fun delete(walletId: String, id: String, version: Long): ApiResult<InvitationDto> = client.call {
        method = HttpMethod.Delete
        url("wallets/$walletId/invitations/$id")
        contentType(ContentType.Application.Json)
        setBody(VersionRequest(version))
    }

    suspend fun preview(token: String): ApiResult<InvitationPreviewDto> = client.call {
        method = HttpMethod.Post
        url("invitations/preview")
        contentType(ContentType.Application.Json)
        setBody(TokenRequest(token))
    }

    suspend fun accept(token: String): ApiResult<MemberDto> = client.call {
        method = HttpMethod.Post
        url("invitations/accept")
        contentType(ContentType.Application.Json)
        setBody(TokenRequest(token))
    }

    suspend fun decline(token: String): ApiResult<Unit> = client.call {
        method = HttpMethod.Post
        url("invitations/decline")
        contentType(ContentType.Application.Json)
        setBody(TokenRequest(token))
    }
}
