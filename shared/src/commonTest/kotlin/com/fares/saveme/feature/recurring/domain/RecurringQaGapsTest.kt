package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.account.jsonBody
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.confirmDraft
import com.fares.saveme.feature.recurring.domain.model.OccurrenceState
import com.fares.saveme.feature.recurring.domain.model.RuleResult
import com.fares.saveme.feature.recurring.occurrenceBody
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleBody
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Host QA gap closers (specs/010-recurring/qa-report.md): behaviours the first test pass left without their own proof. */
class RecurringQaGapsTest {

    /** FR-026: a confirmed occurrence stays confirmed when its transaction is gone (deleted through the transaction editor). */
    @Test
    fun aConfirmedOccurrenceWhoseTransactionWasDeletedStillShowsAsConfirmed() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seedRule(ruleJson(id = "r1"))
        env.seedOccurrence(occurrenceJson(version = 4, status = "confirmed", transactionId = "t-deleted"))

        assertNull(env.transactionDao.get("t-deleted"), "no transaction row is on the phone")
        val view = env.service.observeOccurrence("o1").first()!!
        assertEquals(OccurrenceState.Confirmed, view.state)
        assertEquals("t-deleted", view.occurrence.transactionId)
        assertTrue(env.service.observeOccurrences("w1", null).first().history.any { it.id == "o1" })
    }

    /** Spec 010 edge case "Process killed after confirming offline": the queue and the pending transaction are rows, so a new engine sends them. */
    @Test
    fun aConfirmationMadeOfflineIsSentByAnEngineStartedAfterTheProcessWasKilled() = runTest {
        var online = false
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf("tc1")) { request ->
            if (!online) throw kotlinx.io.IOException("offline")
            assertEquals(HttpMethod.Put, request.method)
            jsonResponse(200, occurrenceBody(occurrenceJson(version = 4, status = "confirmed", transactionId = "tc1")))
        }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seedRule(ruleJson(id = "r1"))
        env.seedOccurrence(occurrenceJson(version = 3))
        env.service.confirm("o1", confirmDraft(amount = 480_000))
        env.engine.drain()
        assertEquals(1, env.pendingDao.rows.value.size, "still waiting: no connection")
        assertNotNull(env.transactionDao.get("tc1"))

        online = true
        val restarted = SyncEngine(
            env.pendingDao, env.scopeDao, listOf(env.ruleHandler, env.occurrenceHandler), env.scheduler,
            currentAccountId = { "u1" }, clock = env.clock,
        )
        restarted.drain()

        val put = env.requests.last { it.method == HttpMethod.Put }
        assertEquals("tc1", put.jsonBody().jsonObject.getValue("transaction_id").jsonPrimitive.content, "same transaction id after the restart")
        assertTrue(env.pendingDao.rows.value.isEmpty())
        assertEquals("confirmed", env.occurrenceDao.get("o1")!!.status)
        assertEquals(1, env.transactionDao.getAll().count { it.id == "tc1" }, "one transaction, never two")
    }

    /** FR-039: while an edit of the rule waits, its occurrences keep the values the phone holds and the rule says so. */
    @Test
    fun whileARuleEditWaitsItsOccurrencesKeepTheirValuesAndTheRuleShowsTheWaitingChange() = runTest {
        val env = RecurringServiceEnv(backgroundScope)
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seedRule(ruleJson(id = "r1", version = 2))
        env.seedOccurrence(occurrenceJson(id = "o1", version = 1, amount = 450_000))
        val shown = env.service.observeRule("r1").first()!!
        assertEquals(RuleResult.Saved, env.service.save("r1", shown.shown.copy(amountMinor = 500_000), 2))

        val rule = env.service.observeRule("r1").first()!!
        assertTrue(rule.hasPendingChange)
        assertEquals(500_000, rule.shown.amountMinor, "the rule shows what was typed")
        val occurrence = env.service.observeOccurrence("o1").first()!!
        assertEquals(450_000, occurrence.shownAmountMinor, "the occurrence keeps the value the phone holds until the server recomputes it")
    }

    /** FR-029/SC-003: a rule's server answer with a body is stored once even when the same answer arrives twice (lost answer, resend). */
    @Test
    fun aResentRuleSaveAnsweredTwiceStoresTheRuleOnce() = runTest {
        val env = RecurringServiceEnv(backgroundScope, newIds = listOf("r-new")) { jsonResponse(200, ruleBody(ruleJson(id = "r-new", version = 1))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.service.create("w1", com.fares.saveme.feature.recurring.ruleDraft(anchorDay = 1))
        env.engine.drain()
        env.engine.drain()
        assertEquals(1, env.ruleDao.rows.value.size)
        assertEquals(1L, env.ruleDao.get("r-new")!!.version)
    }
}
