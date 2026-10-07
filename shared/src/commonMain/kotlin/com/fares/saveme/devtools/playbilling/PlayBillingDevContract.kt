package com.fares.saveme.devtools.playbilling

/** The development-only "Google Play (scripted)" section (spec 018 FR-056): one store, pure reducer (P-16). */
data class PlayBillingDevState(
    val enabled: Boolean = false,
    val unavailable: Boolean = false,
    val purchaseCount: Int = 0,
    val purchaseId: String = "",
    val productId: String = "plus_monthly",
    val pending: Boolean = false,
    val acknowledged: Boolean = false,
    val priceMicros: String = "149000000",
) {
    val canAdd: Boolean get() = purchaseId.isNotBlank() && productId.isNotBlank()
    val canSetPrice: Boolean get() = productId.isNotBlank() && priceMicros.toLongOrNull()?.let { it > 0 } == true
}

sealed interface PlayBillingDevIntent {
    data class SetEnabled(val on: Boolean) : PlayBillingDevIntent
    data class SetUnavailable(val on: Boolean) : PlayBillingDevIntent
    data class PurchaseIdChanged(val value: String) : PlayBillingDevIntent
    data class ProductIdChanged(val value: String) : PlayBillingDevIntent
    data class PendingChanged(val on: Boolean) : PlayBillingDevIntent
    data class AcknowledgedChanged(val on: Boolean) : PlayBillingDevIntent
    data class PriceChanged(val value: String) : PlayBillingDevIntent
    data object AddPurchase : PlayBillingDevIntent
    data object ClearPurchases : PlayBillingDevIntent
    data object EmitUpdate : PlayBillingDevIntent
    data object SetPrice : PlayBillingDevIntent
    data object Back : PlayBillingDevIntent
}

sealed interface PlayBillingDevEffect {
    data object NavigateBack : PlayBillingDevEffect
}

sealed interface PlayBillingDevChange {
    data class Held(val enabled: Boolean, val unavailable: Boolean, val purchaseCount: Int) : PlayBillingDevChange
    data class PurchaseId(val value: String) : PlayBillingDevChange
    data class ProductId(val value: String) : PlayBillingDevChange
    data class Pending(val on: Boolean) : PlayBillingDevChange
    data class Acknowledged(val on: Boolean) : PlayBillingDevChange
    data class Price(val value: String) : PlayBillingDevChange
}

fun reduce(state: PlayBillingDevState, change: PlayBillingDevChange): PlayBillingDevState = when (change) {
    is PlayBillingDevChange.Held -> state.copy(enabled = change.enabled, unavailable = change.unavailable, purchaseCount = change.purchaseCount)
    is PlayBillingDevChange.PurchaseId -> state.copy(purchaseId = change.value.trim())
    is PlayBillingDevChange.ProductId -> state.copy(productId = change.value.trim())
    is PlayBillingDevChange.Pending -> state.copy(pending = change.on)
    is PlayBillingDevChange.Acknowledged -> state.copy(acknowledged = change.on)
    is PlayBillingDevChange.Price -> state.copy(priceMicros = change.value.filter { it.isDigit() })
}
