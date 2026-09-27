package com.fares.saveme.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals

class BudgetStateTest {

    @Test
    fun underEightyPercentIsPositive() {
        assertEquals(BudgetState.Positive, budgetState(spentMinor = 79, limitMinor = 100))
    }

    @Test
    fun eightyToOneHundredIsPending() {
        assertEquals(BudgetState.Pending, budgetState(80, 100))
        assertEquals(BudgetState.Pending, budgetState(100, 100))
    }

    @Test
    fun overOneHundredIsOver() {
        assertEquals(BudgetState.Over, budgetState(101, 100))
    }

    @Test
    fun justUnderEightyStaysPositiveEvenIfItRoundsToEighty() {
        assertEquals(BudgetState.Positive, budgetState(7_995, 10_000))
    }

    @Test
    fun percentIsRoundedToNearestAsOnTheBoard() {
        assertEquals(51, budgetPercent(61_000, 120_000))
        assertEquals(86, budgetPercent(342_000, 400_000))
        assertEquals(128, budgetPercent(154_000, 120_000))
    }

    @Test
    fun zeroLimitCountsAsOver() {
        assertEquals(BudgetState.Over, budgetState(1, 0))
    }
}
