package com.fares.saveme.feature.auth.domain.model

import com.fares.saveme.core.network.ApiErrorCode

sealed interface SignInOutcome {
    data class Success(val deletionCancelled: Boolean) : SignInOutcome
    data object InvalidCredentials : SignInOutcome
    data class Locked(val retryAfterSeconds: Long?) : SignInOutcome
    data class Validation(val fieldErrors: Map<AuthField, String>) : SignInOutcome
    data class RateLimited(val retryAfterSeconds: Long?) : SignInOutcome

    /** 426: the navigation root shows the blocking update screen (central handling, FR-018). */
    data object UpdateRequired : SignInOutcome
    data object Offline : SignInOutcome
    data object PlatformUnsupported : SignInOutcome
    data class Error(val code: ApiErrorCode, val requestId: String?) : SignInOutcome
}
