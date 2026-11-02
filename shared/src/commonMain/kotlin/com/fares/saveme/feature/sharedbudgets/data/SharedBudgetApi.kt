package com.fares.saveme.feature.sharedbudgets.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetInvitationDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetInvitationMetaDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetInvitationPreviewDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetLineDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetLineWriteRequest
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetMemberDto
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetTokenRequest
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetTransferRequest
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetVersionRequest
import com.fares.saveme.feature.sharedbudgets.data.dto.SharedBudgetWriteRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/** A new invitation and, the first time only, its one-time link. The link is never printed (P-09.2). */
class SharedBudgetInvitationPut(val invitation: SharedBudgetInvitationDto, val shareUrl: String?) {
    override fun toString() = "SharedBudgetInvitationPut(${invitation.id}, link=${if (shareUrl == null) "none" else "<redacted>"})"
}

/**
 * The Shared Budget endpoints of shared-budgets.yaml v1.16. All credentialed. Reads never go through here: the budget's own
 * feed and the person's user feed carry everything (spec 009). The budget itself travels through the sync engine; lines,
 * members, invitations and ownership are direct calls that need a connection.
 */
class SharedBudgetApi(private val client: ApiClient) {

    suspend fun put(id: String, request: SharedBudgetWriteRequest): ApiResult<SharedBudgetDto> = client.call {
        method = HttpMethod.Put
        url("shared-budgets/$id")
        contentType(ContentType.Application.Json)
        setBody(request.toJson())
    }

    suspend fun delete(id: String, version: Long): ApiResult<SharedBudgetDto> = client.call {
        method = HttpMethod.Delete
        url("shared-budgets/$id")
        contentType(ContentType.Application.Json)
        setBody(SharedBudgetVersionRequest(version))
    }

    suspend fun archive(id: String, version: Long): ApiResult<SharedBudgetDto> = client.call {
        method = HttpMethod.Post
        url("shared-budgets/$id/archive")
        contentType(ContentType.Application.Json)
        setBody(SharedBudgetVersionRequest(version))
    }

    suspend fun transferOwnership(id: String, userId: String, version: Long): ApiResult<SharedBudgetDto> = client.call {
        method = HttpMethod.Post
        url("shared-budgets/$id/transfer-ownership")
        contentType(ContentType.Application.Json)
        setBody(SharedBudgetTransferRequest(userId, version))
    }

    suspend fun putLine(budgetId: String, lineId: String, request: SharedBudgetLineWriteRequest): ApiResult<SharedBudgetLineDto> = client.call {
        method = HttpMethod.Put
        url("shared-budgets/$budgetId/lines/$lineId")
        contentType(ContentType.Application.Json)
        setBody(request.toJson())
    }

    suspend fun deleteLine(budgetId: String, lineId: String, version: Long): ApiResult<SharedBudgetLineDto> = client.call {
        method = HttpMethod.Delete
        url("shared-budgets/$budgetId/lines/$lineId")
        contentType(ContentType.Application.Json)
        setBody(SharedBudgetVersionRequest(version))
    }

    suspend fun leave(id: String): ApiResult<Unit> = client.call {
        method = HttpMethod.Post
        url("shared-budgets/$id/leave")
    }

    suspend fun removeMember(id: String, userId: String): ApiResult<Unit> = client.call {
        method = HttpMethod.Delete
        url("shared-budgets/$id/members/$userId")
    }

    /** Creates (version 0); the server answers an identical replay with 200 and no link. */
    suspend fun putInvitation(budgetId: String, id: String): ApiResult<SharedBudgetInvitationPut> {
        val result = client.callWithMeta<SharedBudgetInvitationDto> {
            method = HttpMethod.Put
            url("shared-budgets/$budgetId/invitations/$id")
            contentType(ContentType.Application.Json)
            setBody(kotlinx.serialization.json.buildJsonObject { put("version", kotlinx.serialization.json.JsonPrimitive(0)) })
        }
        return when (result) {
            is ApiResult.Success -> {
                val link = runCatching { AppJson.decodeFromJsonElement(SharedBudgetInvitationMetaDto.serializer(), result.value.meta).shareUrl }.getOrNull()
                ApiResult.Success(SharedBudgetInvitationPut(result.value.data, link))
            }
            is ApiResult.Failure -> result
            ApiResult.Offline -> ApiResult.Offline
            ApiResult.Timeout -> ApiResult.Timeout
        }
    }

    suspend fun deleteInvitation(budgetId: String, id: String, version: Long): ApiResult<SharedBudgetInvitationDto> = client.call {
        method = HttpMethod.Delete
        url("shared-budgets/$budgetId/invitations/$id")
        contentType(ContentType.Application.Json)
        setBody(SharedBudgetVersionRequest(version))
    }

    suspend fun preview(token: String): ApiResult<SharedBudgetInvitationPreviewDto> = client.call {
        method = HttpMethod.Post
        url("shared-budget-invitations/preview")
        contentType(ContentType.Application.Json)
        setBody(SharedBudgetTokenRequest(token))
    }

    suspend fun accept(token: String): ApiResult<SharedBudgetMemberDto> = client.call {
        method = HttpMethod.Post
        url("shared-budget-invitations/accept")
        contentType(ContentType.Application.Json)
        setBody(SharedBudgetTokenRequest(token))
    }

    suspend fun decline(token: String): ApiResult<Unit> = client.call {
        method = HttpMethod.Post
        url("shared-budget-invitations/decline")
        contentType(ContentType.Application.Json)
        setBody(SharedBudgetTokenRequest(token))
    }
}
