package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.data.dto.RulePayload
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.RecurringRecord
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.domain.model.RuleResult
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleDraft
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-014, research R-09: "Change from a date" ends the old rule the day before and starts a new one that waits for it. */
class RecurringSplitTest {

    private suspend fun env(scope: kotlinx.coroutines.CoroutineScope, rule: String = ruleJson(version = 2)): RecurringServiceEnv {
        val env = RecurringServiceEnv(scope, newIds = listOf("r2"))
        env.seedWallet(walletJson(id = "w1"))
        env.seedRule(rule)
        env.seedOccurrence(occurrenceJson(id = "o1", ordinal = 1, scheduledDate = "2026-11-01", status = "confirmed", transactionId = "t1"))
        return env
    }

    private suspend fun payload(env: RecurringServiceEnv, id: String) =
        AppJson.decodeFromString(RulePayload.serializer(), env.engine.get("recurring_rule", id)!!.payloadJson)

    private val raise = ruleDraft(amount = 500_000, anchorDay = 1)

    @Test
    fun theOldRuleEndsTheDayBeforeAndTheNewOneContinuesIt() = runTest {
        val env = env(backgroundScope)
        assertEquals(RuleResult.Created("r2"), env.service.changeFrom("r1", LocalDate(2027, 1, 1), raise, baseVersion = 2))

        val old = env.engine.get("recurring_rule", "r1")!!
        assertEquals(2, old.baseVersion)
        assertEquals("2026-12-31", payload(env, "r1").endDate)
        assertNull(payload(env, "r1").occurrenceCount)
        assertEquals(450_000, payload(env, "r1").amountMinor, "the person's change goes to the new rule only")

        val new = env.engine.get("recurring_rule", "r2")!!
        assertEquals(0, new.baseVersion)
        val newPayload = payload(env, "r2")
        assertEquals("r1", newPayload.followsRuleId)
        assertEquals("2027-01-01", newPayload.startDate)
        assertEquals(500_000, newPayload.amountMinor)
        assertEquals("custom", newPayload.preset)
        assertTrue(old.createdOrder < new.createdOrder, "the end is sent before the new rule")
        assertEquals("r1", env.ruleDao.get("r2")!!.followsRuleId)
    }

    @Test
    fun theNewRuleSaysItWaitsForTheOldOne() = runTest {
        val env = env(backgroundScope)
        env.service.changeFrom("r1", LocalDate(2027, 1, 1), raise, 2)
        assertEquals(RecurringSyncState.Held("r1"), env.service.observeRule("r2").first()!!.syncState)
    }

    @Test
    fun theDateMustBeAfterTheLastResolvedRoundAndNotBeforeTheStart() = runTest {
        val env = env(backgroundScope)
        for (bad in listOf(LocalDate(2026, 11, 1), LocalDate(2026, 10, 15), LocalDate(2026, 10, 31), LocalDate(3000, 1, 1))) {
            val errors = assertIs<RuleResult.Invalid>(env.service.changeFrom("r1", bad, raise, 2)).errors
            assertEquals(RuleFieldProblem.FromOutOfRange, errors.getValue(RuleField.From), "date $bad")
        }
        assertEquals(emptyMap(), env.pendingDao.rows.value)
        assertEquals(RuleResult.Created("r2"), env.service.changeFrom("r1", LocalDate(2026, 11, 2), raise, 2))
    }

    @Test
    fun theNewRulesOwnProblemsAreReportedAndNothingIsQueued() = runTest {
        val env = env(backgroundScope)
        val errors = assertIs<RuleResult.Invalid>(env.service.changeFrom("r1", LocalDate(2027, 1, 1), raise.copy(amountMinor = 0), 2)).errors
        assertTrue(RuleField.Amount in errors)
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun aPresetOrATotalRuleCannotBeSplitByAnEndDate() = runTest {
        val gameya = env(backgroundScope, ruleJson(preset = "gameya", count = 10, payoutRound = 4, payoutCategoryId = "ci"))
        assertEquals(RuleResult.ReadOnly, gameya.service.changeFrom("r1", LocalDate(2027, 1, 1), raise, 1))
        val total = env(backgroundScope, ruleJson(basis = "total", count = 12))
        assertEquals(RuleResult.ReadOnly, total.service.changeFrom("r1", LocalDate(2027, 1, 1), raise.copy(amountBasis = AmountBasis.Total, occurrenceCount = 12), 1))
    }

    @Test
    fun aSplitOfARuleNeverSentIsAPlainEdit() = runTest {
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf("r-new"))
        env.seedWallet(walletJson(id = "w1"))
        env.service.create("w1", ruleDraft())
        assertEquals(RuleResult.Saved, env.service.changeFrom("r-new", LocalDate(2027, 1, 1), ruleDraft(amount = 1), 0))
        assertEquals(1, env.pendingDao.rows.value.size)
        assertEquals(1, env.ruleDao.get("r-new")!!.amountMinor)
    }

    @Test
    fun discardingTheOldChangeDiscardsTheNewRuleWithIt() = runTest {
        val env = env(backgroundScope)
        env.service.changeFrom("r1", LocalDate(2027, 1, 1), raise, 2)
        env.service.discard(RecurringRecord.Rule, "r1")
        runCurrent()
        assertNull(env.engine.get("recurring_rule", "r1"))
        assertNull(env.engine.get("recurring_rule", "r2"))
        assertNull(env.ruleDao.get("r2"), "the new rule's placeholder goes")
        assertNotNull(env.ruleDao.get("r1"))
        assertEquals(listOf("r2"), env.discardedRules)
    }

    @Test
    fun usingTheOtherDevicesCopyOrAcceptingADeletionCancelsTheNewRuleToo() = runTest {
        val env = env(backgroundScope)
        env.service.changeFrom("r1", LocalDate(2027, 1, 1), raise, 2)
        val row = env.pendingDao.rows.value.values.first { it.recordId == "r1" }
        env.pendingDao.upsert(row.copy(state = ChangeState.Clash.name, clashJson = ruleJson(version = 5, deletedAt = "2026-10-03T00:00:00Z")))
        env.service.useTheirs(RecurringRecord.Rule, "r1")
        runCurrent()
        assertNull(env.engine.get("recurring_rule", "r2"))
        assertNull(env.ruleDao.get("r2"))
        assertNull(env.ruleDao.get("r1"), "the deletion was accepted")
    }
}
