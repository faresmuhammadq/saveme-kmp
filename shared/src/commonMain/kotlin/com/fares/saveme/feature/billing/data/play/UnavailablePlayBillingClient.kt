package com.fares.saveme.feature.billing.data.play

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The port where no real Google Play adapter is installed: iOS for good ([platformSupported] false) and Android until the Play
 * Billing dependency is approved (true; "not wired"). It reports Google Play as unavailable and holds nothing (FR-004).
 */
class UnavailablePlayBillingClient(override val platformSupported: Boolean) : PlayBillingClient {
    override suspend fun connect(): PlayConnection =
        PlayConnection.Unavailable(if (platformSupported) PlayUnavailable.NotWired else PlayUnavailable.NotSupported)

    override suspend fun productDetails(productIds: Set<String>): PlayResult<List<PlayProduct>> =
        PlayResult.Failed(PlayError.BillingUnavailable)

    override suspend fun purchases(): PlayResult<List<PlayPurchase>> = PlayResult.Failed(PlayError.BillingUnavailable)

    override val purchaseUpdates: Flow<PlayPurchaseUpdate> = emptyFlow()

    override suspend fun launchPurchase(request: PlayLaunchRequest): PlayLaunchResult =
        PlayLaunchResult.Failed(PlayError.BillingUnavailable)
}
