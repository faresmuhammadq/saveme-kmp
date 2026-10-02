package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.auth.jsonResponse
import com.fares.saveme.feature.auth.problemBody
import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.dto
import com.fares.saveme.feature.transactions.transactionJson
import com.fares.saveme.feature.transactions.transactionPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TransactionRepositoryTest {

    private suspend fun TransactionTestEnv.placeholder(id: String = "p1") = repository.insertPlaceholder(
        NewTransaction(id, "u1", "w1", null, "expense", "2026-10-02", 700, "EGP", "c1", null, null, null, false, "u1"),
    )

    @Test
    fun aCreateIsAPlaceholderWithVersionZeroAndTheSignedInPersonAsAuthor() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.placeholder()
        val row = env.transactionDao.get("p1")!!
        assertEquals(0, row.version)
        assertEquals("u1", row.createdBy)
        assertNull(row.deletedAt)
    }

    @Test
    fun refreshStoresPageByPageAndMergesRemovalsOnlyAfterEveryPageSucceeded() = runTest {
        var calls = 0
        val env = TransactionTestEnv(backgroundScope) { request ->
            calls++
            if (request.url.parameters["cursor"] == null) jsonResponse(200, transactionPage(listOf(transactionJson(id = "a")), nextCursor = "n2"))
            else jsonResponse(200, transactionPage(listOf(transactionJson(id = "b"))))
        }
        env.signedIn()
        env.seed(transactionJson(id = "gone", version = 2))
        val result = assertIs<ApiResult.Success<WalletRead>>(env.repository.refreshWallet("w1"))
        assertTrue(result.value.changed)
        assertEquals(2, calls)
        assertEquals(setOf("a", "b"), env.transactionDao.rows.value.keys)
        assertNotNull(env.scopeDao.rows.value["transactions:w1"]?.refreshedAt?.takeIf { it.isNotEmpty() })
        assertEquals(setOf("n2"), env.requests.mapNotNull { it.url.parameters["cursor"] }.toSet())
    }

    @Test
    fun aFailureOnALaterPageKeepsEverythingAndStampsNothing() = runTest {
        val env = TransactionTestEnv(backgroundScope) { request ->
            if (request.url.parameters["cursor"] == null) jsonResponse(200, transactionPage(listOf(transactionJson(id = "a")), nextCursor = "n2"))
            else jsonResponse(503, problemBody(503, "SERVICE_UNAVAILABLE"))
        }
        env.signedIn()
        env.seed(transactionJson(id = "keep", version = 2))
        assertIs<ApiResult.Failure>(env.repository.refreshWallet("w1"))
        assertEquals(setOf("a", "keep"), env.transactionDao.rows.value.keys)
        assertNull(env.scopeDao.rows.value["transactions:w1"])
    }

    @Test
    fun offlineKeepsTheRows() = runTest {
        val env = TransactionTestEnv(backgroundScope) { throw kotlinx.io.IOException("offline") }
        env.signedIn()
        env.seed(transactionJson(id = "keep", version = 2))
        assertEquals(ApiResult.Offline, env.repository.refreshWallet("w1"))
        assertNotNull(env.transactionDao.get("keep"))
    }

    @Test
    fun anUnlistedRowIsRemovedUnlessItIsAPlaceholderOrHasAWaitingChangeOrItsCarry() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, transactionPage(listOf(transactionJson(id = "listed")))) }
        env.signedIn()
        env.seed(transactionJson(id = "synced", version = 2))
        env.seed(transactionJson(id = "queued", version = 2))
        env.seed(transactionJson(id = "carrying", version = 3))
        env.placeholder("p1")
        env.engine.enqueue("transaction", "queued", SyncScope(ScopeKind.Wallet, "w1"), 2, "{}")
        // A row that still carries a confirmed effect stays until a wallet read settles it.
        env.repository.storeServerCopy(dto(transactionJson(id = "carrying", version = 4)), own = true)
        assertIs<ApiResult.Success<WalletRead>>(env.repository.refreshWallet("w1"))
        assertEquals(setOf("listed", "queued", "carrying", "p1"), env.transactionDao.rows.value.keys)
    }

    @Test
    fun aTransferListedOnTheDestinationIsStoredAndAnotherWalletsRowsAreLeftAlone() = runTest {
        val env = TransactionTestEnv(backgroundScope) {
            jsonResponse(200, transactionPage(listOf(transactionJson(id = "tr", walletId = "w9", kind = "transfer", categoryId = null, counterWalletId = "w1"))))
        }
        env.signedIn()
        env.seed(transactionJson(id = "other-wallet", walletId = "w5", version = 2))
        assertIs<ApiResult.Success<WalletRead>>(env.repository.refreshWallet("w1"))
        assertEquals(setOf("tr", "other-wallet"), env.transactionDao.rows.value.keys)
    }

    @Test
    fun theOnlyTheListedRowsOfANoChangeReadReportNoChange() = runTest {
        val env = TransactionTestEnv(backgroundScope) { jsonResponse(200, transactionPage(listOf(transactionJson(id = "a", version = 2)))) }
        env.signedIn()
        env.seed(transactionJson(id = "a", version = 2))
        assertEquals(false, assertIs<ApiResult.Success<WalletRead>>(env.repository.refreshWallet("w1")).value.changed)
    }

    @Test
    fun theCarryIsAddedClearedUpToATimeAndHiddenDeletionsArePurged() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seed(transactionJson(id = "a", version = 1, amount = 1_000))
        env.seed(transactionJson(id = "b", version = 1, amount = 2_000))
        env.clock.now = Instant.parse("2026-10-02T10:00:00Z")
        env.repository.storeServerCopy(dto(transactionJson(id = "a", version = 2, amount = 1_500)), own = true)
        env.clock.now = Instant.parse("2026-10-02T10:00:05Z")
        env.repository.storeServerCopy(dto(transactionJson(id = "b", version = 2, deletedAt = "2026-10-02T10:00:05Z")), own = true)
        assertEquals(-500 + 2_000, env.carryFor("w1"))
        assertEquals(2, env.repository.observeCarried().first().size)

        env.repository.clearCarryConfirmedUpTo(Instant.parse("2026-10-02T10:00:03Z"))
        assertEquals(listOf("b"), env.repository.observeCarried().first().map { it.id })
        assertEquals(2_000, env.carryFor("w1"))
        assertNotNull(env.transactionDao.get("b"))

        env.repository.clearCarryConfirmedUpTo(Instant.parse("2026-10-02T10:00:10Z"))
        assertNull(env.transactionDao.get("b"))
        assertEquals(0, env.carryFor("w1"))
    }

    @Test
    fun aTransfersCarryMovesBothWallets() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.repository.storeServerCopy(dto(transactionJson(id = "tr", kind = "transfer", categoryId = null, counterWalletId = "w2", amount = 5_000)), own = true)
        assertEquals(-5_000, env.carryFor("w1"))
        assertEquals(5_000, env.carryFor("w2"))
    }

    @Test
    fun touchingFindsTheRowsOfEitherSide() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seed(transactionJson(id = "a", walletId = "w1"))
        env.seed(transactionJson(id = "tr", walletId = "w2", kind = "transfer", categoryId = null, counterWalletId = "w1"))
        env.seed(transactionJson(id = "z", walletId = "w3"))
        assertEquals(setOf("a", "tr"), env.repository.touching("w1").map { it.id }.toSet())
    }
}
