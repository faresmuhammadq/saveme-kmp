package com.fares.saveme.feature.recurring.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.network.ProblemDetails
import com.fares.saveme.core.network.ProblemError
import com.fares.saveme.core.sync.BatchOp
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.PendingChange
import com.fares.saveme.core.sync.RecordKey
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.recurring.RecurringTestEnv
import com.fares.saveme.feature.recurring.data.dto.RulePayload
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleBody
import com.fares.saveme.feature.recurring.ruleDto
import com.fares.saveme.feature.recurring.ruleJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecurringRuleSyncHandlerTest {
    private val payload = RulePayload(
        preset = "custom", status = "active", kind = "expense", amountMinor = 450_000, amountBasis = "per_occurrence", currency = "USD",
        categoryId = "c1", description = "Rent", frequency = "monthly", interval = 1, anchorDay = 1, startDate = "2026-11-01",
    )

    private fun change(
        base: Long,
        payload: RulePayload = this.payload,
        delete: Boolean = false,
        id: String = "r1",
    ) = PendingChange(
        recordType = "recurring_rule", recordId = id, accountId = "u1", scope = SyncScope(ScopeKind.Wallet, "w1"),
        baseVersion = base, payloadJson = AppJson.encodeToString(RulePayload.serializer(), payload), isDelete = delete,
        restore = false, revision = 0, createdOrder = 1, state = ChangeState.Waiting, attempts = 0, nextAttemptAt = null,
        clashJson = null, refusal = null, updatedAt = Instant.parse("2026-10-02T10:00:00Z"),
    )

    @Test
    fun recordTypeAndNoRestore() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        assertEquals("recurring_rule", env.ruleHandler.recordType)
        assertFalse(env.ruleHandler.canRestore)
    }

    @Test
    fun aNeverSentRuleIsCreatedAtVersionZeroInTheWalletsPresentCurrency() = runTest {
        val env = RecurringTestEnv(backgroundScope) { jsonResponse(201, ruleBody()) }
        env.signedIn()
        assertIs<ApiResult.Success<*>>(env.ruleHandler.send(change(0)))
        val request = env.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertTrue(request.url.encodedPath.endsWith("/wallets/w1/recurring-rules/r1"))
        assertEquals("0", request.jsonBody()["version"].toString())
        assertEquals("\"EGP\"", request.jsonBody()["currency"].toString(), "the wallet's currency, not the payload's")
    }

    @Test
    fun aSaveSendsTheStoredPresetKindDestinationAndCurrency() = runTest {
        val env = RecurringTestEnv(backgroundScope) { jsonResponse(200, ruleBody()) }
        env.signedIn()
        env.seedRule(ruleJson(version = 3, preset = "installment", kind = "transfer", counterWalletId = "w2", currency = "KWD"))
        val edited = payload.copy(preset = "custom", kind = "expense", counterWalletId = null, amountMinor = 500_000)
        env.ruleHandler.send(change(3, edited))
        val body = env.requests.single().jsonBody()
        assertEquals("\"installment\"", body["preset"].toString())
        assertEquals("\"transfer\"", body["kind"].toString())
        assertEquals("\"w2\"", body["counter_wallet_id"].toString())
        assertEquals("\"KWD\"", body["currency"].toString())
        assertEquals("500000", body["amount_minor"].toString())
        assertEquals("3", body["version"].toString())
    }

    @Test
    fun deleteSendsTheBaseVersionAndBatchItemsCarryTheWallet() = runTest {
        val env = RecurringTestEnv(backgroundScope) { jsonResponse(200, ruleBody(ruleJson(version = 4, deletedAt = "2026-10-02T10:00:00Z"))) }
        env.signedIn()
        env.ruleHandler.send(change(3, delete = true))
        val request = env.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("3", request.jsonBody()["version"].toString())

        val upsert = env.ruleHandler.batchItem(change(0))
        assertEquals(BatchOp.Upsert, upsert.op)
        assertEquals("w1", upsert.walletId)
        assertEquals("recurring_rule", upsert.type)
        assertEquals("0", upsert.data.toString().substringAfter("\"version\":").substringBefore("}"))
        val delete = env.ruleHandler.batchItem(change(3, delete = true))
        assertEquals(BatchOp.Delete, delete.op)
        assertEquals("""{"version":3}""", delete.data.toString())
        assertEquals("w1", delete.walletId)
    }

    @Test
    fun prerequisitesAreTheWalletCategoriesDestinationAndAnUntilSettledKeyForASplit() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        val full = payload.copy(counterWalletId = "w2", payoutCategoryId = "c9", followsRuleId = "old")
        assertEquals(
            listOf(
                RecordKey("wallet", "w1"), RecordKey("wallet", "w2"), RecordKey("category", "c1"), RecordKey("category", "c9"),
                RecordKey("recurring_rule", "old", untilSettled = true),
            ),
            env.ruleHandler.prerequisites(change(0, full)),
        )
        assertEquals(listOf(RecordKey("wallet", "w1"), RecordKey("category", "c1")), env.ruleHandler.prerequisites(change(0)))
        assertEquals(emptyList(), env.ruleHandler.prerequisites(change(2, delete = true)))
    }

    @Test
    fun aTombstoneCopyRemovesTheRuleAndItsOccurrencesButKeepsOnesWithAWaitingChange() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        env.seedRule()
        env.seedOccurrence(occurrenceJson(id = "o1"))
        env.seedOccurrence(occurrenceJson(id = "o2", ordinal = 2))
        env.engine.enqueue("occurrence", "o2", SyncScope(ScopeKind.Wallet, "w1"), 1, """{"status":"skipped","date":"2026-12-01","amountMinor":1}""")
        env.ruleHandler.applyServerCopy(AppJson.parseToJsonElement(ruleJson(version = 2, deletedAt = "2026-10-03T00:00:00Z")))
        assertNull(env.ruleDao.get("r1"))
        assertNull(env.occurrenceDao.get("o1"))
        assertNotNull(env.occurrenceDao.get("o2"))
    }

    @Test
    fun discardingAPlaceholderRemovesItAndLaunchesTheDiscardOfRulesThatFollowIt() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        env.rules.putPlaceholder(newRule("old"))
        env.rules.putPlaceholder(newRule("next", follows = "old"))
        env.ruleHandler.onDiscarded("old")
        assertNull(env.ruleDao.get("old"))
        assertEquals(listOf("next"), env.discardedRules)
        env.ruleHandler.onDiscarded("next")
        assertNull(env.ruleDao.get("next"))
    }

    @Test
    fun discardingARuleTheServerHasKeepsItsCopy() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        env.seedRule()
        env.ruleHandler.onDiscarded("r1")
        assertNotNull(env.ruleDao.get("r1"))
    }

    @Test
    fun anAcceptedCopyClearsTheFollowsLink() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        env.rules.putPlaceholder(newRule("next", follows = "old"))
        env.ruleHandler.applySentAnswer(AppJson.parseToJsonElement(ruleJson(id = "next", version = 1)))
        assertNull(env.ruleDao.get("next")!!.followsRuleId)
        assertEquals(1L, env.ruleDao.get("next")!!.version)
    }

    @Test
    fun refusalFieldsMapEveryPointer() = runTest {
        val env = RecurringTestEnv(backgroundScope)
        val pointers = mapOf(
            "/amount_minor" to "amount", "/amount_basis" to "amountBasis", "/category_id" to "category",
            "/payout_category_id" to "payoutCategory", "/counter_wallet_id" to "destination", "/currency" to "currency",
            "/frequency" to "frequency", "/interval" to "interval", "/anchor_day" to "day", "/start_date" to "start",
            "/end_date" to "end", "/occurrence_count" to "count", "/payout_round" to "payoutRound", "/status" to "status",
            "/preset" to "preset", "/kind" to "kind", "/description" to "note", "/memo" to "details", "/counterparty" to "person",
        )
        val problem = ProblemDetails(
            type = "t", title = "x", status = 422, code = "VALIDATION_FAILED",
            errors = pointers.keys.map { ProblemError(pointer = it, code = "OUT_OF_RANGE") } + ProblemError(pointer = "/timezone", code = "UNKNOWN_FIELD"),
        )
        assertEquals(pointers.values.associateWith { "OUT_OF_RANGE" }, env.ruleHandler.refusalFields(problem))
    }

    private fun newRule(id: String, follows: String? = null) = NewRule(
        id = id, accountId = "u1", walletId = "w1", preset = "custom", status = "active", kind = "expense", amountMinor = 1,
        amountBasis = "per_occurrence", currency = "EGP", categoryId = "c1", counterWalletId = null, description = null, memo = null,
        counterparty = null, autoConfirm = false, frequency = "monthly", interval = 1, anchorDay = 1, startDate = "2026-11-01",
        endDate = null, occurrenceCount = null, payoutRound = null, payoutCategoryId = null, followsRuleId = follows,
    )

    @Test
    fun theHandlerAndTheDtoAgreeOnTheCopy() {
        assertEquals("r1", ruleDto(ruleJson()).id)
    }
}
