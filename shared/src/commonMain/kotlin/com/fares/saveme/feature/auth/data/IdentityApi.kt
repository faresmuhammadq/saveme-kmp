package com.fares.saveme.feature.auth.data

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.publicEndpoint
import com.fares.saveme.feature.auth.data.dto.AcceptedDto
import com.fares.saveme.feature.auth.data.dto.EmailRequest
import com.fares.saveme.feature.auth.data.dto.RegisterRequest
import com.fares.saveme.feature.auth.data.dto.ResetPasswordRequest
import com.fares.saveme.feature.auth.data.dto.SignInRequest
import com.fares.saveme.feature.auth.data.dto.TokenRequest
import com.fares.saveme.feature.auth.data.dto.VerifyEmailDataDto
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

    /** 202; an address that already has an account is 409 EMAIL_ALREADY_REGISTERED. */
    suspend fun register(request: RegisterRequest): ApiResult<AcceptedDto> = client.call {
        method = HttpMethod.Post
        url("auth/register")
        publicEndpoint()
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    /** Works signed in or out: the link can be opened on any device. */
    suspend fun verifyEmail(request: TokenRequest): ApiResult<VerifyEmailDataDto> = client.call {
        method = HttpMethod.Post
        url("auth/verify-email")
        publicEndpoint()
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    /** Credentialed, no body; at most three per hour. */
    suspend fun resendVerification(): ApiResult<AcceptedDto> = client.call {
        method = HttpMethod.Post
        url("auth/resend-verification")
    }

    /** Same answer whether or not the address is registered. */
    suspend fun forgotPassword(request: EmailRequest): ApiResult<AcceptedDto> = client.call {
        method = HttpMethod.Post
        url("auth/forgot-password")
        publicEndpoint()
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    /** 204. Ends every session of the account server-side; signs nobody in. */
    suspend fun resetPassword(request: ResetPasswordRequest): ApiResult<Unit> = client.call {
        method = HttpMethod.Post
        url("auth/reset-password")
        publicEndpoint()
        contentType(ContentType.Application.Json)
        setBody(request)
    }
}
