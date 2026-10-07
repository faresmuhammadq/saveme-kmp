package com.fares.saveme.devtools.playbilling

import com.fares.saveme.feature.billing.data.play.PlayBillingClient
import com.fares.saveme.feature.billing.data.play.PlayConnection
import com.fares.saveme.feature.billing.data.play.PlayError
import com.fares.saveme.feature.billing.data.play.PlayLaunchRequest
import com.fares.saveme.feature.billing.data.play.PlayLaunchResult
import com.fares.saveme.feature.billing.data.play.PlayProduct
import com.fares.saveme.feature.billing.data.play.PlayProductKind
import com.fares.saveme.feature.billing.data.play.PlayPurchase
import com.fares.saveme.feature.billing.data.play.PlayPurchaseState
import com.fares.saveme.feature.billing.data.play.PlayPurchaseUpdate
import com.fares.saveme.feature.billing.data.play.PlayResult
import com.fares.saveme.feature.billing.data.play.PlayUnavailable
import com.fares.saveme.feature.billing.data.play.PurchaseToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

/** What the development-only Google Play holds (spec 018 R-12). Tokens here match the local backend's scripted `fake` gateway. */
data class ScriptedPlayState(
    val purchases: List<PlayPurchase> = emptyList(),
    val unavailable: Boolean = false,
    val prices: Map<String, Long> = emptyMap(),
)

/**
 * Development builds only (FR-056): an in-memory Google Play edited from the Dev tools screen. It never reaches Google. It has no
 * acknowledge or consume either; the local backend's `fake` gateway, scripted with the same tokens, plays the server's part.
 */
class ScriptedPlayBillingClient(private val clock: Clock = Clock.System) : PlayBillingClient {
    private val mutable = MutableStateFlow(ScriptedPlayState())
    val state: StateFlow<ScriptedPlayState> = mutable.asStateFlow()

    private val updates = MutableSharedFlow<PlayPurchaseUpdate>(extraBufferCapacity = 16)

    override val platformSupported: Boolean = true

    override suspend fun connect(): PlayConnection =
        if (mutable.value.unavailable) PlayConnection.Unavailable(PlayUnavailable.BillingUnavailable) else PlayConnection.Ready

    override suspend fun productDetails(productIds: Set<String>): PlayResult<List<PlayProduct>> {
        val held = mutable.value
        if (held.unavailable) return PlayResult.Failed(PlayError.BillingUnavailable)
        return PlayResult.Ok(
            held.prices.filterKeys { it in productIds }.map { (id, micros) -> PlayProduct(id, PlayProductKind.Subscription, micros, CURRENCY) },
        )
    }

    override suspend fun purchases(): PlayResult<List<PlayPurchase>> {
        val held = mutable.value
        return if (held.unavailable) PlayResult.Failed(PlayError.BillingUnavailable) else PlayResult.Ok(held.purchases)
    }

    override val purchaseUpdates: Flow<PlayPurchaseUpdate> = updates

    override suspend fun launchPurchase(request: PlayLaunchRequest): PlayLaunchResult = PlayLaunchResult.Failed(PlayError.FeatureNotSupported)

    fun addPurchase(token: String, productId: String, pending: Boolean, acknowledged: Boolean) {
        val purchase = PlayPurchase(
            token = PurchaseToken(token),
            productId = productId,
            state = if (pending) PlayPurchaseState.Pending else PlayPurchaseState.Purchased,
            acknowledged = acknowledged,
            obfuscatedAccountId = null,
            purchaseTime = clock.now(),
            kind = PlayProductKind.Subscription,
        )
        // The same token replaces its earlier entry: flipping pending to purchased is how a slow payment completes.
        mutable.update { it.copy(purchases = it.purchases.filterNot { held -> held.token == purchase.token } + purchase) }
    }

    fun clearPurchases() = mutable.update { it.copy(purchases = emptyList()) }

    fun setUnavailable(unavailable: Boolean) = mutable.update { it.copy(unavailable = unavailable) }

    fun setPrice(productId: String, micros: Long) = mutable.update { it.copy(prices = it.prices + (productId to micros)) }

    /** What Google Play's listener delivers after a purchase: the current list as purchased. */
    fun emitPurchaseUpdate() {
        updates.tryEmit(PlayPurchaseUpdate.Purchased(mutable.value.purchases))
    }

    private companion object {
        const val CURRENCY = "EGP"
    }
}

/**
 * The dev flavour's port: the scripted client while it is switched on, the build's own otherwise. Release builds never contain it
 * (the module only chooses it under `isDebugTools`).
 */
class SwitchingPlayBillingClient(
    private val real: PlayBillingClient,
    val scripted: ScriptedPlayBillingClient,
) : PlayBillingClient {
    private val scriptedOn = MutableStateFlow(false)
    val scriptedEnabled: StateFlow<Boolean> = scriptedOn.asStateFlow()

    fun setScriptedEnabled(on: Boolean) {
        scriptedOn.value = on
    }

    private val active: PlayBillingClient get() = if (scriptedOn.value) scripted else real

    override val platformSupported: Boolean = true

    override suspend fun connect() = active.connect()

    override suspend fun productDetails(productIds: Set<String>) = active.productDetails(productIds)

    override suspend fun purchases() = active.purchases()

    override val purchaseUpdates: Flow<PlayPurchaseUpdate> = merge(real.purchaseUpdates, scripted.purchaseUpdates)

    override suspend fun launchPurchase(request: PlayLaunchRequest) = active.launchPurchase(request)
}
