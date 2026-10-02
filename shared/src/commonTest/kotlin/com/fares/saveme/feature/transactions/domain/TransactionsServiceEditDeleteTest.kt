package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ChangeState
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.domain.model.SaveResult
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.transactions.txAccount
import com.fares.saveme.feature.wallets.walletJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** FR-005, FR-021, FR-022: edit and delete are local first, based on the version the person saw. */
class TransactionsServiceEditDeleteTest {

    private fun draft(amount: Long = 6_000, kind: String = "income", wallet: String = "other", counter: String? = "w9", category: String? = "c2") =
        TransactionDraft(wallet, kind, LocalDate(2026, 10, 2), amount, "USD", category, counter, " Taxi ", null, null, true)

    private fun payload(env: TransactionTestEnv, id: String = "t1") =
        AppJson.decodeFromString(TransactionPayload.serializer(), env.pendingDao.rows.value.getValue("transaction" to id).payloadJson)

    @Test
    fun saveUsesTheEditorsBaseVersionAndTheStoredKindWalletsAndCurrency() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 4, kind = "expense", currency = "EGP"))
        assertEquals(SaveResult.Saved, env.service.save("t1", draft(), baseVersion = 3))
        val row = env.pendingDao.rows.value.getValue("transaction" to "t1")
        assertEquals(3, row.baseVersion)
        assertEquals("w1", row.scopeId)
        val saved = payload(env)
        assertEquals("expense", saved.kind)
        assertNull(saved.counterWalletId)
        assertEquals("EGP", saved.currency)
        assertEquals("Taxi", saved.description)
        assertEquals(6_000, saved.amountMinor)
    }

    @Test
    fun editingAPlaceholderRewritesTheCreateWithBaseZero() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        val id = (env.service.create(draft(wallet = "w1", kind = "expense", counter = null, category = "c1")) as com.fares.saveme.feature.transactions.domain.model.CreateResult.Created).id
        assertEquals(SaveResult.Saved, env.service.save(id, draft(amount = 9_000), baseVersion = 0))
        assertEquals(0, env.pendingDao.rows.value.getValue("transaction" to id).baseVersion)
        assertEquals(9_000, payload(env, id).amountMinor)
        assertEquals(1, env.pendingDao.rows.value.size)
    }

    @Test
    fun saveIsRefusedForAViewerAnUnverifiedAddressAndAGoneTransactionAndValidates() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1", role = "viewer"))
        env.seedWallet(walletJson(id = "w2"))
        env.seed(transactionJson(id = "v", walletId = "w1", version = 2))
        env.seed(transactionJson(id = "e", walletId = "w2", version = 2))
        assertEquals(SaveResult.NotAllowed, env.service.save("v", draft(), 2))
        assertEquals(SaveResult.NoLongerAvailable, env.service.save("gone", draft(), 2))
        assertEquals(TransactionFieldProblem.AmountNotPositive, assertIs<SaveResult.Invalid>(env.service.save("e", draft(amount = 0), 2)).errors[TransactionField.Amount])
        env.authState.value = AuthState.SignedIn(txAccount.copy(emailVerified = false))
        assertEquals(SaveResult.NotVerified, env.service.save("e", draft(), 2))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun aSaveOfATransactionRemovedWhileEditingPutsTheOpenedCopyBackAndQueuesOnItsVersion() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 4, amount = 4_550))
        val opened = env.service.observeTransaction("t1", "w1").first()!!.server!!
        // A refresh found it deleted elsewhere and removed it from the phone.
        env.repository.remove("t1")

        assertEquals(SaveResult.NoLongerAvailable, env.service.save("t1", draft(amount = 6_000), 4))
        assertEquals(SaveResult.Saved, env.service.save("t1", draft(amount = 6_000), 4, lastSeen = opened))
        val queued = env.pendingDao.rows.value.getValue("transaction" to "t1")
        assertEquals(4, queued.baseVersion)
        assertEquals("w1", queued.scopeId)
        assertEquals(6_000, payload(env).amountMinor)
        assertEquals("expense", payload(env).kind)
        val row = env.transactionDao.get("t1")!!
        assertEquals(4, row.version)
        assertEquals(4_550, row.amountMinor)
        // It is listed again with the person's values, waiting, until the server answers.
        val shown = env.service.observeTransaction("t1", "w1").first()!!
        assertEquals(6_000, shown.shown.amountMinor)
        assertEquals(true, shown.waiting)
    }

    @Test
    fun aRemovedTransactionInAWalletThePersonCanNoLongerWriteIsNotPutBack() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1", role = "viewer"))
        env.seed(transactionJson(version = 4))
        val opened = env.service.observeTransaction("t1", "w1").first()!!.server!!
        env.repository.remove("t1")
        assertEquals(SaveResult.NotAllowed, env.service.save("t1", draft(), 4, lastSeen = opened))
        assertNull(env.transactionDao.get("t1"))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun anUnknownKindCannotBeEditedButCanBeDeleted() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(kind = "refund", version = 2))
        assertEquals(SaveResult.NotAllowed, env.service.save("t1", draft(), 2))
        assertEquals(SaveResult.Saved, env.service.delete("t1"))
    }

    @Test
    fun aConfirmedDeleteQueuesAnEnqueuedDeletionWithTheRowsVersionAndHidesTheRow() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 5))
        assertEquals(SaveResult.Saved, env.service.delete("t1"))
        val row = env.pendingDao.rows.value.getValue("transaction" to "t1")
        assertEquals(true, row.isDelete)
        assertEquals(5, row.baseVersion)
        assertEquals(emptyList(), env.service.observeList("w1").first().days)
        assertNull(env.service.observeTransaction("t1", "w1").first())
    }

    @Test
    fun aNeverSentCreateIsRemovedAtOnceWithNothingToSend() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        val id = (env.service.create(draft(wallet = "w1", kind = "expense", counter = null, category = "c1")) as com.fares.saveme.feature.transactions.domain.model.CreateResult.Created).id
        assertEquals(SaveResult.Saved, env.service.delete(id))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
        assertEquals(emptyMap(), env.transactionDao.rows.value)
        // No deletion is ever sent for something the server never had.
        assertEquals(0, env.requests.count { it.method == io.ktor.http.HttpMethod.Delete })
    }

    @Test
    fun deleteIsRefusedForAViewerAndForUnknownTransactions() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1", role = "viewer"))
        env.seed(transactionJson(version = 2))
        assertEquals(SaveResult.NotAllowed, env.service.delete("t1"))
        assertEquals(SaveResult.NoLongerAvailable, env.service.delete("nope"))
    }

    @Test
    fun keepMineUseTheirsTryAgainAndDiscardCallTheEngine() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 2))
        env.service.save("t1", draft(), 2)
        val live = transactionJson(version = 3, amount = 777)
        env.pendingDao.rows.value = env.pendingDao.rows.value.mapValues { it.value.copy(state = ChangeState.Clash.name, clashJson = live) }
        env.service.keepMine("t1")
        assertEquals(3, env.pendingDao.rows.value.getValue("transaction" to "t1").baseVersion)
        assertEquals(ChangeState.Waiting.name, env.pendingDao.rows.value.getValue("transaction" to "t1").state)

        env.pendingDao.rows.value = env.pendingDao.rows.value.mapValues { it.value.copy(state = ChangeState.Clash.name, clashJson = live) }
        env.service.useTheirs("t1")
        assertNull(env.pendingDao.get("transaction", "t1"))
        assertEquals(777, env.transactionDao.get("t1")!!.amountMinor)

        env.service.save("t1", draft(), 3)
        env.pendingDao.rows.value = env.pendingDao.rows.value.mapValues { it.value.copy(state = ChangeState.Refused.name) }
        env.service.tryAgain("t1")
        assertEquals(ChangeState.Waiting.name, env.pendingDao.rows.value.getValue("transaction" to "t1").state)
        env.service.discard("t1")
        assertNull(env.pendingDao.get("transaction", "t1"))
        assertNotNull(env.transactionDao.get("t1"))
    }

    @Test
    fun discardingAPlaceholderRemovesItFromThePhone() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        val id = (env.service.create(draft(wallet = "w1", kind = "expense", counter = null, category = "c1")) as com.fares.saveme.feature.transactions.domain.model.CreateResult.Created).id
        env.service.discard(id)
        assertNull(env.transactionDao.get(id))
    }

    @Test
    fun useTheirsAsksForOneWalletRead() = runTest {
        val env = TransactionTestEnv(backgroundScope) { throw kotlinx.io.IOException("offline") }
        env.signedIn()
        env.seedWallet(walletJson(id = "w1"))
        env.seed(transactionJson(version = 2))
        env.service.save("t1", draft(), 2)
        env.pendingDao.rows.value = env.pendingDao.rows.value.mapValues { it.value.copy(state = ChangeState.Clash.name, clashJson = transactionJson(version = 3)) }
        env.service.useTheirs("t1")
        com.fares.saveme.feature.transactions.awaitReal { env.requests.any { it.url.encodedPath.endsWith("/wallets") } }
        assert(env.requests.any { it.url.encodedPath.endsWith("/wallets") })
    }
}

