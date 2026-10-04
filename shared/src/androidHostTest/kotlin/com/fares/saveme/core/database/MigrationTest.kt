package com.fares.saveme.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.Refusal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * v1 → v2 (research R-09): a waiting profile edit survives the update (FR-005). The v1 tables are
 * the exported schemas/…/1.json definitions; Room validates the result against v2.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun v1Database(vararg profileInserts: String): File {
        val file = File(context.cacheDir, "migration-${System.nanoTime()}.db")
        AndroidSQLiteDriver().open(file.path).run {
            execSQL(
                "CREATE TABLE `profile` (`user_id` TEXT NOT NULL, `id` TEXT NOT NULL, `version` INTEGER NOT NULL, " +
                    "`display_name` TEXT, `locale` TEXT NOT NULL, `timezone` TEXT NOT NULL, `default_currency` TEXT NOT NULL, " +
                    "`default_currency_locked` INTEGER NOT NULL, `budget_period_start_day` INTEGER NOT NULL, " +
                    "`budget_period_start_day_from` TEXT, `refreshed_at` TEXT NOT NULL, `pending_json` TEXT, " +
                    "`pending_base_version` INTEGER, `conflict_json` TEXT, `rejection_json` TEXT, PRIMARY KEY(`user_id`))",
            )
            execSQL(
                "CREATE TABLE `device_session` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `device_name` TEXT NOT NULL, " +
                    "`platform` TEXT NOT NULL, `app_version` TEXT NOT NULL, `state` TEXT NOT NULL, `signed_in_at` TEXT NOT NULL, " +
                    "`last_used_at` TEXT, `current` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            execSQL("CREATE TABLE `sync_scope` (`scope` TEXT NOT NULL, `refreshedAt` TEXT NOT NULL, PRIMARY KEY(`scope`))")
            profileInserts.forEach { execSQL(it) }
            execSQL("PRAGMA user_version = 1")
            close()
        }
        return file
    }

    private fun profileRow(pending: String?, base: Long?, conflict: String? = null, rejection: String? = null): String {
        fun q(v: String?) = v?.let { "'" + it.replace("'", "''") + "'" } ?: "NULL"
        return "INSERT INTO profile VALUES ('u1','p1',3,'Sara','en','Africa/Cairo','EGP',0,1,NULL,'2026-09-28T10:00:00Z'," +
            "${q(pending)},${base ?: "NULL"},${q(conflict)},${q(rejection)})"
    }

    private fun open(file: File) = Room.databaseBuilder<AppDatabase>(context, file.path)
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        .build()

    @Test
    fun aWaitingEditBecomesAQueuedChange() = runTest {
        val db = open(v1Database(profileRow(pending = """{"display_name":"Sara M"}""", base = 3)))
        val change = db.pendingChangeDao().get("profile", "p1")!!
        assertEquals("Waiting", change.state)
        assertEquals(3L, change.baseVersion)
        assertEquals("""{"display_name":"Sara M"}""", change.payloadJson)
        assertEquals("u1", change.accountId)
        val profile = db.profileDao().get()!!
        assertEquals("Sara", profile.displayName)
        assertEquals(3L, profile.version)
        assertNull(db.syncScopeDao().observeLastPushed().first())
        db.close()
    }

    @Test
    fun conflictsAndRejectionsKeepTheirState() = runTest {
        val clash = open(v1Database(profileRow("{}", 3, conflict = """{"version":6}""")))
        clash.pendingChangeDao().get("profile", "p1")!!.let {
            assertEquals("Clash", it.state)
            assertEquals("""{"version":6}""", it.clashJson)
        }
        clash.close()

        val refused = open(v1Database(profileRow("{}", 3, rejection = """{"DefaultCurrency":"IMMUTABLE_FIELD"}""")))
        refused.pendingChangeDao().get("profile", "p1")!!.let {
            assertEquals("Refused", it.state)
            val refusal = AppJson.decodeFromString(Refusal.serializer(), it.refusalJson!!)
            assertEquals(mapOf("DefaultCurrency" to "IMMUTABLE_FIELD"), refusal.fields)
        }
        refused.close()
    }

    @Test
    fun aProfileWithoutAnEditLeavesTheQueueEmpty() = runTest {
        val db = open(v1Database(profileRow(pending = null, base = null)))
        assertNull(db.pendingChangeDao().get("profile", "p1"))
        db.close()
    }

    @Test
    fun theWholeChainEndsAtV7WithNoFeedPositions() = runTest {
        val db = open(v1Database(profileRow(pending = null, base = null)))
        db.syncScopeDao().markPushed("user:u1", "2026-10-02T10:00:00.000Z")
        assertNull(db.syncScopeDao().feedCursor("user:u1"))
        assertNull(db.syncScopeDao().observeFeedReadAt("user:u1").first())
        db.close()
    }
}
