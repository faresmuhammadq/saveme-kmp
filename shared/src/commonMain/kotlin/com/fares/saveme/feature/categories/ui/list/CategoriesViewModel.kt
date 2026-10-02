package com.fares.saveme.feature.categories.ui.list

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.categories.domain.model.CategoriesOverview
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** The categories screen: always the phone's copy, refreshed from the server on open and on pull (P-04.1, FR-004). */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModel(
    overview: (CategoryKind) -> Flow<CategoriesOverview>,
    private val refresh: suspend (Boolean) -> RefreshOutcome,
    private val isEmailVerified: () -> Boolean,
    timeZoneId: Flow<String> = emptyFlow(),
) : MviViewModel<CategoriesState, CategoriesIntent, CategoriesEffect>(CategoriesState()) {

    private val kind = MutableStateFlow(CategoryKind.Expense)

    init {
        kind.flatMapLatest { overview(it) }
            .onEach { stored -> setState { reduce(it, CategoriesChange.Stored(stored)) } }
            .launchIn(viewModelScope)
        timeZoneId.onEach { id -> setState { reduce(it, CategoriesChange.TimeZone(id)) } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: CategoriesIntent) {
        when (intent) {
            CategoriesIntent.Opened, CategoriesIntent.Refresh -> pull()
            is CategoriesIntent.KindChosen -> {
                kind.value = intent.kind
                setState { reduce(it, CategoriesChange.Kind(intent.kind)) }
            }
            CategoriesIntent.CreateClicked ->
                if (isEmailVerified()) sendEffect(CategoriesEffect.OpenEditor(null, currentState.kind))
                else sendEffect(CategoriesEffect.RequireVerification)
            is CategoriesIntent.CategoryClicked -> {
                // Defaults are read-only: only the person's own rows open the editor (FR-011).
                val own = currentState.own + currentState.archived
                if (own.any { it.id == intent.id && it.clickable }) sendEffect(CategoriesEffect.OpenEditor(intent.id, currentState.kind))
            }
            CategoriesIntent.Back -> sendEffect(CategoriesEffect.NavigateBack)
        }
    }

    /** A pull or an open always reads again, whatever the 60 second floor says (FR-004). */
    private fun pull() {
        if (currentState.refreshing) return
        setState { reduce(it, CategoriesChange.Refreshing(true)) }
        viewModelScope.launch {
            val outcome = refresh(true)
            setState { reduce(it, CategoriesChange.RefreshDone(offline = outcome == RefreshOutcome.Offline, failed = outcome == RefreshOutcome.Failed)) }
        }
    }
}
