package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.transactions.domain.DueState
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** FR-018, FR-019: due and missed come from the phone's copy, ordered missed first then oldest; Home counts writable wallets only. */
class RecurringDueTest {

    private suspend fun env(scope: kotlinx.coroutines.CoroutineScope): RecurringServiceEnv {
        val env = RecurringServiceEnv(scope)
        env.seedWallet(walletJson(id = "w1"))
        env.seedWallet(walletJson(id = "w2", role = "viewer"))
        env.seedRule(ruleJson(id = "r1", walletId = "w1"))
        env.seedRule(ruleJson(id = "r2", walletId = "w2"))
        return env
    }

    @Test
    fun dueItemsAreMissedFirstThenOldestAndUpcomingIsNotDue() = runTest {
        val env = env(backgroundScope)
        // "Today" is 2 October 2026 in the rule's zone.
        env.seedOccurrence(occurrenceJson(id = "o1", ordinal = 1, scheduledDate = "2026-08-01"))
        env.seedOccurrence(occurrenceJson(id = "o2", ordinal = 2, scheduledDate = "2026-09-01"))
        env.seedOccurrence(occurrenceJson(id = "o3", ordinal = 3, scheduledDate = "2026-10-01"))
        env.seedOccurrence(occurrenceJson(id = "o4", ordinal = 4, scheduledDate = "2026-10-02"))
        env.seedOccurrence(occurrenceJson(id = "o5", ordinal = 5, scheduledDate = "2026-11-01"))
        val due = env.links.observeDue("w1").first()
        assertEquals(listOf("o1", "o2", "o3", "o4"), due.map { it.id })
        assertEquals(listOf(DueState.Missed, DueState.Missed, DueState.Missed, DueState.Due), due.map { it.state })
        assertEquals(listOf("o5"), env.service.observeOccurrences("w1").first().upcoming.map { it.id })
    }

    @Test
    fun theHomeCountCoversOnlyWalletsThePersonCanWrite() = runTest {
        val env = env(backgroundScope)
        env.seedOccurrence(occurrenceJson(id = "o1", walletId = "w1", ruleId = "r1", scheduledDate = "2026-10-01"))
        env.seedOccurrence(occurrenceJson(id = "o2", walletId = "w2", ruleId = "r2", scheduledDate = "2026-10-01"))
        assertEquals(1, RecurringDueSummary(env.service).observeDueCount().first())
        // A viewer still sees the due item, read-only.
        val viewerLists = env.service.observeOccurrences("w2").first()
        assertEquals(listOf("o2"), viewerLists.due.map { it.id })
        assertEquals(false, viewerLists.due.single().canWrite)
    }

    @Test
    fun aWaitingConfirmationLeavesTheDueList() = runTest {
        val env = env(backgroundScope)
        env.seedOccurrence(occurrenceJson(id = "o1", walletId = "w1", ruleId = "r1", scheduledDate = "2026-10-01"))
        env.service.confirm("o1", com.fares.saveme.feature.recurring.confirmDraft())
        assertEquals(emptyList(), env.links.observeDue("w1").first())
        assertEquals(0, RecurringDueSummary(env.service).observeDueCount().first())
    }
}
