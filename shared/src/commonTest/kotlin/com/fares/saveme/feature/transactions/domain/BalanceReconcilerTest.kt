package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.awaitReal
import com.fares.saveme.feature.transactions.dto
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.pageOf
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The balance carry and its settling (FR-017, SC-004, research R-09). */
class BalanceReconcilerTest {

    private suspend fun TransactionTestEnv.carried() = transactionDao.rows.value.values.filter { it.carryConfirmedAt != null }

    @Test
    fun aCompletedReadClearsTheCarryItCovered() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, pageOf(listOf(walletJson(id = "w1", balance = 20_000)))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1", balance = 25_000))
        env.repository.storeServerCopy(dto(transactionJson(version = 2, amount = 5_000)), own = true)
        assertEquals(-5_000, env.carryFor("w1"))
        assertTrue(env.reconciler.settle())
        assertEquals(emptyList(), env.carried())
        assertEquals(0, env.carryFor("w1"))
        // The displayed balance is the server's again, with nothing left over: no jump (SC-004).
        assertEquals(20_000, env.wallets.observeWallet("w1").first()!!.displayedBalanceMinor)
    }

    @Test
    fun aFailedOrOfflineReadKeepsTheCarry() = runTest {
        var answer = 503
        val env = TransactionTestEnv(backgroundScope) {
            if (answer == 0) throw kotlinx.io.IOException("offline") else jsonResponse(answer, problemBody(answer, "SERVICE_UNAVAILABLE"))
        }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1", balance = 25_000))
        env.repository.storeServerCopy(dto(transactionJson(version = 2, amount = 5_000)), own = true)
        assertFalse(env.reconciler.settle())
        answer = 0
        assertFalse(env.reconciler.settle())
        assertEquals(1, env.carried().size)
        // Still 250.00 - 50.00: the balance does not jump back while the read keeps failing.
        assertEquals(20_000, env.wallets.observeWallet("w1").first()!!.displayedBalanceMinor)
    }

    @Test
    fun aCarryConfirmedAfterTheReadStartedSurvivesIt() = runTest {
        lateinit var env: TransactionTestEnv
        env = TransactionTestEnv(backgroundScope) {
            // The change is confirmed while the wallet read is in flight: that read may predate it.
            env.clock.now = Instant.parse("2026-10-02T10:00:05Z")
            env.repository.storeServerCopy(dto(transactionJson(id = "late", version = 1, amount = 700)), own = true)
            jsonResponse(200, pageOf(listOf(walletJson(id = "w1", balance = 25_000))))
        }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1", balance = 25_000))
        env.repository.storeServerCopy(dto(transactionJson(version = 2, amount = 5_000)), own = true)
        assertTrue(env.reconciler.settle())
        assertEquals(listOf("late"), env.carried().map { it.id })
    }

    @Test
    fun aTombstoneRowIsPurgedWhenItsCarryClears() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, pageOf(listOf(walletJson(id = "w1")))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 1))
        env.repository.storeServerCopy(dto(transactionJson(version = 2, deletedAt = "2026-10-02T10:00:00Z")), own = true)
        assertEquals(1, env.carried().size)
        assertTrue(env.reconciler.settle())
        assertEquals(emptySet(), env.transactionDao.rows.value.keys)
    }

    @Test
    fun startedItWatchesTheCarryAndSettlesAfterTheDebounce() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, pageOf(listOf(walletJson(id = "w1")))) }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.reconciler.start()
        env.repository.storeServerCopy(dto(transactionJson(version = 2)), own = true)
        awaitReal { env.transactionDao.rows.value.values.none { it.carryConfirmedAt != null } }
        assertEquals(emptyList(), env.carried())
    }

}
