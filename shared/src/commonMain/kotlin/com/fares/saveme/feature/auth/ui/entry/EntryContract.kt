package com.fares.saveme.feature.auth.ui.entry

data class EntryState(val isDebugTools: Boolean = false)

sealed interface EntryIntent {
    data object SignInClicked : EntryIntent
    data object CreateAccountClicked : EntryIntent
    data object DevToolsClicked : EntryIntent
}

sealed interface EntryEffect {
    data object NavigateSignIn : EntryEffect
    data object NavigateRegister : EntryEffect
    data object NavigateDevTools : EntryEffect
}
