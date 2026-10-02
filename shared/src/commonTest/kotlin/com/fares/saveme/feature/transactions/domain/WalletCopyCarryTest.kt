package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.dto
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.data.dto.WalletDto
import com.fares.saveme.feature.wallets.walletBody
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A server copy of a wallet already counts every change of its scope up to its position (`change_seq`): an accepted
 * change it counts carries nothing, whichever arrives first (spec 009 FR-013, SC-005; found by the contract QA when
 * a replayed upload answered the wallet with the expense already in its balance, qa-report.md §4).
 */
class WalletCopyCarryTest {

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = TransactionTestEnv(scope) { jsonResponse(200, walletBody()) }

    private fun wallet(id: String = "w1", balance: Long, seq: Long) = walletJson(id = id, balance = balance).replace("\"change_seq\":7", "\"change_seq\":$seq")

    private fun tx(seq: Long, amount: Long = 5_000, counter: String? = null, kind: String = "expense", version: Long = 1) =
        dto(
            transactionJson(version = version, amount = amount, kind = kind, counterWalletId = counter, categoryId = if (kind == "transfer") null else "c1")
                .replace("\"change_seq\":9", "\"change_seq\":$seq"),
        )

    private suspend fun TransactionTestEnv.shown(walletId: String) = wallets.observeWallet(walletId).first()!!.displayedBalanceMinor

    @Test
    fun anAcceptedChangeTheWalletCopyAlreadyCountsCarriesNothing() = runTest {
        val env = env(backgroundScope)
        // The wallet's answer came first in the upload and already includes the expense (a replay after a lost answer).
        env.seedWallet(wallet(balance = 20_000, seq = 12))
        env.repository.storeServerCopy(tx(seq = 9), own = true)
        assertEquals(0, env.carryFor("w1"))
        assertEquals(20_000, env.shown("w1"), "counted once")
    }

    @Test
    fun aChangeTheWalletCopyDoesNotCountYetKeepsItsCarryUntilACopyThatDoesArrives() = runTest {
        val env = env(backgroundScope)
        env.seedWallet(wallet(balance = 25_000, seq = 7))
        env.repository.storeServerCopy(tx(seq = 9), own = true)
        assertEquals(20_000, env.shown("w1"), "the server's balance plus the carry")

        // A later copy of the wallet (its own answer, saved after the expense in the same upload) counts the expense.
        env.walletRepository.storeServerCopy(AppJson.decodeFromString(WalletDto.serializer(), wallet(balance = 20_000, seq = 10)))
        assertEquals(0, env.carryFor("w1"))
        assertEquals(20_000, env.shown("w1"), "never 15,000")

        // The same through the wallet's feed.
        env.repository.storeServerCopy(tx(seq = 11, amount = 6_000, version = 2), own = true)
        assertEquals(19_000, env.shown("w1"))
        env.walletRepository.storeFeedCopy(AppJson.decodeFromString(WalletDto.serializer(), wallet(balance = 19_000, seq = 11)))
        assertEquals(19_000, env.shown("w1"))
        assertEquals(0, env.carryFor("w1"))
    }

    @Test
    fun aTransfersDestinationPartWaitsForTheDestinationsRead() = runTest {
        val env = env(backgroundScope)
        env.seedWallet(wallet(balance = 25_000, seq = 7))
        env.seedWallet(wallet(id = "w2", balance = 0, seq = 3))
        env.repository.storeServerCopy(tx(seq = 9, counter = "w2", kind = "transfer"), own = true)
        assertEquals(5_000, env.shown("w2"))

        // A copy of the destination far past the transfer's position says nothing about it: positions are per scope.
        env.walletRepository.storeServerCopy(AppJson.decodeFromString(WalletDto.serializer(), wallet(id = "w2", balance = 0, seq = 50)))
        assertEquals(5_000, env.carryFor("w2"))
        env.walletRepository.storeServerCopy(AppJson.decodeFromString(WalletDto.serializer(), wallet(balance = 20_000, seq = 9)))
        assertEquals(0, env.transactionDao.rows.value.getValue("t1").carrySourceMinor)
        assertEquals(20_000, env.shown("w1"))
    }
}
