package com.fares.saveme.feature.recurring.domain.schedule

import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Integer allocation and the gam'eya payout (FR-005, FR-010, research R-08). */
class AllocationTest {
    @Test
    fun aTotalSplitsWithTheRemainderToTheEarliestRounds() {
        val shares = Allocation.split(1_000_000, 12)!!
        assertEquals(List(4) { 83_334L } + List(8) { 83_333L }, shares)
        assertEquals(1_000_000, shares.sum())
    }

    @Test
    fun currenciesWithOtherFractionDigitsSplitInMinorUnits() {
        assertEquals(listOf(3_334L, 3_333L, 3_333L), Allocation.split(10_000, 3)) // KWD 10.000 in fils
        assertEquals(listOf(34L, 33L, 33L), Allocation.split(100, 3)) // JPY has no minor digits; same arithmetic
    }

    @Test
    fun aTotalBelowTheNumberOfRoundsIsRefused() {
        assertNull(Allocation.split(5, 6))
        assertEquals(List(6) { 1L }, Allocation.split(6, 6))
        assertNull(Allocation.split(100, 0))
    }

    @Test
    fun theRoundAmountFollowsTheBasis() {
        assertEquals(500, Allocation.forRound(AmountBasis.PerOccurrence, 500, 12, 3))
        assertEquals(83_334, Allocation.forRound(AmountBasis.Total, 1_000_000, 12, 4))
        assertEquals(83_333, Allocation.forRound(AmountBasis.Total, 1_000_000, 12, 5))
        assertEquals(500, Allocation.forRound(AmountBasis.Total, 500, null, 1))
    }

    @Test
    fun theGameyaPayoutIsTheSumOfTheRounds() {
        assertEquals(10_000_000, Allocation.payout(AmountBasis.PerOccurrence, 1_000_000, 10))
        assertEquals(1_000_000, Allocation.payout(AmountBasis.Total, 1_000_000, 10))
    }

    @Test
    fun aPayoutAboveTheExactLimitIsRefused() {
        assertNull(Allocation.payout(AmountBasis.PerOccurrence, Allocation.MAX_MINOR / 2 + 1, 2))
        assertEquals(Allocation.MAX_MINOR - 1, Allocation.payout(AmountBasis.PerOccurrence, (Allocation.MAX_MINOR - 1) / 2, 2))
    }
}
