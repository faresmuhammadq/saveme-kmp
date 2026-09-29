package com.fares.saveme.feature.auth.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.publicEndpoint
import com.fares.saveme.feature.auth.data.dto.SignInRequest
import com.fares.saveme.feature.auth.data.dto.SignInResultDto
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/** The Identity endpoints this feature uses (contracts/identity-api-client.md). */
class IdentityApi(private val client: ApiClient) {

    suspend fun signIn(request: SignInRequest): ApiResult<SignInResultDto> = client.call {
        method = HttpMethod.Post
        url("auth/login")
        publicEndpoint()
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    /** Ends this device's session. Credentialed, no body, 204. */
    suspend fun signOut(): ApiResult<Unit> = client.call {
        method = HttpMethod.Post
        url("auth/logout")
    }
}
