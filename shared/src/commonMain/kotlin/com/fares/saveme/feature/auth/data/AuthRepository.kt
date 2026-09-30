package com.fares.saveme.feature.auth.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.storage.SecureStore
import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.feature.auth.data.dto.AcceptedDto
import com.fares.saveme.feature.auth.data.dto.AccountSnapshotDto
import com.fares.saveme.feature.auth.data.dto.EmailRequest
import com.fares.saveme.feature.auth.data.dto.RegisterRequest
import com.fares.saveme.feature.auth.data.dto.ResetPasswordRequest
import com.fares.saveme.feature.auth.data.dto.TokenRequest
import com.fares.saveme.feature.auth.data.dto.VerifyEmailDataDto
import com.fares.saveme.feature.auth.data.dto.DeviceDescriptionDto
import com.fares.saveme.feature.auth.data.dto.SignInRequest
import com.fares.saveme.feature.auth.data.dto.SignInResultDto

/** API calls and local persistence only. Returns results; never calls domain code (P-10.6). */
class AuthRepository(
    private val api: IdentityApi,
    private val secureStore: SecureStore,
    private val snapshots: AccountSnapshotStore,
) {
    suspend fun signIn(email: String, password: String, device: DeviceDescriptionDto): ApiResult<SignInResultDto> =
        api.signIn(SignInRequest(email, password, device))

    suspend fun signOut(): ApiResult<Unit> = api.signOut()

    suspend fun register(email: String, password: String, displayName: String?, timezone: String): ApiResult<AcceptedDto> =
        api.register(RegisterRequest(email, password, displayName?.trim()?.ifEmpty { null }, timezone))

    suspend fun verifyEmail(token: String): ApiResult<VerifyEmailDataDto> = api.verifyEmail(TokenRequest(token))

    suspend fun resendVerification(): ApiResult<AcceptedDto> = api.resendVerification()

    suspend fun forgotPassword(email: String): ApiResult<AcceptedDto> = api.forgotPassword(EmailRequest(email))

    suspend fun resetPassword(token: String, password: String): ApiResult<Unit> =
        api.resetPassword(ResetPasswordRequest(token, password))

    suspend fun saveSession(session: StoredSession, snapshot: AccountSnapshotDto) {
        secureStore.write(session)
        snapshots.write(snapshot)
    }

    suspend fun readSession(): StoredSession? = secureStore.read()

    suspend fun readSnapshot(): AccountSnapshotDto? = snapshots.read()

    suspend fun saveSnapshot(snapshot: AccountSnapshotDto) = snapshots.write(snapshot)

    suspend fun markEmailVerified() = snapshots.setEmailVerified()

    suspend fun clearLocal() {
        secureStore.clear()
        snapshots.clear()
    }
}
