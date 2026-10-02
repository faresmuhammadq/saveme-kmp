package com.fares.saveme.core.sync

import kotlin.time.Instant

/** What the account area shows (FR-016): changes still only on the phone, and the last sync. */
data class SyncStatus(
    /** Waiting or being sent. */
    val waiting: Int = 0,
    /** Clashes and refusals: the person has to choose. */
    val needsDecision: Int = 0,
    val lastSyncedAt: Instant? = null,
) {
    val pending: Int get() = waiting + needsDecision
}
