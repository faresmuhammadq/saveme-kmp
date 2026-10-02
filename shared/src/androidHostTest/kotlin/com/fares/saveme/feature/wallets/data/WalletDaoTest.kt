package com.fares.saveme.feature.wallets.data

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.feature.wallets.data.local.WalletEntity
import com.fares.saveme.feature.wallets.data.local.WalletMemberEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The SQL behind the wallet reads, on a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
class WalletDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    private fun wallet(id: String, created: String, archived: String? = null, account: String = "u1", name: String = id) = WalletEntity(
        id, account, "cash", name, "EGP", 100, 100, null, null, archived, false, "owner", 1, 1, created, created, created,
    )

    private fun member(id: String, wallet: String, created: String) = WalletMemberEntity(id, wallet, "u-$id", null, "editor", 1, created)

    @Test
    fun activeWalletsByCreationThenArchivedOnes() = runTest {
        val dao = db.walletDao()
        dao.upsertAll(
            listOf(
                wallet("b", "2026-09-02T00:00:00Z"),
                wallet("old-archived", "2026-08-01T00:00:00Z", archived = "2026-09-05T00:00:00Z"),
                wallet("a", "2026-09-01T00:00:00Z"),
                wallet("other", "2026-09-01T00:00:00Z", account = "u2"),
            ),
        )
        assertEquals(listOf("a", "b", "old-archived"), dao.observeAll("u1").first().map { it.id })
    }

    @Test
    fun upsertReplacesAndDeleteRemoves() = runTest {
        val dao = db.walletDao()
        dao.upsert(wallet("a", "2026-09-01T00:00:00Z", name = "Old"))
        dao.upsert(wallet("a", "2026-09-01T00:00:00Z", name = "New"))
        assertEquals("New", dao.get("a")!!.name)
        dao.delete("a")
        assertNull(dao.get("a"))
    }

    @Test
    fun replacingMembersSwapsTheWholeSet() = runTest {
        val dao = db.walletMemberDao()
        dao.replace("w1", listOf(member("m1", "w1", "2026-09-01T00:00:00Z"), member("m2", "w1", "2026-09-02T00:00:00Z")))
        dao.replace("w2", listOf(member("m3", "w2", "2026-09-01T00:00:00Z")))
        assertEquals(listOf("m1", "m2"), dao.observe("w1").first().map { it.id })
        dao.replace("w1", listOf(member("m4", "w1", "2026-09-03T00:00:00Z")))
        assertEquals(listOf("m4"), dao.observe("w1").first().map { it.id })
        assertEquals(listOf("m3"), dao.observe("w2").first().map { it.id })
    }

    @Test
    fun deleteAllEmptiesBothTables() = runTest {
        db.walletDao().upsert(wallet("a", "2026-09-01T00:00:00Z"))
        db.walletMemberDao().replace("a", listOf(member("m1", "a", "2026-09-01T00:00:00Z")))
        db.walletMemberDao().deleteAll()
        db.walletDao().deleteAll()
        assertEquals(emptyList<WalletEntity>(), db.walletDao().getAll())
        assertEquals(emptyList<WalletMemberEntity>(), db.walletMemberDao().get("a"))
    }
}
