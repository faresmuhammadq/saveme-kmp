package com.fares.saveme.feature.budgets.data

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.feature.budgets.data.local.BudgetEntity
import com.fares.saveme.feature.budgets.data.local.BudgetProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The budget queries and the progress cache against real SQLite (the fakes in commonTest follow these). */
@RunWith(RobolectricTestRunner::class)
class BudgetDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .build()

    @After
    fun close() = db.close()

    private fun budget(id: String, account: String = "u1", archived: String? = null, created: String = "2026-09-01T00:00:00Z", category: String? = "c1") =
        BudgetEntity(id, account, category, "monthly", 600_000, "EGP", archived, 1, 7, created, created, created)

    private fun progress(id: String, account: String = "u1", spent: Long = 10) =
        BudgetProgressEntity(id, account, "2026-10-01", "2026-10-31", 31, 600_000, spent, "EGP", "", spent, "", "2026-10-02T09:00:00Z")

    @Test
    fun budgetsAreReadPerAccountOldestFirstAndSplitByArchived() = runTest {
        val dao = db.budgetDao()
        dao.upsert(budget("late", created = "2026-09-10T00:00:00Z"))
        dao.upsert(budget("early", created = "2026-09-01T00:00:00Z"))
        dao.upsert(budget("same", created = "2026-09-01T00:00:00Z", category = null))
        dao.upsert(budget("old", archived = "2026-09-20T00:00:00Z"))
        dao.upsert(budget("other", account = "u2"))
        assertEquals(listOf("early", "old", "same", "late"), dao.observeAll("u1").first().map { it.id })
        assertEquals(listOf("early", "same", "late"), dao.observeActive("u1").first().map { it.id })
        assertEquals(listOf("old"), dao.observeArchived("u1").first().map { it.id })
        assertEquals(listOf("other"), dao.observeAll("u2").first().map { it.id })
        assertNull(dao.get("none"))
        assertEquals("early", dao.observe("early").first()!!.id)
    }

    @Test
    fun anUpsertReplacesAndDeleteWithProgressRemovesBothInOneTransaction() = runTest {
        val dao = db.budgetDao()
        dao.upsert(budget("b1"))
        dao.upsert(budget("b1").copy(amountMinor = 7))
        db.budgetProgressDao().upsertAll(listOf(progress("b1"), progress("b2")))
        assertEquals(7L, dao.get("b1")!!.amountMinor)
        dao.deleteWithProgress("b1")
        assertNull(dao.get("b1"))
        assertNull(db.budgetProgressDao().get("b1"))
        assertEquals("another budget's progress stays", 10L, db.budgetProgressDao().get("b2")!!.spentMinor)
    }

    @Test
    fun aWalkReplacesTheRowsOfNonArchivedBudgetsAndKeepsTheArchivedOnes() = runTest {
        val dao = db.budgetProgressDao()
        dao.upsertAll(listOf(progress("keep", spent = 1), progress("gone", spent = 2), progress("archived", spent = 3), progress("ghost", spent = 4), progress("elsewhere", account = "u2", spent = 5)))
        dao.replaceWalk("u1", listOf(progress("keep", spent = 100), progress("new", spent = 200)), archivedIds = listOf("archived"))
        assertEquals(setOf("keep", "new", "archived", "elsewhere"), dao.getAll().map { it.budgetId }.toSet())
        assertEquals(100L, dao.get("keep")!!.spentMinor)
        assertEquals(3L, dao.get("archived")!!.spentMinor)
        assertEquals("another account's rows are never touched", 5L, dao.get("elsewhere")!!.spentMinor)
    }

    @Test
    fun anEmptyWalkClearsEveryRowExceptTheArchivedBudgetsOnes() = runTest {
        val dao = db.budgetProgressDao()
        dao.upsertAll(listOf(progress("a"), progress("old")))
        dao.replaceWalk("u1", emptyList(), archivedIds = listOf("old"))
        assertEquals(listOf("old"), dao.getAll().map { it.budgetId })
        dao.replaceWalk("u1", emptyList(), archivedIds = emptyList())
        assertEquals(emptyList<BudgetProgressEntity>(), dao.getAll())
    }

    @Test
    fun progressIsObservedPerAccountAndDeleteAllEmptiesBothTables() = runTest {
        db.budgetProgressDao().upsertAll(listOf(progress("a"), progress("b", account = "u2")))
        assertEquals(listOf("a"), db.budgetProgressDao().observeAll("u1").first().map { it.budgetId })
        db.budgetDao().upsert(budget("a"))
        db.budgetDao().deleteAll()
        db.budgetProgressDao().deleteAll()
        assertEquals(emptyList<BudgetEntity>(), db.budgetDao().getAll())
        assertEquals(emptyList<BudgetProgressEntity>(), db.budgetProgressDao().getAll())
    }

    @Test
    fun entitiesNeverPrintTheirAmounts() {
        assertEquals("BudgetEntity(b1, version=1)", budget("b1").toString())
        assertEquals("BudgetProgressEntity(b1)", progress("b1").toString())
    }
}
