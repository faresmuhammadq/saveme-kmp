package com.fares.saveme.feature.auth.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * How many recurring occurrences are due in wallets the person can write to, for Home's "Due" card (spec 010 FR-019b).
 * Auth owns the interface so Home never imports recurring (P-10.4); the recurring module rebinds [NoDueSummary].
 */
interface DueSummary {
    fun observeDueCount(): Flow<Int>

    /** The most urgent due item (missed first, then oldest), for the card's second line; null when nothing is due. */
    fun observeFirstDue(): Flow<DueGlimpse?> = flowOf(null)
}

/** The title (null when the rule has no description) and date of the most urgent due item. */
data class DueGlimpse(val title: String?, val date: kotlinx.datetime.LocalDate) {
    override fun toString() = "DueGlimpse"
}

object NoDueSummary : DueSummary {
    override fun observeDueCount(): Flow<Int> = flowOf(0)
}
