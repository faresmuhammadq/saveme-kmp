package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.data.OwnMembershipFeedApplier
import com.fares.saveme.feature.wallets.memberJson
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Losing a wallet through the person's feed uses the wallet-removal path of specs 006 and 008 (spec 009 FR-011, Clarification 1). */
class TransactionsWalletCleanupFeedTest {

    private fun ended(walletId: String) = AppJson.parseToJsonElement(
        memberJson("m-$walletId", "u1", "Me", walletId = walletId).replace("\"deleted_at\":null", "\"deleted_at\":\"2026-10-02T00:00:00Z\""),
    )

    @Test
    fun losingWalletBKeepsATransferFromAThatStillTouchesAKeptWalletAndItsQueuedChangeAndRemovesWhatTouchesOnlyB() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "wa", version = 2))
        env.seedWallet(walletJson(id = "wb", version = 2))
        env.seed(transactionJson(id = "tr", walletId = "wa", kind = "transfer", categoryId = null, counterWalletId = "wb", version = 2))
        env.seed(transactionJson(id = "only-b", walletId = "wb", version = 2))
        env.seed(transactionJson(id = "only-a", walletId = "wa", version = 2))
        val payload = """{"kind":"expense","date":"2026-10-01","amountMinor":1,"currency":"EGP"}"""
        env.engine.enqueue("transaction", "tr", SyncScope(ScopeKind.Wallet, "wa"), 2, payload)
        env.engine.enqueue("transaction", "only-b", SyncScope(ScopeKind.Wallet, "wb"), 2, payload)

        OwnMembershipFeedApplier(env.walletRepository).apply(SyncScope(ScopeKind.User, "u1"), listOf(ended("wb")))
        runCurrent()

        assertNull(env.walletDao.get("wb"))
        assertNotNull(env.transactionDao.get("tr"), "it still touches wallet A")
        assertNotNull(env.pendingDao.get("transaction", "tr"), "and its queued change stays to be answered by the server")
        assertNull(env.transactionDao.get("only-b"))
        assertNull(env.pendingDao.get("transaction", "only-b"), "queued changes of what was removed go with it")
        assertNotNull(env.transactionDao.get("only-a"))
        assertEquals(setOf("tr", "only-a"), env.transactionDao.rows.value.keys)
    }
}
