package com.fares.saveme.feature.auth.domain

import com.fares.saveme.feature.auth.domain.model.AccountSnapshot
import kotlinx.serialization.Serializable

@Serializable
enum class SignOutReason { None, SessionEnded, PasswordReset, UserSignedOut }

sealed interface AuthState {
    data object Unknown : AuthState
    data class SignedOut(val reason: SignOutReason) : AuthState
    data class SignedIn(val account: AccountSnapshot, val deletionCancelled: Boolean = false) : AuthState
}
