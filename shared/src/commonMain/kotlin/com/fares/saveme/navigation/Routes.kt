package com.fares.saveme.navigation

import androidx.navigation3.runtime.NavKey
import com.fares.saveme.feature.auth.domain.SignOutReason
import com.fares.saveme.feature.auth.ui.checkinbox.CheckInboxKind
import kotlinx.serialization.Serializable

@Serializable
data object EntryKey : NavKey

@Serializable
data class SignInKey(val prefillEmail: String? = null, val reason: SignOutReason? = null) : NavKey

@Serializable
data object HomeKey : NavKey

@Serializable
data class UpdateRequiredKey(val minimumVersion: String?) : NavKey

@Serializable
data object DevToolsKey : NavKey

@Serializable
data object CatalogKey : NavKey

@Serializable
data object AccountKey : NavKey

@Serializable
data object ProfileKey : NavKey

@Serializable
data object DeviceSessionsKey : NavKey

@Serializable
data object ChangePasswordKey : NavKey

@Serializable
data object DeleteAccountKey : NavKey

// Auth completion (spec 003). Keys carrying a link secret never print it (FR-009).

@Serializable
data class RegisterKey(val prefillEmail: String? = null) : NavKey

@Serializable
data class CheckInboxKey(val email: String, val kind: CheckInboxKind) : NavKey

@Serializable
data class VerifyEmailKey(val token: String) : NavKey {
    override fun toString() = "VerifyEmailKey(token=<redacted>)"
}

@Serializable
data class ForgotPasswordKey(val prefillEmail: String? = null) : NavKey

@Serializable
data class ResetPasswordKey(val token: String) : NavKey {
    override fun toString() = "ResetPasswordKey(token=<redacted>)"
}

@Serializable
data object PasteLinkKey : NavKey
