package com.fares.saveme.feature.auth.ui

import androidx.compose.runtime.Composable
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.auth.domain.FieldError
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

/**
 * User-facing copy is chosen from the error code alone. This function cannot see the server's
 * `detail` text, so it can never show it (FR-014, P-02.4, P-06.5).
 */
fun ApiErrorCode.message(): StringResource = when (this) {
    ApiErrorCode.INVALID_CREDENTIALS -> Res.string.error_invalid_credentials
    ApiErrorCode.ACCOUNT_LOCKED -> Res.string.error_rate_limited_soon
    ApiErrorCode.EMAIL_NOT_VERIFIED -> Res.string.error_email_not_verified
    ApiErrorCode.VERIFICATION_TOKEN_INVALID -> Res.string.error_verification_link_invalid
    ApiErrorCode.RESET_TOKEN_INVALID -> Res.string.error_reset_link_invalid
    ApiErrorCode.VALIDATION_FAILED -> Res.string.error_validation
    ApiErrorCode.RATE_LIMITED -> Res.string.error_rate_limited_soon
    ApiErrorCode.UNAUTHENTICATED -> Res.string.error_session_ended
    ApiErrorCode.CLIENT_VERSION_UNSUPPORTED -> Res.string.error_update_required
    ApiErrorCode.SERVICE_UNAVAILABLE, ApiErrorCode.INTERNAL_ERROR -> Res.string.error_service_unavailable
    ApiErrorCode.CLIENT_VERSION_REQUIRED, ApiErrorCode.CLIENT_VERSION_INVALID, ApiErrorCode.MALFORMED_JSON,
    ApiErrorCode.NOT_ACCEPTABLE, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, ApiErrorCode.FORBIDDEN,
    ApiErrorCode.NOT_FOUND, ApiErrorCode.UNKNOWN -> Res.string.error_generic_reference
}

fun FieldError.message(): StringResource = when (this) {
    FieldError.Required -> Res.string.field_error_required
    FieldError.EmailInvalid -> Res.string.field_error_email_invalid
    FieldError.EmailTooLong -> Res.string.field_error_email_too_long
    FieldError.PasswordTooShort -> Res.string.field_error_password_too_short
    FieldError.PasswordTooLong -> Res.string.field_error_password_too_long
    FieldError.PasswordEqualsEmail -> Res.string.field_error_password_equals_email
    FieldError.DisplayNameTooLong -> Res.string.field_error_display_name_too_long
}

/** A field problem is either our own check or an entry from the server's `errors[]` (FR-015). */
sealed interface FieldProblem {
    data class Client(val error: FieldError) : FieldProblem
    data class Server(val code: String) : FieldProblem
}

@Composable
fun FieldProblem?.text(): String? = when (this) {
    null -> null
    is FieldProblem.Client -> stringResource(error.message())
    is FieldProblem.Server -> stringResource(if (code == "REQUIRED") Res.string.field_error_required else Res.string.field_error_server)
}

/** Whole minutes to wait, rounded up; null when the server gave no wait time. */
fun minutesToWait(retryAfterSeconds: Long?): Int? =
    retryAfterSeconds?.let { ((it + 59) / 60).toInt().coerceAtLeast(1) }
