package com.fares.saveme.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.sync.PendingChangeEntity
import com.fares.saveme.feature.account.data.local.DeviceSessionEntity
import com.fares.saveme.feature.account.data.local.ProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The SQL behind the invariants in spec §4, on a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
class AccountDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    private fun profile(name: String?, version: Long = 1) = ProfileEntity(
        userId = "u1", id = "p1", version = version, displayName = name, locale = "en", timezone = "Africa/Cairo",
        defaultCurrency = "EGP", defaultCurrencyLocked = false, budgetPeriodStartDay = 1,
        budgetPeriodStartDayFrom = null, refreshedAt = "2026-09-28T10:00:00Z",
    )

    /** Spec 004: a waiting edit lives in pending_change, so a refresh of the server copy can't touch it. */
    @Test
    fun refreshNeverOverwritesAPendingEdit() = runTest {
        val dao = db.profileDao()
        dao.upsert(profile("Sara"))
        db.pendingChangeDao().upsert(pending("p1", """{"display_name":"Sara M"}""", baseVersion = 1))

        dao.updateServerCopy("u1", "p1", 2, "Sara (web)", "ar", "Africa/Cairo", "EGP", true, 5, null, "2026-09-28T11:00:00Z")

        val row = dao.get()!!
        assertEquals("Sara (web)", row.displayName)
        assertEquals(2L, row.version)
        val change = db.pendingChangeDao().get("profile", "p1")!!
        assertEquals("""{"display_name":"Sara M"}""", change.payloadJson)
        assertEquals(1L, change.baseVersion)
    }

    @Test
    fun sessionsAreReplacedWholeAndOrderedNewestFirst() = runTest {
        val dao = db.deviceSessionDao()
        dao.replaceAll(listOf(session("a", "2026-09-01T00:00:00Z"), session("b", "2026-09-02T00:00:00Z")))
        dao.replaceAll(listOf(session("c", "2026-09-03T00:00:00Z"), session("b", "2026-09-02T00:00:00Z")))
        assertEquals(listOf("c", "b"), dao.observeAll().first().map { it.id })
    }

    @Test
    fun clearAllTablesEmptiesEverything() = runTest {
        db.profileDao().upsert(profile("Sara"))
        db.deviceSessionDao().replaceAll(listOf(session("a", "2026-09-01T00:00:00Z")))
        db.syncScopeDao().upsert(SyncScopeEntity(SyncScopeEntity.PROFILE, "2026-09-28T10:00:00Z"))
        db.pendingChangeDao().upsert(pending("p1", "{}", baseVersion = 1))

        DatabaseCleaner(db).clear()

        assertNull(db.profileDao().get())
        assertEquals(emptyList<DeviceSessionEntity>(), db.deviceSessionDao().observeAll().first())
        assertNull(db.syncScopeDao().observe(SyncScopeEntity.PROFILE).first())
        assertNull(db.pendingChangeDao().get("profile", "p1"))
    }

    private fun pending(id: String, payload: String, baseVersion: Long) = PendingChangeEntity(
        recordType = "profile", recordId = id, accountId = "u1", scopeKind = "user", scopeId = "u1",
        baseVersion = baseVersion, payloadJson = payload, isDelete = false, restore = false, revision = 0,
        createdOrder = 1, state = "Waiting", attempts = 0, nextAttemptAt = null, clashJson = null,
        refusalJson = null, updatedAt = "2026-09-28T10:00:00Z",
    )

    private fun session(id: String, signedInAt: String) = DeviceSessionEntity(
        id = id, userId = "u1", deviceName = "Pixel", platform = "android", appVersion = "1.0.0",
        state = "active", signedInAt = signedInAt, lastUsedAt = null, current = id == "a",
    )
}
