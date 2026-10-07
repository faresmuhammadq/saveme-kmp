package com.fares.saveme.feature.billing.ui.plan

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.billing.domain.CurrentPlan
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import com.fares.saveme.feature.billing.domain.model.Money
import com.fares.saveme.feature.billing.domain.model.Subscription
import com.fares.saveme.feature.billing.domain.play.PlanActivated
import com.fares.saveme.feature.billing.domain.play.PlayNotes
import com.fares.saveme.feature.billing.domain.play.RestoreResult
import com.fares.saveme.feature.billing.domain.play.RestoreRetry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
    /** Google Play (spec 018): the port's `platformSupported`; false on iOS (FR-055). */
    playSupported: Boolean = false,
    notes: Flow<PlayNotes> = emptyFlow(),
    /** Runs a purchase pass (FR-010); never throws, shows nothing. */
    private val runPurchasePass: suspend () -> Unit = {},
    /** Google Play's prices for these product ids, at most once a minute (FR-038). */
    private val loadPlayPrices: suspend (Set<String>) -> Map<String, Money> = { emptyMap() },
    private val restorePurchases: suspend (RestoreRetry?) -> RestoreResult = { RestoreResult.GooglePlayUnavailable },
    /** Google Play's subscription centre for this app and an optional product (FR-054). */
    private val subscriptionsUrl: (String?) -> String = { "" },
    private val consumeActivated: () -> PlanActivated? = { null },
    private val screenOpened: () -> Unit = {},
    private val screenClosed: () -> Unit = {},
) : MviViewModel<PlanState, PlanIntent, PlanEffect>(PlanState(playSupported = playSupported)) {

    private var opened = false
    private var restoreRetry: RestoreRetry? = null
    private var pricesRequestedFor: Set<String> = emptySet()

    init {
        screenOpened()
        plan.onEach { value -> setState { reduce(it, PlanChange.Entitlements(value)) } }.launchIn(viewModelScope)
        subscription.onEach { value -> setState { reduce(it, PlanChange.SubscriptionChanged(value)) } }.launchIn(viewModelScope)
        catalogue.onEach { value ->
            setState { reduce(it, PlanChange.Catalogue(value)) }
            val ids = productIds()
            if (ids.isNotEmpty() && ids != pricesRequestedFor) requestPrices()
        }.launchIn(viewModelScope)
        timeZoneId.onEach { id -> setState { reduce(it, PlanChange.TimeZone(id)) } }.launchIn(viewModelScope)
        notes.onEach { value ->
            setState { reduce(it, PlanChange.Notes(value)) }
            if (value.activated != null) consumeActivated()?.let { sendEffect(PlanEffect.ShowActivated(it.planName)) }
        }.launchIn(viewModelScope)
    }

    override fun onCleared() {
        screenClosed()
        super.onCleared()
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
                viewModelScope.launch { runPurchasePass() }
                requestPrices()
            }
            PlanIntent.Refresh -> if (!currentState.refreshing) {
                viewModelScope.launch { pull() }
                viewModelScope.launch { runPurchasePass() }
                requestPrices()
            }
            PlanIntent.Back -> sendEffect(PlanEffect.NavigateBack)
            PlanIntent.EnterCode -> sendEffect(PlanEffect.OpenRedeem)
            PlanIntent.RestorePurchases -> if (currentState.restore != RestoreUi.Running) restore(null)
            PlanIntent.RetryRestore -> if (currentState.restore != RestoreUi.Running) restore(restoreRetry)
            PlanIntent.DismissRestoreResult -> {
                restoreRetry = null
                setState { reduce(it, PlanChange.Restore(RestoreUi.Idle)) }
            }
            PlanIntent.OpenManage -> {
                val state = currentState
                sendEffect(PlanEffect.OpenUrl(subscriptionsUrl(state.manageProductId ?: state.notes.pausedProducts.firstOrNull())))
            }
            PlanIntent.OpenFixPayment -> {
                val state = currentState
                val product = when (val problem = state.problems.firstOrNull { it is PaymentProblemUi.Grace || it is PaymentProblemUi.Hold }) {
                    is PaymentProblemUi.Hold -> problem.productId
                    else -> state.manageProductId
                }
                sendEffect(PlanEffect.OpenUrl(subscriptionsUrl(product)))
            }
        }
    }

    private fun productIds(): Set<String> = currentState.plans.orEmpty().mapNotNull { it.playProductId }.toSet()

    private fun requestPrices() {
        val ids = productIds()
        if (ids.isEmpty() || !currentState.playSupported) return
        pricesRequestedFor = ids
        viewModelScope.launch {
            val prices = loadPlayPrices(ids)
            setState { reduce(it, PlanChange.PlayPricesLoaded(prices)) }
        }
    }

    private fun restore(retry: RestoreRetry?) {
        setState { reduce(it, PlanChange.Restore(RestoreUi.Running)) }
        viewModelScope.launch {
            val result = restorePurchases(retry)
            restoreRetry = (result as? RestoreResult.Refused)?.retry
            val next = when (result) {
                is RestoreResult.Done -> result.summary.messageParts().let { if (it.isEmpty()) RestoreUi.Idle else RestoreUi.Result(it) }
                RestoreResult.GooglePlayUnavailable -> RestoreUi.Refused(RestoreRefusal.GooglePlayUnavailable)
                RestoreResult.SignedOut -> RestoreUi.Idle
                is RestoreResult.Offline -> RestoreUi.Refused(RestoreRefusal.Offline)
                is RestoreResult.RateLimited -> RestoreUi.Refused(RestoreRefusal.RateLimited, retryAfterSeconds = result.retryAfterSeconds)
                is RestoreResult.ServiceUnavailable, is RestoreResult.InProgress -> RestoreUi.Refused(RestoreRefusal.Busy)
                is RestoreResult.Failed -> RestoreUi.Refused(RestoreRefusal.Failed, requestId = result.requestId)
            }
            setState { reduce(it, PlanChange.Restore(next)) }
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
