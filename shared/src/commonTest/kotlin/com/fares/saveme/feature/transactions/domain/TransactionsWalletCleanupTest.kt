package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A removed wallet takes its transactions and their waiting changes with it (FR-031, research R-19). */
class TransactionsWalletCleanupTest {

    @Test
    fun rowsTouchingNoRemainingWalletGoWithTheirWaitingChangesAndATransferToAWalletOnThePhoneStays() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w2"))
        env.seed(transactionJson(id = "plain", walletId = "w1", version = 2))
        env.seed(transactionJson(id = "out", walletId = "w1", kind = "transfer", categoryId = null, counterWalletId = "w2", version = 2))
        env.seed(transactionJson(id = "other", walletId = "w2", version = 2))
        env.engine.enqueue("transaction", "plain", SyncScope(ScopeKind.Wallet, "w1"), 2, """{"kind":"expense","date":"2026-10-01","amountMinor":1,"currency":"EGP"}""")
        // The wallet's own row is already gone, as the wallet repository removes it before telling participants.
        env.cleanup.onWalletDeleted("w1")
        runCurrent()
        assertNull(env.transactionDao.get("plain"))
        assertNotNull(env.transactionDao.get("out"))
        assertNotNull(env.transactionDao.get("other"))
        assertNull(env.pendingDao.get("transaction", "plain"))
    }

    @Test
    fun aTransferFromAWalletThatLeftIsKeptWhileTheDestinationIsOnThePhoneAndGoesWhenItLeavesToo() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(id = "in", walletId = "w9", kind = "transfer", categoryId = null, counterWalletId = "w1", version = 2))
        env.cleanup.onWalletDeleted("w9")
        runCurrent()
        assertNotNull(env.transactionDao.get("in"))
        env.walletDao.delete("w1")
        env.cleanup.onWalletDeleted("w1")
        runCurrent()
        assertEquals(emptySet(), env.transactionDao.rows.value.keys)
    }
}
