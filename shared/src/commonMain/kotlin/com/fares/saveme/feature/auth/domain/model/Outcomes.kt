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

// Auth completion (spec 003), data-model §3.

sealed interface RegisterOutcome {
    data object Accepted : RegisterOutcome

    /** 409: registration is the one surface allowed to say so (P-09.4, v1.2.0). */
    data object AlreadyRegistered : RegisterOutcome
    data class Validation(val fieldErrors: Map<AuthField, String>) : RegisterOutcome
    data class RateLimited(val retryAfterSeconds: Long?) : RegisterOutcome
    data object Offline : RegisterOutcome
    data class Error(val code: ApiErrorCode, val requestId: String?) : RegisterOutcome
}

sealed interface VerifyOutcome {
    data object Verified : VerifyOutcome
    data object LinkInvalid : VerifyOutcome
    data object Offline : VerifyOutcome
    data class Error(val code: ApiErrorCode, val requestId: String?) : VerifyOutcome
}

sealed interface ResendOutcome {
    data object Sent : ResendOutcome
    data class RateLimited(val retryAfterSeconds: Long?) : ResendOutcome
    data object Offline : ResendOutcome
    data class Error(val code: ApiErrorCode, val requestId: String?) : ResendOutcome
}

sealed interface ForgotOutcome {
    /** Same for any well-formed address (uniform answers, P-09.4). */
    data object Sent : ForgotOutcome
    data class Validation(val fieldErrors: Map<AuthField, String>) : ForgotOutcome
    data class RateLimited(val retryAfterSeconds: Long?) : ForgotOutcome
    data object Offline : ForgotOutcome
    data class Error(val code: ApiErrorCode, val requestId: String?) : ForgotOutcome
}

sealed interface ResetOutcome {
    data object Done : ResetOutcome
    data object LinkInvalid : ResetOutcome
    data class Validation(val fieldErrors: Map<AuthField, String>) : ResetOutcome
    data class RateLimited(val retryAfterSeconds: Long?) : ResetOutcome
    data object Offline : ResetOutcome
    data class Error(val code: ApiErrorCode, val requestId: String?) : ResetOutcome
}
