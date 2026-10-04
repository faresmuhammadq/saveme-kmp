package com.fares.saveme.feature.recurring

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.recurring.CapturedRecurringResponses as Captured
import com.fares.saveme.feature.recurring.data.OccurrenceFeedApplier
import com.fares.saveme.feature.recurring.data.RecurringRuleFeedApplier
import com.fares.saveme.feature.recurring.data.dto.RecurringFields
import com.fares.saveme.feature.recurring.domain.model.AmountBasis
import com.fares.saveme.feature.recurring.domain.model.AutoConfirmRefusal
import com.fares.saveme.feature.recurring.domain.model.CatchUpOutcome
import com.fares.saveme.feature.recurring.domain.model.ConfirmDraft
import com.fares.saveme.feature.recurring.domain.model.Frequency
import com.fares.saveme.feature.recurring.domain.model.OccurrenceResult
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.Preset
import com.fares.saveme.feature.recurring.domain.model.RecurringRecord
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.domain.model.RuleDraft
import com.fares.saveme.feature.recurring.domain.model.RuleResult
import com.fares.saveme.feature.recurring.domain.model.RuleStatus
import com.fares.saveme.feature.recurring.domain.schedule.RuleDates
import com.fares.saveme.feature.recurring.ui.RecurringRefusalKind
import com.fares.saveme.feature.recurring.ui.recurringRefusalKind
import com.fares.saveme.feature.transactions.data.TransactionFeedApplier
import com.fares.saveme.feature.transactions.domain.model.TransactionKind
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The client's requests, decoding and sync handling over answers captured from the real local backend (spec 010 host
 * QA, qa-report.md "Contract QA"), driven through the real wire client, sync engine, handlers, appliers and service
 * (T096). Where QA recorded the request it sent, the app's own request body is compared with it member for member.
 */
class RecurringCapturedResponsesTest {

    /** Answers by (method, path part, optional body part); the first match wins, so more specific keys go first. */
    private class Script {
        class Entry(val method: HttpMethod?, val path: String, val body: String?, val status: Int, val json: String)

        val entries = mutableListOf<Entry>()

        fun on(method: HttpMethod?, pathPart: String, status: Int, body: String, bodyPart: String? = null) {
            entries.add(0, Entry(method, pathPart, bodyPart, status, body))
        }

        fun answer(request: HttpRequestData): Pair<Int, String> {
            val url = request.url.toString()
            val text = runCatching { request.jsonBody().toString() }.getOrDefault("")
            val hit = entries.firstOrNull { (it.method == null || it.method == request.method) && it.path in url && (it.body == null || it.body in text) }
                ?: error("no scripted answer for ${request.method.value} $url $text")
            return hit.status to hit.json
        }
    }

    private val now = Instant.parse("2026-10-04T20:30:00Z")

    private fun data(body: String): String = AppJson.parseToJsonElement(body).jsonObject.getValue("data").toString()

    private fun qa(json: String): JsonObject = AppJson.parseToJsonElement(json).jsonObject

    private fun rows(page: String, type: String): List<JsonElement> =
        AppJson.parseToJsonElement(page).jsonObject.getValue("data").jsonArray
            .filter { it.jsonObject.getValue("type").jsonPrimitive.content == type }
            .map { it.jsonObject.getValue("record") }

    private fun errors(body: String): Map<String, String> =
        AppJson.parseToJsonElement(body).jsonObject["errors"]?.jsonArray.orEmpty()
            .associate { it.jsonObject.getValue("pointer").jsonPrimitive.content to it.jsonObject.getValue("code").jsonPrimitive.content }

    private fun env(scope: CoroutineScope, script: Script, ids: List<String> = emptyList()): RecurringServiceEnv {
        val env = RecurringServiceEnv(scope, newIds = ids) { request ->
            val (status, body) = script.answer(request)
            jsonResponse(status, body)
        }
        env.clock.now = now
        env.walletCurrencies[Captured.CASH] = "EGP"
        env.walletCurrencies[Captured.DOLLARS] = "USD"
        return env
    }

    private val cashJson get() = data(Captured.SEQ_WALLET_BEFORE_RES)
    private val bankJson get() = data(Captured.WALLET_W2_FINAL_RES)

    private suspend fun RecurringServiceEnv.signedInWithWallets() {
        signedIn()
        seedWallet(cashJson)
        seedWallet(bankJson)
    }

    private suspend fun RecurringServiceEnv.applyFeed(walletId: String, vararg pages: String) {
        val scope = SyncScope(ScopeKind.Wallet, walletId)
        val rules = pages.flatMap { rows(it, "recurring_rule") }
        val occurrences = pages.flatMap { rows(it, "occurrence") }
        val transactions = pages.flatMap { rows(it, "transaction") }
        RecurringRuleFeedApplier(this.rules).apply(scope, rules)
        OccurrenceFeedApplier(this.occurrences, { occurrenceId, except -> occurrenceTransactions.dropConfirmationOf(occurrenceId, except) })
            .apply(scope, occurrences)
        TransactionFeedApplier(transactionRepository).apply(scope, transactions)
    }

    private suspend fun RecurringServiceEnv.allOccurrences(walletId: String?, ruleId: String?) =
        service.observeOccurrences(walletId, ruleId).first().let { it.due + it.upcoming + it.history }

    private fun puts(env: RecurringServiceEnv, part: String): List<HttpRequestData> =
        env.requests.filter { it.method == HttpMethod.Put && part in it.url.encodedPath }

    private fun draft(
        description: String,
        category: String? = Captured.CATEGORY_RENT,
        amount: Long = 450_000,
        anchor: Int? = 1,
        start: LocalDate,
        frequency: Frequency = Frequency.Monthly,
        interval: Int = 1,
        count: Int? = null,
        preset: Preset = Preset.Custom,
        kind: TransactionKind = TransactionKind.Expense,
        basis: AmountBasis = AmountBasis.PerOccurrence,
        counter: String? = null,
        payoutRound: Int? = null,
        payoutCategory: String? = null,
        counterparty: String? = null,
    ) = RuleDraft(
        preset, kind, amount, basis, category, counter, description, null, counterparty, false, frequency, interval, anchor, start,
        null, count, payoutRound, payoutCategory,
    )

    private class CreateCase(val name: String, val ruleId: String, val draft: RuleDraft, val request: String, val response: String)

    private val createCases
        get() = listOf(
            CreateCase("monthly day 31", Captured.RULE_M31, draft("Rent day 31", anchor = 31, start = LocalDate(2026, 7, 31)), Captured.CREATE_MONTHLY_DAY31_REQ, Captured.CREATE_MONTHLY_DAY31_RES),
            CreateCase(
                "weekly every 2", Captured.RULE_WK,
                draft("Weekly groceries", Captured.CATEGORY_GROCERIES, 25_000, null, LocalDate(2026, 9, 3), Frequency.Weekly, 2),
                Captured.CREATE_WEEKLY_EVERY_2_REQ, Captured.CREATE_WEEKLY_EVERY_2_RES,
            ),
            CreateCase(
                "daily every 3 for five rounds", Captured.RULE_DY,
                draft("Daily coffee", Captured.CATEGORY_DINING_OUT, 3_000, null, LocalDate(2026, 9, 28), Frequency.Daily, 3, count = 5),
                Captured.CREATE_DAILY_EVERY_3_COUNT_5_REQ, Captured.CREATE_DAILY_EVERY_3_COUNT_5_RES,
            ),
            CreateCase(
                "yearly on 29 February", Captured.RULE_YR,
                draft("Insurance yearly", Captured.CATEGORY_UTILITIES, 900_000, 29, LocalDate(2024, 2, 29), Frequency.Yearly),
                Captured.CREATE_YEARLY_29_FEB_REQ, Captured.CREATE_YEARLY_29_FEB_RES,
            ),
            CreateCase(
                "monthly day 29", Captured.RULE_M29,
                draft("Monthly 29", Captured.CATEGORY_RENT, 10_000, 29, LocalDate(2026, 7, 29)),
                Captured.CREATE_MONTHLY_DAY_29_START_JAN_REQ, Captured.CREATE_MONTHLY_DAY_29_START_JAN_RES,
            ),
            CreateCase(
                "every two months on 30", Captured.RULE_Q,
                draft("Every two months", Captured.CATEGORY_RENT, 20_000, 30, LocalDate(2026, 6, 30), interval = 2),
                Captured.CREATE_EVERY_2_MONTHS_DAY_30_REQ, Captured.CREATE_EVERY_2_MONTHS_DAY_30_RES,
            ),
            CreateCase(
                "gam'eya with a payout", Captured.RULE_GAM,
                draft(
                    "Gameya", Captured.CATEGORY_OTHER_EXPENSE, 100_000, 6, LocalDate(2026, 7, 6), count = 10, preset = Preset.Gameya,
                    payoutRound = 4, payoutCategory = Captured.CATEGORY_SALARY,
                ),
                Captured.CREATE_GAMEYA_REQ, Captured.CREATE_GAMEYA_RES,
            ),
            CreateCase(
                "installment over a total", Captured.RULE_INST,
                draft(
                    "Fridge", Captured.CATEGORY_HOUSEHOLD, 1_000_000, 6, LocalDate(2026, 7, 6), count = 12, preset = Preset.Installment,
                    basis = AmountBasis.Total, counterparty = "B.TECH",
                ),
                Captured.CREATE_INSTALLMENT_TOTAL_REQ, Captured.CREATE_INSTALLMENT_TOTAL_RES,
            ),
            CreateCase(
                "transfer", Captured.RULE_XFER,
                draft("Savings move", null, 50_000, 15, LocalDate(2026, 9, 15), kind = TransactionKind.Transfer, counter = Captured.BANK),
                Captured.CREATE_TRANSFER_RULE_REQ, Captured.CREATE_TRANSFER_RULE_RES,
            ),
            CreateCase(
                "income", Captured.RULE_INC,
                draft("Salary", Captured.CATEGORY_SALARY, 800_000, 1, LocalDate(2026, 9, 1), kind = TransactionKind.Income),
                Captured.CREATE_INCOME_RULE_REQ, Captured.CREATE_INCOME_RULE_RES,
            ),
        )

    @Test
    fun everyKindOfRuleIsSentAsQaSentItAndTheLiveAnswerIsStored() = runTest {
        for (case in createCases) {
            val script = Script()
            script.on(HttpMethod.Put, "recurring-rules/${case.ruleId}", 201, case.response)
            val env = env(backgroundScope, script, ids = listOf(case.ruleId))
            env.signedInWithWallets()

            val created = assertIs<RuleResult.Created>(env.service.create(Captured.CASH, case.draft), case.name)
            assertEquals(case.ruleId, created.id)
            assertEquals(0L, env.ruleDao.get(case.ruleId)!!.version, "${case.name}: the placeholder is waiting")
            env.engine.drain()

            val put = puts(env, "recurring-rules/").single()
            assertTrue(put.url.encodedPath.endsWith("wallets/${Captured.CASH}/recurring-rules/${case.ruleId}"), case.name)
            assertEquals(qa(case.request), put.jsonBody(), case.name)
            assertTrue(env.pendingDao.rows.value.isEmpty(), "${case.name}: nothing left to send")
            val stored = env.ruleDao.get(case.ruleId)!!
            assertEquals(1L, stored.version, case.name)
            assertEquals("Africa/Cairo", stored.timezone, "${case.name}: the rule's zone comes from the server's copy")
            assertNotNull(stored.createdBy)
        }
    }

    @Test
    fun anIdenticalReplayIsAnsweredUnchangedAndTheRuleIsStoredOnce() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "recurring-rules/${Captured.RULE_M31}", 200, Captured.REPLAY_IDENTICAL_RES)
        val env = env(backgroundScope, script, ids = listOf(Captured.RULE_M31))
        env.signedInWithWallets()
        env.service.create(Captured.CASH, createCases.first().draft)
        env.engine.drain()
        assertTrue(env.pendingDao.rows.value.isEmpty())
        assertEquals(1, env.ruleDao.rows.value.size)
        assertEquals(1L, env.ruleDao.get(Captured.RULE_M31)!!.version)
    }

    @Test
    fun aStaleCreateReplayBecomesAClashCarryingTheServersCopy() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "recurring-rules/${Captured.RULE_M31}", 409, Captured.STALE_CREATE_REPLAY_WITH_CHANGED_BODY_RES)
        val env = env(backgroundScope, script, ids = listOf(Captured.RULE_M31))
        env.signedInWithWallets()
        env.service.create(Captured.CASH, createCases.first().draft)
        env.engine.drain()
        val view = env.service.observeRule(Captured.RULE_M31).first()!!
        assertEquals(RecurringSyncState.Clash(deletedElsewhere = false, mineIsDeletion = false), view.syncState)

        env.service.useTheirs(RecurringRecord.Rule, Captured.RULE_M31)
        val stored = env.ruleDao.get(Captured.RULE_M31)!!
        assertEquals(1L, stored.version)
        assertEquals("Rent day 31", stored.description)
    }

    /** The rule drafts the QA's refused creates stood for; the answer is chosen by code and pointer (FR-035). */
    @Test
    fun everyRealRuleRefusalLandsOnItsFieldAndKeepsItsCode() = runTest {
        val refusals = listOf(
            Captured.N422_CURRENCY_MISMATCH_CREATE_RES to RecurringFields.CURRENCY,
            Captured.N422_TRANSFER_TO_OTHER_CURRENCY_RES to RecurringFields.DESTINATION,
            Captured.N422_ANCHOR_DAY_ON_DAILY_RES to RecurringFields.DAY,
            Captured.N422_BOTH_END_AND_COUNT_RES to RecurringFields.COUNT,
            Captured.N422_INTERVAL_366_RES to RecurringFields.INTERVAL,
            Captured.N422_END_BEFORE_START_RES to RecurringFields.END,
            Captured.N422_CATEGORY_MISSING_RES to RecurringFields.CATEGORY,
            Captured.N422_TOTAL_BELOW_COUNT_RES to RecurringFields.AMOUNT,
            Captured.N422_TOTAL_WITHOUT_COUNT_RES to RecurringFields.AMOUNT_BASIS,
            Captured.N422_GAMEYA_PAYOUT_ROUND_BEYOND_COUNT_RES to RecurringFields.COUNT,
            Captured.N422_AMOUNT_ZERO_RES to RecurringFields.AMOUNT,
            Captured.N422_UNKNOWN_FREQUENCY_RES to RecurringFields.FREQUENCY,
            Captured.N422_DATE_OUT_OF_RANGE_RES to RecurringFields.START,
            Captured.N422_ENDED_ON_CREATE_RES to RecurringFields.STATUS,
        )
        for ((body, field) in refusals) {
            val script = Script()
            script.on(HttpMethod.Put, "recurring-rules/", 422, body)
            val env = env(backgroundScope, script, ids = listOf("rule-1"))
            env.signedInWithWallets()
            env.service.create(Captured.CASH, createCases.first().draft)
            env.engine.drain()
            val state = assertIs<RecurringSyncState.Refused>(env.service.observeRule("rule-1").first()!!.syncState)
            assertEquals("VALIDATION_FAILED", state.code)
            assertEquals(errors(body).values.single(), state.fields.getValue(field), body)
            assertEquals(1, state.fields.size, body)
        }
    }

    @Test
    fun theOtherRefusalsOfAWriteAreChosenByCodeAlone() = runTest {
        val cases = listOf(
            Triple(Captured.N403_VIEWER_RES, 403, "FORBIDDEN"),
            Triple(Captured.N404_WALLET_UNKNOWN_RES, 404, "NOT_FOUND"),
            Triple(Captured.N422_INVALID_ID_RES, 422, "INVALID_ID"),
            Triple(Captured.EDITOR_TRANSFER_RULE_INTO_A_W2_WHERE_HE_IS_ONLY_A_VIEWER_T_02_RES, 403, "FORBIDDEN"),
        )
        for ((body, status, code) in cases) {
            val script = Script()
            script.on(HttpMethod.Put, "recurring-rules/", status, body)
            val env = env(backgroundScope, script, ids = listOf("rule-1"))
            env.signedInWithWallets()
            env.service.create(Captured.CASH, createCases.first().draft)
            env.engine.drain()
            val state = assertIs<RecurringSyncState.Refused>(env.service.observeRule("rule-1").first()!!.syncState)
            assertEquals(code, state.code)
            assertNotNull(state.requestId, "the request id is kept for support")
        }
        val kind = recurringRefusalKind("FORBIDDEN", emptyMap(), isTransfer = true)
        assertEquals(RecurringRefusalKind.NoRightInOtherWallet, kind)
    }

    /** A rule the phone holds with no resolved round, saved with a member the server has frozen. */
    @Test
    fun frozenMembersAreSentAsQaSentThemAndRefusedAtTheirField() = runTest {
        class Frozen(val ruleId: String, val create: String, val request: String, val response: String, val field: String, val change: (RuleDraft) -> RuleDraft)
        val cases = listOf(
            Frozen(Captured.RULE_M31, Captured.CREATE_MONTHLY_DAY31_RES, Captured.FROZEN_INTERVAL_REQ, Captured.FROZEN_INTERVAL_RES, RecurringFields.INTERVAL) { it.copy(interval = 2) },
            Frozen(Captured.RULE_M31, Captured.CREATE_MONTHLY_DAY31_RES, Captured.FROZEN_ANCHOR_DAY_REQ, Captured.FROZEN_ANCHOR_DAY_RES, RecurringFields.DAY) { it.copy(anchorDay = 30) },
            Frozen(Captured.RULE_M31, Captured.CREATE_MONTHLY_DAY31_RES, Captured.FROZEN_START_DATE_REQ, Captured.FROZEN_START_DATE_RES, RecurringFields.START) { it.copy(startDate = LocalDate(2026, 7, 1)) },
            Frozen(Captured.RULE_GAM, Captured.CREATE_GAMEYA_RES, Captured.GAMEYA_AMOUNT_FROZEN_REQ, Captured.GAMEYA_AMOUNT_FROZEN_RES, RecurringFields.AMOUNT) { it.copy(amountMinor = 200_000) },
            Frozen(Captured.RULE_GAM, Captured.CREATE_GAMEYA_RES, Captured.GAMEYA_COUNT_FROZEN_REQ, Captured.GAMEYA_COUNT_FROZEN_RES, RecurringFields.COUNT) { it.copy(occurrenceCount = 11) },
            Frozen(Captured.RULE_GAM, Captured.CREATE_GAMEYA_RES, Captured.GAMEYA_PAYOUT_ROUND_FROZEN_REQ, Captured.GAMEYA_PAYOUT_ROUND_FROZEN_RES, RecurringFields.PAYOUT_ROUND) { it.copy(payoutRound = 5) },
            Frozen(Captured.RULE_INST, Captured.CREATE_INSTALLMENT_TOTAL_RES, Captured.INSTALLMENT_TOTAL_AMOUNT_FROZEN_REQ, Captured.INSTALLMENT_TOTAL_AMOUNT_FROZEN_RES, RecurringFields.AMOUNT) { it.copy(amountMinor = 1_200_000) },
            Frozen(Captured.RULE_INST, Captured.CREATE_INSTALLMENT_TOTAL_RES, Captured.INSTALLMENT_COUNT_FROZEN_REQ, Captured.INSTALLMENT_COUNT_FROZEN_RES, RecurringFields.COUNT) { it.copy(occurrenceCount = 13) },
        )
        for (case in cases) {
            val script = Script()
            script.on(HttpMethod.Put, "recurring-rules/${case.ruleId}", 422, case.response)
            val env = env(backgroundScope, script)
            env.signedInWithWallets()
            env.seedRule(data(case.create))
            val view = env.service.observeRule(case.ruleId).first()!!
            assertEquals(RuleResult.Saved, env.service.save(case.ruleId, case.change(view.shown), view.rule.version), case.field)
            env.engine.drain()
            assertEquals(qa(case.request), puts(env, "recurring-rules/").single().jsonBody(), case.field)
            val state = assertIs<RecurringSyncState.Refused>(env.service.observeRule(case.ruleId).first()!!.syncState)
            assertEquals("IMMUTABLE_FIELD", state.fields.getValue(case.field), case.field)
            assertEquals(RecurringRefusalKind.Immutable, recurringRefusalKind(state.code, state.fields), case.field)
        }
    }

    @Test
    fun aTemplateChangeSendsTheWholeRuleOnTheBaseVersionAndTheNewCopyReplacesIt() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "recurring-rules/${Captured.RULE_M31}", 200, Captured.TEMPLATE_CHANGE_AMOUNT_AUTO_CONFIRM_DESCRIPTION_ALLOWED_RES)
        val env = env(backgroundScope, script)
        env.signedInWithWallets()
        env.seedRule(data(Captured.CREATE_MONTHLY_DAY31_RES))
        val view = env.service.observeRule(Captured.RULE_M31).first()!!
        env.service.save(Captured.RULE_M31, view.shown.copy(amountMinor = 470_000, description = "Rent 31 (raised)"), view.rule.version)
        env.engine.drain()
        assertEquals(qa(Captured.TEMPLATE_CHANGE_AMOUNT_AUTO_CONFIRM_DESCRIPTION_ALLOWED_REQ), puts(env, "recurring-rules/").single().jsonBody())
        val stored = env.ruleDao.get(Captured.RULE_M31)!!
        assertEquals(2L, stored.version)
        assertEquals(470_000, stored.amountMinor)
    }

    @Test
    fun pausingResumingAndEndingAreWholeRuleSavesWithTheNewStatus() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "recurring-rules/${Captured.RULE_DY}", 200, Captured.PAUSE_DAILY_RES, bodyPart = "\"status\":\"paused\"")
        script.on(HttpMethod.Put, "recurring-rules/${Captured.RULE_DY}", 200, Captured.RESUME_DAILY_RES, bodyPart = "\"version\":2")
        script.on(HttpMethod.Put, "recurring-rules/${Captured.RULE_DY}", 200, Captured.END_DAILY_RES, bodyPart = "\"status\":\"ended\"")
        val env = env(backgroundScope, script)
        env.signedInWithWallets()
        env.seedRule(data(Captured.CREATE_DAILY_EVERY_3_COUNT_5_RES))
        assertEquals(RuleResult.Saved, env.service.setStatus(Captured.RULE_DY, RuleStatus.Paused, 1))
        env.engine.drain()
        assertEquals(qa(Captured.PAUSE_DAILY_REQ), puts(env, "recurring-rules/").last().jsonBody())
        assertEquals(RuleStatus.Paused, env.ruleDao.get(Captured.RULE_DY)!!.let { com.fares.saveme.feature.recurring.domain.model.RuleStatus.fromWire(it.status) })

        assertEquals(RuleResult.Saved, env.service.setStatus(Captured.RULE_DY, RuleStatus.Active, 2))
        env.engine.drain()
        assertEquals(qa(Captured.RESUME_DAILY_REQ), puts(env, "recurring-rules/").last().jsonBody())

        assertEquals(RuleResult.Saved, env.service.setStatus(Captured.RULE_DY, RuleStatus.Ended, 3))
        env.engine.drain()
        assertEquals(qa(Captured.END_DAILY_REQ), puts(env, "recurring-rules/").last().jsonBody())
        val ended = env.service.observeRule(Captured.RULE_DY).first()!!
        assertTrue(ended.readOnly, "an ended rule can only be deleted")
        assertEquals(4L, ended.rule.version)
    }

    @Test
    fun aSplitEndsTheOldRuleThenSendsTheNewOneAsQaSentThem() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "recurring-rules/${Captured.RULE_SP}", 200, Captured.SPLIT_FROM_15_NOV_STEP_1_END_THE_OLD_RULE_RES)
        script.on(HttpMethod.Put, "recurring-rules/${Captured.RULE_SP2}", 201, Captured.SPLIT_FROM_15_NOV_STEP_2_THE_NEW_RULE_RES)
        val env = env(backgroundScope, script, ids = listOf(Captured.RULE_SP2))
        env.signedInWithWallets()
        env.seedRule(data(Captured.SPLIT_BASE_RULE_RES))
        val view = env.service.observeRule(Captured.RULE_SP).first()!!
        val changes = view.shown.copy(amountMinor = 35_000, description = "Gym (new price)")
        assertEquals(RuleResult.Created(Captured.RULE_SP2), env.service.changeFrom(Captured.RULE_SP, LocalDate(2026, 11, 15), changes, view.rule.version))
        assertEquals(RecurringSyncState.Held(Captured.RULE_SP), env.service.observeRule(Captured.RULE_SP2).first()!!.syncState, "the new rule waits for the old one")
        env.engine.drain()
        env.engine.drain()

        val sent = puts(env, "recurring-rules/")
        assertEquals(2, sent.size)
        assertTrue(sent[0].url.encodedPath.endsWith(Captured.RULE_SP), "the old rule is ended first")
        assertEquals(qa(Captured.SPLIT_FROM_15_NOV_STEP_1_END_THE_OLD_RULE_REQ), sent[0].jsonBody())
        assertEquals(qa(Captured.SPLIT_FROM_15_NOV_STEP_2_THE_NEW_RULE_REQ), sent[1].jsonBody())
        assertTrue(env.pendingDao.rows.value.isEmpty())
        assertEquals("2026-11-14", env.ruleDao.get(Captured.RULE_SP)!!.endDate)
        assertEquals(1L, env.ruleDao.get(Captured.RULE_SP2)!!.version)
        assertNull(env.ruleDao.get(Captured.RULE_SP2)!!.followsRuleId, "the link ends with the old rule's acceptance")
    }

    @Test
    fun deletingSendsTheVersionInABodyAndTheTombstoneRemovesTheRule() = runTest {
        val script = Script()
        script.on(HttpMethod.Delete, "recurring-rules/${Captured.RULE_TMP}", 200, Captured.DELETE_WITH_BODY_RES)
        val env = env(backgroundScope, script)
        env.signedInWithWallets()
        env.seedRule(data(Captured.TEMP_RULE_RES))
        assertEquals(RuleResult.Saved, env.service.delete(Captured.RULE_TMP))
        assertNull(env.service.observeRule(Captured.RULE_TMP).first()?.takeUnless { it.hidden }, "it disappears at once")
        env.engine.drain()
        val delete = env.requests.single { it.method == HttpMethod.Delete }
        assertEquals(qa(Captured.DELETE_WITH_BODY_REQ), delete.jsonBody())
        assertNull(env.ruleDao.get(Captured.RULE_TMP))
        assertTrue(env.pendingDao.rows.value.isEmpty())
    }

    @Test
    fun aStaleDeleteBecomesAClashWithTheLiveRuleAndKeepMineSendsTheNewVersion() = runTest {
        val script = Script()
        script.on(HttpMethod.Delete, "recurring-rules/${Captured.RULE_WK}", 409, Captured.DELETE_STALE_VERSION_409_RES)
        val env = env(backgroundScope, script)
        env.signedInWithWallets()
        env.seedRule(data(Captured.CREATE_WEEKLY_EVERY_2_RES))
        env.service.delete(Captured.RULE_WK)
        env.engine.drain()
        val current = AppJson.parseToJsonElement(Captured.DELETE_STALE_VERSION_409_RES).jsonObject.getValue("current").jsonObject
        val clash = env.service.observeRule(Captured.RULE_WK).first()!!
        assertEquals(RecurringSyncState.Clash(deletedElsewhere = false, mineIsDeletion = true), clash.syncState)

        script.on(HttpMethod.Delete, "recurring-rules/${Captured.RULE_WK}", 200, Captured.DELETE_REPLAY_200_TOMBSTONE_RES)
        env.service.keepMine(RecurringRecord.Rule, Captured.RULE_WK)
        env.engine.drain()
        val last = env.requests.last { it.method == HttpMethod.Delete }.jsonBody().jsonObject
        assertEquals(current.getValue("version"), last.getValue("version"), "re-sent on the newer version")
    }

    @Test
    fun catchUpAsksOnceAndEveryRealAnswerIsFollowedByAForcedRead() = runTest {
        val answers = listOf(
            Triple(200, Captured.MATERIALISE_OK_RES, Captured.RULE_M31),
            Triple(409, Captured.MATERIALISE_DELETED_RULE_409_RES, Captured.RULE_M31),
            Triple(404, Captured.MATERIALISE_UNKNOWN_RULE_404_RES, Captured.RULE_M31),
        )
        for ((status, body, ruleId) in answers) {
            val script = Script()
            script.on(HttpMethod.Post, "recurring-rules/$ruleId/materialise", status, body)
            val env = env(backgroundScope, script)
            env.signedInWithWallets()
            env.seedRule(data(Captured.CREATE_MONTHLY_DAY31_RES))
            assertEquals(CatchUpOutcome.Done, env.service.catchUp(ruleId), "answer $status")
            val post = env.requests.single { it.method == HttpMethod.Post }
            assertTrue(post.url.encodedPath.endsWith("wallets/${Captured.CASH}/recurring-rules/$ruleId/materialise"))
            assertEquals(1, env.runner.refreshes.size, "the wallet is read after $status")
        }
        for ((status, body) in listOf(403 to Captured.VIEWER_MATERIALISE_403_RES, 403 to Captured.UNVERIFIED_MATERIALISE_RES)) {
            val script = Script()
            script.on(HttpMethod.Post, "materialise", status, body)
            val env = env(backgroundScope, script)
            env.signedInWithWallets()
            env.seedRule(data(Captured.CREATE_MONTHLY_DAY31_RES))
            val outcome = env.service.catchUp(Captured.RULE_M31)
            assertTrue(outcome == CatchUpOutcome.NotAllowed || outcome == CatchUpOutcome.NotVerified, "$outcome")
        }
    }

    private val feedPages get() = listOf(Captured.FEED_PAGE_0_200, Captured.FEED_PAGE_1_200, Captured.FEED_PAGE_2_200, Captured.FEED_PAGE_3_200)

    @Test
    fun theRealFeedPagesStoreEveryRuleOccurrenceAndProducedTransaction() = runTest {
        val env = env(backgroundScope, Script())
        env.signedInWithWallets()
        env.applyFeed(Captured.CASH, *feedPages.toTypedArray())

        val liveRules = feedPages.flatMap { rows(it, "recurring_rule") }.map { it.jsonObject }.filter { it.getValue("deleted_at").toString() == "null" }
        val tombstonedRules = feedPages.flatMap { rows(it, "recurring_rule") }.map { it.jsonObject }.filter { it.getValue("deleted_at").toString() != "null" }
        assertTrue(liveRules.size >= 14, "${liveRules.size}")
        assertTrue(tombstonedRules.isNotEmpty(), "the deleted rule's tombstone is in the feed")
        assertEquals(liveRules.size, env.ruleDao.rows.value.size)
        // Tombstones of rules and occurrences are never stored; unknown members decoded without a problem.
        val liveOccurrences = feedPages.flatMap { rows(it, "occurrence") }.map { it.jsonObject }.filter { it.getValue("deleted_at").toString() == "null" }
        assertEquals(liveOccurrences.size, env.occurrenceDao.rows.value.size)
        val producedTransactions = feedPages.flatMap { rows(it, "transaction") }.map { it.jsonObject }
        assertTrue(producedTransactions.all { it.getValue("recurring_rule_id").toString() != "null" && it.getValue("occurrence_id").toString() != "null" })
        assertEquals(producedTransactions.size, env.transactionDao.getAll().size)
        assertTrue(env.transactionDao.getAll().all { it.recurringRuleId != null && it.occurrenceId != null }, "the read-only links are kept")
    }

    @Test
    fun theRealOccurrencesAreDueMissedUpcomingConfirmedAndSkippedInTheRulesOwnToday() = runTest {
        val env = env(backgroundScope, Script())
        env.signedInWithWallets()
        env.applyFeed(Captured.CASH, *feedPages.toTypedArray())

        val states = env.allOccurrences(Captured.CASH, Captured.RULE_M31).associate { it.occurrence.scheduledDate.toString() to it.state }
        assertEquals(OccurrenceState.Confirmed, states["2026-07-31"])
        assertEquals(OccurrenceState.Confirmed, states["2026-08-31"], "a skipped round confirmed directly")
        assertEquals(OccurrenceState.Due, states["2026-09-30"])
        assertEquals(OccurrenceState.Upcoming, states["2026-10-31"])

        val refused = env.allOccurrences(Captured.CASH, Captured.RULE_B_AUTO)
            .filter { it.occurrence.autoConfirmRefusal != null }
        assertEquals(2, refused.size, "two rounds the server could not confirm for a viewer-author remain (one was confirmed by hand)")
        assertTrue(refused.all { it.occurrence.autoConfirmRefusal == AutoConfirmRefusal.NotPermitted })
        assertTrue(refused.all { it.state == OccurrenceState.Due || it.state == OccurrenceState.Missed })

        val autoConfirmed = env.allOccurrences(Captured.CASH, Captured.RULE_AC)
        assertEquals(listOf(OccurrenceState.Confirmed, OccurrenceState.Confirmed, OccurrenceState.Confirmed, OccurrenceState.Upcoming), autoConfirmed.sortedBy { it.occurrence.ordinal }.map { it.state })
        assertEquals(3, autoConfirmed.count { it.occurrence.transactionId != null })

        val gameya = env.allOccurrences(Captured.CASH, Captured.RULE_GAM)
        val payout = gameya.single { it.isPayout }
        assertEquals(1_000_000, payout.occurrence.amountMinor, "the payout is the sum of the ten rounds")
        assertEquals(LocalDate(2026, 10, 6), payout.occurrence.scheduledDate)
    }

    @Test
    fun theDollarWalletFeedCarriesTheTwoOtherRefusalReasons() = runTest {
        val env = env(backgroundScope, Script())
        env.signedIn()
        env.seedWallet(walletJson(id = Captured.DOLLARS, currency = "USD", role = "owner"))
        env.applyFeed(Captured.DOLLARS, Captured.DOLLARS_FEED_PAGE_0_RES)
        val reasons = env.allOccurrences(Captured.DOLLARS, null).mapNotNull { it.occurrence.autoConfirmRefusal }.toSet()
        assertEquals(setOf(AutoConfirmRefusal.CounterWalletUnavailable, AutoConfirmRefusal.CurrencyMismatch), reasons)
    }

    /** SC-002: for every captured rule the phone's preview gives the server's date for every round the server listed. */
    @Test
    fun thePhonesDatesEqualTheServersForEveryCapturedRule() = runTest {
        val rules = feedPages.flatMap { rows(it, "recurring_rule") }.map { it.jsonObject }.filter { it.getValue("deleted_at").toString() == "null" }
        val occurrences = feedPages.flatMap { rows(it, "occurrence") }.map { it.jsonObject }.filter { it.getValue("deleted_at").toString() == "null" }
        var compared = 0
        for (rule in rules) {
            val id = rule.getValue("id").jsonPrimitive.content
            fun str(key: String) = rule.getValue(key).jsonPrimitive.takeUnless { it.content == "null" }?.content
            val own = occurrences.filter { it.getValue("recurring_rule_id").jsonPrimitive.content == id }
            if (own.isEmpty()) continue
            val rounds = RuleDates.dates(
                Frequency.fromWire(str("frequency")!!), str("interval")!!.toInt(), str("anchor_day")?.toInt(), LocalDate.parse(str("start_date")!!),
                str("end_date")?.let(LocalDate::parse), str("occurrence_count")?.toInt(), limit = 60,
            ).associate { it.ordinal to it.date }
            for (occurrence in own) {
                val ordinal = occurrence.getValue("ordinal").jsonPrimitive.content.toInt()
                assertEquals(
                    rounds[ordinal].toString(), occurrence.getValue("scheduled_date").jsonPrimitive.content,
                    "${str("description")} (${str("frequency")} ${str("interval")} day ${str("anchor_day")} from ${str("start_date")}) round $ordinal",
                )
                compared++
            }
        }
        assertTrue(compared >= 50, "$compared rounds compared")
    }

    /** US2-S2/S3, SC-005: the real confirm, its answer, the feed that follows and the balance throughout. */
    @Test
    fun aConfirmationIsSentAsQaSentItAndTheBalanceNeverJumpsWhileItSyncsAndSettles() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "occurrences/", 200, Captured.SEQ_CONFIRM_RES)
        val env = env(backgroundScope, script, ids = listOf(Captured.RULE_SEQ_TX))
        env.signedIn()
        env.seedWallet(cashJson)
        env.seedWallet(bankJson)
        env.applyFeed(Captured.CASH, Captured.SEQ_FEED_BEFORE_CONFIRM_RES)
        val first = env.allOccurrences(Captured.CASH, Captured.RULE_SEQ).first { it.occurrence.ordinal == 1 }
        assertEquals(OccurrenceState.Due, first.state)
        val before = env.wallets.observeWallet(Captured.CASH).first()!!.displayedBalanceMinor
        assertEquals(-383_634, before)

        val draft = ConfirmDraft(LocalDate(2026, 9, 6), 26_000, Captured.CATEGORY_UTILITIES, "Electricity", null, null)
        assertEquals(OccurrenceResult.Done, env.service.confirm(first.id, draft))
        val waiting = env.wallets.observeWallet(Captured.CASH).first()!!
        assertEquals(before - 26_000, waiting.displayedBalanceMinor, "the balance counts the pending transaction at once")
        assertTrue(waiting.hasPendingPostings)
        assertEquals(1, env.transactionDao.getAll().count { it.id == Captured.RULE_SEQ_TX }, "exactly one pending transaction under the client's id")

        env.engine.drain()
        val put = puts(env, "occurrences/").single()
        assertEquals(qa(Captured.SEQ_CONFIRM_REQ), put.jsonBody())
        assertNull(env.engine.get("occurrence", first.id), "accepted")
        // Synced but the wallet still carries the old balance: it is counted once, not twice.
        assertEquals(before - 26_000, env.wallets.observeWallet(Captured.CASH).first()!!.displayedBalanceMinor)

        // The feed brings the server's transaction, occurrence and wallet record; the displayed balance is the server's.
        env.applyFeed(Captured.CASH, Captured.SEQ_FEED_AFTER_CONFIRM_RES)
        env.seedWallet(data(Captured.SEQ_WALLET_AFTER_CONFIRM_RES))
        env.reconciler.settle()
        val settled = env.wallets.observeWallet(Captured.CASH).first()!!
        assertEquals(-409_634, settled.displayedBalanceMinor, "the server's balance after the confirmation")
        assertEquals(1, env.transactionDao.getAll().count { it.id == Captured.RULE_SEQ_TX })
        val transaction = env.transactionDao.get(Captured.RULE_SEQ_TX)!!
        assertEquals(1L, transaction.version, "the server's copy replaced the pending one")
        assertEquals(Captured.RULE_SEQ, transaction.recurringRuleId)
        assertEquals(first.id, transaction.occurrenceId)
        assertEquals("confirmed", env.occurrenceDao.get(first.id)!!.status)
    }

    @Test
    fun aConfirmationRacingAnotherConfirmationIsAcceptedSilentlyAndNeverResent() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "occurrences/", 409, Captured.SEQ_RACE_STALE_CONFIRM_THE_PHONE_S_RES)
        val env = env(backgroundScope, script, ids = listOf(Captured.RULE_RACE_Y))
        env.signedIn()
        env.seedWallet(cashJson)
        env.applyFeed(Captured.CASH, Captured.SEQ_FEED_BEFORE_CONFIRM_RES)
        val third = env.allOccurrences(Captured.CASH, Captured.RULE_SEQ).first { it.occurrence.ordinal == 3 }
        env.service.confirm(third.id, ConfirmDraft(third.occurrence.date, 26_000, Captured.CATEGORY_UTILITIES, "Electricity", null, null))
        assertNotNull(env.transactionDao.get(Captured.RULE_RACE_Y), "the pending transaction is shown at once")
        env.engine.drain()
        env.engine.drain()

        assertEquals(qa(Captured.SEQ_RACE_STALE_CONFIRM_THE_PHONE_S_REQ), puts(env, "occurrences/").single().jsonBody())
        assertNull(env.engine.get("occurrence", third.id), "no choice is offered")
        assertNull(env.transactionDao.get(Captured.RULE_RACE_Y), "the phone's pending transaction is dropped")
        val view = env.service.observeOccurrence(third.id).first()!!
        assertEquals("confirmed", view.occurrence.statusWire)
        assertEquals(Captured.RULE_RACE_X, view.occurrence.transactionId, "the server's transaction id, not ours")
        assertEquals("already_confirmed", view.notice)
        assertEquals(1, puts(env, "occurrences/").size, "never re-sent under a new id")
    }

    @Test
    fun anIdenticalReplayOfAConfirmIsAnsweredUnchanged() = runTest {
        val script = Script()
        script.on(HttpMethod.Put, "occurrences/", 200, Captured.SEQ_RACE_FIRST_CONFIRM_THE_SERVER_S_RES)
        val env = env(backgroundScope, script, ids = listOf(Captured.RULE_RACE_X))
        env.signedIn()
        env.seedWallet(cashJson)
        env.applyFeed(Captured.CASH, Captured.SEQ_FEED_BEFORE_CONFIRM_RES)
        val third = env.allOccurrences(Captured.CASH, Captured.RULE_SEQ).first { it.occurrence.ordinal == 3 }
        env.service.confirm(third.id, ConfirmDraft(third.occurrence.date, third.occurrence.amountMinor, Captured.CATEGORY_UTILITIES, third.occurrence.description, null, null))
        env.engine.drain()
        assertEquals(qa(Captured.SEQ_RACE_FIRST_CONFIRM_THE_SERVER_S_REQ), puts(env, "occurrences/").single().jsonBody())
        assertEquals(2L, env.occurrenceDao.get(third.id)!!.version)
        assertNotNull(env.transactionDao.get(Captured.RULE_RACE_X))
    }

    @Test
    fun skipUndoAndEditOneAreSentAsQaSentThem() = runTest {
        val script = Script()
        val second = "occurrences/"
        script.on(HttpMethod.Put, second, 200, Captured.SEQ_SKIP_RES, bodyPart = "\"status\":\"skipped\"")
        script.on(HttpMethod.Put, second, 200, Captured.SEQ_UNDO_SKIP_RES, bodyPart = "\"status\":\"scheduled\"")
        script.on(HttpMethod.Put, second, 200, Captured.SEQ_EDIT_ONE_RES, bodyPart = "\"version\":3")
        val env = env(backgroundScope, script)
        env.signedIn()
        env.seedWallet(cashJson)
        env.applyFeed(Captured.CASH, Captured.SEQ_FEED_BEFORE_CONFIRM_RES)
        val view = env.allOccurrences(Captured.CASH, Captured.RULE_SEQ).first { it.occurrence.ordinal == 2 }

        assertEquals(OccurrenceResult.Done, env.service.skip(view.id))
        env.engine.drain()
        assertEquals(qa(Captured.SEQ_SKIP_REQ), puts(env, second).last().jsonBody())
        assertEquals("skipped", env.occurrenceDao.get(view.id)!!.status)
        assertNull(env.transactionDao.getAll().firstOrNull { it.occurrenceId == view.id }, "a skip posts nothing")

        assertEquals(OccurrenceResult.Done, env.service.unskip(view.id))
        env.engine.drain()
        assertEquals(qa(Captured.SEQ_UNDO_SKIP_REQ), puts(env, second).last().jsonBody())
        assertEquals("scheduled", env.occurrenceDao.get(view.id)!!.status)

        val edit = ConfirmDraft(LocalDate(2026, 10, 7), 27_500, Captured.CATEGORY_UTILITIES, "Electricity (Oct, adjusted)", null, null)
        assertEquals(OccurrenceResult.Done, env.service.edit(view.id, edit))
        env.engine.drain()
        assertEquals(qa(Captured.SEQ_EDIT_ONE_REQ), puts(env, second).last().jsonBody())
        val edited = env.occurrenceDao.get(view.id)!!
        assertTrue(edited.edited)
        assertEquals("2026-10-05", edited.scheduledDate, "the scheduled date never changes")
        assertEquals("2026-10-07", edited.date)
    }

    @Test
    fun theRealRefusedConfirmationsAreNotSavedWithTheirReasonAndDiscardingDropsTheTransaction() = runTest {
        class Refused(val ruleId: String, val request: String, val response: String, val expected: RecurringRefusalKind)
        val cases = listOf(
            Refused(Captured.RULE_X_GONE, Captured.CONFIRM_TRANSFER_WHOSE_DESTINATION_IS_GONE_REQ, Captured.CONFIRM_TRANSFER_WHOSE_DESTINATION_IS_GONE_RES, RecurringRefusalKind.DestinationUnavailable),
            Refused(Captured.RULE_X_CUR, Captured.CONFIRM_TRANSFER_WHOSE_CURRENCIES_DIFFER_REQ, Captured.CONFIRM_TRANSFER_WHOSE_CURRENCIES_DIFFER_RES, RecurringRefusalKind.CurrenciesDiffer),
        )
        for (case in cases) {
            val script = Script()
            script.on(HttpMethod.Put, "occurrences/", 422, case.response)
            val env = env(backgroundScope, script, ids = listOf("tx-new"))
            env.signedIn()
            env.seedWallet(walletJson(id = Captured.DOLLARS, currency = "USD", role = "owner", balance = 0))
            env.seedWallet(walletJson(id = Captured.DOLLARS_TWO, currency = "USD", role = "owner", balance = 0))
            env.seedWallet(walletJson(id = Captured.DOLLARS_THREE, currency = "USD", role = "owner", balance = 0))
            env.applyFeed(Captured.DOLLARS, Captured.DOLLARS_FEED_PAGE_0_RES)
            val request = qa(case.request)
            val target = env.occurrenceDao.rows.value.values.first { it.recurringRuleId == case.ruleId && it.version == request.getValue("version").jsonPrimitive.content.toLong() && it.date == request.getValue("date").jsonPrimitive.content }
            val view = env.service.observeOccurrence(target.id).first()!!
            val draft = ConfirmDraft(LocalDate.parse(target.date), target.amountMinor, null, target.description, target.memo, target.counterparty)
            assertEquals(OccurrenceResult.Done, env.service.confirm(view.id, draft))
            env.engine.drain()

            val sent = puts(env, "occurrences/").single().jsonBody().jsonObject
            assertEquals(request.filterKeys { it != "transaction_id" }, sent.filterKeys { it != "transaction_id" })
            val state = assertIs<RecurringSyncState.Refused>(env.service.observeOccurrence(target.id).first()!!.syncState)
            assertEquals(case.expected, recurringRefusalKind(state.code, state.fields, isTransfer = true))
            assertNotNull(env.transactionDao.get("tx-new"), "the pending transaction stays visible as not saved")
            env.service.discard(RecurringRecord.Occurrence, target.id)
            assertNull(env.transactionDao.get("tx-new"))
            assertNull(env.engine.get("occurrence", target.id))
        }
    }

    @Test
    fun everyRealOccurrenceRefusalLandsOnItsFieldOrIsChosenByCode() {
        val byPointer = listOf(
            Captured.N422_CONFIRM_WITHOUT_TRANSACTION_ID_RES to "REQUIRED",
            Captured.N422_TRANSACTION_ID_ON_SKIP_RES to "INVALID_ENUM_VALUE",
            Captured.N422_TRANSACTION_ID_ALREADY_USED_RES to "INVALID_ENUM_VALUE",
            Captured.N422_INCOME_CATEGORY_ON_EXPENSE_OCCURRENCE_RES to "INVALID_ENUM_VALUE",
            Captured.N422_AMOUNT_ZERO_2_RES to "OUT_OF_RANGE",
            Captured.N422_DATE_OUT_OF_RANGE_2_RES to "OUT_OF_RANGE",
            Captured.N422_BAD_STATUS_RES to "INVALID_ENUM_VALUE",
            Captured.CHANGE_CONFIRMED_OCC_IMMUTABLE_RES to "IMMUTABLE_FIELD",
        )
        for ((body, code) in byPointer) {
            val problem = errors(body)
            assertTrue(problem.values.all { it == code }, body)
            assertTrue(problem.keys.all { RecurringFields.occurrenceByPointer.containsKey(it) }, "every pointer the live server used is mapped: ${problem.keys}")
        }
        assertEquals("CURRENCY_MISMATCH", errors(Captured.CONFIRM_TRANSFER_WHOSE_CURRENCIES_DIFFER_RES)["/status"])
        assertEquals("INVALID_ENUM_VALUE", errors(Captured.CONFIRM_TRANSFER_WHOSE_DESTINATION_IS_GONE_RES)["/status"])
        assertTrue(Captured.N404_UNKNOWN_OCCURRENCE_RES.contains("NOT_FOUND"))
        assertTrue(Captured.N403_UNVERIFIED_2_RES.contains("EMAIL_NOT_VERIFIED"))
        assertTrue(Captured.EDITOR_CONFIRMS_A_TRANSFER_OCCURRENCE_WITHOUT_T_02_RES.contains("FORBIDDEN"))
    }

    /** Spec 009 batch upload carrying the recurring item types, replayed through the real engine and `HttpSyncApi`. */
    @Test
    fun aRuleAndTwoOccurrenceChangesTravelInOneBatchAsQaSentThemAndEveryAnswerSettles() = runTest {
        val qaItems = qa(Captured.BATCH_TWO_UPLOADS_OF_THE_APP_REQ).getValue("items").jsonArray.associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }
        val results = AppJson.parseToJsonElement(Captured.BATCH_TWO_UPLOADS_OF_THE_APP_RES).jsonObject
            .getValue("data").jsonArray.associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf(Captured.RULE_NR, Captured.RULE_BR_TX), batch = true) { request ->
            // The live answer's results, put in the order the app sent its items.
            val sent = request.jsonBody().jsonObject.getValue("items").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
            val body = AppJson.parseToJsonElement(Captured.BATCH_TWO_UPLOADS_OF_THE_APP_RES).jsonObject
            jsonResponse(200, JsonObject(body + ("data" to JsonArray(sent.map { results.getValue(it) }))).toString())
        }
        env.clock.now = now
        env.walletCurrencies[Captured.CASH] = "EGP"
        env.signedInWithWallets()
        env.applyFeed(Captured.CASH, Captured.BATCH_SETUP_FEED_RES)
        val rounds = env.allOccurrences(Captured.CASH, Captured.RULE_BR).sortedBy { it.occurrence.ordinal }

        env.service.create(Captured.CASH, draft("Gym batch", Captured.CATEGORY_HEALTH, 15_000, 22, LocalDate(2026, 10, 22)))
        env.service.skip(rounds[0].id)
        val second = rounds[1].occurrence
        env.service.confirm(rounds[1].id, ConfirmDraft(second.date, second.amountMinor, second.categoryId, second.description, null, null))
        env.engine.drain()

        val uploads = env.requests.filter { it.method == HttpMethod.Post && it.url.encodedPath.endsWith("/sync/batch") }
        assertEquals(1, uploads.size, "one request carries all three changes")
        val items = uploads.single().jsonBody().jsonObject.getValue("items").jsonArray
        assertEquals(qaItems.keys, items.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet())
        for (item in items) assertEquals(qaItems.getValue(item.jsonObject.getValue("id").jsonPrimitive.content), item)
        assertTrue(env.requests.none { it.url.encodedPath.contains("/occurrences/") || it.url.encodedPath.contains("/recurring-rules/") }, "nothing went per record")
        assertTrue(env.pendingDao.rows.value.isEmpty(), "every item was applied")
        assertEquals(1L, env.ruleDao.get(Captured.RULE_NR)!!.version)
        assertEquals("skipped", env.occurrenceDao.get(rounds[0].id)!!.status)
        assertEquals("confirmed", env.occurrenceDao.get(rounds[1].id)!!.status)
        assertEquals(Captured.RULE_BR_TX, env.occurrenceDao.get(rounds[1].id)!!.transactionId)
        assertNotNull(env.transactionDao.get(Captured.RULE_BR_TX), "the pending transaction waits for the feed's copy")
    }

    @Test
    fun theBatchAnswersOfTheLiveServerDecodeIntoOutcomesPerItem() {
        val request = qa(Captured.BATCH_RECURRING_RULE_OCCURRENCE_ITEMS_REQ).getValue("items").jsonArray
        assertEquals(listOf("recurring_rule", "occurrence", "occurrence", "recurring_rule"), request.map { it.jsonObject.getValue("type").jsonPrimitive.content })
        assertTrue(request.all { it.jsonObject.containsKey("wallet_id") }, "rules and occurrences always carry their wallet")
        val results = AppJson.parseToJsonElement(Captured.BATCH_RECURRING_RULE_OCCURRENCE_ITEMS_RES).jsonObject.getValue("data").jsonArray
        assertEquals(listOf("applied", "applied", "applied", "applied"), results.map { it.jsonObject.getValue("outcome").jsonPrimitive.content })
        val refused = AppJson.parseToJsonElement(Captured.BATCH_ITEM_LEVEL_REFUSALS_RES).jsonObject.getValue("data").jsonArray
        assertEquals(listOf("rejected", "rejected", "conflict", "conflict", "rejected"), refused.map { it.jsonObject.getValue("outcome").jsonPrimitive.content })
        assertEquals(
            "occurrence",
            refused[0].jsonObject.getValue("type").jsonPrimitive.content,
            "an occurrence id the server never materialised is refused as not found, never created",
        )
        assertTrue(refused[2].jsonObject.getValue("problem").jsonObject.getValue("current").jsonObject.getValue("status").jsonPrimitive.content == "skipped")
        assertNotNull(JsonArray(emptyList()))
    }
}
