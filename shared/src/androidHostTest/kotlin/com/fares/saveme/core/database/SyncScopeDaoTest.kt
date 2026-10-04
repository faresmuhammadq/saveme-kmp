package com.fares.saveme.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The scope table's SQL for feed positions (spec 009 FR-003, FR-028, data-model §1). */
@RunWith(RobolectricTestRunner::class)
class SyncScopeDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val dao = db.syncScopeDao()

    @After
    fun close() = db.close()

    @Test
    fun aPositionAndAReadStampAreStoredWithoutTouchingTheLegacyOrPushStamps() = runTest {
        dao.upsert(SyncScopeEntity("wallet:w1", "2026-09-30T08:00:00Z", "2026-10-01T09:00:00.000Z"))
        dao.storeFeedPage("wallet:w1", "c-1", null)
        dao.markFeedRead("wallet:w1", "2026-10-02T10:00:00.000Z")
        val row = dao.observe("wallet:w1").first()!!
        assertEquals("c-1", row.feedCursor)
        assertEquals("2026-10-02T10:00:00.000Z", row.feedReadAt)
        assertEquals("2026-09-30T08:00:00Z", row.refreshedAt)
        assertEquals("2026-10-01T09:00:00.000Z", row.lastPushedAt)
        assertEquals("c-1", dao.feedCursor("wallet:w1"))
        assertEquals("2026-10-02T10:00:00.000Z", dao.observeFeedReadAt("wallet:w1").first())
    }

    @Test
    fun aScopeThatWasNeverReadHasNoPositionAndStoringOneCreatesTheRow() = runTest {
        assertNull(dao.feedCursor("user:u1"))
        assertNull(dao.observeFeedReadAt("user:u1").first())
        dao.storeFeedPage("user:u1", "c-1", null)
        assertEquals("", dao.observe("user:u1").first()!!.refreshedAt)
        assertEquals("c-1", dao.feedCursor("user:u1"))
    }

    @Test
    fun clearingAScopeNullsBothAndLeavesTheOtherColumnsAndScopes() = runTest {
        dao.markPushed("wallet:w1", "2026-10-01T09:00:00.000Z")
        dao.storeFeedPage("wallet:w1", "c-1", null)
        dao.markFeedRead("wallet:w1", "2026-10-02T10:00:00.000Z")
        dao.storeFeedPage("wallet:w2", "c-2", null)

        dao.clearFeed("wallet:w1")

        val cleared = dao.observe("wallet:w1").first()!!
        assertNull(cleared.feedCursor)
        assertNull(cleared.feedReadAt)
        assertEquals("2026-10-01T09:00:00.000Z", cleared.lastPushedAt)
        assertEquals("c-2", dao.feedCursor("wallet:w2"))
    }

    @Test
    fun aScopeCreatedByAPushHasNoPositionAndAPushKeepsAnExistingOne() = runTest {
        dao.markPushed("wallet:w1", "2026-10-01T09:00:00.000Z")
        assertNull(dao.feedCursor("wallet:w1"))
        dao.storeFeedPage("wallet:w1", "c-1", null)
        dao.markPushed("wallet:w1", "2026-10-02T09:00:00.000Z")
        assertEquals("c-1", dao.feedCursor("wallet:w1"))
        assertNotNull(dao.observe("wallet:w1").first()!!.lastPushedAt)
    }

    @Test
    fun signOutRemovesEveryPosition() = runTest {
        dao.storeFeedPage("user:u1", "c-1", null)
        dao.storeFeedPage("wallet:w1", "c-2", null)
        dao.markFeedRead("user:u1", "2026-10-02T10:00:00.000Z")
        dao.deleteAll()
        assertNull(dao.feedCursor("user:u1"))
        assertNull(dao.feedCursor("wallet:w1"))
        assertNull(dao.observeFeedReadAt("user:u1").first())
    }

    @Test
    fun aPageStoresItsCursorAndPositionTogetherAndKeepsTheOtherStamps() = runTest {
        dao.upsert(SyncScopeEntity("wallet:w1", "2026-10-01T08:00:00Z", lastPushedAt = "2026-10-01T09:00:00.000Z"))
        dao.markFeedRead("wallet:w1", "2026-10-02T10:00:00.000Z")
        dao.storeFeedPage("wallet:w1", "c-1", 17L)
        assertEquals(FeedPlace("c-1", 17L), dao.feedPlace("wallet:w1"))
        val row = dao.observe("wallet:w1").first()!!
        assertEquals("2026-10-01T08:00:00Z", row.refreshedAt)
        assertEquals("2026-10-01T09:00:00.000Z", row.lastPushedAt)
        assertEquals("2026-10-02T10:00:00.000Z", row.feedReadAt)
    }

    @Test
    fun aPageWithoutAPositionOverwritesAnEarlierOne() = runTest {
        dao.storeFeedPage("wallet:w1", "c-1", 17L)
        dao.storeFeedPage("wallet:w1", "c-2", null)
        assertEquals(FeedPlace("c-2", null), dao.feedPlace("wallet:w1"))
    }

    @Test
    fun clearingTheFeedForgetsCursorPositionAndReadStamp() = runTest {
        dao.storeFeedPage("wallet:w1", "c-1", 17L)
        dao.markFeedRead("wallet:w1", "2026-10-02T10:00:00.000Z")
        dao.clearFeed("wallet:w1")
        assertEquals(FeedPlace(null, null), dao.feedPlace("wallet:w1"))
        assertNull(dao.observeFeedReadAt("wallet:w1").first())
    }

    @Test
    fun aScopeNeverReadHasNoPlace() = runTest {
        assertNull(dao.feedPlace("wallet:nobody"))
    }

    @Test
    fun deletingEveryScopeRemovesThePositionsToo() = runTest {
        dao.storeFeedPage("user:u1", "c-1", 3L)
        dao.storeFeedPage("wallet:w1", "c-2", 4L)
        dao.deleteAll()
        assertNull(dao.feedPlace("user:u1"))
        assertNull(dao.feedPlace("wallet:w1"))
    }
}
