package com.fares.saveme.feature.auth.ui.update

/** [installedVersion] is this install's own version name, shown next to the one it needs; null when unknown. */
data class UpdateRequiredState(val minimumVersion: String?, val installedVersion: String? = null)

sealed interface UpdateRequiredIntent {
    data object UpdateClicked : UpdateRequiredIntent
}

sealed interface UpdateRequiredEffect {
    data class OpenStore(val url: String) : UpdateRequiredEffect
}

const val STORE_URL = "https://play.google.com/store/apps/details?id=com.fares.saveme"
