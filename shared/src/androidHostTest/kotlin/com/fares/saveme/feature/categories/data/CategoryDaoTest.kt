package com.fares.saveme.feature.categories.data

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.feature.categories.data.local.CategoryEntity
import com.fares.saveme.feature.categories.data.local.CategoryReferenceEntity
import com.fares.saveme.feature.categories.data.local.PlatformCategoryEntity
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

/** The SQL behind the category reads, on a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
class CategoryDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    private fun category(id: String, account: String = "u1", kind: String = "expense", name: String = id, created: String = "2026-09-01T00:00:00Z") =
        CategoryEntity(id, account, kind, name, null, null, null, false, 1, 1, created, created, created)

    private fun platform(id: String, kind: String, position: Int) =
        PlatformCategoryEntity(id, "key_$id", kind, "ع", "en", "cart", "#2E7D32", position, null, "2026-09-01T00:00:00Z")

    private fun reference(id: String, wallet: String, category: String) =
        CategoryReferenceEntity(id, wallet, category, "u2", "expense", "Partner", null, null, 1, 1, "2026-09-01T00:00:00Z")

    @Test
    fun ownCategoriesAreReadPerAccountInCreationOrder() = runTest {
        val dao = db.categoryDao()
        dao.upsertAll(
            listOf(
                category("b", created = "2026-09-02T00:00:00Z"), category("a", created = "2026-09-01T00:00:00Z"),
                category("other", account = "u2"), category("inc", kind = "income", created = "2026-09-03T00:00:00Z"),
            ),
        )
        val own = dao.observeAll("u1").first()
        assertEquals(listOf("a", "b", "inc"), own.map { it.id })
        assertEquals(listOf("expense", "expense", "income"), own.map { it.kind })
    }

    @Test
    fun upsertReplacesMarkInUseAndDeleteRemoves() = runTest {
        val dao = db.categoryDao()
        dao.upsert(category("a", name = "Old"))
        dao.upsert(category("a", name = "New"))
        assertEquals("New", dao.get("a")!!.name)
        dao.markInUse("a")
        assertTrue(dao.get("a")!!.inUse)
        dao.delete("a")
        assertNull(dao.get("a"))
    }

    @Test
    fun theDefaultSetIsReplacedAsAWholeInPlatformOrder() = runTest {
        val dao = db.platformCategoryDao()
        dao.replaceAll(listOf(platform("g2", "expense", 2), platform("g1", "expense", 1), platform("s", "income", 20)))
        assertEquals(listOf("g1", "g2", "s"), dao.observeAll().first().map { it.id })
        dao.replaceAll(listOf(platform("n1", "expense", 1)))
        assertEquals(listOf("n1"), dao.getAll().map { it.id })
    }

    @Test
    fun referencesAreReplacedPerWalletAndAreUniquePerWalletAndCategory() = runTest {
        val dao = db.categoryReferenceDao()
        dao.replace("w1", listOf(reference("r1", "w1", "p1"), reference("r2", "w1", "p2")))
        dao.replace("w2", listOf(reference("r3", "w2", "p1")))
        assertEquals(listOf("r1", "r2"), dao.observe("w1").first().map { it.id })
        dao.replace("w1", listOf(reference("r4", "w1", "p9")))
        assertEquals(listOf("r4"), dao.observe("w1").first().map { it.id })
        assertEquals(listOf("r3"), dao.observe("w2").first().map { it.id })
        val duplicate = runCatching { dao.insertAll(listOf(reference("r5", "w2", "p1"))) }
        assertTrue("a second reference for the same wallet and category is refused", duplicate.isFailure)
        dao.deleteFor("w2")
        assertEquals(0, dao.get("w2").size)
    }

    @Test
    fun deleteAllEmptiesEachTable() = runTest {
        db.categoryDao().upsert(category("a"))
        db.platformCategoryDao().replaceAll(listOf(platform("g", "expense", 1)))
        db.categoryReferenceDao().replace("w1", listOf(reference("r1", "w1", "p1")))
        db.categoryDao().deleteAll()
        db.platformCategoryDao().deleteAll()
        db.categoryReferenceDao().deleteAll()
        assertEquals(emptyList<CategoryEntity>(), db.categoryDao().getAll())
        assertEquals(emptyList<PlatformCategoryEntity>(), db.platformCategoryDao().getAll())
        assertEquals(emptyList<CategoryReferenceEntity>(), db.categoryReferenceDao().observeAll().first())
    }
}
