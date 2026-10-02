package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.domain.model.CreateResult
import com.fares.saveme.feature.transactions.domain.model.Direction
import com.fares.saveme.feature.transactions.domain.model.SaveResult
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-010 to FR-014: a transfer is one transaction listed in both wallets. */
class TransactionsServiceTransferTest {

    private fun transfer(amount: Long = 50_000) = TransactionDraft("w1", "transfer", LocalDate(2026, 10, 2), amount, "EGP", null, "w2", null, null, null, false)

    private suspend fun TransactionTestEnv.twoWallets() {
        seedWallet(walletJson(id = "w1", name = "Cash", balance = 100_000, createdAt = "2026-09-01T00:00:00Z"))
        seedWallet(walletJson(id = "w2", name = "Bank", balance = 200_000, createdAt = "2026-09-02T00:00:00Z"))
    }

    @Test
    fun createSendsTheDestinationNoCategoryTheSourcesCurrencyAndTheSourceScope() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.twoWallets()
        val id = assertIs<CreateResult.Created>(env.service.create(transfer())).id
        val queued = env.pendingDao.get("transaction", id)!!
        assertEquals("w1", queued.scopeId)
        val payload = AppJson.decodeFromString(TransactionPayload.serializer(), queued.payloadJson)
        assertEquals("w2", payload.counterWalletId)
        assertNull(payload.categoryId)
        assertEquals("EGP", payload.currency)
    }

    @Test
    fun itIsListedInBothWalletsAndMovesBothDisplayedBalancesWhileItWaits() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.twoWallets()
        assertIs<CreateResult.Created>(env.service.create(transfer()))
        assertEquals(1, env.service.observeList("w1").first().totalCount)
        assertEquals(1, env.service.observeList("w2").first().totalCount)
        assertEquals(50_000, env.wallets.observeWallet("w1").first()!!.displayedBalanceMinor)
        assertEquals(250_000, env.wallets.observeWallet("w2").first()!!.displayedBalanceMinor)
        assertEquals(Direction.Out, env.service.observeList("w1").first().days.single().items.single().direction)
        assertEquals(Direction.In, env.service.observeList("w2").first().days.single().items.single().direction)
        assertTrue(env.wallets.observeWallet("w2").first()!!.hasPendingPostings)
    }

    @Test
    fun editNeedsOwnerOrEditorInBothWalletsAndTheWalletsStayTheStoredOnes() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seedWallet(walletJson(id = "w2", role = "viewer"))
        env.seed(transactionJson(id = "tr", walletId = "w1", kind = "transfer", categoryId = null, counterWalletId = "w2", version = 2))
        assertEquals(SaveResult.NotAllowed, env.service.save("tr", transfer().copy(counterWalletId = "w3"), 2))
        val view = env.service.observeTransaction("tr", "w1").first()!!
        assertFalse(view.canEdit)
        // Deleting is offered where the person writes and the other wallet is writable or not on the phone (FR-013).
        assertFalse(view.canDelete)
        env.seedWallet(walletJson(id = "w2", role = "editor"))
        assertEquals(SaveResult.Saved, env.service.save("tr", transfer().copy(counterWalletId = "w3"), 2))
        assertEquals("w2", AppJson.decodeFromString(TransactionPayload.serializer(), env.pendingDao.get("transaction", "tr")!!.payloadJson).counterWalletId)
    }

    @Test
    fun aTransferFromAWalletThatWasDeletedCanBeDeletedFromTheDestinationButNotEdited() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(id = "tr", walletId = "w9", kind = "transfer", categoryId = null, counterWalletId = "w1", version = 2))
        val view = env.service.observeTransaction("tr", "w1").first()!!
        assertFalse(view.canEdit)
        assertTrue(view.canDelete)
        assertEquals(SaveResult.Saved, env.service.delete("tr"))
        assertEquals(SyncScope(ScopeKind.Wallet, "w9").key, env.pendingDao.get("transaction", "tr")!!.let { "${it.scopeKind}:${it.scopeId}" })
    }

    @Test
    fun aViewerOfTheWalletItIsViewedFromCannotDeleteIt() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seedWallet(walletJson(id = "w2", role = "viewer"))
        env.seed(transactionJson(id = "tr", walletId = "w1", kind = "transfer", categoryId = null, counterWalletId = "w2", version = 2))
        assertFalse(env.service.observeTransaction("tr", "w2").first()!!.canDelete)
    }
}
