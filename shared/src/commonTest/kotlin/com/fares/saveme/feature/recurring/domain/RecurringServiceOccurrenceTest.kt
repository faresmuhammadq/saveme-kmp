package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.confirmDraft
import com.fares.saveme.feature.recurring.data.dto.OccurrencePayload
import com.fares.saveme.feature.recurring.domain.model.ConfirmField
import com.fares.saveme.feature.recurring.domain.model.OccurrenceResult
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.transactions.txAccount
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-021 to FR-023, FR-026: one confirmation is one queued write plus one phone-only transaction with a stable id. */
class RecurringServiceOccurrenceTest {

    private suspend fun env(scope: kotlinx.coroutines.CoroutineScope, occurrence: String = occurrenceJson(version = 3), role: String = "owner"): RecurringServiceEnv {
        val env = RecurringServiceEnv(scope, newIds = listOf("tc1", "tc2"))
        env.seedWallet(walletJson(id = "w1", role = role))
        env.seedRule(ruleJson(id = "r1"))
        env.seedOccurrence(occurrence)
        return env
    }

    private suspend fun payload(env: RecurringServiceEnv, id: String = "o1") =
        AppJson.decodeFromString(OccurrencePayload.serializer(), env.engine.get("occurrence", id)!!.payloadJson)

    @Test
    fun confirmingQueuesOneWriteOnTheBaseVersionAndShowsTheTransactionAtOnce() = runTest {
        val env = env(backgroundScope)
        assertEquals(OccurrenceResult.Done, env.service.confirm("o1", confirmDraft(amount = 480_000, date = LocalDate(2026, 11, 2))))

        val change = env.engine.get("occurrence", "o1")!!
        assertEquals(3, change.baseVersion)
        assertEquals(1, env.pendingDao.rows.value.size)
        val p = payload(env)
        assertEquals("confirmed", p.status)
        assertEquals("tc1", p.transactionId)
        assertEquals(480_000, p.amountMinor)
        assertEquals("2026-11-02", p.date)

        val tx = assertNotNull(env.transactionDao.get("tc1"))
        assertTrue(tx.pendingConfirmation)
        assertEquals(0, tx.version)
        assertEquals(480_000, tx.amountMinor)
        assertEquals("o1", tx.occurrenceId)
        assertEquals("r1", tx.recurringRuleId)
        assertNull(env.engine.get("transaction", "tc1"), "the transaction itself is never sent")
    }

    @Test
    fun confirmingAgainWhileWaitingKeepsTheTransactionIdAndFollowsTheNewValues() = runTest {
        val env = env(backgroundScope)
        env.service.confirm("o1", confirmDraft(amount = 480_000))
        env.service.confirm("o1", confirmDraft(amount = 495_000, description = "Rent Nov"))
        assertEquals("tc1", payload(env).transactionId)
        assertEquals(3, env.engine.get("occurrence", "o1")!!.baseVersion)
        val tx = env.transactionDao.get("tc1")!!
        assertEquals(495_000, tx.amountMinor)
        assertEquals("Rent Nov", tx.description)
        assertNull(env.transactionDao.get("tc2"))
    }

    @Test
    fun aTransferShowsBothWalletsOfThePendingTransaction() = runTest {
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf("tc1"))
        env.seedWallet(walletJson(id = "w1"))
        env.seedWallet(walletJson(id = "w3"))
        env.seedRule(ruleJson(id = "r1", kind = "transfer", categoryId = null, counterWalletId = "w3"))
        env.seedOccurrence(occurrenceJson(kind = "transfer", categoryId = null).replace("\"counter_wallet_id\":null", "\"counter_wallet_id\":\"w3\""))
        assertEquals(OccurrenceResult.Done, env.service.confirm("o1", confirmDraft(category = "c1")))
        val tx = env.transactionDao.get("tc1")!!
        assertEquals("w3", tx.counterWalletId)
        assertNull(tx.categoryId, "a transfer has no category")
    }

    @Test
    fun cancellingAConfirmationDiscardsTheWriteAndTheTransaction() = runTest {
        val env = env(backgroundScope)
        env.service.confirm("o1", confirmDraft())
        env.service.cancelConfirmation("o1")
        assertNull(env.engine.get("occurrence", "o1"))
        assertNull(env.transactionDao.get("tc1"))
        // Nothing waits any more: a second cancel and a cancel of a skip are harmless.
        env.service.cancelConfirmation("o1")
        env.service.skip("o1")
        env.service.cancelConfirmation("o1")
        assertEquals("skipped", payload(env).status)
    }

    @Test
    fun skipAndUndoSkipSendNoTransactionId() = runTest {
        val env = env(backgroundScope)
        assertEquals(OccurrenceResult.Done, env.service.skip("o1"))
        assertEquals("skipped", payload(env).status)
        assertNull(payload(env).transactionId)
        assertEquals(3, env.engine.get("occurrence", "o1")!!.baseVersion)
        assertEquals(OccurrenceState.Skipped, env.service.observeOccurrence("o1").first()!!.state)

        // Undoing a skip that was never sent leaves nothing to send.
        assertEquals(OccurrenceResult.Done, env.service.unskip("o1"))
        assertNull(env.engine.get("occurrence", "o1"))
    }

    @Test
    fun undoingASkipTheServerHasQueuesAScheduledWrite() = runTest {
        val env = env(backgroundScope, occurrenceJson(version = 4, status = "skipped"))
        assertEquals(OccurrenceResult.Done, env.service.unskip("o1"))
        assertEquals("scheduled", payload(env).status)
        assertNull(payload(env).transactionId)
        assertEquals(4, env.engine.get("occurrence", "o1")!!.baseVersion)
    }

    @Test
    fun aSkippedOccurrenceCanBeConfirmedDirectly() = runTest {
        val env = env(backgroundScope, occurrenceJson(version = 4, status = "skipped"))
        assertEquals(OccurrenceResult.Done, env.service.confirm("o1", confirmDraft()))
        assertEquals("confirmed", payload(env).status)
    }

    @Test
    fun editingOneOccurrenceQueuesItsNewDateAmountAndTextsKeepingItsStatus() = runTest {
        val env = env(backgroundScope)
        val draft = confirmDraft(date = LocalDate(2026, 11, 5), amount = 470_000, description = "Rent, late")
        assertEquals(OccurrenceResult.Done, env.service.edit("o1", draft))
        val p = payload(env)
        assertEquals("scheduled", p.status)
        assertEquals("2026-11-05", p.date)
        assertEquals(470_000, p.amountMinor)
        assertNull(p.transactionId)
        val view = env.service.observeOccurrence("o1").first()!!
        assertEquals(LocalDate(2026, 11, 5), view.shownDate)
        assertEquals(LocalDate(2026, 11, 1), view.occurrence.scheduledDate, "the scheduled date never changes")
        // Putting it back the way the server has it leaves nothing to send.
        env.service.edit("o1", confirmDraft())
        assertNull(env.engine.get("occurrence", "o1"))
    }

    @Test
    fun editingWhileAConfirmationWaitsChangesTheConfirmation() = runTest {
        val env = env(backgroundScope)
        env.service.confirm("o1", confirmDraft())
        env.service.edit("o1", confirmDraft(amount = 1_000))
        assertEquals("confirmed", payload(env).status)
        assertEquals(1_000, env.transactionDao.get("tc1")!!.amountMinor)
    }

    @Test
    fun aWaitingConfirmationIsNotTurnedIntoASkip() = runTest {
        val env = env(backgroundScope)
        env.service.confirm("o1", confirmDraft())
        assertEquals(OccurrenceResult.ReadOnly, env.service.skip("o1"))
        assertEquals("confirmed", payload(env).status)
    }

    @Test
    fun aConfirmedOccurrenceAnUnknownStatusAndAnUnknownRuleAreReadOnly() = runTest {
        val confirmed = env(backgroundScope, occurrenceJson(status = "confirmed", transactionId = "t9"))
        val unknown = env(backgroundScope, occurrenceJson(status = "parked"))
        for (e in listOf(confirmed, unknown)) {
            assertEquals(OccurrenceResult.ReadOnly, e.service.confirm("o1", confirmDraft()))
            assertEquals(OccurrenceResult.ReadOnly, e.service.skip("o1"))
            assertEquals(OccurrenceResult.ReadOnly, e.service.edit("o1", confirmDraft()))
            assertEquals(emptyMap(), e.pendingDao.rows.value)
            assertNull(e.transactionDao.get("tc1"))
        }
        val oddRule = env(backgroundScope)
        oddRule.seedRule(ruleJson(id = "r1", frequency = "fortnightly"))
        assertEquals(OccurrenceResult.ReadOnly, oddRule.service.confirm("o1", confirmDraft()))
    }

    @Test
    fun theConfirmFormIsValidated() = runTest {
        val env = env(backgroundScope)
        val errors = assertIs<OccurrenceResult.Invalid>(env.service.confirm("o1", confirmDraft(amount = 0, category = null, date = LocalDate(1800, 1, 1), description = "x".repeat(101)))).errors
        assertIs<RuleFieldProblem.OutOfRange>(errors.getValue(ConfirmField.Amount))
        assertIs<RuleFieldProblem.OutOfRange>(errors.getValue(ConfirmField.Date))
        assertEquals(RuleFieldProblem.Required, errors.getValue(ConfirmField.Category))
        assertEquals(RuleFieldProblem.TooLong(100), errors.getValue(ConfirmField.Note))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
        assertNull(env.transactionDao.get("tc1"))
    }

    @Test
    fun anUnverifiedAddressAViewerAndAMissingOccurrenceQueueNothing() = runTest {
        val viewer = env(backgroundScope, role = "viewer")
        assertEquals(OccurrenceResult.NotAllowed, viewer.service.confirm("o1", confirmDraft()))
        assertEquals(OccurrenceResult.NotAllowed, viewer.service.skip("o1"))
        val unverified = env(backgroundScope)
        unverified.authState.value = AuthState.SignedIn(txAccount.copy(emailVerified = false))
        assertEquals(OccurrenceResult.NotVerified, unverified.service.confirm("o1", confirmDraft()))
        assertEquals(OccurrenceResult.NoLongerAvailable, env(backgroundScope).service.confirm("nope", confirmDraft()))
        assertEquals(emptyMap(), viewer.pendingDao.rows.value + unverified.pendingDao.rows.value)
        assertNull(viewer.transactionDao.get("tc1"))
    }

    @Test
    fun aTransferIsNotOfferedWhenTheOtherWalletIsReadOnlyForThePerson() = runTest {
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf("tc1"))
        env.seedWallet(walletJson(id = "w1"))
        env.seedWallet(walletJson(id = "w3", role = "viewer"))
        env.seedRule(ruleJson(id = "r1", kind = "transfer", categoryId = null, counterWalletId = "w3"))
        env.seedOccurrence(occurrenceJson(kind = "transfer", categoryId = null).replace("\"counter_wallet_id\":null", "\"counter_wallet_id\":\"w3\""))
        assertEquals(OccurrenceResult.NotAllowed, env.service.confirm("o1", confirmDraft()))
    }

    @Test
    fun theNoticeIsClearedWhenDismissed() = runTest {
        val env = env(backgroundScope)
        env.occurrences.storeCopy(com.fares.saveme.feature.recurring.occurrenceDto(occurrenceJson(version = 4, status = "confirmed", transactionId = "t9")), respectQueued = false, noticeAlreadyConfirmed = true)
        assertEquals("already_confirmed", env.service.observeOccurrence("o1").first()!!.notice)
        env.service.dismissNotice("o1")
        assertNull(env.service.observeOccurrence("o1").first()!!.notice)
    }
}
