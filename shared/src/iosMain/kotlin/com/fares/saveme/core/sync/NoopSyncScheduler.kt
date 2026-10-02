package com.fares.saveme.core.sync

import kotlin.time.Duration

/** iOS has no live API yet (spec 001), so there is nothing to send in the background. */
class NoopSyncScheduler : SyncScheduler {
    override fun enqueue(delay: Duration) = Unit

    override fun cancel() = Unit
}
