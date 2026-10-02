package com.fares.saveme.core.sync

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.sync.PendingChangeEntity
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.categories.data.CategoryApi
import com.fares.saveme.feature.categories.data.CategoryRepository
import com.fares.saveme.feature.categories.data.local.CategoryEntity
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import com.fares.saveme.feature.wallets.data.WalletApi
import com.fares.saveme.feature.wallets.data.WalletRepository
import com.fares.saveme.feature.wallets.data.local.WalletEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The sweep queries on real Room tables never remove a row with a waiting change, a placeholder or a carried transaction (spec 009 FR-007). */
@RunWith(RobolectricTestRunner::class)
class FeedSweepRoomTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    private val stamp = "2026-10-01T09:00:00Z"
    private val client = AuthTestEnv { error("no request in this test") }.client

    private suspend fun queued(type: String, id: String) {
        db.pendingChangeDao().upsert(
            PendingChangeEntity(type, id, "u1", "user", "u1", 2, "{}", false, false, 0, 1, "Waiting", 0, null, null, null, stamp),
        )
    }

    private suspend fun hasQueued(type: String, id: String) = db.pendingChangeDao().get(type, id) != null

    @Test
    fun theCategorySweepKeepsQueuedAndPlaceholderRows() = runTest {
        val repo = CategoryRepository(CategoryApi(client), db.categoryDao(), db.platformCategoryDao(), db.syncScopeDao(), { "u1" }, { hasQueued("category", it) })
        fun cat(id: String, version: Long) = CategoryEntity(id, "u1", "expense", id, null, null, null, false, version, 1, stamp, stamp, stamp)
        db.categoryDao().upsertAll(listOf(cat("seen", 2), cat("gone", 2), cat("queued", 2), cat("placeholder", 0)))
        queued("category", "queued")
        repo.sweepOwn(setOf("seen"))
        assertEquals(setOf("seen", "queued", "placeholder"), db.categoryDao().getAll().map { it.id }.toSet())
    }

    @Test
    fun theWalletRecordSweepKeepsAQueuedOrPlaceholderWallet() = runTest {
        val repo = WalletRepository(WalletApi(client), db.walletDao(), db.walletMemberDao(), db.syncScopeDao(), { "u1" }, { hasQueued("wallet", it) })
        fun wallet(id: String, version: Long) = WalletEntity(id, "u1", "cash", id, "EGP", 1, 1, null, null, null, false, "owner", version, 1, stamp, stamp, stamp)
        db.walletDao().upsertAll(listOf(wallet("gone", 2), wallet("queued", 2), wallet("placeholder", 0)))
        queued("wallet", "queued")
        repo.sweepWallet("gone", emptySet())
        repo.sweepWallet("queued", emptySet())
        repo.sweepWallet("placeholder", emptySet())
        assertEquals(setOf("queued", "placeholder"), db.walletDao().getAll().map { it.id }.toSet())
    }

    @Test
    fun theTransactionSweepKeepsQueuedPlaceholderAndCarriedRowsAndOtherWalletsRows() = runTest {
        val repo = TransactionRepository(db.transactionDao(), db.syncScopeDao(), { "u1" }, { hasQueued("transaction", it) })
        fun tx(id: String, wallet: String = "w1", counter: String? = null, version: Long = 2, confirmed: String? = null) = TransactionEntity(
            id, "u1", wallet, counter, "expense", "2026-10-01", 100, "EGP", "c1", null, null, null, false, "u1", null, null, version, 1,
            stamp, stamp, null, stamp, if (confirmed != null) -100 else 0, 0, confirmed,
        )
        db.transactionDao().upsertAll(
            listOf(
                tx("seen"), tx("gone"), tx("queued"), tx("placeholder", version = 0), tx("carried", confirmed = "2026-10-02T10:00:00.000Z"),
                tx("into", wallet = "w2", counter = "w1"), tx("elsewhere", wallet = "w2"),
            ),
        )
        queued("transaction", "queued")
        repo.sweepWallet("w1", setOf("seen"))
        assertEquals(setOf("seen", "queued", "placeholder", "carried", "elsewhere"), db.transactionDao().getAll().map { it.id }.toSet())
    }

    @Test
    fun theWalletListsOldestReadIsMissingWhenAnAcceptedWalletWasNeverRead() = runTest {
        val dao = db.walletDao()
        fun wallet(id: String, version: Long) = WalletEntity(id, "u1", "cash", id, "EGP", 1, 1, null, null, null, false, "owner", version, 1, stamp, stamp, stamp)
        assertNull(dao.observeOldestFeedRead().first())
        dao.upsertAll(listOf(wallet("a", 2), wallet("b", 2), wallet("placeholder", 0)))
        assertEquals("", dao.observeOldestFeedRead().first())
        db.syncScopeDao().markFeedRead("wallet:a", "2026-10-02T10:00:00.000Z")
        assertEquals("b was never read", "", dao.observeOldestFeedRead().first())
        db.syncScopeDao().markFeedRead("wallet:b", "2026-10-02T09:00:00.000Z")
        assertEquals("the placeholder does not count", "2026-10-02T09:00:00.000Z", dao.observeOldestFeedRead().first())
    }

    @Test
    fun theOwnMembershipQueryFindsOnlyThePersonsWallets() = runTest {
        val members = db.walletMemberDao()
        members.insertAll(
            listOf(
                com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m1", "w1", "u1", null, "owner", 1, stamp),
                com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m2", "w1", "u2", null, "editor", 1, stamp),
                com.fares.saveme.feature.wallets.data.local.WalletMemberEntity("m3", "w2", "u2", null, "owner", 1, stamp),
            ),
        )
        assertEquals(listOf("w1"), members.ownWalletIds("u1"))
    }
}
