package com.fares.saveme.core.sync

import com.fares.saveme.core.database.FeedPlace
import kotlin.test.Test
import kotlin.test.assertEquals

/** One case per row of contracts/sync-heads-client.md, first match wins (spec 011 FR-005, R-04). */
class HeadsDecisionTest {

    private fun head(changeSeq: Long, purged: Long = 0) = WalletHeadDto("w1", changeSeq, purged)

    private fun decide(place: FeedPlace?, head: WalletHeadDto?) = HeadsDecision.decide(place, head)

    @Test
    fun aWalletThatIsNotListedIsReadAsBefore() {
        assertEquals(WalletReadPlan.Read, decide(FeedPlace("c", 10), null))
    }

    @Test
    fun aWalletWithNoPlaceIsRead() {
        assertEquals(WalletReadPlan.Read, decide(null, head(10)))
        assertEquals(WalletReadPlan.Read, decide(FeedPlace(null, null), head(10)))
        assertEquals(WalletReadPlan.Read, decide(FeedPlace(null, 10), head(10)), "a position without a cursor is no place")
    }

    @Test
    fun aPlaceWithoutAPositionIsReadSoItsPositionGetsStored() {
        assertEquals(WalletReadPlan.Read, decide(FeedPlace("c", null), head(10)))
        assertEquals(WalletReadPlan.Read, decide(FeedPlace("c", null), head(0)))
    }

    @Test
    fun aPurgeHorizonAboveThePositionReadsFromTheStartEvenWhenTheHeadMoved() {
        assertEquals(WalletReadPlan.ReadFromStart, decide(FeedPlace("c", 10), head(changeSeq = 50, purged = 11)))
        assertEquals(WalletReadPlan.ReadFromStart, decide(FeedPlace("c", 10), head(changeSeq = 10, purged = 11)), "also when the head equals the position")
    }

    @Test
    fun aHorizonEqualToThePositionIsNotExpiredAndTheHeadDecides() {
        assertEquals(WalletReadPlan.Skip, decide(FeedPlace("c", 10), head(changeSeq = 10, purged = 10)))
        assertEquals(WalletReadPlan.Read, decide(FeedPlace("c", 10), head(changeSeq = 12, purged = 10)))
    }

    @Test
    fun aHeadEqualToThePositionIsSkipped() {
        assertEquals(WalletReadPlan.Skip, decide(FeedPlace("c", 10), head(10)))
        assertEquals(WalletReadPlan.Skip, decide(FeedPlace("c", 0), head(0)))
    }

    @Test
    fun aHeadAheadOfThePositionIsRead() {
        assertEquals(WalletReadPlan.Read, decide(FeedPlace("c", 10), head(11)))
    }

    @Test
    fun aHeadBehindThePositionIsReadAndTheServerDecidesWhetherThePlaceIsValid() {
        assertEquals(WalletReadPlan.Read, decide(FeedPlace("c", 10), head(9)))
    }
}
