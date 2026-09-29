package com.fares.saveme.feature.account.ui

import androidx.compose.runtime.Composable
import com.fares.saveme.core.network.ApiErrorCode
import com.fares.saveme.feature.auth.ui.message
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.error_generic
import saveme.shared.generated.resources.error_generic_reference
import saveme.shared.generated.resources.error_offline

/** A screen-level problem on the account screens; copy chosen by code only (P-02.4). */
sealed interface AccountBanner {
    data object Offline : AccountBanner
    data class Error(val code: ApiErrorCode, val requestId: String?) : AccountBanner
}

@Composable
fun AccountBanner.text(): String = when (this) {
    AccountBanner.Offline -> stringResource(Res.string.error_offline)
    is AccountBanner.Error -> {
        val resource = code.message()
        when {
            resource != Res.string.error_generic_reference -> stringResource(resource)
            requestId != null -> stringResource(Res.string.error_generic_reference, requestId)
            else -> stringResource(Res.string.error_generic)
        }
    }
}
