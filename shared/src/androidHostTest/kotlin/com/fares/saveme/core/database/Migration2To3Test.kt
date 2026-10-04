package com.fares.saveme.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.fares.saveme.feature.wallets.data.local.WalletEntity
import com.fares.saveme.feature.wallets.data.local.WalletMemberEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * v2 → v3 (spec 006, research R-02): the wallet tables arrive and nothing else moves. The v2 tables
 * are the exported schemas/…/2.json definitions; Room validates the result against v3.
 */
@RunWith(RobolectricTestRunner::class)
class Migration2To3Test {

    private val context = RuntimeEnvironment.getApplication()

    private fun v2Database(): File {
        val file = File(context.cacheDir, "migration23-${System.nanoTime()}.db")
        AndroidSQLiteDriver().open(file.path).run {
            execSQL(
                "CREATE TABLE `profile` (`user_id` TEXT NOT NULL, `id` TEXT NOT NULL, `version` INTEGER NOT NULL, " +
                    "`display_name` TEXT, `locale` TEXT NOT NULL, `timezone` TEXT NOT NULL, `default_currency` TEXT NOT NULL, " +
                    "`default_currency_locked` INTEGER NOT NULL, `budget_period_start_day` INTEGER NOT NULL, " +
                    "`budget_period_start_day_from` TEXT, `refreshed_at` TEXT NOT NULL, PRIMARY KEY(`user_id`))",
            )
            execSQL(
                "CREATE TABLE `device_session` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `device_name` TEXT NOT NULL, " +
                    "`platform` TEXT NOT NULL, `app_version` TEXT NOT NULL, `state` TEXT NOT NULL, `signed_in_at` TEXT NOT NULL, " +
                    "`last_used_at` TEXT, `current` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            execSQL("CREATE TABLE `sync_scope` (`scope` TEXT NOT NULL, `refreshedAt` TEXT NOT NULL, `last_pushed_at` TEXT, PRIMARY KEY(`scope`))")
            execSQL(
                "CREATE TABLE `pending_change` (`record_type` TEXT NOT NULL, `record_id` TEXT NOT NULL, `account_id` TEXT NOT NULL, " +
                    "`scope_kind` TEXT NOT NULL, `scope_id` TEXT NOT NULL, `base_version` INTEGER NOT NULL, `payload_json` TEXT NOT NULL, " +
                    "`is_delete` INTEGER NOT NULL, `restore` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `created_order` INTEGER NOT NULL, " +
                    "`state` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `next_attempt_at` TEXT, `clash_json` TEXT, `refusal_json` TEXT, " +
                    "`updated_at` TEXT NOT NULL, PRIMARY KEY(`record_type`, `record_id`))",
            )
            execSQL("INSERT INTO profile VALUES ('u1','p1',3,'Sara','en','Africa/Cairo','EGP',0,1,NULL,'2026-09-28T10:00:00Z')")
            execSQL(
                "INSERT INTO pending_change VALUES ('profile','p1','u1','user','u1',3,'{}',0,0,0,1,'Waiting',0,NULL,NULL,NULL,'2026-09-28T10:00:00Z')",
            )
            execSQL("PRAGMA user_version = 2")
            close()
        }
        return file
    }

    private fun open(file: File) = Room.databaseBuilder<AppDatabase>(context, file.path)
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
        .build()

    @Test
    fun existingRowsSurviveAndTheWalletTablesWork() = runTest {
        val db = open(v2Database())
        assertEquals("Sara", db.profileDao().get()!!.displayName)
        assertNotNull(db.pendingChangeDao().get("profile", "p1"))
        db.walletDao().upsert(
            WalletEntity("w1", "u1", "cash", "Pocket", "EGP", 100, 100, null, null, null, false, "owner", 1, 1, "2026-09-28T10:00:00Z", "2026-09-28T10:00:00Z", "2026-09-28T10:00:00Z"),
        )
        db.walletMemberDao().insertAll(listOf(WalletMemberEntity("m1", "w1", "u1", "Sara", "owner", 1, "2026-09-28T10:00:00Z")))
        assertEquals("Pocket", db.walletDao().get("w1")!!.name)
        assertEquals(1, db.walletMemberDao().get("w1").size)
        db.close()
    }
}
