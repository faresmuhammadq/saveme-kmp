package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.data.dto.RulePayload
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.domain.model.RuleResult
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleDraft
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.transactions.txAccount
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-012, FR-013, FR-015, FR-016, FR-034: a saved rule keeps what cannot change and refuses what the server would. */
class RecurringServiceChangeTest {

    private suspend fun env(scope: kotlinx.coroutines.CoroutineScope, rule: String = ruleJson(), role: String = "owner"): RecurringServiceEnv {
        val env = RecurringServiceEnv(scope)
        env.seedWallet(walletJson(id = "w1", role = role))
        env.seedRule(rule)
        return env
    }

    private suspend fun payloadOf(env: RecurringServiceEnv, id: String = "r1") =
        AppJson.decodeFromString(RulePayload.serializer(), env.engine.get("recurring_rule", id)!!.payloadJson)

    private fun editorDraft() = ruleDraft(amount = 500_000, description = "Rent 2027", start = LocalDate(2026, 11, 1), anchorDay = 1)

    @Test
    fun savingSendsTheWholeRuleOnTheEditorsBaseVersionKeepingPresetKindAndDestination() = runTest {
        val env = env(backgroundScope, ruleJson(version = 4, kind = "expense"))
        val draft = editorDraft().copy(kind = com.fares.saveme.feature.transactions.domain.model.TransactionKind.Income, preset = com.fares.saveme.feature.recurring.domain.model.Preset.Gameya)
        assertEquals(RuleResult.Saved, env.service.save("r1", draft, baseVersion = 4))
        val change = env.engine.get("recurring_rule", "r1")!!
        assertEquals(4, change.baseVersion)
        val payload = payloadOf(env)
        assertEquals("custom", payload.preset)
        assertEquals("expense", payload.kind)
        assertEquals(500_000, payload.amountMinor)
        assertEquals("active", payload.status)
        assertEquals("EGP", payload.currency)
    }

    @Test
    fun anEditAfterAnEditKeepsTheOldestBaseVersion() = runTest {
        val env = env(backgroundScope, ruleJson(version = 4))
        env.service.save("r1", editorDraft(), 4)
        env.service.save("r1", editorDraft().copy(amountMinor = 600_000), 4)
        assertEquals(4, env.engine.get("recurring_rule", "r1")!!.baseVersion)
        assertEquals(600_000, payloadOf(env).amountMinor)
    }

    @Test
    fun frozenScheduleMembersAreRefusedOnceARoundIsResolved() = runTest {
        val env = env(backgroundScope)
        env.seedOccurrence(occurrenceJson(id = "o1", status = "confirmed", transactionId = "t1"))
        val errors = assertIs<RuleResult.Invalid>(
            env.service.save("r1", editorDraft().copy(interval = 2, startDate = LocalDate(2026, 12, 1), anchorDay = 5), 1),
        ).errors
        assertEquals(RuleFieldProblem.Frozen, errors.getValue(RuleField.Interval))
        assertEquals(RuleFieldProblem.Frozen, errors.getValue(RuleField.Start))
        assertEquals(RuleFieldProblem.Frozen, errors.getValue(RuleField.Day))
        assertFalse(RuleField.Amount in errors, "a per-round amount of a custom rule stays editable")
        assertNull(env.engine.get("recurring_rule", "r1"))
        // The template still saves.
        assertEquals(RuleResult.Saved, env.service.save("r1", editorDraft(), 1))
        val view = env.service.observeRule("r1").first()!!
        assertTrue(view.frozen.schedule)
        assertFalse(view.frozen.amountAndCount)
    }

    @Test
    fun aGameyaOrATotalAlsoFreezesTheAmountAndTheCount() = runTest {
        val env = env(backgroundScope, ruleJson(preset = "gameya", count = 10, payoutRound = 4, payoutCategoryId = "ci"))
        env.seedOccurrence(occurrenceJson(id = "o1", status = "skipped"))
        val draft = editorDraft().copy(occurrenceCount = 9, payoutRound = 4, payoutCategoryId = "ci")
        val errors = assertIs<RuleResult.Invalid>(env.service.save("r1", draft, 1)).errors
        assertEquals(RuleFieldProblem.Frozen, errors.getValue(RuleField.Amount))
        assertEquals(RuleFieldProblem.Frozen, errors.getValue(RuleField.Count))
    }

    @Test
    fun anEndThatCutsOffAResolvedRoundIsRefused() = runTest {
        val env = env(backgroundScope)
        env.seedOccurrence(occurrenceJson(id = "o1", ordinal = 2, scheduledDate = "2026-12-01", status = "confirmed", transactionId = "t1"))
        val before = assertIs<RuleResult.Invalid>(env.service.save("r1", editorDraft().copy(endDate = LocalDate(2026, 11, 30)), 1)).errors
        assertEquals(RuleFieldProblem.CutsResolvedRound, before.getValue(RuleField.End))
        val count = assertIs<RuleResult.Invalid>(env.service.save("r1", editorDraft().copy(occurrenceCount = 1), 1)).errors
        assertEquals(RuleFieldProblem.CutsResolvedRound, count.getValue(RuleField.Count))
        assertEquals(RuleResult.Saved, env.service.save("r1", editorDraft().copy(endDate = LocalDate(2026, 12, 1)), 1))
    }

    @Test
    fun anEndedRuleAndARuleWithValuesThisAppDoesNotKnowAreReadOnly() = runTest {
        for (rule in listOf(ruleJson(status = "ended"), ruleJson(frequency = "fortnightly"), ruleJson(preset = "tontine"), ruleJson(status = "archived"))) {
            val env = env(backgroundScope, rule)
            assertEquals(RuleResult.ReadOnly, env.service.save("r1", editorDraft(), 1))
            assertEquals(RuleResult.ReadOnly, env.service.setStatus("r1", RuleStatus.Active, 1))
            assertNull(env.engine.get("recurring_rule", "r1"))
            val view = env.service.observeRule("r1").first()!!
            assertTrue(view.readOnly)
        }
    }

    @Test
    fun aViewerAndAnUnverifiedAddressChangeNothing() = runTest {
        val viewer = env(backgroundScope, role = "viewer")
        assertEquals(RuleResult.NotAllowed, viewer.service.save("r1", editorDraft(), 1))
        assertEquals(RuleResult.NotAllowed, viewer.service.delete("r1"))
        assertEquals(RuleResult.NotAllowed, viewer.service.setStatus("r1", RuleStatus.Paused, 1))
        val unverified = env(backgroundScope)
        unverified.authState.value = AuthState.SignedIn(txAccount.copy(emailVerified = false))
        assertEquals(RuleResult.NotVerified, unverified.service.save("r1", editorDraft(), 1))
        assertEquals(RuleResult.NotVerified, unverified.service.delete("r1"))
        assertEquals(emptyMap(), viewer.pendingDao.rows.value + unverified.pendingDao.rows.value)
    }

    @Test
    fun pausingResumingAndEndingQueueTheWholeRuleWithTheNewStatus() = runTest {
        val env = env(backgroundScope, ruleJson(version = 2))
        assertEquals(RuleResult.Saved, env.service.setStatus("r1", RuleStatus.Paused, 2))
        assertEquals("paused", payloadOf(env).status)
        assertEquals(RuleResult.Saved, env.service.setStatus("r1", RuleStatus.Active, 2))
        assertEquals("active", payloadOf(env).status)
        assertEquals(RuleResult.Saved, env.service.setStatus("r1", RuleStatus.Ended, 2))
        assertEquals("ended", payloadOf(env).status)
        assertEquals(2, env.engine.get("recurring_rule", "r1")!!.baseVersion)
        assertEquals(RuleResult.ReadOnly, env.service.setStatus("r1", RuleStatus.Unknown, 2))
    }

    @Test
    fun savingAPausedRuleKeepsItPaused() = runTest {
        val env = env(backgroundScope, ruleJson(status = "paused"))
        env.service.save("r1", editorDraft(), 1)
        assertEquals("paused", payloadOf(env).status)
    }

    @Test
    fun deletingASentRuleHidesItAtOnceAndQueuesTheDeletionWithItsVersion() = runTest {
        val env = env(backgroundScope, ruleJson(version = 3))
        env.seedOccurrence(occurrenceJson(id = "o1"))
        assertEquals(RuleResult.Saved, env.service.delete("r1"))
        val change = assertNotNull(env.engine.get("recurring_rule", "r1"))
        assertTrue(change.isDelete)
        assertEquals(3, change.baseVersion)
        assertEquals(null, env.service.observeRule("r1").first(), "hidden at once")
        assertTrue(env.service.observeOccurrences(null).first().isEmpty, "its unconfirmed occurrences too")
    }

    @Test
    fun deletingARuleThatNeverReachedTheServerRemovesItLocallyAndSendsNothing() = runTest {
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf("r-new"))
        env.seedWallet(walletJson(id = "w1"))
        env.service.create("w1", ruleDraft())
        assertEquals(RuleResult.Saved, env.service.delete("r-new"))
        assertNull(env.ruleDao.get("r-new"))
        assertNull(env.engine.get("recurring_rule", "r-new"))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun editingARuleThatNeverReachedTheServerRewritesTheSameCreate() = runTest {
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf("r-new"))
        env.seedWallet(walletJson(id = "w1"))
        env.service.create("w1", ruleDraft())
        assertEquals(RuleResult.Saved, env.service.save("r-new", ruleDraft(amount = 700_000), baseVersion = 0))
        assertEquals(0, env.engine.get("recurring_rule", "r-new")!!.baseVersion)
        assertEquals(700_000, payloadOf(env, "r-new").amountMinor)
        assertEquals(700_000, env.ruleDao.get("r-new")!!.amountMinor)
        assertEquals(0, env.ruleDao.get("r-new")!!.version)
    }

    @Test
    fun anotherMembersRuleCanBeEditedByAnEditor() = runTest {
        val env = env(backgroundScope, ruleJson(), role = "editor")
        assertEquals(RuleResult.Saved, env.service.save("r1", editorDraft(), 1))
    }
}
