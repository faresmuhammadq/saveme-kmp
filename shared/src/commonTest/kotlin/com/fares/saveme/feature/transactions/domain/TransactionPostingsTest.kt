package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.NewTransaction
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.dto
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.core.network.AppJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.transactions.transactionBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What unsent transactions do to a wallet's balance (FR-017, FR-029). */
class TransactionPostingsTest {

    private fun payload(amount: Long, kind: String = "expense", date: String = "2026-10-01", counter: String? = null, category: String? = "c1") =
        AppJson.encodeToString(TransactionPayload.serializer(), TransactionPayload(kind, date, amount, "EGP", category, counter))

    private val w1 = SyncScope(ScopeKind.Wallet, "w1")

    @Test
    fun nothingWaitingMeansNoDeltaAndNothingPending() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        assertEquals(0, env.postings.deltaFor("w1").first())
        assertFalse(env.postings.hasPending("w1").first())
    }

    @Test
    fun aWaitingCreateCountsItsWholeEffect() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.repository.insertPlaceholder(NewTransaction("t1", "u1", "w1", null, "expense", "2026-10-01", 4_550, "EGP", "c1", null, null, null, false, "u1"))
        env.engine.enqueue("transaction", "t1", w1, 0, payload(4_550))
        assertEquals(-4_550, env.postings.deltaFor("w1").first())
        assertTrue(env.postings.hasPending("w1").first())
        assertEquals(0, env.postings.deltaFor("w2").first())
        assertFalse(env.postings.hasPending("w2").first())
    }

    @Test
    fun anEditCountsOnlyTheDifferenceAndADateOnlyChangeCountsNothing() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seed(transactionJson(version = 2, amount = 4_000))
        env.engine.enqueue("transaction", "t1", w1, 2, payload(5_500))
        assertEquals(-1_500, env.postings.deltaFor("w1").first())
        env.engine.enqueue("transaction", "t1", w1, 2, payload(4_000, date = "2026-09-01"))
        assertEquals(0, env.postings.deltaFor("w1").first())
        assertTrue(env.postings.hasPending("w1").first())
    }

    @Test
    fun aWaitingDeletionTakesTheEffectOut() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seed(transactionJson(version = 2, amount = 4_000))
        env.engine.enqueueDelete("transaction", "t1", w1, 2)
        assertEquals(4_000, env.postings.deltaFor("w1").first())
    }

    @Test
    fun aTransferMovesBothSidesAndKeepsBothWalletsMarkedPending() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.repository.insertPlaceholder(NewTransaction("t1", "u1", "w1", "w2", "transfer", "2026-10-01", 5_000, "EGP", null, null, null, null, false, "u1"))
        env.engine.enqueue("transaction", "t1", w1, 0, payload(5_000, "transfer", counter = "w2", category = null))
        assertEquals(-5_000, env.postings.deltaFor("w1").first())
        assertEquals(5_000, env.postings.deltaFor("w2").first())
        assertTrue(env.postings.hasPending("w2").first())
    }

    @Test
    fun clashAndRefusedRowsStillCountAndTheCarryJoinsIn() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seed(transactionJson(version = 2, amount = 4_000))
        env.engine.enqueue("transaction", "t1", w1, 2, payload(5_000))
        env.pendingDao.rows.value = env.pendingDao.rows.value.mapValues { it.value.copy(state = ChangeState.Clash.name) }
        assertEquals(-1_000, env.postings.deltaFor("w1").first())
        env.pendingDao.rows.value = env.pendingDao.rows.value.mapValues { it.value.copy(state = ChangeState.Refused.name) }
        assertEquals(-1_000, env.postings.deltaFor("w1").first())
        // Another transaction's synced change still waits for a wallet read.
        env.repository.storeServerCopy(dto(transactionJson(id = "t2", amount = 700, version = 1)), own = true)
        assertEquals(-1_700, env.postings.deltaFor("w1").first())
    }

    @Test
    fun theDeltaDoesNotMoveAcrossTheSendAndTheBalanceNeverJumps() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(201, transactionBody(transactionJson(version = 1, amount = 4_550))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.repository.insertPlaceholder(NewTransaction("t1", "u1", "w1", null, "expense", "2026-10-01", 4_550, "EGP", "c1", null, null, null, false, "u1"))
        env.engine.enqueue("transaction", "t1", w1, 0, payload(4_550))
        val seen = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.postings.deltaFor("w1").toList(seen) }
        env.engine.drain()
        // The row is version 1 with a carry and the queue row is gone: the delta never left -45.50.
        assertEquals(1, env.transactionDao.get("t1")!!.version)
        assertEquals(null, env.pendingDao.get("transaction", "t1"))
        assertEquals(setOf(-4_550L), seen.toSet(), "$seen")
        // A wallet read that included the change settles the carry and the delta is zero again.
        env.repository.clearCarryConfirmedUpTo(kotlin.time.Instant.parse("2027-01-01T00:00:00Z"))
        assertEquals(0, env.postings.deltaFor("w1").first())
    }
}
