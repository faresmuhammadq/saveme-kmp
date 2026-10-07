package com.fares.saveme.devtools.playbilling

import androidx.lifecycle.viewModelScope
import com.fares.saveme.core.presentation.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Edits the scripted Google Play held by the dev flavour's port. Development builds only. */
class PlayBillingDevViewModel(
    private val client: SwitchingPlayBillingClient,
) : MviViewModel<PlayBillingDevState, PlayBillingDevIntent, PlayBillingDevEffect>(PlayBillingDevState()) {

    init {
        combine(client.scriptedEnabled, client.scripted.state) { enabled, held -> PlayBillingDevChange.Held(enabled, held.unavailable, held.purchases.size) }
            .onEach { change -> setState { reduce(it, change) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: PlayBillingDevIntent) {
        when (intent) {
            is PlayBillingDevIntent.SetEnabled -> client.setScriptedEnabled(intent.on)
            is PlayBillingDevIntent.SetUnavailable -> client.scripted.setUnavailable(intent.on)
            is PlayBillingDevIntent.PurchaseIdChanged -> setState { reduce(it, PlayBillingDevChange.PurchaseId(intent.value)) }
            is PlayBillingDevIntent.ProductIdChanged -> setState { reduce(it, PlayBillingDevChange.ProductId(intent.value)) }
            is PlayBillingDevIntent.PendingChanged -> setState { reduce(it, PlayBillingDevChange.Pending(intent.on)) }
            is PlayBillingDevIntent.AcknowledgedChanged -> setState { reduce(it, PlayBillingDevChange.Acknowledged(intent.on)) }
            is PlayBillingDevIntent.PriceChanged -> setState { reduce(it, PlayBillingDevChange.Price(intent.value)) }
            PlayBillingDevIntent.AddPurchase -> currentState.takeIf { it.canAdd }?.let {
                client.scripted.addPurchase(it.purchaseId, it.productId, it.pending, it.acknowledged)
            }
            PlayBillingDevIntent.ClearPurchases -> client.scripted.clearPurchases()
            PlayBillingDevIntent.EmitUpdate -> client.scripted.emitPurchaseUpdate()
            PlayBillingDevIntent.SetPrice -> currentState.takeIf { it.canSetPrice }?.let {
                client.scripted.setPrice(it.productId, it.priceMicros.toLong())
            }
            PlayBillingDevIntent.Back -> sendEffect(PlayBillingDevEffect.NavigateBack)
        }
    }
}
