package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.domain.model.CatchUpOutcome
import com.fares.saveme.feature.recurring.ruleBody
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.transactions.txAccount
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FR-028, research R-11: "Check for new dates" asks the server once, then reads the wallet; never queued, never automatic. */
class RecurringCatchUpTest {

    private var answer: () -> Pair<Int, String> = { 200 to ruleBody() }

    private suspend fun env(scope: kotlinx.coroutines.CoroutineScope, role: String = "owner", rule: String = ruleJson(version = 2), online: Boolean = true): RecurringServiceEnv {
        val env = RecurringServiceEnv(scope) {
            if (!online) throw kotlinx.io.IOException("offline")
            val (status, body) = answer()
            jsonResponse(status, body)
        }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1", role = role))
        env.seedRule(rule)
        return env
    }

    private fun RecurringServiceEnv.calls() = requests.filter { it.method == HttpMethod.Post && it.url.encodedPath.endsWith("/materialise") }

    @Test
    fun onlineItAsksOnceThenReadsTheWalletForced() = runTest {
        val env = env(backgroundScope)
        assertEquals(CatchUpOutcome.Done, env.service.catchUp("r1"))
        assertEquals(1, env.calls().size)
        assertTrue(env.calls().single().url.encodedPath.endsWith("/wallets/w1/recurring-rules/r1/materialise"))
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.Wallet("w1") to true), env.runner.refreshes)
        assertEquals(emptyMap(), env.pendingDao.rows.value, "nothing is queued")
    }

    @Test
    fun aVanishedOrChangedRuleStillGetsTheFollowUpRead() = runTest {
        for ((status, code) in listOf(404 to "NOT_FOUND", 409 to "VERSION_CONFLICT")) {
            val env = env(backgroundScope)
            answer = { status to problemBody(status, code) }
            assertEquals(CatchUpOutcome.Done, env.service.catchUp("r1"), "$status")
            assertEquals(1, env.runner.refreshes.size)
        }
    }

    @Test
    fun offlineNeedsAConnectionAndReadsNothing() = runTest {
        val env = env(backgroundScope, online = false)
        assertEquals(CatchUpOutcome.NeedsConnection, env.service.catchUp("r1"))
        assertTrue(env.runner.refreshes.isEmpty())
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun theFollowUpReadCanFindNoConnection() = runTest {
        val env = env(backgroundScope)
        env.runner.outcome = ReadOutcome.Offline
        assertEquals(CatchUpOutcome.NeedsConnection, env.service.catchUp("r1"))
    }

    @Test
    fun theAnswerIsChosenByCode() = runTest {
        val forbidden = env(backgroundScope)
        answer = { 403 to problemBody(403, "FORBIDDEN") }
        assertEquals(CatchUpOutcome.NotAllowed, forbidden.service.catchUp("r1"))
        answer = { 403 to problemBody(403, "EMAIL_NOT_VERIFIED") }
        assertEquals(CatchUpOutcome.NotVerified, env(backgroundScope).service.catchUp("r1"))
        answer = { 422 to problemBody(422, "VALIDATION_FAILED") }
        assertEquals(CatchUpOutcome.Failed("req-42"), env(backgroundScope).service.catchUp("r1"))
        assertTrue(forbidden.runner.refreshes.isEmpty())
    }

    @Test
    fun anEndedRuleAViewerAnUnsentRuleAndAnUnverifiedAddressAreNotOffered() = runTest {
        val ended = env(backgroundScope, rule = ruleJson(status = "ended"))
        assertEquals(CatchUpOutcome.NotAllowed, ended.service.catchUp("r1"))
        val viewer = env(backgroundScope, role = "viewer")
        assertEquals(CatchUpOutcome.NotAllowed, viewer.service.catchUp("r1"))
        val unverified = env(backgroundScope)
        unverified.authState.value = AuthState.SignedIn(txAccount.copy(emailVerified = false))
        assertEquals(CatchUpOutcome.NotVerified, unverified.service.catchUp("r1"))
        assertEquals(CatchUpOutcome.NotAllowed, ended.service.catchUp("nope"))
        assertTrue((ended.calls() + viewer.calls() + unverified.calls()).isEmpty())
    }

    @Test
    fun theServiceNeverAsksOnItsOwn() = runTest {
        val env = env(backgroundScope)
        env.service.observeRules(null)
        env.service.save("r1", com.fares.saveme.feature.recurring.ruleDraft(), 2)
        assertTrue(env.calls().isEmpty())
    }
}
