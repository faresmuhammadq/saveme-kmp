package com.fares.saveme.core.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Waits between tries after a busy or failing server (FR-009). It never gives up. */
class Backoff(
    private val steps: List<Duration> = listOf(2.seconds, 5.seconds, 15.seconds, 30.seconds, 60.seconds, 2.minutes, 5.minutes),
) {
    /** [attempts] counts failures so far (1 for the first); the server's wait wins when longer. */
    fun delayFor(attempts: Int, retryAfterSeconds: Long?): Duration {
        val step = steps[(attempts - 1).coerceIn(0, steps.lastIndex)]
        val server = retryAfterSeconds?.seconds ?: Duration.ZERO
        return maxOf(step, server)
    }

    /**
     * After a send that couldn't reach the server: none for the first ([attempts] = 1), then the
     * normal steps capped at [offlineCap], so a phone that comes back online sends within a minute.
     */
    fun offlineDelay(attempts: Int, offlineCap: Duration = 30.seconds): Duration =
        if (attempts <= 1) Duration.ZERO else minOf(delayFor(attempts - 1, null), offlineCap)
}
