package com.fares.saveme.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.fares.saveme.feature.budgets.data.local.BudgetEntity
import com.fares.saveme.feature.budgets.data.local.BudgetProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * v8 → v9 (spec 012, data-model §4, FR-020): the `budget` and `budget_progress` tables are added and the person's own feed place
 * (cursor, read stamp, integer position) is forgotten once; wallet scopes and every other row are kept. The v8 file is built from
 * the exported `schemas/…/8.json` (every table and index), so Room validates the result against v9.
 */
@RunWith(RobolectricTestRunner::class)
class Migration8To9Test {

    private val context = RuntimeEnvironment.getApplication()

    private fun v8Database(): File {
        val schema = Json.parseToJsonElement(File("schemas/com.fares.saveme.core.database.AppDatabase/8.json").readText())
            .jsonObject.getValue("database").jsonObject
        val file = File(context.cacheDir, "migration89-${System.nanoTime()}.db")
        AndroidSQLiteDriver().open(file.path).run {
            for (entity in schema.getValue("entities").jsonArray.map { it.jsonObject }) {
                val table = entity.getValue("tableName").jsonPrimitive.content
                execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                (entity["indices"] as? JsonArray)?.map { it.jsonObject }?.forEach { index ->
                    execSQL(index.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            execSQL("INSERT INTO profile VALUES ('u1','p1',3,'Sara','en','Africa/Cairo','EGP',0,25,NULL,'2026-10-01T10:00:00Z')")
            execSQL(
                "INSERT INTO wallet VALUES ('w1','u1','cash','Pocket','EGP',100,100,NULL,NULL,NULL,0,'owner',1,1," +
                    "'2026-10-01T10:00:00Z','2026-10-01T10:00:00Z','2026-10-01T10:00:00Z')",
            )
            execSQL(
                "INSERT INTO transactions VALUES ('t1','u1','w1',NULL,'expense','2026-10-01',4550,'EGP',NULL,NULL,NULL,NULL,0,'u1',NULL,NULL,2,7," +
                    "'2026-10-01T10:00:00Z','2026-10-01T10:00:00Z','2026-10-01T10:30:00Z','2026-10-01T10:00:00Z',0,0,NULL,0)",
            )
            execSQL(
                "INSERT INTO pending_change VALUES ('transaction','t1','u1','wallet','w1',2,'{}',0,0,0,1,'Waiting',0,NULL,NULL,NULL,'2026-10-01T10:00:00Z')",
            )
            execSQL("INSERT INTO sync_scope VALUES ('profile','2026-10-01T10:00:00Z',NULL,NULL,NULL,NULL)")
            execSQL("INSERT INTO sync_scope VALUES ('user:u1','2026-10-01T10:00:00Z','2026-10-01T09:00:00Z','user-cursor','2026-10-02T08:00:00.000Z',41)")
            execSQL("INSERT INTO sync_scope VALUES ('wallet:w1','2026-10-01T10:00:00Z','2026-10-01T09:30:00Z','wallet-cursor','2026-10-02T08:30:00.000Z',812)")
            execSQL("INSERT INTO sync_scope VALUES ('wallet:w2','2026-10-01T10:00:00Z',NULL,'wallet-two','2026-10-02T08:31:00.000Z',133)")
            execSQL("PRAGMA user_version = 8")
            close()
        }
        return file
    }

    private fun open(file: File) = Room.databaseBuilder<AppDatabase>(context, file.path)
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(MIGRATION_8_9, MIGRATION_9_10)
        .build()

    @Test
    fun theUserScopesPlaceIsForgottenAndTheWalletScopesLoseTheirsOnlyToTheNextMigration() = runTest {
        val db = open(v8Database())
        val dao = db.syncScopeDao()
        val user = dao.observe("user:u1").first()!!
        assertNull(user.feedCursor)
        assertNull(user.feedReadAt)
        assertNull(user.feedPosition)
        assertEquals("2026-10-01T09:00:00Z", user.lastPushedAt)
        assertEquals("2026-10-01T10:00:00Z", user.refreshedAt)
        assertNull(dao.feedPlace("user:u1")?.cursor)
        // The database opens at the current version, so 9 -> 10 also ran and forgot the wallet scopes' places (spec 013); Migration9To10Test
        // proves that step alone. 8 -> 9 itself leaves wallet scopes alone: its SQL names only `user:%`.
        for (scope in listOf("wallet:w1", "wallet:w2")) assertEquals(FeedPlace(null, null), dao.feedPlace(scope))
        assertEquals("2026-10-01T09:30:00Z", dao.observe("wallet:w1").first()!!.lastPushedAt)
        assertNotNull("a scope that is not a feed scope is untouched", dao.observe("profile").first())
        db.close()
    }

    @Test
    fun everyOtherRowIsKeptIncludingTheWaitingChange() = runTest {
        val db = open(v8Database())
        assertEquals("Sara", db.profileDao().get()!!.displayName)
        assertEquals(25, db.profileDao().get()!!.budgetPeriodStartDay)
        assertEquals("Pocket", db.walletDao().get("w1")!!.name)
        assertEquals(4_550L, db.transactionDao().get("t1")!!.amountMinor)
        assertNotNull(db.pendingChangeDao().get("transaction", "t1"))
        db.close()
    }

    @Test
    fun theBudgetTablesExistWithTheirColumnsAndRoundTripRows() = runTest {
        val db = open(v8Database())
        val budget = BudgetEntity("b1", "u1", null, "monthly", 600_000, "EGP", null, 0, 0, "2026-10-02T10:00:00Z", "2026-10-02T10:00:00Z", "2026-10-02T10:00:00Z")
        db.budgetDao().upsert(budget)
        db.budgetDao().upsert(budget.copy(id = "b2", categoryId = "c1", archivedAt = "2026-10-03T00:00:00Z", version = 3))
        val progress = BudgetProgressEntity("b1", "u1", "2026-10-01", "2026-10-31", 31, 600_000, 45_000, "EGP", "KWD,USD", 44_000, "USD", "2026-10-02T10:00:00Z")
        db.budgetProgressDao().upsertAll(listOf(progress))
        assertEquals(listOf(budget.copy()).size, db.budgetDao().observeActive("u1").first().size)
        assertEquals(budget.id, db.budgetDao().observeActive("u1").first().single().id)
        assertEquals("b2", db.budgetDao().observeArchived("u1").first().single().id)
        assertEquals(null, db.budgetDao().get("b1")!!.categoryId)
        assertEquals(progress, db.budgetProgressDao().get("b1"))
        db.close()
    }

    @Test
    fun theExportedSchemaHasTheTwoTablesAndTheIndexAndNothingElseChanged() {
        fun tables(version: Int): Map<String, JsonObject> = Json.parseToJsonElement(File("schemas/com.fares.saveme.core.database.AppDatabase/$version.json").readText())
            .jsonObject.getValue("database").jsonObject.getValue("entities").jsonArray.map { it.jsonObject }
            .associateBy { it.getValue("tableName").jsonPrimitive.content }
        val before = tables(8)
        val after = tables(9)
        assertEquals(before.keys + setOf("budget", "budget_progress"), after.keys)
        before.keys.forEach { assertEquals(it, before[it], after[it]) }
        val budget = after.getValue("budget")
        assertEquals(
            listOf("id", "account_id", "category_id", "period_type", "amount_minor", "currency", "archived_at", "version", "change_seq", "created_at", "updated_at", "refreshed_at"),
            budget.getValue("fields").jsonArray.map { it.jsonObject.getValue("columnName").jsonPrimitive.content },
        )
        assertEquals(
            listOf("index_budget_account_id_archived_at"),
            budget.getValue("indices").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content },
        )
        assertEquals(
            listOf("budget_id", "account_id", "period_start", "period_end", "days_in_period", "limit_minor", "spent_minor", "currency", "other_currencies", "local_spent_minor", "local_other_currencies", "read_at"),
            after.getValue("budget_progress").getValue("fields").jsonArray.map { it.jsonObject.getValue("columnName").jsonPrimitive.content },
        )
        assertTrue(File("schemas/com.fares.saveme.core.database.AppDatabase/9.json").exists())
    }
}
