package com.fares.saveme.feature.billing.ui.plan

import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.billing.domain.CurrentPlan
import com.fares.saveme.feature.billing.domain.model.BillingPeriod
import com.fares.saveme.feature.billing.domain.model.CataloguePlan
import com.fares.saveme.feature.billing.domain.model.Money
import com.fares.saveme.feature.billing.domain.model.Subscription
import com.fares.saveme.feature.billing.domain.model.SubscriptionStatus
import com.fares.saveme.feature.billing.domain.play.PlayNotes
import com.fares.saveme.feature.billing.domain.play.RestoreMessagePart
import kotlin.time.Instant

private const val GOOGLE_PLAY_PROVIDER = "google_play"

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

/** A Google Play payment problem the Plan screen explains (FR-031). Dates show in [PlanState.timeZoneId] (P-05). */
sealed interface PaymentProblemUi {
    /** Grace: Google Play could not take the payment; access holds until [until]. */
    data class Grace(val until: Instant, val planName: String) : PaymentProblemUi

    /** A verify answer in this run reported the subscription on hold; [planName] is the catalogue plan of the held product. */
    data class Hold(val planName: String, val productId: String) : PaymentProblemUi

    data class Paused(val productId: String) : PaymentProblemUi
}

/** Why a tapped restore did not finish (FR-024, FR-025). */
enum class RestoreRefusal { GooglePlayUnavailable, Offline, RateLimited, Busy, Failed }

/** The "Restore purchases" button's state and the one message under it (FR-021–FR-025). */
sealed interface RestoreUi {
    data object Idle : RestoreUi
    data object Running : RestoreUi

    /** [parts] is the combined message (FR-023); the screen joins the sentences in order. */
    data class Result(val parts: List<RestoreMessagePart>) : RestoreUi
    data class Refused(val kind: RestoreRefusal, val retryAfterSeconds: Long? = null, val requestId: String? = null) : RestoreUi
}

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
    /** False on iOS: no Restore, Manage or Fix payment there (FR-055). */
    val playSupported: Boolean = false,
    val restore: RestoreUi = RestoreUi.Idle,
    /** Google Play's own prices by product id, in integer minor units; empty until Google Play answers (FR-036, FR-037). */
    val playPrices: Map<String, Money> = emptyMap(),
    val notes: PlayNotes = PlayNotes(),
) {
    /** The governing subscription is Google Play's and Google Play exists on this phone (FR-030). */
    val showManage: Boolean
        get() = playSupported && subscription?.let { it.isPaid && it.provider == GOOGLE_PLAY_PROVIDER } == true

    /** The Google Play product behind the governing subscription's plan, through the catalogue (backend gap 3). */
    val manageProductId: String?
        get() = subscription?.let { sub -> plans?.firstOrNull { it.id == sub.planId }?.playProductId }

    val problems: List<PaymentProblemUi>
        get() {
            if (!playSupported) return emptyList()
            return buildList {
                val sub = subscription
                if (showManage && sub != null && sub.status == SubscriptionStatus.Grace && sub.graceUntil != null) {
                    add(PaymentProblemUi.Grace(sub.graceUntil, sub.planName))
                }
                notes.heldProducts.forEach { product ->
                    // Without a catalogue plan for the product there is no name to give; the note waits for the catalogue.
                    plans?.firstOrNull { it.playProductId == product }?.let { add(PaymentProblemUi.Hold(it.name, product)) }
                }
                notes.pausedProducts.forEach { add(PaymentProblemUi.Paused(it)) }
            }
        }

    val showPending: Boolean get() = playSupported && notes.pendingProducts.isNotEmpty()

    val showAnotherAccount: Boolean get() = playSupported && notes.anotherAccount

    /** "Test purchase" only when the purchase behind the governing Google Play subscription said so in this run (FR-034). */
    val showTestLabel: Boolean
        get() = showManage && manageProductId?.let { it in notes.testProducts } == true

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
                price = catalogued.playProductId?.let { playPrices[it] },
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
    data object EnterCode : PlanIntent
    data object RestorePurchases : PlanIntent
    data object RetryRestore : PlanIntent
    data object DismissRestoreResult : PlanIntent
    data object OpenManage : PlanIntent
    data object OpenFixPayment : PlanIntent
}

sealed interface PlanEffect {
    data object NavigateBack : PlanEffect
    data object OpenRedeem : PlanEffect

    /** Google Play's subscription centre; the route opens it and reports a failure (FR-054). */
    data class OpenUrl(val url: String) : PlanEffect

    /** "You're on <plan>." (FR-017). */
    data class ShowActivated(val planName: String) : PlanEffect
}

sealed interface PlanChange {
    data class Entitlements(val plan: CurrentPlan?) : PlanChange
    data class SubscriptionChanged(val subscription: Subscription?) : PlanChange
    data class Catalogue(val plans: List<CataloguePlan>?) : PlanChange
    data object CatalogueUnavailable : PlanChange
    data object RefreshStarted : PlanChange
    data class RefreshFinished(val entitlements: RefreshOutcome, val catalogue: RefreshOutcome) : PlanChange
    data class TimeZone(val id: String) : PlanChange
    data class PlaySupport(val supported: Boolean) : PlanChange
    data class PlayPricesLoaded(val prices: Map<String, Money>) : PlanChange
    data class Notes(val notes: PlayNotes) : PlanChange
    data class Restore(val restore: RestoreUi) : PlanChange
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
    is PlanChange.PlaySupport -> state.copy(playSupported = change.supported)
    is PlanChange.PlayPricesLoaded -> state.copy(playPrices = change.prices)
    is PlanChange.Notes -> state.copy(notes = change.notes)
    is PlanChange.Restore -> state.copy(restore = change.restore)
}
