package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.data.dto.TransactionPayload
import com.fares.saveme.feature.transactions.domain.model.CreateResult
import com.fares.saveme.feature.transactions.domain.model.TransactionDraft
import com.fares.saveme.feature.transactions.domain.model.TransactionField
import com.fares.saveme.feature.transactions.domain.model.TransactionFieldProblem
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
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** FR-002 to FR-009: recording is local first and refused up front when it could not succeed. */
class TransactionsServiceCreateTest {

    private fun draft(
        wallet: String = "w1",
        kind: String = "expense",
        amount: Long = 4_550,
        category: String? = "c1",
        counter: String? = null,
        date: LocalDate = LocalDate(2026, 10, 2),
        note: String? = null,
        memo: String? = null,
        person: String? = null,
        currency: String = "EGP",
    ) = TransactionDraft(wallet, kind, date, amount, currency, category, counter, note, memo, person, cleared = false)

    private fun invalid(result: CreateResult) = assertIs<CreateResult.Invalid>(result).errors

    @Test
    fun anUnverifiedAddressQueuesNothing() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.authState.value = AuthState.SignedIn(txAccount.copy(emailVerified = false))
        assertEquals(CreateResult.NotVerified, env.service.create(draft()))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
        assertEquals(emptyMap(), env.transactionDao.rows.value)
    }

    @Test
    fun aViewerAnArchivedWalletAndAnUnknownWalletAreRefused() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1", role = "viewer"))
        env.seedWallet(walletJson(id = "w2", archivedAt = "2026-09-30T00:00:00Z"))
        assertEquals(CreateResult.NotAllowed, env.service.create(draft(wallet = "w1")))
        assertEquals(CreateResult.NotAllowed, env.service.create(draft(wallet = "w2")))
        assertEquals(CreateResult.NoWallet, env.service.create(draft(wallet = "nope")))
        assertEquals(emptyMap(), env.pendingDao.rows.value)
    }

    @Test
    fun theAmountMustBePositiveAndAtMostTheLargestTheServerAccepts() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        assertEquals(TransactionFieldProblem.AmountNotPositive, invalid(env.service.create(draft(amount = 0)))[TransactionField.Amount])
        assertEquals(TransactionFieldProblem.AmountNotPositive, invalid(env.service.create(draft(amount = -5)))[TransactionField.Amount])
        assertEquals(TransactionFieldProblem.AmountOutOfRange, invalid(env.service.create(draft(amount = 9_007_199_254_740_992L)))[TransactionField.Amount])
        assertIs<CreateResult.Created>(env.service.create(draft(amount = 9_007_199_254_740_991L)))
    }

    @Test
    fun theDateStaysBetween1900And2999() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        assertEquals(TransactionFieldProblem.DateOutOfRange, invalid(env.service.create(draft(date = LocalDate(1899, 12, 31))))[TransactionField.Date])
        assertEquals(TransactionFieldProblem.DateOutOfRange, invalid(env.service.create(draft(date = LocalDate(3000, 1, 1))))[TransactionField.Date])
        assertIs<CreateResult.Created>(env.service.create(draft(date = LocalDate(1900, 1, 1))))
        assertIs<CreateResult.Created>(env.service.create(draft(date = LocalDate(2999, 12, 31))))
    }

    @Test
    fun anIncomeOrExpenseNeedsACategoryAndATransferNeedsADestinationThatQualifies() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seedWallet(walletJson(id = "w2"))
        env.seedWallet(walletJson(id = "w3", currency = "USD"))
        assertEquals(TransactionFieldProblem.CategoryRequired, invalid(env.service.create(draft(category = null)))[TransactionField.Category])
        assertEquals(TransactionFieldProblem.CategoryRequired, invalid(env.service.create(draft(kind = "income", category = "  ")))[TransactionField.Category])
        assertEquals(TransactionFieldProblem.DestinationRequired, invalid(env.service.create(draft(kind = "transfer", category = null)))[TransactionField.Destination])
        // Another currency, the source itself and an unknown wallet are not destinations (FR-010).
        listOf("w3", "w1", "nope").forEach { destination ->
            assertEquals(
                TransactionFieldProblem.DestinationRequired,
                invalid(env.service.create(draft(kind = "transfer", category = null, counter = destination)))[TransactionField.Destination],
                destination,
            )
        }
        assertIs<CreateResult.Created>(env.service.create(draft(kind = "transfer", category = null, counter = "w2")))
    }

    @Test
    fun textLimitsCountCodePointsAfterTrimmingAndBlankBecomesNone() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        assertEquals(TransactionFieldProblem.TooLong(100), invalid(env.service.create(draft(note = "x".repeat(101))))[TransactionField.Note])
        assertEquals(TransactionFieldProblem.TooLong(1000), invalid(env.service.create(draft(memo = "x".repeat(1001))))[TransactionField.Details])
        assertEquals(TransactionFieldProblem.TooLong(100), invalid(env.service.create(draft(person = "y".repeat(101))))[TransactionField.Person])
        // 100 emoji are 100 code points although 200 UTF-16 units.
        assertIs<CreateResult.Created>(env.service.create(draft(note = "😀".repeat(100))))
        assertIs<CreateResult.Created>(env.service.create(draft(note = " " + "a".repeat(100) + "  ")))
        val created = assertIs<CreateResult.Created>(env.service.create(draft(note = "   ", memo = "", person = " ")))
        val payload = AppJson.decodeFromString(TransactionPayload.serializer(), env.pendingDao.get("transaction", created.id)!!.payloadJson)
        assertNull(payload.description)
        assertNull(payload.memo)
        assertNull(payload.counterparty)
    }

    @Test
    fun aValidCreateWritesAPlaceholderAndOneQueueRowInTheSourceWalletScopeWithTheWalletsCurrency() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1", currency = "EGP"))
        val created = assertIs<CreateResult.Created>(env.service.create(draft(currency = "USD", note = " Lunch ")))
        assertEquals("t-new-1", created.id)
        val row = env.transactionDao.get(created.id)!!
        assertEquals(0, row.version)
        assertEquals("EGP", row.currency)
        assertEquals("u1", row.createdBy)
        assertEquals(4_550, row.amountMinor)
        val queued = assertNotNull(env.pendingDao.get("transaction", created.id))
        assertEquals("wallet", queued.scopeKind)
        assertEquals("w1", queued.scopeId)
        assertEquals(0, queued.baseVersion)
        val payload = AppJson.decodeFromString(TransactionPayload.serializer(), queued.payloadJson)
        assertEquals("Lunch", payload.description)
        assertEquals("EGP", payload.currency)
        assertEquals("c1", payload.categoryId)
        assertEquals("w1", env.keyValueLast(env))
    }

    private suspend fun TransactionTestEnv.keyValueLast(env: TransactionTestEnv) = env.auth.keyValues.get(DefaultTransactionsService.LAST_WALLET_KEY)

    @Test
    fun aTransferHasNoCategoryAndTheSourcesCurrency() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        env.seedWallet(walletJson(id = "w2"))
        val created = assertIs<CreateResult.Created>(env.service.create(draft(kind = "transfer", category = "c1", counter = "w2")))
        val payload = AppJson.decodeFromString(TransactionPayload.serializer(), env.pendingDao.get("transaction", created.id)!!.payloadJson)
        assertNull(payload.categoryId)
        assertEquals("w2", payload.counterWalletId)
        assertEquals("EGP", payload.currency)
        assertEquals(ScopeKind.Wallet.wire, env.pendingDao.get("transaction", created.id)!!.scopeKind)
    }

    @Test
    fun theIdIsAUuidV7() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        val service = DefaultTransactionsService(
            env.repository, env.engine, env.wallets, env.categories, env.authState, env.auth.keyValues, env.reconciler, backgroundScope, env.clock,
        )
        val id = assertIs<CreateResult.Created>(service.create(draft())).id
        val uuid = Uuid.parse(id)
        assertEquals('7', uuid.toString()[14])
        assertTrue(env.transactionDao.rows.value.containsKey(id))
    }

    @Test
    fun theWalletStaysCreatableOfflineAndNothingNeedsAConnection() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seedWallet(walletJson(id = "w1"))
        assertIs<CreateResult.Created>(env.service.create(draft()))
        assertEquals(0, env.requests.size)
        assertEquals(1, env.service.observeList("w1").first().days.single().items.size)
    }
}
