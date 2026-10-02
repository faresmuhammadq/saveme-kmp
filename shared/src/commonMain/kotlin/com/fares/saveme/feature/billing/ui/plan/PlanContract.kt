package com.fares.saveme.feature.billing.ui.plan

import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.billing.domain.CurrentPlan
import com.fares.saveme.feature.billing.domain.model.BillingPeriod
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import com.fares.saveme.feature.billing.domain.model.Money
import com.fares.saveme.feature.billing.domain.model.Subscription
import com.fares.saveme.feature.billing.domain.model.SubscriptionStatus
import kotlin.time.Instant

enum class CatalogueStatus { Loading, Loaded, Unavailable }

/** What the status line says; dates are shown in [PlanState.timeZoneId] (P-05). */
sealed interface PlanStatusUi {
    data class Renews(val on: Instant) : PlanStatusUi
    data class Ends(val on: Instant) : PlanStatusUi
    data object NoEnd : PlanStatusUi
    data class Grace(val until: Instant) : PlanStatusUi
}

data class CurrentPlanUi(
    val name: String,
    val capabilities: List<CapabilityLine>,
    /** Null on the free plan, for an unknown status, and when the subscription is another plan's. */
    val status: PlanStatusUi?,
    /** True until the first subscription answer settles; the screen shows a placeholder. */
    val statusLoading: Boolean,
)

data class PlanCardUi(
    val name: String,
    /** Null for the default plan, which has no billing period to show (FR-013). */
    val period: BillingPeriod?,
    val price: Money?,
    val capabilities: List<CapabilityLine>,
    val isCurrent: Boolean,
)

data class PlanState(
    val plan: CurrentPlan? = null,
    val subscription: Subscription? = null,
    /** True once the first refresh finished, whatever its outcome. */
    val settled: Boolean = false,
    val plans: List<CataloguePlan>? = null,
    val catalogueStatus: CatalogueStatus = CatalogueStatus.Loading,
    val refreshing: Boolean = false,
    /** The last refresh failed: show "may be out of date". */
    val stale: Boolean = false,
    val timeZoneId: String = "UTC",
) {
    val current: CurrentPlanUi?
        get() = plan?.let {
            CurrentPlanUi(
                name = it.name,
                capabilities = capabilityLines(it.entitlements),
                status = subscription?.takeIf { sub -> sub.planId == it.id }?.toStatus(),
                statusLoading = subscription == null && !settled,
            )
        }

    val cards: List<PlanCardUi>
        get() = plans.orEmpty().map { catalogued ->
            PlanCardUi(
                name = catalogued.name,
                period = catalogued.period.takeUnless { catalogued.isDefault || it == BillingPeriod.Unknown },
                price = catalogued.price,
                capabilities = capabilityLines(catalogued.features),
                isCurrent = plan?.id == catalogued.id,
            )
        }
}

private fun Subscription.toStatus(): PlanStatusUi? = when (status) {
    SubscriptionStatus.Active -> when {
        periodEnd == null -> PlanStatusUi.NoEnd
        autoRenew == true -> PlanStatusUi.Renews(periodEnd)
        else -> PlanStatusUi.Ends(periodEnd)
    }
    SubscriptionStatus.Grace -> graceUntil?.let { PlanStatusUi.Grace(it) }
    null -> null
}

sealed interface PlanIntent {
    data object Opened : PlanIntent
    data object Refresh : PlanIntent
    data object Back : PlanIntent
}

sealed interface PlanEffect {
    data object NavigateBack : PlanEffect
}

sealed interface PlanChange {
    data class Entitlements(val plan: CurrentPlan?) : PlanChange
    data class SubscriptionChanged(val subscription: Subscription?) : PlanChange
    data class Catalogue(val plans: List<CataloguePlan>?) : PlanChange
    data object CatalogueUnavailable : PlanChange
    data object RefreshStarted : PlanChange
    data class RefreshFinished(val entitlements: RefreshOutcome, val catalogue: RefreshOutcome) : PlanChange
    data class TimeZone(val id: String) : PlanChange
}

fun reduce(state: PlanState, change: PlanChange): PlanState = when (change) {
    is PlanChange.Entitlements -> state.copy(plan = change.plan)
    is PlanChange.SubscriptionChanged -> state.copy(subscription = change.subscription)
    is PlanChange.Catalogue -> if (change.plans == null) {
        state
    } else {
        state.copy(plans = change.plans, catalogueStatus = CatalogueStatus.Loaded)
    }
    PlanChange.CatalogueUnavailable ->
        // A catalogue we already hold stays on screen; only a never-loaded one is "unavailable".
        if (state.plans == null) state.copy(catalogueStatus = CatalogueStatus.Unavailable) else state
    PlanChange.RefreshStarted -> state.copy(
        refreshing = true,
        catalogueStatus = if (state.plans == null) CatalogueStatus.Loading else state.catalogueStatus,
    )
    is PlanChange.RefreshFinished -> state.copy(
        refreshing = false,
        settled = true,
        stale = change.entitlements != RefreshOutcome.Done,
    )
    is PlanChange.TimeZone -> state.copy(timeZoneId = change.id)
}
