package com.fares.saveme.feature.auth.ui

import androidx.compose.runtime.Composable
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.feature.auth.ui.signin.errorText
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.*

/** Form-level messages shared by register, forgot password and reset password (spec 003). */
sealed interface FormBanner {
    data class RateLimited(val minutes: Int?) : FormBanner
    data object Validation : FormBanner
    data object Offline : FormBanner
    data class Error(val code: ApiErrorCode, val requestId: String?) : FormBanner
}

fun FormBanner.tone(): AlertTone = when (this) {
    is FormBanner.RateLimited, FormBanner.Offline -> AlertTone.Warning
    FormBanner.Validation, is FormBanner.Error -> AlertTone.Error
}

@Composable
fun FormBanner.text(): String = when (this) {
    is FormBanner.RateLimited -> minutes?.let { pluralStringResource(Res.plurals.error_rate_limited, it, it) }
        ?: stringResource(Res.string.error_rate_limited_soon)
    FormBanner.Validation -> stringResource(Res.string.error_validation)
    FormBanner.Offline -> stringResource(Res.string.error_offline)
    is FormBanner.Error -> errorText(code.message(), requestId)
}
