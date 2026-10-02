package com.fares.saveme.feature.transactions.data

import com.fares.saveme.feature.transactions.TransactionTestEnv
import com.fares.saveme.feature.transactions.dto
import com.fares.saveme.feature.transactions.transactionJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun theCarryIsAddedClearedPerWalletUpToATimeAndHiddenDeletionsArePurged() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.seed(transactionJson(id = "a", version = 1, amount = 1_000))
        env.seed(transactionJson(id = "b", version = 1, amount = 2_000))
        env.clock.now = Instant.parse("2026-10-02T10:00:00Z")
        env.repository.storeServerCopy(dto(transactionJson(id = "a", version = 2, amount = 1_500)), own = true)
        env.clock.now = Instant.parse("2026-10-02T10:00:05Z")
        env.repository.storeServerCopy(dto(transactionJson(id = "b", version = 2, deletedAt = "2026-10-02T10:00:05Z")), own = true)
        assertEquals(-500 + 2_000, env.carryFor("w1"))
        assertEquals(2, env.repository.observeCarried().first().size)

        env.repository.clearCarryFor("w1", Instant.parse("2026-10-02T10:00:03Z"))
        assertEquals(listOf("b"), env.repository.observeCarried().first().map { it.id })
        assertEquals(2_000, env.carryFor("w1"))
        assertNotNull(env.transactionDao.get("b"))

        env.repository.clearCarryFor("w1", Instant.parse("2026-10-02T10:00:10Z"))
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
    fun aWalletsReadClearsOnlyThatWalletsPartOfATransfersCarry() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.clock.now = Instant.parse("2026-10-02T10:00:00Z")
        env.repository.storeServerCopy(dto(transactionJson(id = "tr", kind = "transfer", categoryId = null, counterWalletId = "w2", amount = 5_000)), own = true)

        env.repository.clearCarryFor("w1", Instant.parse("2026-10-02T10:00:05Z"))
        assertEquals(0, env.carryFor("w1"))
        assertEquals(5_000, env.carryFor("w2"), "the destination keeps its part until its own read")
        assertEquals(listOf("tr"), env.repository.observeCarried().first().map { it.id }, "still carried by the destination")

        env.repository.clearCarryFor("w2", Instant.parse("2026-10-02T10:00:06Z"))
        assertEquals(0, env.carryFor("w2"))
        assertEquals(emptyList(), env.repository.observeCarried().first())
    }

    @Test
    fun aReadThatStartedBeforeTheConfirmationClearsNothing() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        env.clock.now = Instant.parse("2026-10-02T10:00:10Z")
        env.repository.storeServerCopy(dto(transactionJson(id = "a", amount = 1_000)), own = true)
        env.repository.clearCarryFor("w1", Instant.parse("2026-10-02T10:00:05Z"))
        assertEquals(-1_000, env.carryFor("w1"))
    }

    @Test
    fun theWalletsUpdatedTimeIsItsFeedReadTimeAndFallsBackToTheLegacyOne() = runTest {
        val env = TransactionTestEnv(backgroundScope)
        assertNull(env.repository.observeRefreshedAt("w1").first())
        env.scopeDao.upsert(com.fares.saveme.core.database.SyncScopeEntity("transactions:w1", "2026-09-30T08:00:00Z"))
        assertEquals("2026-09-30T08:00:00Z", env.repository.observeRefreshedAt("w1").first())
        env.scopeDao.markFeedRead("wallet:w1", "2026-10-02T10:00:00.000Z")
        assertEquals("2026-10-02T10:00:00.000Z", env.repository.observeRefreshedAt("w1").first())
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
