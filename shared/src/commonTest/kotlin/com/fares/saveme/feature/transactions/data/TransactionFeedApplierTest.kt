package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.dto
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A wallet's transactions from its feed (spec 009 FR-004, FR-005, FR-007, FR-012). */
class TransactionFeedApplierTest {

    private val w1 = SyncScope(ScopeKind.Wallet, "w1")
    private val w2 = SyncScope(ScopeKind.Wallet, "w2")

    private fun json(text: String): JsonElement = AppJson.parseToJsonElement(text)

    @Test
    fun aRowIsAppliedWhenItsVersionIsTheSameOrNewerAndIgnoredWhenLower() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        val applier = TransactionFeedApplier(env.repository)
        applier.apply(w1, listOf(json(transactionJson(version = 3, amount = 1_000))))
        applier.apply(w1, listOf(json(transactionJson(version = 2, amount = 9_999))))
        assertEquals(1_000L, env.transactionDao.get("t1")!!.amountMinor)
        applier.apply(w1, listOf(json(transactionJson(version = 3, amount = 1_100))))
        assertEquals(1_100L, env.transactionDao.get("t1")!!.amountMinor)
        applier.apply(w1, listOf(json(transactionJson(version = 4, amount = 1_200))))
        assertEquals(1_200L, env.transactionDao.get("t1")!!.amountMinor)
        assertEquals("u1", env.transactionDao.get("t1")!!.accountId)
    }

    @Test
    fun aTransferFromEitherWalletsFeedIsStoredOnceAndListedUnderBoth() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seedWallet(walletJson(id = "w2"))
        val transfer = transactionJson(id = "tr", walletId = "w1", kind = "transfer", categoryId = null, counterWalletId = "w2", amount = 5_000)
        val applier = TransactionFeedApplier(env.repository)
        applier.apply(w1, listOf(json(transfer)))
        applier.apply(w2, listOf(json(transfer))) // the identical record on the destination's feed
        assertEquals(1, env.transactionDao.getAll().count { it.id == "tr" })
        assertEquals(listOf("tr"), env.repository.observeForWallet("u1", "w1").first().map { it.id })
        assertEquals(listOf("tr"), env.repository.observeForWallet("u1", "w2").first().map { it.id })
        assertEquals(0, env.carryFor("w1") + env.carryFor("w2"), "a feed row never adds a carry")
    }

    @Test
    fun aTombstoneRemovesTheRowUnlessAChangeWaits() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seed(transactionJson(id = "gone", version = 2))
        env.seed(transactionJson(id = "queued", version = 2))
        env.engine.enqueue("transaction", "queued", w1, 2, "{}")
        TransactionFeedApplier(env.repository).apply(
            w1,
            listOf(json(transactionJson(id = "gone", version = 3, deletedAt = "2026-10-02T00:00:00Z")), json(transactionJson(id = "queued", version = 3, deletedAt = "2026-10-02T00:00:00Z"))),
        )
        assertNull(env.transactionDao.get("gone"))
        val queued = assertNotNull(env.transactionDao.get("queued"))
        assertEquals(2L, queued.version, "it stays as it was, so the waiting change meets the deletion as a clash")
        assertNotNull(env.engine.get("transaction", "queued"))
    }

    @Test
    fun aTombstoneForARowWithAnUnsettledCarryKeepsItHiddenWithoutAddingToTheCarry() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seed(transactionJson(version = 1, amount = 4_550))
        env.repository.storeServerCopy(dto(transactionJson(version = 2, amount = 4_550, deletedAt = "2026-10-02T10:00:00Z")), own = true)
        val carryBefore = env.carryFor("w1")
        TransactionFeedApplier(env.repository).apply(w1, listOf(json(transactionJson(version = 2, amount = 4_550, deletedAt = "2026-10-02T10:00:00Z"))))
        val row = assertNotNull(env.transactionDao.get("t1"))
        assertNotNull(row.deletedAt)
        assertNotNull(row.carryConfirmedAt)
        assertEquals(carryBefore, env.carryFor("w1"))
    }

    @Test
    fun aFeedRowReplacingARowWithACarryKeepsTheCarry() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.repository.storeServerCopy(dto(transactionJson(version = 1, amount = 4_550)), own = true)
        TransactionFeedApplier(env.repository).apply(w1, listOf(json(transactionJson(version = 1, amount = 4_550))))
        assertEquals(-4_550, env.carryFor("w1"))
    }

    @Test
    fun aSweepOfAWalletRemovesOnlyUnseenSyncedRowsTouchingItAndKeepsQueuedPlaceholderAndCarriedOnes() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seed(transactionJson(id = "seen", version = 2))
        env.seed(transactionJson(id = "gone", version = 2))
        env.seed(transactionJson(id = "queued", version = 2))
        env.seed(transactionJson(id = "carried", version = 2))
        env.seed(transactionJson(id = "elsewhere", walletId = "w2", version = 2))
        env.seed(transactionJson(id = "into", walletId = "w2", kind = "transfer", categoryId = null, counterWalletId = "w1", version = 2))
        env.repository.insertPlaceholder(NewTransaction("fresh", "u1", "w1", null, "expense", "2026-10-02", 700, "EGP", "c1", null, null, null, false, "u1"))
        env.engine.enqueue("transaction", "queued", w1, 2, "{}")
        env.repository.storeServerCopy(dto(transactionJson(id = "carried", version = 3)), own = true)

        TransactionFeedApplier(env.repository).sweep(w1, setOf("seen"))

        assertEquals(setOf("seen", "queued", "carried", "elsewhere", "fresh"), env.transactionDao.rows.value.keys, "the transfer into w1 is unseen and goes; other wallets' rows stay")
        assertTrue("into" !in env.transactionDao.rows.value.keys)
    }
}
