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
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A recurring confirmation shows as a phone-only transaction that counts in the balance at once (spec 010 R-07, SC-005). */
class PendingConfirmationTest {

    private fun env(scope: kotlinx.coroutines.CoroutineScope) = TransactionTestEnv(scope) { jsonResponse(200, walletBody()) }

    private fun confirmation(
        id: String = "tc1",
        amount: Long = 4_500,
        kind: String = "expense",
        counter: String? = null,
        occurrence: String = "o1",
    ) = PendingConfirmation(
        transactionId = id, walletId = "w1", counterWalletId = counter, kind = kind, amountMinor = amount, currency = "EGP",
        date = LocalDate.parse("2026-10-01"), categoryId = if (kind == "transfer") null else "c1", description = "Rent", memo = null,
        counterparty = null, ruleId = "r1", occurrenceId = occurrence,
    )

    private fun wallet(id: String = "w1", balance: Long, seq: Long) =
        walletJson(id = id, balance = balance).replace("\"change_seq\":7", "\"change_seq\":$seq")

    private fun serverCopy(seq: Long, amount: Long = 4_500, kind: String = "expense", counter: String? = null) = dto(
        transactionJson(
            id = "tc1", amount = amount, kind = kind, counterWalletId = counter, occurrenceId = "o1", recurringRuleId = "r1",
            categoryId = if (kind == "transfer") null else "c1",
        ).replace("\"change_seq\":9", "\"change_seq\":$seq"),
    )

    private suspend fun TransactionTestEnv.shown(walletId: String) = wallets.observeWallet(walletId).first()!!.displayedBalanceMinor

    @Test
    fun placingInsertsAVersionZeroRowWithBothLinksAndQueuesNothing() = runTest {
        val env = env(backgroundScope)
        env.occurrenceTransactions.placeConfirmation(confirmation())
        val row = env.transactionDao.get("tc1")!!
        assertEquals(0, row.version)
        assertTrue(row.pendingConfirmation)
        assertEquals("r1", row.recurringRuleId)
        assertEquals("o1", row.occurrenceId)
        assertNull(env.engine.get("transaction", "tc1"), "never queued as a transaction change (ADR-0018 rule 6)")
    }

    @Test
    fun theBalanceCountsItOnceAndBothWalletsOfATransfer() = runTest {
        val env = env(backgroundScope)
        env.occurrenceTransactions.placeConfirmation(confirmation())
        assertEquals(-4_500, env.postings.deltaFor("w1").first())
        assertTrue(env.postings.hasPending("w1").first())
        assertEquals(0, env.postings.deltaFor("w2").first())
        env.occurrenceTransactions.placeConfirmation(confirmation(id = "tc2", amount = 1_000, kind = "transfer", counter = "w2", occurrence = "o2"))
        assertEquals(-5_500, env.postings.deltaFor("w1").first())
        assertEquals(1_000, env.postings.deltaFor("w2").first())
        assertTrue(env.postings.hasPending("w2").first())
    }

    @Test
    fun updatingChangesTheValuesAndKeepsTheId() = runTest {
        val env = env(backgroundScope)
        env.occurrenceTransactions.placeConfirmation(confirmation())
        env.occurrenceTransactions.updateConfirmation(confirmation(amount = 3_000))
        assertEquals(3_000, env.transactionDao.get("tc1")!!.amountMinor)
        assertEquals(1, env.transactionDao.getAll().size)
        assertEquals(-3_000, env.postings.deltaFor("w1").first())
    }

    @Test
    fun droppingRemovesAPhoneOnlyRowButNeverAServerCopy() = runTest {
        val env = env(backgroundScope)
        env.occurrenceTransactions.placeConfirmation(confirmation())
        env.occurrenceTransactions.dropConfirmation("tc1")
        assertNull(env.transactionDao.get("tc1"))
        assertEquals(0, env.postings.deltaFor("w1").first())

        env.seed(transactionJson(id = "tx", occurrenceId = "o9"))
        env.occurrenceTransactions.dropConfirmation("tx")
        assertNotNull(env.transactionDao.get("tx"))
    }

    @Test
    fun droppingByOccurrenceKeepsTheExceptedId() = runTest {
        val env = env(backgroundScope)
        env.occurrenceTransactions.placeConfirmation(confirmation())
        env.occurrenceTransactions.dropConfirmationOf("o1", exceptTransactionId = "tc1")
        assertNotNull(env.transactionDao.get("tc1"))
        env.occurrenceTransactions.dropConfirmationOf("o1", exceptTransactionId = "other")
        assertNull(env.transactionDao.get("tc1"))
    }

    @Test
    fun confirmThenAcceptedThenTransactionRowThenWalletRowNeverDropsOrDoublesTheBalance() = runTest {
        val env = env(backgroundScope)
        env.seedWallet(wallet(balance = 25_000, seq = 7))
        assertEquals(25_000, env.shown("w1"))

        env.occurrenceTransactions.placeConfirmation(confirmation())
        assertEquals(20_500, env.shown("w1"), "confirm counts at once")

        // The occurrence answer is accepted: the phone-only row stays, nothing else changes.
        assertEquals(20_500, env.shown("w1"))

        // The feed brings the transaction before the wallet copy: its effect moves into the carry.
        env.repository.storeFeedCopy(serverCopy(seq = 9))
        val row = env.transactionDao.get("tc1")!!
        assertFalse(row.pendingConfirmation)
        assertEquals(20_500, env.shown("w1"), "no drop and no double")
        assertEquals(-4_500, env.carryFor("w1"))

        // The wallet copy at or past the transaction's position counts it: the carry goes.
        env.walletRepository.storeFeedCopy(AppJson.decodeFromString(WalletDto.serializer(), wallet(balance = 20_500, seq = 9)))
        assertEquals(0, env.carryFor("w1"))
        assertEquals(20_500, env.shown("w1"))
    }

    @Test
    fun aWalletCopyThatAlreadyCountsTheTransactionLeavesNoSourceCarry() = runTest {
        val env = env(backgroundScope)
        env.seedWallet(wallet(balance = 20_500, seq = 12))
        env.occurrenceTransactions.placeConfirmation(confirmation())
        env.repository.storeFeedCopy(serverCopy(seq = 9))
        assertEquals(0, env.carryFor("w1"))
        assertEquals(20_500, env.shown("w1") + 0, "the placeholder's effect is gone, the copy counts it once")
    }

    @Test
    fun aTransfersDestinationKeepsItsPartUntilItsOwnRead() = runTest {
        val env = env(backgroundScope)
        env.seedWallet(wallet(balance = 10_000, seq = 7))
        env.seedWallet(wallet(id = "w2", balance = 0, seq = 3))
        env.occurrenceTransactions.placeConfirmation(confirmation(amount = 1_000, kind = "transfer", counter = "w2"))
        env.repository.storeFeedCopy(serverCopy(seq = 9, amount = 1_000, kind = "transfer", counter = "w2"))
        assertEquals(1_000, env.shown("w2"))
        assertEquals(1_000, env.carryFor("w2"))
    }

    @Test
    fun aSweepNeverRemovesAPendingRowButSweepConfirmationsDropsOneWhoseTransactionNeverCame() = runTest {
        val env = env(backgroundScope)
        env.occurrenceTransactions.placeConfirmation(confirmation())
        env.repository.sweepWallet("w1", emptySet())
        assertNotNull(env.transactionDao.get("tc1"))

        env.occurrenceTransactions.sweepConfirmations("w1", setOf("someone-else"))
        assertNotNull(env.transactionDao.get("tc1"))
        env.occurrenceTransactions.sweepConfirmations("w1", setOf("tc1"))
        assertNull(env.transactionDao.get("tc1"))
    }
}
