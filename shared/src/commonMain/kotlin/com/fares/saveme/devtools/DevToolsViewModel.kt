package com.fares.saveme.devtools

import com.fares.saveme.core.presentation.MviViewModel

class DevToolsViewModel : MviViewModel<DevToolsState, DevToolsIntent, DevToolsEffect>(DevToolsState) {
    override fun onIntent(intent: DevToolsIntent) {
        sendEffect(
            when (intent) {
                DevToolsIntent.ComponentsClicked -> DevToolsEffect.NavigateCatalog
                DevToolsIntent.PasteLinkClicked -> DevToolsEffect.NavigatePasteLink
                DevToolsIntent.SimulatePushClicked -> DevToolsEffect.NavigateSimulatePush
                DevToolsIntent.PlayBillingClicked -> DevToolsEffect.NavigatePlayBilling
                DevToolsIntent.Back -> DevToolsEffect.NavigateBack
            },
        )
    }
}
