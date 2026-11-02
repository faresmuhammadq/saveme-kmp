package com.fares.saveme.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetEntity
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetEntryEntity
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetLineEntity
import com.fares.saveme.feature.sharedbudgets.data.local.SharedBudgetMemberEntity
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
 * v10 → v11 (backend ADR-0027): the four Shared Budget tables, `transactions.shared_budget_id`, and the person's own feed place
 * forgotten once (memberships ride the user feed, which skipped `shared_budget_member` rows until now). Wallet scopes and every other
 * row are kept. The v10 file is built from the exported `schemas/…/10.json`, so Room validates the result against v11.
 */
@RunWith(RobolectricTestRunner::class)
class Migration10To11Test {

    private val context = RuntimeEnvironment.getApplication()
    private val schemas = "schemas/com.fares.saveme.core.database.AppDatabase"

    private fun v10Database(): File {
        val schema = Json.parseToJsonElement(File("$schemas/10.json").readText()).jsonObject.getValue("database").jsonObject
        val file = File(context.cacheDir, "migration1011-${System.nanoTime()}.db")
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
            execSQL("INSERT INTO sync_scope VALUES ('profile','2026-10-01T10:00:00Z',NULL,NULL,NULL,NULL)")
            execSQL("INSERT INTO sync_scope VALUES ('user:u1','2026-10-01T10:00:00Z','2026-10-01T09:00:00Z','user-cursor','2026-10-02T08:00:00.000Z',41)")
            execSQL("INSERT INTO sync_scope VALUES ('wallet:w1','2026-10-01T10:00:00Z','2026-10-01T09:30:00Z','wallet-cursor','2026-10-02T08:30:00.000Z',812)")
            execSQL("PRAGMA user_version = 10")
            close()
        }
        return file
    }

    private fun open(file: File) = Room.databaseBuilder<AppDatabase>(context, file.path)
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(MIGRATION_10_11)
        .build()

    @Test
    fun theUserScopesPlaceIsForgottenOnceAndEveryWalletScopeKeepsItsOwn() = runTest {
        val db = open(v10Database())
        val dao = db.syncScopeDao()
        val user = dao.observe("user:u1").first()!!
        assertNull(user.feedCursor)
        assertNull(user.feedReadAt)
        assertNull(user.feedPosition)
        assertEquals("the push stamp is kept", "2026-10-01T09:00:00Z", user.lastPushedAt)
        val wallet = dao.observe("wallet:w1").first()!!
        assertEquals("wallet-cursor", wallet.feedCursor)
        assertEquals(812L, wallet.feedPosition)
        assertNotNull("a scope that is not a feed scope is untouched", dao.observe("profile").first())
        db.close()
    }

    @Test
    fun everyOtherRowIsKept() = runTest {
        val db = open(v10Database())
        assertEquals("Sara", db.profileDao().get()!!.displayName)
        assertEquals("Pocket", db.walletDao().get("w1")!!.name)
        db.close()
    }

    @Test
    fun theSharedBudgetTablesExistWithTheirColumnsAndRoundTripRows() = runTest {
        val db = open(v10Database())
        val dao = db.sharedBudgetDao()
        dao.upsert(SharedBudgetEntity("s1", "u1", "Home", "EGP", 1, "Africa/Cairo", 2_000_000, null, "[]", "[]", 1, 5, "t", "t", "t"))
        dao.upsertMember(SharedBudgetMemberEntity("m1", "s1", "u1", "Sara", "owner", false, 1, 2, "t"))
        dao.upsertLine(SharedBudgetLineEntity("l1", "s1", "c1", 500_000, "Groceries", null, null, "[]", 1, 3, "t"))
        dao.upsertEntry(SharedBudgetEntryEntity("e1", "s1", "u1", "c1", "2026-10-03", 12_500, "Milk", null, 1, 4, "t"))
        assertEquals(listOf("s1"), dao.observeMine("u1").first().map { it.id })
        assertEquals(listOf("m1"), dao.observeMembers("s1").first().map { it.id })
        assertEquals(listOf("l1"), dao.observeLines("s1").first().map { it.id })
        assertEquals(listOf("e1"), dao.observeEntries("s1", "2026-10-01", "2026-10-31").first().map { it.id })
        dao.deleteBudgetWithChildren("s1")
        assertNull(dao.get("s1"))
        assertTrue(dao.entries("s1").isEmpty() && dao.lines("s1").isEmpty() && dao.members("s1").isEmpty())
        db.close()
    }

    @Test
    fun aTransactionCarriesTheBudgetItCountsToward() = runTest {
        val db = open(v10Database())
        val connection = AndroidSQLiteDriver().open(context.getDatabasePath("x").path.let { File(context.cacheDir, "probe-${System.nanoTime()}.db").path })
        connection.close()
        val columns = Json.parseToJsonElement(File("$schemas/11.json").readText()).jsonObject.getValue("database").jsonObject
            .getValue("entities").jsonArray.map { it.jsonObject }.first { it.getValue("tableName").jsonPrimitive.content == "transactions" }
            .getValue("fields").jsonArray.map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
        assertTrue(columns.toString(), "shared_budget_id" in columns)
        db.close()
    }

    @Test
    fun theExportedSchemaOnlyAddsTheFourTablesAndTheTwoColumns() {
        fun tables(version: Int): Map<String, JsonObject> = Json.parseToJsonElement(File("$schemas/$version.json").readText())
            .jsonObject.getValue("database").jsonObject.getValue("entities").jsonArray.map { it.jsonObject }
            .associateBy { it.getValue("tableName").jsonPrimitive.content }
        val before = tables(10)
        val after = tables(11)
        assertEquals(before.keys + setOf("shared_budget", "shared_budget_member", "shared_budget_line", "shared_budget_entry"), after.keys)
        (before.keys - "transactions" - "recurring_rule").forEach { assertEquals(it, before[it], after[it]) }
        fun fields(table: JsonObject) = table.getValue("fields").jsonArray.map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
        assertEquals(fields(before.getValue("transactions")) + "shared_budget_id", fields(after.getValue("transactions")).let { all ->
            all.filter { it != "shared_budget_id" }.let { rest -> rest + "shared_budget_id" }
        })
        assertEquals(fields(before.getValue("recurring_rule")) + "shared_budget_id", fields(after.getValue("recurring_rule")).let { all ->
            all.filter { it != "shared_budget_id" } + "shared_budget_id"
        })
        val entry = fields(after.getValue("shared_budget_entry"))
        assertTrue(entry.toString(), entry.none { "wallet" in it || "memo" in it || "counterparty" in it || "transaction" in it })
    }
}
