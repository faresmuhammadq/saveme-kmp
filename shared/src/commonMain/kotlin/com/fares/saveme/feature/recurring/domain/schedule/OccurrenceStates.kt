package com.fares.saveme.feature.recurring.domain.schedule

import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.OccurrenceStatus
import kotlinx.datetime.LocalDate

/** What the derivation needs of an occurrence; [status] already has the waiting changes applied (pending skip, pending confirm). */
data class StateSlot(val id: String, val date: LocalDate, val status: OccurrenceStatus, val isPayout: Boolean)

/**
 * Due, missed and upcoming are derived on the phone, never stored (spec 010 R-08, FR-018): due = scheduled and dated on
 * or before the rule's today; missed = due while a later non-payout occurrence of the same rule is also due.
 */
object OccurrenceStates {
    fun derive(slot: StateSlot, siblings: Collection<StateSlot>, today: LocalDate): OccurrenceState =
        deriveAll(siblings.plus(slot).distinctBy { it.id }, today).getValue(slot.id)

    /** The state of every slot of one rule. */
    fun deriveAll(slots: Collection<StateSlot>, today: LocalDate): Map<String, OccurrenceState> {
        val latestDueRound = slots.filter { it.status == OccurrenceStatus.Scheduled && !it.isPayout && it.date <= today }.maxOfOrNull { it.date }
        return slots.associate { slot ->
            slot.id to when (slot.status) {
                OccurrenceStatus.Confirmed -> OccurrenceState.Confirmed
                OccurrenceStatus.Skipped -> OccurrenceState.Skipped
                OccurrenceStatus.Unknown -> OccurrenceState.Unknown
                OccurrenceStatus.Scheduled ->
                    if (slot.date > today) OccurrenceState.Upcoming
                    else if (latestDueRound != null && slot.date < latestDueRound) OccurrenceState.Missed
                    else OccurrenceState.Due
            }
        }
    }
}
