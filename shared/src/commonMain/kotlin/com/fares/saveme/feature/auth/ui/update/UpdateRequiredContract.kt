package com.fares.saveme.feature.auth.ui.update

data class UpdateRequiredState(val minimumVersion: String?)

sealed interface UpdateRequiredIntent {
    data object UpdateClicked : UpdateRequiredIntent
}

sealed interface UpdateRequiredEffect {
    data class OpenStore(val url: String) : UpdateRequiredEffect
}

const val STORE_URL = "https://play.google.com/store/apps/details?id=com.fares.saveme"
