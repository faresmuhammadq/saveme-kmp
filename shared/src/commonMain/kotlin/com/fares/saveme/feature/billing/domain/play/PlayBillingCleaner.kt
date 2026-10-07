package com.fares.saveme.feature.billing.domain.play

import com.fares.saveme.core.session.UserDataCleaner

/**
 * Sign-out clears every in-memory billing note, pending mark, refused set, price and backoff timer (FR-051). Nothing of this
 * feature is persisted, so nothing else needs clearing.
 */
class PlayBillingCleaner(
    private val notes: PlayBillingNotes,
    private val prices: PlayPriceLoader,
    /** Resolved when it runs: the verifier needs the session manager, which needs this cleaner (the Koin cycle of spec 006). */
    private val cancelTimers: () -> Unit,
) : UserDataCleaner {
    override suspend fun clear() {
        cancelTimers()
        notes.reset()
        prices.clear()
    }
}
