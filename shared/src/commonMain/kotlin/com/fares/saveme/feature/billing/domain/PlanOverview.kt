package com.fares.saveme.feature.billing.domain

import com.fares.saveme.core.locale.AppLanguageStore
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.billing.data.CatalogueRepository
import com.fares.saveme.feature.billing.data.SubscriptionRepository
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import com.fares.saveme.feature.billing.domain.model.Subscription
import com.fares.saveme.feature.billing.domain.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The plan the person is on, as the Plan screen displays it. Display only: never gate on it (P-12.1). */
data class CurrentPlan(val id: String, val name: String, val entitlements: EntitlementSet)

/** Everything the Plan screen reads: the account's plan, the subscription and the catalogue. */
class PlanOverview(
    private val sessions: SessionManager,
    private val subscriptions: SubscriptionRepository,
    private val catalogue: CatalogueRepository,
    private val languages: AppLanguageStore,
) {
    val current: Flow<CurrentPlan?> = sessions.state
        .map { state ->
            (state as? AuthState.SignedIn)?.account?.let { CurrentPlan(it.planId, it.plan, EntitlementSet(it.features)) }
        }
        .distinctUntilChanged()

    /** Null until a subscription has been read at least once on this device. */
    val subscription: Flow<Subscription?> = subscriptions.stored.map { it?.toDomain() }

    /** Null until a catalogue has been read at least once; kept offline. */
    val plans: Flow<List<CataloguePlan>?> = catalogue.stored.map { record -> record?.plans?.map { it.toDomain() } }

    /** Reads both stored records so the screen can render before any call answers. */
    suspend fun loadStored() {
        subscriptions.load()
        catalogue.load()
    }

    /** Re-requests the catalogue in the app's current language and stores it (FR-017). */
    suspend fun loadCatalogue(): RefreshOutcome = when (val result = catalogue.fetch()) {
        is ApiResult.Success -> {
            catalogue.save(languages.effective().tag, result.value)
            RefreshOutcome.Done
        }
        ApiResult.Offline, ApiResult.Timeout -> RefreshOutcome.Offline
        is ApiResult.Failure -> RefreshOutcome.Failed
    }
}
