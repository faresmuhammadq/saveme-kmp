package com.fares.saveme.feature.auth.ui.update

import com.fares.saveme.core.presentation.MviViewModel

class UpdateRequiredViewModel(minimumVersion: String?) :
    MviViewModel<UpdateRequiredState, UpdateRequiredIntent, UpdateRequiredEffect>(UpdateRequiredState(minimumVersion)) {

    override fun onIntent(intent: UpdateRequiredIntent) {
        when (intent) {
            UpdateRequiredIntent.UpdateClicked -> sendEffect(UpdateRequiredEffect.OpenStore(STORE_URL))
        }
    }
}
