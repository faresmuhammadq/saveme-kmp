package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.data.dto.OccurrencePayload
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.transactions.domain.ConfirmFromDraftResult
import com.fares.saveme.feature.transactions.domain.DueState
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-031: a hand entry is offered the received occurrence it may duplicate; accepting confirms that occurrence instead. */
class RecurringMatchesTest {

    private val today = LocalDate(2026, 10, 2)

    private suspend fun env(scope: kotlinx.coroutines.CoroutineScope, role: String = "owner"): RecurringServiceEnv {
        val env = RecurringServiceEnv(scope, newIds = listOf("tc1"))
        env.seedWallet(walletJson(id = "w1", role = role))
        env.seedWallet(walletJson(id = "w3"))
        env.seedRule(ruleJson(id = "r1"))
        return env
    }

    private suspend fun RecurringServiceEnv.matches(date: LocalDate = today, kind: String = "expense", category: String? = "c1", counter: String? = null) =
        links.observeMatches("w1", kind, category, counter, date).first()

    @Test
    fun aReceivedScheduledOccurrenceOfTheSameKindAndCategoryWithinAWeekMatches() = runTest {
        val env = env(backgroundScope)
        env.seedOccurrence(occurrenceJson(id = "o1", scheduledDate = "2026-10-01"))
        env.seedOccurrence(occurrenceJson(id = "o2", ordinal = 2, scheduledDate = "2026-10-08"))
        env.seedOccurrence(occurrenceJson(id = "o3", ordinal = 3, scheduledDate = "2026-10-09"))
        env.seedOccurrence(occurrenceJson(id = "o4", ordinal = 4, scheduledDate = "2026-09-24"))
        assertEquals(listOf("o1", "o2", "o3"), env.matches().map { it.id }, "nearest first; 7 days is in, 8 is out")
        assertEquals(DueState.Due, env.matches().first().state)
    }

    @Test
    fun otherKindsCategoriesAndStatesDoNotMatch() = runTest {
        val env = env(backgroundScope)
        env.seedOccurrence(occurrenceJson(id = "o1", scheduledDate = "2026-10-01", categoryId = "c2"))
        env.seedOccurrence(occurrenceJson(id = "o2", ordinal = 2, scheduledDate = "2026-10-01", status = "skipped"))
        env.seedOccurrence(occurrenceJson(id = "o3", ordinal = 3, scheduledDate = "2026-10-01", status = "confirmed", transactionId = "t1"))
        env.seedOccurrence(occurrenceJson(id = "o4", ordinal = 4, scheduledDate = "2026-10-01", kind = "income"))
        assertEquals(emptyList(), env.matches())
        assertEquals(emptyList(), env.matches(category = null))
    }

    @Test
    fun aWaitingConfirmationOrSkipAlreadyCountsAsResolved() = runTest {
        val env = env(backgroundScope)
        env.seedOccurrence(occurrenceJson(id = "o1", scheduledDate = "2026-10-01"))
        env.seedOccurrence(occurrenceJson(id = "o2", ordinal = 2, scheduledDate = "2026-10-02"))
        env.service.confirm("o1", com.fares.saveme.feature.recurring.confirmDraft())
        env.service.skip("o2")
        assertEquals(emptyList(), env.matches())
    }

    @Test
    fun anEndedRuleAndAViewerGetNoOffer() = runTest {
        val ended = env(backgroundScope)
        ended.seedRule(ruleJson(id = "r1", status = "ended"))
        ended.seedOccurrence(occurrenceJson(id = "o1", scheduledDate = "2026-10-01"))
        assertEquals(emptyList(), ended.matches())
        val viewer = env(backgroundScope, role = "viewer")
        viewer.seedOccurrence(occurrenceJson(id = "o1", scheduledDate = "2026-10-01"))
        assertEquals(emptyList(), viewer.matches())
    }

    @Test
    fun aRuleWithoutReceivedOccurrencesHasNothingToMatch() = runTest {
        assertEquals(emptyList(), env(backgroundScope).matches())
    }

    @Test
    fun atMostThreeNearestFirst() = runTest {
        val env = env(backgroundScope)
        listOf("2026-10-06", "2026-10-04", "2026-10-02", "2026-10-03", "2026-09-30").forEachIndexed { i, d ->
            env.seedOccurrence(occurrenceJson(id = "o$i", ordinal = i + 1, scheduledDate = d))
        }
        assertEquals(listOf("2026-10-02", "2026-10-03", "2026-09-30"), env.matches().map { it.date.toString() })
    }

    @Test
    fun aTransferMatchesByDestination() = runTest {
        val env = env(backgroundScope)
        env.seedRule(ruleJson(id = "r1", kind = "transfer", categoryId = null, counterWalletId = "w3"))
        env.seedOccurrence(occurrenceJson(id = "o1", scheduledDate = "2026-10-01", kind = "transfer", categoryId = null).replace("\"counter_wallet_id\":null", "\"counter_wallet_id\":\"w3\""))
        assertEquals(listOf("o1"), env.matches(kind = "transfer", category = null, counter = "w3").map { it.id })
        assertEquals(emptyList(), env.matches(kind = "transfer", category = null, counter = "w4"))
    }

    private fun draft(amount: Long = 4_300_00, date: LocalDate = today, note: String? = null) =
        TransactionDraft("w1", "expense", date, amount, "EGP", "c1", null, note, null, null, false)

    @Test
    fun acceptingConfirmsTheOccurrenceWithTheDraftsAmountDateAndTextsKeepingTheRulesTitle() = runTest {
        val env = env(backgroundScope)
        env.seedOccurrence(occurrenceJson(id = "o1", scheduledDate = "2026-10-01", description = "Rent"))
        assertEquals(ConfirmFromDraftResult.Confirmed("o1"), env.links.confirmFromDraft("o1", draft()))
        val payload = AppJson.decodeFromString(OccurrencePayload.serializer(), env.engine.get("occurrence", "o1")!!.payloadJson)
        assertEquals("confirmed", payload.status)
        assertEquals(430_000, payload.amountMinor)
        assertEquals("2026-10-02", payload.date)
        assertEquals("Rent", payload.description)
        assertEquals("tc1", payload.transactionId)
        assertNull(env.engine.get("transaction", "tc1"), "the draft is not also recorded")
        assertEquals(430_000, env.transactionDao.get("tc1")!!.amountMinor)
        assertEquals("Paid cash", run {
            env.service.cancelConfirmation("o1")
            env.links.confirmFromDraft("o1", draft(note = "Paid cash"))
            AppJson.decodeFromString(OccurrencePayload.serializer(), env.engine.get("occurrence", "o1")!!.payloadJson).description
        })
    }

    @Test
    fun acceptingAnUnavailableOccurrenceOrAsAViewerAnswersWithoutQueuing() = runTest {
        val env = env(backgroundScope)
        assertEquals(ConfirmFromDraftResult.NotAvailable, env.links.confirmFromDraft("nope", draft()))
        val viewer = env(backgroundScope, role = "viewer")
        viewer.seedOccurrence(occurrenceJson(id = "o1", scheduledDate = "2026-10-01"))
        assertEquals(ConfirmFromDraftResult.NotAllowed, viewer.links.confirmFromDraft("o1", draft()))
        assertTrue(env.pendingDao.rows.value.isEmpty() && viewer.pendingDao.rows.value.isEmpty())
    }
}
