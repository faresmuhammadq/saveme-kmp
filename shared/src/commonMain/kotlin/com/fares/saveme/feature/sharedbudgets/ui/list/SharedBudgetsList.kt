package com.fares.saveme.feature.sharedbudgets.ui.list

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.sharedbudgets.domain.model.SharedBudgetProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class SharedBudgetsState(
    val budgets: List<SharedBudgetProgress> = emptyList(),
    /** False until the phone's copy has been read once: a skeleton, not the empty state. */
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && budgets.isEmpty()
    val active: List<SharedBudgetProgress> get() = budgets.filterNot { it.archived }
    val archived: List<SharedBudgetProgress> get() = budgets.filter { it.archived }
}

sealed interface SharedBudgetsIntent {
    data class BudgetClicked(val id: String) : SharedBudgetsIntent
    data object AddClicked : SharedBudgetsIntent
    data object Back : SharedBudgetsIntent
}

sealed interface SharedBudgetsEffect {
    data class OpenBudget(val id: String) : SharedBudgetsEffect
    data object OpenNew : SharedBudgetsEffect
    data object NavigateBack : SharedBudgetsEffect
}

/** The Shared Budgets the person belongs to, always from the phone's copy (backend ADR-0027). */
class SharedBudgetsViewModel(
    budgets: Flow<List<SharedBudgetProgress>>,
) : MviViewModel<SharedBudgetsState, SharedBudgetsIntent, SharedBudgetsEffect>(SharedBudgetsState()) {
    init {
        budgets.onEach { list -> setState { it.copy(budgets = list, loaded = true) } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: SharedBudgetsIntent) {
        when (intent) {
            is SharedBudgetsIntent.BudgetClicked ->
                if (currentState.budgets.any { it.id == intent.id }) sendEffect(SharedBudgetsEffect.OpenBudget(intent.id))
            SharedBudgetsIntent.AddClicked -> sendEffect(SharedBudgetsEffect.OpenNew)
            SharedBudgetsIntent.Back -> sendEffect(SharedBudgetsEffect.NavigateBack)
        }
    }
}
