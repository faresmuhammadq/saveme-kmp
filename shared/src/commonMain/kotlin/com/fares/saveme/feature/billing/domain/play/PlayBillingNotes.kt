package com.fares.saveme.feature.billing.domain.play

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

/** "You're on <plan>." shown once (FR-017). */
data class PlanActivated(val planName: String)

/**
 * What this run knows about the signed-in person's Google Play purchases (spec 018 data-model §3). Memory only, per person;
 * tokens are never held, only one-way digests. Sets of products carry catalogue product ids, never tokens or prices.
 */
data class PlayNotes(
    val userId: String? = null,
    /** Google Play reports it pending, or the server answered `202` and Google Play still lists it (FR-033). */
    val pendingProducts: Set<String> = emptySet(),
    /** After `PURCHASE_ALREADY_LINKED` or `linked_elsewhere` in this run (FR-032). */
    val anotherAccount: Boolean = false,
    /** From a verify answer's `purchase.state` in this run (FR-031). */
    val heldProducts: Set<String> = emptySet(),
    val pausedProducts: Set<String> = emptySet(),
    /** From a verify answer's `test: true` in this run (FR-034). */
    val testProducts: Set<String> = emptySet(),
    /** Purchases (by digest) not sent again by automatic triggers in this run (FR-016). */
    val refused: Set<String> = emptySet(),
    /** Pending purchases (by digest) already sent in this run (FR-013). */
    val pendingSent: Set<String> = emptySet(),
    /** Purchased purchases the server answered `202` for: digest to the product id and the earliest recheck (FR-015). */
    val answeredPending: Map<String, AnsweredPending> = emptyMap(),
    /** Automatic sends so far and the earliest next one, per purchase (digest); cleared by every outside trigger (FR-016). */
    val retry: Map<String, RetryState> = emptyMap(),
    val catalogueReloaded: Boolean = false,
    val activated: PlanActivated? = null,
)

data class RetryState(val sends: Int, val nextAt: Instant)

data class AnsweredPending(val productId: String, val recheckAfter: Instant)

/**
 * The one holder of [PlayNotes]: reset when another person signs in or at sign-out (FR-051). Every write names the person it is
 * for, so a late answer for a person who has gone is dropped.
 */
class PlayBillingNotes {
    private val mutable = MutableStateFlow(PlayNotes())
    val notes: StateFlow<PlayNotes> = mutable.asStateFlow()

    private val screens = MutableStateFlow(0)

    /** The Plan screen is showing (FR-017): only then does a changed plan announce itself. */
    val planScreenShowing: Boolean get() = screens.value > 0

    fun planScreenOpened() = screens.update { it + 1 }

    fun planScreenClosed() = screens.update { (it - 1).coerceAtLeast(0) }

    fun current(userId: String): PlayNotes = mutable.value.takeIf { it.userId == userId } ?: PlayNotes(userId = userId)

    /** Applies [change] to [userId]'s notes (starting fresh when the notes were another person's). */
    fun update(userId: String, change: (PlayNotes) -> PlayNotes) {
        mutable.update { held -> change(if (held.userId == userId) held else PlayNotes(userId = userId)) }
    }

    /** Reads the one-shot "activated" note and removes it (FR-017). */
    fun consumeActivated(): PlanActivated? {
        var taken: PlanActivated? = null
        mutable.update { held ->
            taken = held.activated
            if (held.activated != null) held.copy(activated = null) else held
        }
        return taken
    }

    fun reset() {
        mutable.value = PlayNotes()
    }
}
