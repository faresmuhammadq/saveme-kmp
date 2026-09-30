package com.fares.saveme.core.network

/** The catalogue's `retryable` value (api-conventions: Retrying). */
enum class RetryClass { Automatic, AfterChange, Never }

/**
 * Error codes the app branches on, from `saveme-backend/docs/openapi/error-catalog.yaml`.
 * Anything else is [UNKNOWN]; the raw value stays on [ProblemDetails.code].
 */
enum class ApiErrorCode(val retryClass: RetryClass) {
    VALIDATION_FAILED(RetryClass.AfterChange),
    MALFORMED_JSON(RetryClass.AfterChange),
    CLIENT_VERSION_REQUIRED(RetryClass.AfterChange),
    CLIENT_VERSION_INVALID(RetryClass.AfterChange),
    CLIENT_VERSION_UNSUPPORTED(RetryClass.Never),
    UNAUTHENTICATED(RetryClass.Never),
    FORBIDDEN(RetryClass.Never),
    NOT_FOUND(RetryClass.Never),
    NOT_ACCEPTABLE(RetryClass.Never),
    UNSUPPORTED_MEDIA_TYPE(RetryClass.Never),
    RATE_LIMITED(RetryClass.Automatic),
    INTERNAL_ERROR(RetryClass.Automatic),
    SERVICE_UNAVAILABLE(RetryClass.Automatic),
    INVALID_CREDENTIALS(RetryClass.Never),
    ACCOUNT_LOCKED(RetryClass.Automatic),
    EMAIL_NOT_VERIFIED(RetryClass.Never),
    EMAIL_ALREADY_REGISTERED(RetryClass.Never),
    VERIFICATION_TOKEN_INVALID(RetryClass.AfterChange),
    RESET_TOKEN_INVALID(RetryClass.AfterChange),
    VERSION_CONFLICT(RetryClass.AfterChange),
    UNKNOWN(RetryClass.Never);

    companion object {
        private val byName = entries.filter { it != UNKNOWN }.associateBy { it.name }

        fun from(code: String): ApiErrorCode = byName[code] ?: UNKNOWN
    }
}
