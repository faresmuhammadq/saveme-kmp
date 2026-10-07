package com.fares.saveme.feature.billing.play

import com.fares.saveme.feature.billing.data.play.PlayBillingClient
import com.fares.saveme.feature.billing.data.play.PlayConnection
import com.fares.saveme.feature.billing.data.play.PlayLaunchRequest
import com.fares.saveme.feature.billing.data.play.PlayLaunchResult
import com.fares.saveme.feature.billing.data.play.PlayProduct
import com.fares.saveme.feature.billing.data.play.PlayProductKind
import com.fares.saveme.feature.billing.data.play.PlayPurchase
import com.fares.saveme.feature.billing.data.play.PlayPurchaseState
import com.fares.saveme.feature.billing.data.play.PlayPurchaseUpdate
import com.fares.saveme.feature.billing.data.play.PlayResult
import com.fares.saveme.feature.billing.data.play.PurchaseToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Instant

/** A scripted Google Play for host tests. Every call is recorded; there is no acknowledge or consume to record (FR-002). */
class FakePlayBillingClient(override var platformSupported: Boolean = true) : PlayBillingClient {
    var connection: PlayConnection = PlayConnection.Ready
    var purchasesResult: PlayResult<List<PlayPurchase>> = PlayResult.Ok(emptyList())
    var productsResult: PlayResult<List<PlayProduct>> = PlayResult.Ok(emptyList())
    var launchResult: PlayLaunchResult = PlayLaunchResult.Launched
    val updates = MutableSharedFlow<PlayPurchaseUpdate>(extraBufferCapacity = 16)

    /** One entry per port call, in order: `connect`, `purchases`, `productDetails`, `launch`. */
    val calls = mutableListOf<String>()
    val productRequests = mutableListOf<Set<String>>()

    override suspend fun connect(): PlayConnection {
        calls += "connect"
        return connection
    }

    override suspend fun productDetails(productIds: Set<String>): PlayResult<List<PlayProduct>> {
        calls += "productDetails"
        productRequests += productIds
        return productsResult
    }

    override suspend fun purchases(): PlayResult<List<PlayPurchase>> {
        calls += "purchases"
        return purchasesResult
    }

    override val purchaseUpdates: Flow<PlayPurchaseUpdate> = updates

    override suspend fun launchPurchase(request: PlayLaunchRequest): PlayLaunchResult {
        calls += "launch"
        return launchResult
    }

    fun own(vararg purchases: PlayPurchase) {
        purchasesResult = PlayResult.Ok(purchases.toList())
    }
}

private val BASE_TIME = Instant.parse("2026-10-02T09:00:00Z")

/** A purchase as Google Play reports it; [minutesAgo] orders the most recent first. */
fun playPurchase(
    token: String,
    productId: String = "plus_monthly",
    state: PlayPurchaseState = PlayPurchaseState.Purchased,
    acknowledged: Boolean = false,
    minutesAgo: Int = 0,
) = PlayPurchase(
    token = PurchaseToken(token),
    productId = productId,
    state = state,
    acknowledged = acknowledged,
    obfuscatedAccountId = null,
    purchaseTime = Instant.fromEpochSeconds(BASE_TIME.epochSeconds - minutesAgo * 60L),
    kind = PlayProductKind.Subscription,
)
