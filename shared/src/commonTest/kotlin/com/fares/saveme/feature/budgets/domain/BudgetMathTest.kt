package com.fares.saveme.feature.budgets.domain

import com.fares.saveme.feature.budgets.domain.model.BudgetPeriod
import com.fares.saveme.feature.budgets.domain.model.FigureSource
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Integer arithmetic with the server's rounding (FR-013, research R-07, P-03). */
class BudgetMathTest {

    @Test
    fun remainingIsNegativeWhenOverAndOverLimitIsExactlySpentAboveLimit() {
        assertEquals(250_000, BudgetMath.remaining(350_000, 600_000))
        assertEquals(-50_000, BudgetMath.remaining(650_000, 600_000))
        assertFalse(BudgetMath.overLimit(600_000, 600_000))
        assertTrue(BudgetMath.overLimit(600_001, 600_000))
        assertFalse(BudgetMath.overLimit(0, 600_000))
    }

    @Test
    fun percentageIsWholeAndHalfUp() {
        assertEquals(86, BudgetMath.percent(3_420, 4_000))
        assertEquals(0, BudgetMath.percent(0, 4_000))
        assertEquals(112, BudgetMath.percent(4_480, 4_000))
        assertEquals(58, BudgetMath.percent(350_000, 600_000))
        assertEquals(1, BudgetMath.percent(5, 1_000))
        assertEquals(0, BudgetMath.percent(4, 1_000))
    }

    @Test
    fun projectionIsSpentTimesDaysOverElapsedRoundedUp() {
        // The captured example: 350,000 x 30 / 8 = 1,312,500; the backend rounds up (ADR-0019).
        assertEquals(1_312_500L, BudgetMath.projectedEnd(350_000, 30, 8))
        assertEquals(4L, BudgetMath.projectedEnd(1, 31, 10))
        assertEquals(31L, BudgetMath.projectedEnd(1, 31, 1))
        assertEquals(0L, BudgetMath.projectedEnd(0, 31, 5))
        assertEquals(6_000L, BudgetMath.projectedEnd(6_000, 30, 30))
    }

    @Test
    fun noProjectionWithoutElapsedDaysOrWhenItWouldOverflow() {
        assertNull(BudgetMath.projectedEnd(100, 30, null))
        assertNull(BudgetMath.projectedEnd(100, 30, 0))
        assertNull(BudgetMath.projectedEnd(Long.MAX_VALUE / 30 + 1, 30, 2), "the product would overflow")
        assertNull(BudgetMath.projectedEnd(BudgetMath.MAX_MINOR, 31, 1), "above 2^53 - 1")
        assertEquals(BudgetMath.MAX_MINOR, BudgetMath.projectedEnd(BudgetMath.MAX_MINOR, 31, 31))
    }

    @Test
    fun figuresAreDerivedFromSpentAndLimitForEveryCurrencyDigit() {
        val period = BudgetPeriod(LocalDate(2026, 9, 25), LocalDate(2026, 10, 24), 30, 8)
        // EGP has 2 digits, KWD 3, JPY 0: the arithmetic is on minor units, so the same integers work for all of them.
        listOf("EGP", "KWD", "JPY").forEach { currency ->
            val figures = BudgetMath.figures(350_000, 600_000, period, listOf("USD"), FigureSource.Phone, includesWaiting = true)
            assertEquals(350_000, figures.spentMinor, currency)
            assertEquals(250_000, figures.remainingMinor)
            assertEquals(58, figures.percent)
            assertEquals(1_312_500L, figures.projectedMinor)
            assertFalse(figures.overLimit)
            assertEquals(listOf("USD"), figures.otherCurrencies)
            assertTrue(figures.includesWaiting)
        }
        val over = BudgetMath.figures(650_000, 600_000, period, emptyList(), FigureSource.Server, includesWaiting = false)
        assertTrue(over.overLimit)
        assertEquals(-50_000, over.remainingMinor)
        assertEquals(108, over.percent)
    }

    @Test
    fun aPastOrFuturePeriodHasNoProjection() {
        val past = BudgetPeriod(LocalDate(2026, 8, 1), LocalDate(2026, 8, 31), 31, null)
        assertNull(BudgetMath.figures(100, 1_000, past, emptyList(), FigureSource.Phone, false).projectedMinor)
    }
}
