package com.fares.saveme.navigation

import androidx.navigation3.runtime.NavKey
import com.fares.saveme.feature.auth.domain.SignOutReason
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
