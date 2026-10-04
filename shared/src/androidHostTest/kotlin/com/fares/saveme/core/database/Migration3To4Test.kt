package com.fares.saveme.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.fares.saveme.feature.categories.data.local.CategoryEntity
import com.fares.saveme.feature.categories.data.local.CategoryReferenceEntity
import com.fares.saveme.feature.categories.data.local.PlatformCategoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * v3 → v4 (spec 007, research R-02): the category tables arrive and nothing else moves. The v3 tables
 * are the exported schemas/…/3.json definitions; Room validates the result against v4.
 */
@RunWith(RobolectricTestRunner::class)
class Migration3To4Test {

    private val context = RuntimeEnvironment.getApplication()

    private fun v3Database(): File {
        val file = File(context.cacheDir, "migration34-${System.nanoTime()}.db")
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
            execSQL(
                "CREATE TABLE `wallet` (`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`currency` TEXT NOT NULL, `opening_balance_minor` INTEGER NOT NULL, `balance_minor` INTEGER NOT NULL, `icon` TEXT, " +
                    "`color` TEXT, `archived_at` TEXT, `currency_locked` INTEGER NOT NULL, `my_role` TEXT NOT NULL, `version` INTEGER NOT NULL, " +
                    "`change_seq` INTEGER NOT NULL, `created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL, `refreshed_at` TEXT NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            execSQL("CREATE INDEX `index_wallet_account_id_archived_at_created_at` ON `wallet` (`account_id`, `archived_at`, `created_at`)")
            execSQL(
                "CREATE TABLE `wallet_member` (`id` TEXT NOT NULL, `wallet_id` TEXT NOT NULL, `member_user_id` TEXT NOT NULL, " +
                    "`display_name` TEXT, `role` TEXT NOT NULL, `version` INTEGER NOT NULL, `created_at` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            execSQL("CREATE INDEX `index_wallet_member_wallet_id` ON `wallet_member` (`wallet_id`)")
            execSQL("INSERT INTO profile VALUES ('u1','p1',3,'Sara','en','Africa/Cairo','EGP',0,1,NULL,'2026-09-28T10:00:00Z')")
            execSQL(
                "INSERT INTO wallet VALUES ('w1','u1','cash','Pocket','EGP',100,100,NULL,NULL,NULL,0,'owner',1,1," +
                    "'2026-09-28T10:00:00Z','2026-09-28T10:00:00Z','2026-09-28T10:00:00Z')",
            )
            execSQL(
                "INSERT INTO pending_change VALUES ('wallet','w1','u1','wallet','w1',1,'{}',0,0,0,1,'Waiting',0,NULL,NULL,NULL,'2026-09-28T10:00:00Z')",
            )
            execSQL("PRAGMA user_version = 3")
            close()
        }
        return file
    }

    private fun open(file: File) = Room.databaseBuilder<AppDatabase>(context, file.path)
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        .build()

    @Test
    fun existingRowsSurviveAndTheCategoryTablesWork() = runTest {
        val db = open(v3Database())
        assertEquals("Sara", db.profileDao().get()!!.displayName)
        assertEquals("Pocket", db.walletDao().get("w1")!!.name)
        assertNotNull(db.pendingChangeDao().get("wallet", "w1"))

        val stamp = "2026-10-02T10:00:00Z"
        db.categoryDao().upsert(CategoryEntity("c1", "u1", "expense", "Coffee", "restaurant", "#1F7F8C", null, false, 1, 1, stamp, stamp, stamp))
        db.platformCategoryDao().replaceAll(listOf(PlatformCategoryEntity("d1", "groceries", "expense", "بقالة", "Groceries", "cart", "#2E7D32", 1, null, stamp)))
        db.categoryReferenceDao().replace("w1", listOf(CategoryReferenceEntity("r1", "w1", "p1", "u2", "expense", "Partner", null, null, 1, 1, stamp)))
        assertEquals("Coffee", db.categoryDao().get("c1")!!.name)
        assertEquals("Groceries", db.platformCategoryDao().getAll().single().nameEn)
        assertEquals(1, db.categoryReferenceDao().get("w1").size)
        db.close()
    }
}
