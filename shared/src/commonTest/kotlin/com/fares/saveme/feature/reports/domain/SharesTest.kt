package com.fares.saveme.feature.reports.domain

import com.fares.saveme.feature.reports.domain.model.Share
import kotlin.test.Test
import kotlin.test.assertEquals

/** FR-007: integer, half up, never from the server. */
class SharesTest {

    @Test
    fun roundsHalfUpAsTheBrief() {
        assertEquals(Share.Percent(64), Shares.of(350_000, 550_000))
        assertEquals(Share.Percent(22), Shares.of(120_000, 550_000))
        assertEquals(Share.Percent(15), Shares.of(80_000, 550_000))
    }

    @Test
    fun exactlyHalfRoundsUp() {
        assertEquals(Share.Percent(13), Shares.of(25, 200)) // 12.5
        assertEquals(Share.Percent(1), Shares.of(5, 1_000)) // 0.5
        assertEquals(Share.Percent(3), Shares.of(5, 200)) // 2.5
    }

    @Test
    fun aNonZeroValueUnderHalfAPercentIsUnderOne() {
        assertEquals(Share.UnderOne, Shares.of(4, 1_000))
        assertEquals(Share.UnderOne, Shares.of(1, 1_000_000))
    }

    @Test
    fun noShareWithoutATotal() {
        assertEquals(Share.None, Shares.of(0, 0))
        assertEquals(Share.None, Shares.of(10, 0))
    }

    @Test
    fun theWholeTotalIsOneHundredAndNothingOverflowsAtTheLimit() {
        assertEquals(Share.Percent(100), Shares.of(550_000, 550_000))
        val limit = (1L shl 53) - 1
        assertEquals(Share.Percent(100), Shares.of(limit, limit))
        assertEquals(Share.Percent(50), Shares.of(limit / 2, limit))
    }

    @Test
    fun theBarFillIsAFractionBetweenZeroAndOne() {
        assertEquals(0f, Shares.fraction(0, 100))
        assertEquals(1f, Shares.fraction(100, 100))
        assertEquals(0.25f, Shares.fraction(25, 100))
        assertEquals(0f, Shares.fraction(5, 0))
    }
}
