package com.fares.saveme.feature.recurring.domain.schedule

import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.OccurrenceStatus
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Due, missed and upcoming are derived, never stored (FR-018, ADR-0018 rule 8). */
class OccurrenceStateTest {
    private fun slot(id: String, date: String, status: OccurrenceStatus = OccurrenceStatus.Scheduled, payout: Boolean = false) =
        StateSlot(id, LocalDate.parse(date), status, payout)

    private val today = LocalDate.parse("2026-10-15")

    @Test
    fun dueMeansScheduledAndDatedOnOrBeforeToday() {
        val s = listOf(slot("a", "2026-10-15"), slot("b", "2026-10-16"))
        val states = OccurrenceStates.deriveAll(s, today)
        assertEquals(OccurrenceState.Due, states["a"])
        assertEquals(OccurrenceState.Upcoming, states["b"])
    }

    @Test
    fun missedMeansDueWhileALaterNonPayoutIsAlsoDue() {
        val states = OccurrenceStates.deriveAll(listOf(slot("a", "2026-08-15"), slot("b", "2026-09-15"), slot("c", "2026-10-15")), today)
        assertEquals(OccurrenceState.Missed, states["a"])
        assertEquals(OccurrenceState.Missed, states["b"])
        assertEquals(OccurrenceState.Due, states["c"])
    }

    @Test
    fun aPayoutNeverMakesAnotherMissedButCanBeMissedItself() {
        val onlyPayoutLater = OccurrenceStates.deriveAll(listOf(slot("a", "2026-09-15"), slot("p", "2026-10-01", payout = true)), today)
        assertEquals(OccurrenceState.Due, onlyPayoutLater["a"])
        val roundAfterPayout = OccurrenceStates.deriveAll(listOf(slot("p", "2026-09-15", payout = true), slot("b", "2026-10-15")), today)
        assertEquals(OccurrenceState.Missed, roundAfterPayout["p"])
        val sameDay = OccurrenceStates.deriveAll(listOf(slot("r", "2026-10-15"), slot("p", "2026-10-15", payout = true)), today)
        assertEquals(OccurrenceState.Due, sameDay["r"])
        assertEquals(OccurrenceState.Due, sameDay["p"])
    }

    @Test
    fun resolvedAndUnknownStatusesKeepTheirOwnState() {
        val states = OccurrenceStates.deriveAll(
            listOf(
                slot("c", "2026-09-15", OccurrenceStatus.Confirmed), slot("s", "2026-09-16", OccurrenceStatus.Skipped),
                slot("u", "2026-09-17", OccurrenceStatus.Unknown), slot("d", "2026-10-15"),
            ),
            today,
        )
        assertEquals(OccurrenceState.Confirmed, states["c"])
        assertEquals(OccurrenceState.Skipped, states["s"])
        assertEquals(OccurrenceState.Unknown, states["u"])
        assertEquals(OccurrenceState.Due, states["d"], "a resolved sibling never makes it missed")
    }

    @Test
    fun overlaysAppliedFirstChangeTheDerivation() {
        // A pending skip of the later due round: the earlier one is the latest due and stays plain due.
        val skipped = OccurrenceStates.deriveAll(listOf(slot("a", "2026-09-15"), slot("b", "2026-10-15", OccurrenceStatus.Skipped)), today)
        assertEquals(OccurrenceState.Due, skipped["a"])
        val confirmed = OccurrenceStates.deriveAll(listOf(slot("a", "2026-09-15"), slot("b", "2026-10-15", OccurrenceStatus.Confirmed)), today)
        assertEquals(OccurrenceState.Due, confirmed["a"])
    }

    @Test
    fun theSingleSlotFormMatches() {
        val a = slot("a", "2026-09-15")
        val b = slot("b", "2026-10-15")
        assertEquals(OccurrenceState.Missed, OccurrenceStates.derive(a, listOf(b), today))
    }
}
