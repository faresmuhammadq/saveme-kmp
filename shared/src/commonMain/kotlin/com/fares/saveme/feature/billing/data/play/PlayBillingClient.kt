package com.fares.saveme.feature.billing.data.play

import kotlinx.coroutines.flow.Flow
import kotlin.jvm.JvmInline
import kotlin.time.Instant

/**
 * The only place the app touches Google Play (spec 018 contracts/play-billing-port.md). Plain Kotlin types: no Play Billing
 * Library type crosses it, and it imports nothing from `domain` (P-10.6).
 *
 * There is deliberately **no acknowledge and no consume** member: the server acknowledges every purchase it links, the app
 * never does (FR-002, backend spec 024 "Frozen for C10"). Implementations never log a token, a reference or a price.
 */
interface PlayBillingClient {
    /** False on iOS: Google Play cannot exist there (FR-055). True on Android even while no real adapter is wired. */
    val platformSupported: Boolean

    /** Idempotent and cheap when already connected. */
    suspend fun connect(): PlayConnection

    /** One price per known product (the base plan's recurring price, R-10). Unknown products are absent. */
    suspend fun productDetails(productIds: Set<String>): PlayResult<List<PlayProduct>>

    /** Subscriptions and one-time products Google Play reports as owned now, pending and purchased, in any order. */
    suspend fun purchases(): PlayResult<List<PlayPurchase>>

    /** Hot, buffered, never completes: what Google Play's purchase listener delivers. */
    val purchaseUpdates: Flow<PlayPurchaseUpdate>

    /** Opens Google Play's purchase sheet (US6 only; nothing in the shipped app calls it while the switch is off). */
    suspend fun launchPurchase(request: PlayLaunchRequest): PlayLaunchResult
}

sealed interface PlayConnection {
    data object Ready : PlayConnection
    data class Unavailable(val reason: PlayUnavailable) : PlayConnection
}

enum class PlayUnavailable { NotSupported, NotWired, BillingUnavailable, Disconnected, Error }

sealed interface PlayResult<out T> {
    data class Ok<T>(val value: T) : PlayResult<T>
    data class Failed(val code: PlayError) : PlayResult<Nothing>
}

enum class PlayError {
    Network,
    ServiceUnavailable,
    BillingUnavailable,
    ItemUnavailable,
    ItemAlreadyOwned,
    DeveloperError,
    FeatureNotSupported,
    Unknown,
}

/** A purchase token is a secret: it never prints (P-09.2). */
@JvmInline
value class PurchaseToken(val value: String) {
    override fun toString() = "PurchaseToken(***)"
}

/** The server-issued value passed to Google Play as the obfuscated account id (US6): memory only, never printed. */
@JvmInline
value class AccountReference(val value: String) {
    override fun toString() = "AccountReference(***)"
}

enum class PlayPurchaseState { Pending, Purchased }

enum class PlayProductKind { Subscription, OneTime }

/** One purchase as Google Play reports it. Never stored by the app. */
data class PlayPurchase(
    val token: PurchaseToken,
    val productId: String,
    val state: PlayPurchaseState,
    val acknowledged: Boolean,
    val obfuscatedAccountId: String?,
    val purchaseTime: Instant,
    val kind: PlayProductKind,
) {
    override fun toString() = "PlayPurchase(product=$productId, state=$state, acknowledged=$acknowledged)"
}

/** The base plan's recurring price in micro-units (a one-time product: its price). Prices never print (FR-050). */
data class PlayProduct(
    val productId: String,
    val kind: PlayProductKind,
    val priceMicros: Long,
    val currency: String,
    val basePlanId: String? = null,
    val offerToken: String? = null,
) {
    override fun toString() = "PlayProduct(product=$productId, kind=$kind)"
}

sealed interface PlayPurchaseUpdate {
    data class Purchased(val purchases: List<PlayPurchase>) : PlayPurchaseUpdate
    data object Cancelled : PlayPurchaseUpdate
    data class Failed(val code: PlayError) : PlayPurchaseUpdate
}

data class PlayLaunchRequest(
    val productId: String,
    val offerToken: String,
    val obfuscatedAccountId: AccountReference,
)

sealed interface PlayLaunchResult {
    data object Launched : PlayLaunchResult
    data class Failed(val code: PlayError) : PlayLaunchResult
}
