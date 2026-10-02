package com.fares.saveme.feature.billing.ui.plan

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.billing.domain.CurrentPlan
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import com.fares.saveme.feature.billing.domain.model.Subscription
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class PlanViewModel(
    plan: Flow<CurrentPlan?>,
    subscription: Flow<Subscription?>,
    catalogue: Flow<List<CataloguePlan>?>,
    timeZoneId: Flow<String>,
    private val loadStored: suspend () -> Unit,
    /** Entitlements and subscription, never skipped (FR-006). */
    private val refresh: suspend () -> RefreshOutcome,
    private val loadCatalogue: suspend () -> RefreshOutcome,
) : MviViewModel<PlanState, PlanIntent, PlanEffect>(PlanState()) {

    private var opened = false

    init {
        plan.onEach { value -> setState { reduce(it, PlanChange.Entitlements(value)) } }.launchIn(viewModelScope)
        subscription.onEach { value -> setState { reduce(it, PlanChange.SubscriptionChanged(value)) } }.launchIn(viewModelScope)
        catalogue.onEach { value -> setState { reduce(it, PlanChange.Catalogue(value)) } }.launchIn(viewModelScope)
        timeZoneId.onEach { id -> setState { reduce(it, PlanChange.TimeZone(id)) } }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: PlanIntent) {
        when (intent) {
            PlanIntent.Opened -> if (!opened) {
                opened = true
                // The stored records render at once; the refresh follows.
                viewModelScope.launch {
                    loadStored()
                    pull()
                }
            }
            PlanIntent.Refresh -> if (!currentState.refreshing) viewModelScope.launch { pull() }
            PlanIntent.Back -> sendEffect(PlanEffect.NavigateBack)
        }
    }

    private suspend fun pull() {
        setState { reduce(it, PlanChange.RefreshStarted) }
        val (entitlements, catalogue) = coroutineScope {
            val entitlements = async { refresh() }
            val catalogue = async { loadCatalogue() }
            entitlements.await() to catalogue.await()
        }
        if (catalogue != RefreshOutcome.Done) setState { reduce(it, PlanChange.CatalogueUnavailable) }
        setState { reduce(it, PlanChange.RefreshFinished(entitlements, catalogue)) }
    }
}
