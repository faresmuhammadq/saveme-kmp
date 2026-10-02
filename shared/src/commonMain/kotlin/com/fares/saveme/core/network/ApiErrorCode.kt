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
    FEATURE_NOT_ENTITLED(RetryClass.Never),

    /** 409 on deleting a category something still uses; distinct from [VERSION_CONFLICT] (spec 007, FR-026). */
    CATEGORY_IN_USE(RetryClass.Never),

    /** 400 on a feed read whose stored position is unparseable or not this account's (spec 009, FR-009, FR-032). */
    INVALID_CURSOR(RetryClass.AfterChange),

    /** 410 on a feed read whose position is behind the purge horizon (spec 009, FR-008, FR-032). */
    SYNC_CURSOR_EXPIRED(RetryClass.AfterChange),
    UNKNOWN(RetryClass.Never);

    companion object {
        private val byName = entries.filter { it != UNKNOWN }.associateBy { it.name }

        fun from(code: String): ApiErrorCode = byName[code] ?: UNKNOWN
    }
}
