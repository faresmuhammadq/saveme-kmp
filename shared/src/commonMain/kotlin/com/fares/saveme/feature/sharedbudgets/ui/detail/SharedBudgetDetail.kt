package com.fares.saveme.feature.sharedbudgets.ui.detail

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.sharedbudgets.domain.model.SharedBudgetProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class SharedBudgetDetailState(
    val progress: SharedBudgetProgress? = null,
    val loaded: Boolean = false,
)

sealed interface SharedBudgetDetailIntent {
    data object Back : SharedBudgetDetailIntent
}

sealed interface SharedBudgetDetailEffect {
    data object NavigateBack : SharedBudgetDetailEffect
}

/**
 * One Shared Budget: overall and per-category progress, who spent what, and when the phone last heard from the server.
 * A budget that leaves the phone (the person left or was removed) closes the screen.
 */
class SharedBudgetDetailViewModel(
    detail: Flow<SharedBudgetProgress?>,
) : MviViewModel<SharedBudgetDetailState, SharedBudgetDetailIntent, SharedBudgetDetailEffect>(SharedBudgetDetailState()) {
    init {
        detail.onEach { progress ->
            if (progress == null && currentState.loaded) {
                sendEffect(SharedBudgetDetailEffect.NavigateBack)
            } else {
                setState { it.copy(progress = progress, loaded = true) }
            }
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: SharedBudgetDetailIntent) {
        when (intent) {
            SharedBudgetDetailIntent.Back -> sendEffect(SharedBudgetDetailEffect.NavigateBack)
        }
    }
}
