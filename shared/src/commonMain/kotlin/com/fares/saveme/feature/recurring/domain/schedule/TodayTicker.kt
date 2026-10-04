package com.fares.saveme.feature.recurring.domain.schedule

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Emits the present instant, then again at the start of every minute while collected, so due states re-derive when a
 * rule's day changes without any write (spec 010 R-08, ADR-0018 rule 8).
 */
class TodayTicker(private val clock: Clock = Clock.System) {
    fun ticks(): Flow<Instant> = flow {
        while (true) {
            val now = clock.now()
            emit(now)
            delay(MINUTE_MS - now.toEpochMilliseconds() % MINUTE_MS)
        }
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
