package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** FR-008: where a transaction can be recorded and what Home preselects. */
class WritableWalletsTest {

    @Test
    fun onlyActiveVisibleWalletsWhereThePersonOwnsOrEditsAreOffered() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "own", name = "Own", role = "owner", createdAt = "2026-09-01T00:00:00Z"))
        env.seedWallet(walletJson(id = "edit", name = "Edit", role = "editor", createdAt = "2026-09-02T00:00:00Z"))
        env.seedWallet(walletJson(id = "view", name = "View", role = "viewer", createdAt = "2026-09-03T00:00:00Z"))
        env.seedWallet(walletJson(id = "arch", name = "Arch", role = "owner", archivedAt = "2026-09-30T00:00:00Z"))
        assertEquals(listOf("own", "edit"), env.service.observeWritableWallets().first().map { it.id })
    }

    @Test
    fun aWalletHiddenByItsOwnWaitingDeletionIsNotOffered() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.engine.enqueueDelete("wallet", "w1", com.fares.saveme.core.sync.SyncScope(com.fares.saveme.core.sync.ScopeKind.Wallet, "w1"), 1)
        assertEquals(emptyList(), env.service.observeWritableWallets().first())
        assertNull(env.service.defaultWalletId())
    }

    @Test
    fun theDefaultIsTheLastWalletRecordedInWhileItStillQualifiesElseTheFirst() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "a", createdAt = "2026-09-01T00:00:00Z"))
        env.seedWallet(walletJson(id = "b", createdAt = "2026-09-02T00:00:00Z"))
        assertEquals("a", env.service.defaultWalletId())
        env.auth.keyValues.set(DefaultTransactionsService.LAST_WALLET_KEY, "b")
        assertEquals("b", env.service.defaultWalletId())
        env.auth.keyValues.set(DefaultTransactionsService.LAST_WALLET_KEY, "gone")
        assertEquals("a", env.service.defaultWalletId())
    }

    @Test
    fun withNoQualifyingWalletThereIsNoDefault() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "v", role = "viewer"))
        assertNull(env.service.defaultWalletId())
    }
}
