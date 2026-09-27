package com.fares.saveme.feature.auth.ui.entry

import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.presentation.MviViewModel

class EntryViewModel(config: AppConfig) :
    MviViewModel<EntryState, EntryIntent, EntryEffect>(EntryState(isDebugTools = config.isDebugTools)) {

    override fun onIntent(intent: EntryIntent) {
        when (intent) {
            EntryIntent.SignInClicked -> sendEffect(EntryEffect.NavigateSignIn)
            EntryIntent.CreateAccountClicked -> sendEffect(EntryEffect.NavigateRegister)
            EntryIntent.DevToolsClicked -> if (currentState.isDebugTools) sendEffect(EntryEffect.NavigateDevTools)
        }
    }
}
