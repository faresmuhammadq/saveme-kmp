package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.data.dto.RulePayload
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.Frequency
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.domain.model.RuleResult
import com.fares.saveme.feature.recurring.domain.schedule.RuleDates
import com.fares.saveme.feature.recurring.ruleDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.transactions.txAccount
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-001 to FR-009: a rule is saved on the phone first, refused up front when the server would refuse it. */
class RecurringServiceCreateTest {

    private fun errors(result: RuleResult) = assertIs<RuleResult.Invalid>(result).errors

    @Test
    fun anUnverifiedAddressQueuesNothing() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.authState.value = AuthState.SignedIn(txAccount.copy(emailVerified = false))
        assertEquals(RuleResult.NotVerified, env.service.create("w1", ruleDraft()))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
        assertEquals(emptyMap(), env.ruleDao.rows.value)
    }

    @Test
    fun aViewerAnArchivedWalletAndAnUnknownWalletAreRefused() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1", role = "viewer"))
        env.seedWallet(walletJson(id = "w2", archivedAt = "2026-09-30T00:00:00Z"))
        assertEquals(RuleResult.NotAllowed, env.service.create("w1", ruleDraft()))
        assertEquals(RuleResult.NotAllowed, env.service.create("w2", ruleDraft()))
        assertEquals(RuleResult.NotAllowed, env.service.create("nope", ruleDraft()))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun aValidRuleIsAPlaceholderPlusOneQueuedWriteInTheWalletsCurrency() = runTest {
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf("r-new"))
        env.seedWallet(walletJson(id = "w1", currency = "KWD"))
        val result = env.service.create("w1", ruleDraft(description = "  Rent  ", start = LocalDate(2026, 11, 29)))
        assertEquals(RuleResult.Created("r-new"), result)

        val row = assertNotNull(env.ruleDao.get("r-new"))
        assertEquals(0, row.version)
        assertEquals("KWD", row.currency)
        assertEquals("active", row.status)
        val change = assertNotNull(env.engine.get("recurring_rule", "r-new"))
        assertEquals(0, change.baseVersion)
        assertEquals("w1", change.scope.id)
        val payload = AppJson.decodeFromString(RulePayload.serializer(), change.payloadJson)
        assertEquals("KWD", payload.currency)
        assertEquals("Rent", payload.description)
        assertEquals(false, payload.autoConfirm)
        assertEquals(29, payload.anchorDay)
        assertNull(payload.followsRuleId)
    }

    @Test
    fun aDailyOrWeeklyRuleHasNoAnchorDay() = runTest {
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf("r1"))
        env.seedWallet(walletJson(id = "w1"))
        env.service.create("w1", ruleDraft(frequency = Frequency.Weekly, anchorDay = 5))
        assertNull(env.ruleDao.get("r1")!!.anchorDay)
    }

    @Test
    fun theServersRulesAreCheckedBeforeAnythingIsQueued() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        fun field(draft: com.fares.saveme.feature.recurring.domain.model.RuleDraft, f: RuleField) = errors(runBlockingCreate(env, draft)).getValue(f)

        assertEquals(RuleFieldProblem.OutOfRange(1, 9_007_199_254_740_991L), field(ruleDraft(amount = 0), RuleField.Amount))
        assertIs<RuleFieldProblem.OutOfRange>(field(ruleDraft(amount = 9_007_199_254_740_992L), RuleField.Amount))
        assertIs<RuleFieldProblem.OutOfRange>(field(ruleDraft(interval = 0), RuleField.Interval))
        assertIs<RuleFieldProblem.OutOfRange>(field(ruleDraft(interval = 366), RuleField.Interval))
        assertIs<RuleFieldProblem.OutOfRange>(field(ruleDraft(anchorDay = 32), RuleField.Day))
        assertIs<RuleFieldProblem.OutOfRange>(field(ruleDraft(count = 0), RuleField.Count))
        assertIs<RuleFieldProblem.OutOfRange>(field(ruleDraft(count = 1001), RuleField.Count))
        assertEquals(RuleFieldProblem.Invalid, field(ruleDraft(end = LocalDate(2026, 10, 31)), RuleField.End))
        assertEquals(RuleFieldProblem.Invalid, field(ruleDraft(end = LocalDate(2027, 1, 1), count = 3), RuleField.End))
        assertIs<RuleFieldProblem.OutOfRange>(field(ruleDraft(start = LocalDate(1899, 12, 31)), RuleField.Start))
        assertIs<RuleFieldProblem.OutOfRange>(field(ruleDraft(end = LocalDate(3000, 1, 1)), RuleField.End))
        assertEquals(RuleFieldProblem.Required, field(ruleDraft(category = null), RuleField.Category))
        assertEquals(RuleFieldProblem.Required, field(ruleDraft(kind = TransactionKind.Transfer, category = null, counter = null), RuleField.Destination))
        assertEquals(RuleFieldProblem.Invalid, field(ruleDraft(kind = TransactionKind.Transfer, category = null, counter = "w1"), RuleField.Destination))
        assertEquals(RuleFieldProblem.TooLong(100), field(ruleDraft(description = "x".repeat(101)), RuleField.Note))
        assertEquals(RuleFieldProblem.Invalid, field(ruleDraft(basis = AmountBasis.Total), RuleField.Basis))
        assertEquals(RuleFieldProblem.TotalTooSmall, field(ruleDraft(basis = AmountBasis.Total, amount = 5, count = 12), RuleField.Amount))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
        assertEquals(emptyMap(), env.ruleDao.rows.value)
    }

    private fun runBlockingCreate(env: RecurringServiceEnv, draft: com.fares.saveme.feature.recurring.domain.model.RuleDraft): RuleResult =
        kotlinx.coroutines.runBlocking { env.service.create("w1", draft) }

    @Test
    fun aTransferNeedsASameCurrencyWritableDestination() = runTest {
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf("r1"))
        env.seedWallet(walletJson(id = "w1"))
        env.seedWallet(walletJson(id = "w2", currency = "USD"))
        env.seedWallet(walletJson(id = "w3", currency = "EGP"))
        assertEquals(RuleFieldProblem.Invalid, errors(env.service.create("w1", ruleDraft(kind = TransactionKind.Transfer, category = null, counter = "w2"))).getValue(RuleField.Destination))
        assertEquals(RuleResult.Created("r1"), env.service.create("w1", ruleDraft(kind = TransactionKind.Transfer, category = "c1", counter = "w3")))
        val row = env.ruleDao.get("r1")!!
        assertNull(row.categoryId, "a transfer has no category")
        assertEquals("w3", row.counterWalletId)
    }

    @Test
    fun thePreviewIsTheSchedulesOwnDates() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        val draft = ruleDraft(start = LocalDate(2026, 1, 31), anchorDay = 31)
        val shown = env.service.preview(draft).rounds.map { it.date }
        val expected = RuleDates.dates(Frequency.Monthly, 1, 31, LocalDate(2026, 1, 31), null, null, 5).map { it.date }
        assertEquals(expected, shown)
        assertEquals(LocalDate(2026, 2, 28), shown[1])
        assertEquals(LocalDate(2026, 4, 30), shown[3])
        assertTrue(env.service.preview(draft.copy(preset = Preset.Custom, frequency = Frequency.Unknown)).rounds.isEmpty())
    }
}
