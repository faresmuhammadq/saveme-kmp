package com.fares.saveme.feature.recurring.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.recurring.RecurringServiceEnv
import com.fares.saveme.feature.recurring.confirmDraft
import com.fares.saveme.feature.recurring.data.dto.OccurrencePayload
import com.fares.saveme.feature.recurring.domain.model.RecurringRecord
import com.fares.saveme.feature.recurring.domain.model.RecurringSyncState
import com.fares.saveme.feature.recurring.occurrenceBody
import com.fares.saveme.feature.recurring.occurrenceJson
import com.fares.saveme.feature.recurring.ruleJson
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-024, FR-025, SC-004: the engine over fakes meets the server's answers to a confirmation. */
class OccurrenceClashTest {

    private var answer: () -> Pair<Int, String> = { 200 to occurrenceBody(occurrenceJson(version = 4, status = "confirmed", transactionId = "tc1")) }

    private suspend fun env(scope: kotlinx.coroutines.CoroutineScope): RecurringServiceEnv {
        val env = RecurringServiceEnv(scope, newIds = listOf("tc1", "tc2")) {
            val (status, body) = answer()
            jsonResponse(status, body)
        }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seedRule(ruleJson(id = "r1"))
        env.seedOccurrence(occurrenceJson(version = 3))
        return env
    }

    private fun conflict(current: String) = 409 to problemBody(409, "VERSION_CONFLICT", ""","current":$current""")

    private fun RecurringServiceEnv.puts(): List<HttpRequestData> = requests.filter { it.method == HttpMethod.Put && "/occurrences/" in it.url.encodedPath }

    private fun HttpRequestData.json() = (body as TextContent).text

    @Test
    fun aClashWithAnOccurrenceConfirmedElsewhereIsAcceptedWithoutAskingAndNeverResent() = runTest {
        val env = env(backgroundScope)
        answer = { conflict(occurrenceJson(version = 5, status = "confirmed", transactionId = "other-tx")) }
        env.service.confirm("o1", confirmDraft())
        env.engine.drain()
        env.engine.drain()

        assertNull(env.engine.get("occurrence", "o1"), "no choice is left")
        assertNull(env.transactionDao.get("tc1"), "the pending transaction goes")
        val view = env.service.observeOccurrence("o1").first()!!
        assertEquals("confirmed", view.occurrence.statusWire)
        assertEquals("other-tx", view.occurrence.transactionId)
        assertEquals("already_confirmed", view.notice)
        assertEquals(1, env.puts().size, "never re-sent under a new id")
    }

    @Test
    fun aClashWithAnOccurrenceTheOtherDeviceMerelyChangedAsksAndKeepMineResendsTheSameTransactionId() = runTest {
        val env = env(backgroundScope)
        answer = { conflict(occurrenceJson(version = 5, amount = 400_000)) }
        env.service.confirm("o1", confirmDraft())
        env.engine.drain()
        val clash = env.service.observeOccurrence("o1").first()!!
        assertIs<RecurringSyncState.Clash>(clash.syncState)
        assertNotNull(env.transactionDao.get("tc1"), "the pending transaction waits for the choice")

        answer = { 200 to occurrenceBody(occurrenceJson(version = 6, status = "confirmed", transactionId = "tc1")) }
        env.service.keepMine(RecurringRecord.Occurrence, "o1")
        env.engine.drain()

        val sent = env.puts()
        assertEquals(2, sent.size)
        assertTrue("\"transaction_id\":\"tc1\"" in sent.last().json(), sent.last().json())
        assertTrue("\"version\":5" in sent.last().json(), sent.last().json())
        assertNull(env.engine.get("occurrence", "o1"))
        assertEquals("confirmed", env.occurrenceDao.get("o1")!!.status)
        assertNotNull(env.transactionDao.get("tc1"), "the feed replaces it with the server's copy")
    }

    @Test
    fun useTheOtherDevicesTakesItsCopyAndDropsThePendingTransaction() = runTest {
        val env = env(backgroundScope)
        answer = { conflict(occurrenceJson(version = 5, amount = 400_000)) }
        env.service.confirm("o1", confirmDraft())
        env.engine.drain()
        env.service.useTheirs(RecurringRecord.Occurrence, "o1")
        assertNull(env.engine.get("occurrence", "o1"))
        assertNull(env.transactionDao.get("tc1"))
        assertEquals(400_000, env.occurrenceDao.get("o1")!!.amountMinor)
    }

    @Test
    fun aDeletedOccurrenceOffersOnlyTheDeletionToBeAccepted() = runTest {
        val env = env(backgroundScope)
        answer = { conflict(occurrenceJson(version = 6, deletedAt = "2026-10-03T00:00:00Z")) }
        env.service.confirm("o1", confirmDraft())
        env.engine.drain()
        val view = env.service.observeOccurrence("o1").first()!!
        assertEquals(RecurringSyncState.Clash(deletedElsewhere = true, mineIsDeletion = false), view.syncState)
        env.service.keepMine(RecurringRecord.Occurrence, "o1")
        assertEquals(ChangeState.Clash, env.engine.get("occurrence", "o1")!!.state, "keep mine is not offered for a final deletion")

        env.service.useTheirs(RecurringRecord.Occurrence, "o1")
        assertNull(env.occurrenceDao.get("o1"))
        assertNull(env.transactionDao.get("tc1"))
    }

    @Test
    fun aRefusedConfirmationIsNotSavedAndDiscardingItDropsTheTransaction() = runTest {
        val env = env(backgroundScope)
        answer = { 422 to problemBody(422, "VALIDATION_FAILED", ""","errors":[{"pointer":"/status","code":"CURRENCY_MISMATCH"}]""") }
        env.service.confirm("o1", confirmDraft())
        env.engine.drain()
        val refused = assertIs<RecurringSyncState.Refused>(env.service.observeOccurrence("o1").first()!!.syncState)
        assertEquals("VALIDATION_FAILED", refused.code)
        assertEquals(mapOf("status" to "CURRENCY_MISMATCH"), refused.fields)
        assertNotNull(env.transactionDao.get("tc1"))

        env.service.discard(RecurringRecord.Occurrence, "o1")
        assertNull(env.engine.get("occurrence", "o1"))
        assertNull(env.transactionDao.get("tc1"))
    }

    @Test
    fun tryAgainAfterARefusalQueuesTheSameWriteAgain() = runTest {
        val env = env(backgroundScope)
        answer = { 403 to problemBody(403, "FORBIDDEN") }
        env.service.confirm("o1", confirmDraft())
        env.engine.drain()
        assertEquals(ChangeState.Refused, env.engine.get("occurrence", "o1")!!.state)
        env.service.tryAgain(RecurringRecord.Occurrence, "o1")
        val change = env.engine.get("occurrence", "o1")!!
        assertEquals(ChangeState.Waiting, change.state)
        assertEquals("tc1", AppJson.decodeFromString(OccurrencePayload.serializer(), change.payloadJson).transactionId)
    }

    @Test
    fun theBalanceCountsTheConfirmationWhileItWaitsAndTheSentAnswerDoesNotMoveIt() = runTest {
        val env = env(backgroundScope)
        val before = env.wallets.observeWallet("w1").first()!!.displayedBalanceMinor
        env.service.confirm("o1", confirmDraft(amount = 450_000))
        val waiting = env.wallets.observeWallet("w1").first()!!.displayedBalanceMinor
        assertEquals(before - 450_000, waiting)
        env.engine.drain()
        assertNull(env.engine.get("occurrence", "o1"))
        assertEquals(waiting, env.wallets.observeWallet("w1").first()!!.displayedBalanceMinor, "accepted: the row stays until the feed brings the transaction")
    }
}
