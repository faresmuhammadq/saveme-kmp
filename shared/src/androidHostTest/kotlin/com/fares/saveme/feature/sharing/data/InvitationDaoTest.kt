package com.fares.saveme.feature.sharing.data

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.feature.sharing.data.local.InvitationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The invitation table's SQL (spec 013 data-model §1); the repository's logic is tested on the fake. */
@RunWith(RobolectricTestRunner::class)
class InvitationDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val dao = db.invitationDao()

    @After
    fun close() = db.close()

    private fun row(id: String, wallet: String = "w1", account: String = "u1", created: String = "2026-10-02T10:00:00Z", expires: String? = "2026-10-09T10:00:00Z", status: String = "pending") =
        InvitationEntity(id, account, wallet, "viewer", "link", null, account, status, expires, 1, 1, created, created, created)

    @Test
    fun pendingRowsOfAWalletAndAccountComeOldestFirstThenById() = runTest {
        dao.upsert(row("c", created = "2026-10-02T12:00:00Z"))
        dao.upsert(row("b", created = "2026-10-02T10:00:00Z"))
        dao.upsert(row("a", created = "2026-10-02T10:00:00Z"))
        dao.upsert(row("other-wallet", wallet = "w2"))
        dao.upsert(row("other-account", account = "u2"))
        dao.upsert(row("closed", status = "revoked"))
        assertEquals(listOf("a", "b", "c"), dao.observePending("u1", "w1").first().map { it.id })
    }

    @Test
    fun getUpsertAndDeleteByIdAndForAWallet() = runTest {
        dao.upsert(row("a"))
        dao.upsert(row("a").copy(role = "editor", version = 2))
        assertEquals("editor", dao.get("a")!!.role)
        dao.upsert(row("b", wallet = "w2"))
        dao.deleteForWallet("w1")
        assertNull(dao.get("a"))
        assertEquals(listOf("b"), dao.getAll().map { it.id })
        dao.delete("b")
        assertTrue(dao.getAll().isEmpty())
        dao.upsert(row("c"))
        dao.deleteAll()
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun theLivePendingCountCountsUnexpiredAndNullExpiryAtAnInstant() = runTest {
        dao.upsert(row("live", expires = "2026-10-09T10:00:00Z"))
        dao.upsert(row("placeholder", expires = null))
        dao.upsert(row("expired", expires = "2026-10-02T09:59:59Z"))
        dao.upsert(row("edge", expires = "2026-10-02T10:00:00Z"))
        dao.upsert(row("closed", status = "accepted"))
        dao.upsert(row("other", wallet = "w2"))
        assertEquals(2, dao.observeLivePendingCount("u1", "w1", "2026-10-02T10:00:00Z").first())
        assertEquals(4, dao.observeLivePendingCount("u1", "w1", "2026-10-02T09:00:00Z").first())
        assertEquals(1, dao.observeLivePendingCount("u1", "w1", "2026-10-10T00:00:00Z").first())
    }

    @Test
    fun theEntityHasNoSecretLikeColumnAndPrintsOnlyIdAndVersion() {
        val entity = row("a").copy(inviteeEmail = "karim@example.com")
        assertEquals("InvitationEntity(a, version=1)", entity.toString())
        val fields = InvitationEntity::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fields.toString(), fields.none { "token" in it || "url" in it || "secret" in it || "link" in it })
    }
}
