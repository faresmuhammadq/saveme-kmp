package com.fares.saveme.feature.budgets.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DayTickerTest {

    @Test
    fun emitsNowThenAgainAtTheStartOfEveryMinute() = runTest {
        val start = Instant.parse("2026-10-31T23:58:30Z").toEpochMilliseconds()
        val clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(start + testScheduler.currentTime)
        }
        val seen = mutableListOf<Instant>()
        backgroundScope.launch { DayTicker(clock).ticks().collect { seen += it } }
        runCurrent()
        assertEquals(listOf(Instant.parse("2026-10-31T23:58:30Z")), seen)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(Instant.parse("2026-10-31T23:59:00Z"), seen.last())
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), seen.last())
        assertEquals(3, seen.size)
    }
}
