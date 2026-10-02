package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** FR-010: where a transfer can go. */
class DestinationChoicesTest {

    @Test
    fun onlyActiveVisibleSameCurrencyWalletsWhereThePersonCanWriteOtherThanTheSource() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "src", currency = "EGP", createdAt = "2026-09-01T00:00:00Z"))
        env.seedWallet(walletJson(id = "bank", name = "Bank", currency = "EGP", role = "editor", createdAt = "2026-09-02T00:00:00Z"))
        env.seedWallet(walletJson(id = "usd", currency = "USD", createdAt = "2026-09-03T00:00:00Z"))
        env.seedWallet(walletJson(id = "viewer", role = "viewer", createdAt = "2026-09-04T00:00:00Z"))
        env.seedWallet(walletJson(id = "arch", archivedAt = "2026-09-30T00:00:00Z"))
        assertEquals(listOf("bank"), env.service.observeDestinations("src").first().map { it.id })
        assertEquals("Bank", env.service.observeDestinations("src").first().single().name)
    }

    @Test
    fun aSingleWalletAccountHasNoDestination() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "src"))
        assertEquals(emptyList(), env.service.observeDestinations("src").first())
        assertEquals(emptyList(), env.service.observeDestinations("unknown").first())
    }
}
