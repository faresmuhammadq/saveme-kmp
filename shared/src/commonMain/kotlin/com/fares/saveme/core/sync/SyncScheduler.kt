package com.fares.saveme.core.sync

import kotlin.time.Duration

/**
 * Runs [SyncEngine.drain] later, outside any screen: once a connection exists, after [delay].
 * Android uses WorkManager so it survives process death (research R-03); iOS is a no-op.
 */
interface SyncScheduler {
    fun enqueue(delay: Duration = Duration.ZERO)

    fun cancel()
}
