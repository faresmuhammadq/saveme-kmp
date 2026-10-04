package com.fares.saveme.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
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
 * v7 → v8 (spec 011, data-model §1): `sync_scope.feed_position` is added and nothing else moves. The v7 file is built
 * from the exported `schemas/…/7.json` (every table and index), so Room validates the result against v8.
 */
@RunWith(RobolectricTestRunner::class)
class Migration7To8Test {

    private val context = RuntimeEnvironment.getApplication()

    private fun v7Database(): File {
        val schema = Json.parseToJsonElement(File("schemas/com.fares.saveme.core.database.AppDatabase/7.json").readText())
            .jsonObject.getValue("database").jsonObject
        val file = File(context.cacheDir, "migration78-${System.nanoTime()}.db")
        AndroidSQLiteDriver().open(file.path).run {
            for (entity in schema.getValue("entities").jsonArray.map { it.jsonObject }) {
                val table = entity.getValue("tableName").jsonPrimitive.content
                execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                (entity["indices"] as? JsonArray)?.map { it.jsonObject }?.forEach { index ->
                    execSQL(index.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            execSQL("INSERT INTO profile VALUES ('u1','p1',3,'Sara','en','Africa/Cairo','EGP',0,1,NULL,'2026-10-01T10:00:00Z')")
            execSQL(
                "INSERT INTO wallet VALUES ('w1','u1','cash','Pocket','EGP',100,100,NULL,NULL,NULL,0,'owner',1,1," +
                    "'2026-10-01T10:00:00Z','2026-10-01T10:00:00Z','2026-10-01T10:00:00Z')",
            )
            execSQL(
                "INSERT INTO transactions VALUES ('t1','u1','w1',NULL,'expense','2026-10-01',4550,'EGP',NULL,NULL,NULL,NULL,0,'u1',NULL,NULL,2,7," +
                    "'2026-10-01T10:00:00Z','2026-10-01T10:00:00Z','2026-10-01T10:30:00Z','2026-10-01T10:00:00Z',0,0,NULL,0)",
            )
            execSQL(
                "INSERT INTO pending_change VALUES ('transaction','t1','u1','wallet','w1',2,'{}',0,1,0,1,'Waiting',0,NULL,NULL,NULL,'2026-10-01T10:00:00Z')",
            )
            execSQL("INSERT INTO sync_scope VALUES ('wallets','2026-10-01T10:00:00Z',NULL,NULL,NULL)")
            execSQL("INSERT INTO sync_scope VALUES ('user:u1','2026-10-01T10:00:00Z','2026-10-01T09:00:00Z','user-cursor','2026-10-02T08:00:00.000Z')")
            execSQL("INSERT INTO sync_scope VALUES ('wallet:w1','2026-10-01T10:00:00Z','2026-10-01T09:30:00Z','wallet-cursor','2026-10-02T08:30:00.000Z')")
            execSQL("PRAGMA user_version = 7")
            close()
        }
        return file
    }

    private fun open(file: File) = Room.databaseBuilder<AppDatabase>(context, file.path)
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(MIGRATION_7_8)
        .build()

    @Test
    fun everyStoredRowAndCursorSurvivesAndNoPositionIsInvented() = runTest {
        val db = open(v7Database())
        val user = db.syncScopeDao().observe("user:u1").first()!!
        assertEquals("user-cursor", user.feedCursor)
        assertEquals("2026-10-02T08:00:00.000Z", user.feedReadAt)
        assertEquals("2026-10-01T09:00:00Z", user.lastPushedAt)
        assertNull(user.feedPosition)
        val wallet = db.syncScopeDao().observe("wallet:w1").first()!!
        assertEquals("wallet-cursor", wallet.feedCursor)
        assertEquals("2026-10-02T08:30:00.000Z", wallet.feedReadAt)
        assertEquals("2026-10-01T09:30:00Z", wallet.lastPushedAt)
        assertEquals("2026-10-01T10:00:00Z", wallet.refreshedAt)
        assertNull(wallet.feedPosition)
        assertEquals(FeedPlace("wallet-cursor", null), db.syncScopeDao().feedPlace("wallet:w1"))
        assertNull(db.syncScopeDao().feedPlace("wallet:none"))

        assertEquals("Sara", db.profileDao().get()!!.displayName)
        assertEquals("Pocket", db.walletDao().get("w1")!!.name)
        assertEquals(4_550L, db.transactionDao().get("t1")!!.amountMinor)
        val restore = db.pendingChangeDao().get("transaction", "t1")
        assertNotNull(restore)
        assertTrue("the waiting restore keeps its flag", restore!!.restore)
        db.close()
    }

    @Test
    fun aMigratedScopeTakesAPositionWithItsNextPage() = runTest {
        val db = open(v7Database())
        db.syncScopeDao().storeFeedPage("wallet:w1", "wallet-cursor-2", 41L)
        assertEquals(FeedPlace("wallet-cursor-2", 41L), db.syncScopeDao().feedPlace("wallet:w1"))
        db.close()
    }

    @Test
    fun theExportedSchemaNamesTheNewColumnAndNothingElseChanged() {
        val v7 = File("schemas/com.fares.saveme.core.database.AppDatabase/7.json").readText()
        val v8 = File("schemas/com.fares.saveme.core.database.AppDatabase/8.json").readText()
        assertTrue("feed_position" in v8)
        assertTrue("feed_position" !in v7)
        fun tables(text: String): Map<String, JsonObject> = Json.parseToJsonElement(text).jsonObject.getValue("database").jsonObject
            .getValue("entities").jsonArray.map { it.jsonObject }.associateBy { it.getValue("tableName").jsonPrimitive.content }
        val before = tables(v7)
        val after = tables(v8)
        assertEquals(before.keys, after.keys)
        before.keys.filter { it != "sync_scope" }.forEach { assertEquals(it, before[it], after[it]) }
    }
}
