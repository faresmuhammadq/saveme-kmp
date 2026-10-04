package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.data.dto.RulePayload
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.Frequency
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.RuleField
import com.fares.saveme.feature.recurring.domain.model.RuleFieldProblem
import com.fares.saveme.feature.recurring.domain.model.RuleResult
import com.fares.saveme.feature.recurring.ruleDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** FR-010: the gam'eya and installment shapes are fixed by the preset; the server's other rules still apply. */
class RecurringPresetsTest {

    private fun gameya(count: Int? = 10, round: Int? = 4, payout: String? = "ci", amount: Long = 100_000) =
        ruleDraft(preset = Preset.Gameya, amount = amount, count = count, payoutRound = round, payoutCategory = payout, frequency = Frequency.Weekly, interval = 3, end = LocalDate(2030, 1, 1))

    private suspend fun create(env: RecurringServiceEnv, draft: com.fares.saveme.feature.recurring.domain.model.RuleDraft) = env.service.create("w1", draft)

    @Test
    fun aGameyaIsMonthlyEveryOneExpenseWithNoEndDate() = runTest {
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf("r1"))
        env.seedWallet(walletJson(id = "w1"))
        assertEquals(RuleResult.Created("r1"), create(env, gameya().copy(kind = TransactionKind.Income)))
        val payload = AppJson.decodeFromString(RulePayload.serializer(), env.engine.get("recurring_rule", "r1")!!.payloadJson)
        assertEquals("gameya", payload.preset)
        assertEquals("monthly", payload.frequency)
        assertEquals(1, payload.interval)
        assertEquals("expense", payload.kind)
        assertNull(payload.endDate)
        assertEquals(10, payload.occurrenceCount)
        assertEquals(4, payload.payoutRound)
        assertEquals("ci", payload.payoutCategoryId)
    }

    @Test
    fun aGameyaNeedsACountOfTwoToOneTwentyAPayoutRoundWithinItAndAPayoutCategory() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        fun errors(d: com.fares.saveme.feature.recurring.domain.model.RuleDraft) = kotlinx.coroutines.runBlocking { assertIs<RuleResult.Invalid>(create(env, d)).errors }
        assertEquals(RuleFieldProblem.Required, errors(gameya(count = null)).getValue(RuleField.Count))
        assertIs<RuleFieldProblem.OutOfRange>(errors(gameya(count = 1)).getValue(RuleField.Count))
        assertIs<RuleFieldProblem.OutOfRange>(errors(gameya(count = 121)).getValue(RuleField.Count))
        assertEquals(RuleFieldProblem.Required, errors(gameya(round = null)).getValue(RuleField.PayoutRound))
        assertEquals(RuleFieldProblem.OutOfRange(1, 10), errors(gameya(round = 11)).getValue(RuleField.PayoutRound))
        assertEquals(RuleFieldProblem.Required, errors(gameya(payout = null)).getValue(RuleField.PayoutCategory))
        assertEquals(RuleFieldProblem.PayoutTooLarge, errors(gameya(amount = 9_007_199_254_740_991L)).getValue(RuleField.Amount))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun anInstallmentIsMonthlyExpenseOrTransferWithACountAndATotalAtLeastOneUnitPerRound() = runTest {
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf("r1", "r2"))
        env.seedWallet(walletJson(id = "w1"))
        env.seedWallet(walletJson(id = "w3"))
        val ok = ruleDraft(preset = Preset.Installment, basis = AmountBasis.Total, amount = 1_000_000, count = 12, frequency = Frequency.Daily, payoutRound = 3, payoutCategory = "ci")
        assertEquals(RuleResult.Created("r1"), create(env, ok))
        val payload = AppJson.decodeFromString(RulePayload.serializer(), env.engine.get("recurring_rule", "r1")!!.payloadJson)
        assertEquals("monthly", payload.frequency)
        assertNull(payload.payoutRound, "an installment sends no payout members")
        assertNull(payload.payoutCategoryId)
        assertEquals(RuleResult.Created("r2"), create(env, ok.copy(kind = TransactionKind.Transfer, categoryId = null, counterWalletId = "w3")))
        val bad = kotlinx.coroutines.runBlocking { assertIs<RuleResult.Invalid>(create(env, ok.copy(amountMinor = 11)).also { }).errors }
        assertEquals(RuleFieldProblem.TotalTooSmall, bad.getValue(RuleField.Amount))
        assertEquals(RuleField.Kind, kotlinx.coroutines.runBlocking { assertIs<RuleResult.Invalid>(create(env, ok.copy(kind = TransactionKind.Income)).also { }).errors }.keys.single())
    }

    @Test
    fun thePreviewOfAGameyaListsEveryRoundAndThePayoutSum() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        val preview = env.service.preview(gameya(count = 10, round = 4, amount = 100_000))
        assertEquals(10 + 1, preview.rounds.size)
        assertEquals(1_000_000L, preview.payoutMinor)
        assertEquals(1, preview.rounds.count { it.isPayout })
    }

    @Test
    fun thePreviewOfATotalInstallmentGivesTheRemainderToTheEarliestRounds() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        val preview = env.service.preview(ruleDraft(preset = Preset.Installment, basis = AmountBasis.Total, amount = 1_000_000, count = 12))
        assertEquals(List(4) { 83_334L } + List(8) { 83_333L }, preview.rounds.map { it.amountMinor })
        assertEquals(1_000_000L, preview.rounds.sumOf { it.amountMinor })
    }
}
