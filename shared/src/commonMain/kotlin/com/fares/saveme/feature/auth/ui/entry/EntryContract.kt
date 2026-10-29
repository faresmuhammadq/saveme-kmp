package com.fares.saveme.feature.auth.ui.entry

data class EntryState(val isDebugTools: Boolean = false, val serverAddress: String? = null)

sealed interface EntryIntent {
    data object SignInClicked : EntryIntent
    data object CreateAccountClicked : EntryIntent
    data object DevToolsClicked : EntryIntent
    data object ServerClicked : EntryIntent
}

sealed interface EntryEffect {
    data object NavigateSignIn : EntryEffect
    data object NavigateRegister : EntryEffect
    data object NavigateDevTools : EntryEffect
    data object NavigateServer : EntryEffect
}
