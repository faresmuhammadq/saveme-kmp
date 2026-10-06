package com.fares.saveme.feature.account.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.data.dto.ChangePasswordRequest
import com.fares.saveme.feature.account.data.dto.DeletionScheduleDto
import com.fares.saveme.feature.account.data.dto.DeletionPlanDto
import com.fares.saveme.feature.account.data.dto.DeletionRequestDto
import com.fares.saveme.feature.account.data.dto.MeDto
import com.fares.saveme.feature.account.data.dto.ProfileDto
import com.fares.saveme.feature.account.data.dto.ProfileWriteRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/** The account endpoints of identity.yaml (contracts/account-api-client.md). All credentialed. */
class AccountApi(private val client: ApiClient) {

    suspend fun endSession(id: String): ApiResult<Unit> = client.call {
        method = HttpMethod.Delete
        url("auth/sessions/$id")
    }

    suspend fun endOtherSessions(): ApiResult<Unit> = client.call {
        method = HttpMethod.Delete
        url("auth/sessions")
    }

    /** A wrong current password is 401 INVALID_CREDENTIALS on a live session: never a sign-out (R-04). */
    suspend fun changePassword(request: ChangePasswordRequest): ApiResult<Unit> = client.call {
        method = HttpMethod.Post
        url("auth/change-password")
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun getMe(): ApiResult<MeDto> = client.call {
        method = HttpMethod.Get
        url("me")
    }

    /** 409 VERSION_CONFLICT carries the stored profile in `current`; nothing is merged server-side. */
    suspend fun saveProfile(request: ProfileWriteRequest): ApiResult<ProfileDto> = client.call {
        method = HttpMethod.Put
        url("me/profile")
        contentType(ContentType.Application.Json)
        setBody(request.toJson())
    }

    /** The deletion state and who would own each shared wallet the person owns (spec 016, backend spec 022). */
    suspend fun deletionPlan(): ApiResult<DeletionPlanDto> = client.call {
        method = HttpMethod.Get
        url("account/deletion-request")
    }

    /**
     * A wrong password is 401 INVALID_CREDENTIALS on a live session: never a sign-out (R-04). The body carries only the
     * password and, when owners were chosen, `successors` (spec 016 FR-037).
     */
    suspend fun requestDeletion(body: DeletionRequestDto): ApiResult<DeletionScheduleDto> = client.call {
        method = HttpMethod.Post
        url("account/deletion-request")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    suspend fun cancelDeletion(): ApiResult<Unit> = client.call {
        method = HttpMethod.Delete
        url("account/deletion-request")
    }
}
