package com.fares.saveme.feature.budgets.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Emits the present instant, then again at the start of every minute while collected, so days elapsed, the projection and
 * the period itself re-derive when the day turns without any write (research R-08, US5-S6). Budgets keeps its own copy so
 * it never depends on recurring.
 */
class DayTicker(private val clock: Clock = Clock.System) {
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
