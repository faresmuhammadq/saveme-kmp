package com.fares.saveme.feature.account.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.Page
import com.fares.saveme.feature.account.data.dto.ChangePasswordRequest
import com.fares.saveme.feature.account.data.dto.DeletionScheduleDto
import com.fares.saveme.feature.account.data.dto.PasswordConfirmation
import com.fares.saveme.feature.account.data.dto.DeviceSessionDto
import com.fares.saveme.feature.account.data.dto.MeDto
import com.fares.saveme.feature.account.data.dto.ProfileDto
import com.fares.saveme.feature.account.data.dto.ProfileWriteRequest
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/** The account endpoints of identity.yaml (contracts/account-api-client.md). All credentialed. */
class AccountApi(private val client: ApiClient) {

    suspend fun listSessions(cursor: String?): ApiResult<Page<DeviceSessionDto>> = client.callPage {
        method = HttpMethod.Get
        url("auth/sessions")
        parameter("filter[state]", "active")
        parameter("limit", PAGE_SIZE)
        if (cursor != null) parameter("cursor", cursor)
    }

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

    /** A wrong password is 401 INVALID_CREDENTIALS on a live session: never a sign-out (R-04). */
    suspend fun requestDeletion(password: String): ApiResult<DeletionScheduleDto> = client.call {
        method = HttpMethod.Post
        url("account/deletion-request")
        contentType(ContentType.Application.Json)
        setBody(PasswordConfirmation(password))
    }

    suspend fun cancelDeletion(): ApiResult<Unit> = client.call {
        method = HttpMethod.Delete
        url("account/deletion-request")
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}
