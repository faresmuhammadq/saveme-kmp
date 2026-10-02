package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ReadOutcome
import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.awaitReal
import com.fares.saveme.feature.transactions.data.TransactionFeedApplier
import com.fares.saveme.feature.transactions.dto
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The balance carry settles per wallet, when that wallet's feed read completes (spec 009 R-09, FR-013, SC-005). */
class BalanceReconcilerFeedTest {

    private fun TransactionTestEnv.carried() = transactionDao.rows.value.values.filter { it.carryConfirmedAt != null }

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = TransactionTestEnv(scope) { jsonResponse(200, walletBody()) }

    /** The wallet read: the wallet feed re-sends the wallet record with the balance that includes the change. */
    private fun TransactionTestEnv.readsWallet(id: String, balance: Long) {
        runner.onRefresh = { target, _ -> if (target == ReadTarget.Wallet(id)) seedWallet(walletJson(id = id, balance = balance, version = 5)) }
    }

    @Test
    fun afterAnAcceptedSendTheReconcilerAsksTheRunnerToReadTheTouchedWalletOnly() = runTest {
        val env = env(backgroundScope)
        env.seedWallet(walletJson(id = "w1", balance = 25_000))
        env.repository.storeServerCopy(dto(transactionJson(version = 2, amount = 5_000)), own = true)
        assertTrue(env.reconciler.settle())
        assertEquals(listOf<Pair<ReadTarget, Boolean>>(ReadTarget.Wallet("w1") to true), env.runner.refreshes)
    }

    @Test
    fun aCompletedWalletReadClearsThatWalletsCarryAndTheBalanceNeverJumps() = runTest {
        val env = env(backgroundScope)
        env.seedWallet(walletJson(id = "w1", balance = 25_000))
        env.repository.storeServerCopy(dto(transactionJson(version = 2, amount = 5_000)), own = true)
        assertEquals(20_000, env.wallets.observeWallet("w1").first()!!.displayedBalanceMinor, "server balance plus the carry")
        env.readsWallet("w1", balance = 20_000)

        assertTrue(env.reconciler.settle())

        assertEquals(emptyList(), env.carried())
        assertEquals(0, env.carryFor("w1"))
        assertEquals(20_000, env.wallets.observeWallet("w1").first()!!.displayedBalanceMinor, "the server's balance, nothing left over")
    }

    @Test
    fun aFailedOrOfflineReadKeepsTheCarryAndTheDisplayedBalance() = runTest {
        val env = env(backgroundScope)
        env.seedWallet(walletJson(id = "w1", balance = 25_000))
        env.repository.storeServerCopy(dto(transactionJson(version = 2, amount = 5_000)), own = true)
        for (outcome in listOf(ReadOutcome.Failed, ReadOutcome.Offline)) {
            env.runner.outcome = outcome
            assertFalse(env.reconciler.settle())
        }
        assertEquals(1, env.carried().size)
        assertEquals(20_000, env.wallets.observeWallet("w1").first()!!.displayedBalanceMinor)
    }

    @Test
    fun aCarryConfirmedAfterTheReadStartedSurvivesIt() = runTest {
        val env = env(backgroundScope)
        env.seedWallet(walletJson(id = "w1", balance = 25_000))
        env.repository.storeServerCopy(dto(transactionJson(version = 2, amount = 5_000)), own = true)
        env.runner.onRefresh = { _, _ ->
            // Confirmed while the read is in flight: that read may predate it.
            env.clock.now = Instant.parse("2026-10-02T10:00:05Z")
            env.repository.storeServerCopy(dto(transactionJson(id = "late", version = 1, amount = 700)), own = true)
        }
        assertTrue(env.reconciler.settle())
        assertEquals(listOf("late"), env.carried().map { it.id })
    }

    @Test
    fun aTransfersTwoWalletsAreReadOneAtATimeAndAFailedOneKeepsItsPart() = runTest {
        val env = env(backgroundScope)
        env.seedWallet(walletJson(id = "w1", balance = 25_000))
        env.seedWallet(walletJson(id = "w2", balance = 0))
        env.repository.storeServerCopy(
            dto(transactionJson(id = "tr", kind = "transfer", categoryId = null, counterWalletId = "w2", amount = 5_000)), own = true,
        )
        env.runner.onRefresh = { target, _ ->
            // The destination's read is cut off.
            env.runner.outcome = if (target == ReadTarget.Wallet("w2")) ReadOutcome.Offline else ReadOutcome.Done
        }
        assertFalse(env.reconciler.settle())
        assertEquals(listOf<ReadTarget>(ReadTarget.Wallet("w1"), ReadTarget.Wallet("w2")), env.runner.refreshes.map { it.first })
        assertEquals(0, env.carryFor("w1"))
        assertEquals(5_000, env.carryFor("w2"), "the destination keeps its part until its own read completes")
        assertEquals(listOf("tr"), env.carried().map { it.id })

        env.runner.onRefresh = { _, _ -> }
        env.runner.outcome = ReadOutcome.Done
        assertTrue(env.reconciler.settle())
        assertEquals(emptyList(), env.carried())
    }

    @Test
    fun aTombstoneRowIsPurgedWhenItsCarryClears() = runTest {
        val env = env(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 1))
        env.repository.storeServerCopy(dto(transactionJson(version = 2, deletedAt = "2026-10-02T10:00:00Z")), own = true)
        assertEquals(1, env.carried().size)
        assertTrue(env.reconciler.settle())
        assertEquals(emptySet(), env.transactionDao.rows.value.keys)
    }

    @Test
    fun theBalanceNeverJumpsAcrossUploadThenAPageOfTheTombstoneThenAPageOfTheWalletRecord() = runTest {
        val env = env(backgroundScope)
        env.seedWallet(walletJson(id = "w1", balance = 25_000))
        env.seed(transactionJson(version = 1, amount = 5_000))
        val balance = { kotlinx.coroutines.runBlocking { env.wallets.observeWallet("w1").first()!!.displayedBalanceMinor } }
        // The phone deleted it: the answer is a tombstone that still carries the effect.
        env.repository.storeServerCopy(dto(transactionJson(version = 2, amount = 5_000, deletedAt = "2026-10-02T10:00:00Z")), own = true)
        assertEquals(30_000, balance())

        // Page 1 of the wallet feed brings the same tombstone: nothing moves.
        val applier = TransactionFeedApplier(env.repository)
        val scope = SyncScope(ScopeKind.Wallet, "w1")
        applier.apply(scope, listOf<JsonElement>(AppJson.parseToJsonElement(transactionJson(version = 2, amount = 5_000, deletedAt = "2026-10-02T10:00:00Z"))))
        assertEquals(30_000, balance(), "the hidden row keeps its carry; the tombstone from the feed adds none")
        assertEquals(1, env.carried().size)

        // The read completes: the wallet record carries the new balance and the carry clears with it.
        env.readsWallet("w1", balance = 30_000)
        assertTrue(env.reconciler.settle())
        assertEquals(30_000, balance())
        assertEquals(emptySet(), env.transactionDao.rows.value.keys)
    }

    @Test
    fun startedItWatchesTheCarryAndSettlesAfterTheDebounce() = runTest {
        val env = env(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.reconciler.start()
        env.repository.storeServerCopy(dto(transactionJson(version = 2)), own = true)
        awaitReal { env.transactionDao.rows.value.values.none { it.carryConfirmedAt != null } }
        assertEquals(emptyList(), env.carried())
    }

    @Test
    fun aRequestedWalletReadNamesTheWalletsAndNothingElseGetsReadForOne() = runTest {
        val env = env(backgroundScope)
        env.reconciler.requestWalletRead("w1", "w2", "w1")
        awaitReal { env.runner.refreshes.size == 2 }
        assertEquals(listOf<ReadTarget>(ReadTarget.Wallet("w1"), ReadTarget.Wallet("w2")), env.runner.refreshes.map { it.first })
        env.reconciler.requestWalletRead()
        awaitReal { env.runner.refreshes.size == 3 }
        assertEquals(ReadTarget.Everything, env.runner.refreshes.last().first)
    }
}
