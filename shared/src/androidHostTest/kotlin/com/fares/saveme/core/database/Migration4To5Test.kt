package com.fares.saveme.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * v4 → v5 (spec 008, research R-02): the transactions table arrives and nothing else moves. The v4 tables
 * are the exported schemas/…/4.json definitions; Room validates the result against v5.
 */
@RunWith(RobolectricTestRunner::class)
class Migration4To5Test {

    private val context = RuntimeEnvironment.getApplication()

    private fun v4Database(): File {
        val file = File(context.cacheDir, "migration45-${System.nanoTime()}.db")
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
            execSQL(
                "CREATE TABLE `category` (`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`icon` TEXT, `color` TEXT, `archived_at` TEXT, `in_use` INTEGER NOT NULL, `version` INTEGER NOT NULL, " +
                    "`change_seq` INTEGER NOT NULL, `created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL, `refreshed_at` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            execSQL("CREATE INDEX `index_category_account_id_kind_archived_at` ON `category` (`account_id`, `kind`, `archived_at`)")
            execSQL(
                "CREATE TABLE `platform_category` (`id` TEXT NOT NULL, `system_key` TEXT NOT NULL, `kind` TEXT NOT NULL, `name_ar` TEXT NOT NULL, " +
                    "`name_en` TEXT NOT NULL, `icon` TEXT NOT NULL, `color` TEXT NOT NULL, `position` INTEGER NOT NULL, `archived_at` TEXT, " +
                    "`updated_at` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            execSQL("CREATE INDEX `index_platform_category_kind_position` ON `platform_category` (`kind`, `position`)")
            execSQL(
                "CREATE TABLE `category_reference` (`id` TEXT NOT NULL, `wallet_id` TEXT NOT NULL, `category_id` TEXT NOT NULL, " +
                    "`owner_user_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT, `color` TEXT, " +
                    "`version` INTEGER NOT NULL, `change_seq` INTEGER NOT NULL, `updated_at` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            execSQL("CREATE INDEX `index_category_reference_wallet_id` ON `category_reference` (`wallet_id`)")
            execSQL("CREATE UNIQUE INDEX `index_category_reference_wallet_id_category_id` ON `category_reference` (`wallet_id`, `category_id`)")
            execSQL("INSERT INTO profile VALUES ('u1','p1',3,'Sara','en','Africa/Cairo','EGP',0,1,NULL,'2026-09-28T10:00:00Z')")
            execSQL(
                "INSERT INTO wallet VALUES ('w1','u1','cash','Pocket','EGP',100,100,NULL,NULL,NULL,0,'owner',1,1," +
                    "'2026-09-28T10:00:00Z','2026-09-28T10:00:00Z','2026-09-28T10:00:00Z')",
            )
            execSQL(
                "INSERT INTO category VALUES ('c1','u1','expense','Coffee',NULL,NULL,NULL,0,1,1,'2026-09-28T10:00:00Z'," +
                    "'2026-09-28T10:00:00Z','2026-09-28T10:00:00Z')",
            )
            execSQL(
                "INSERT INTO pending_change VALUES ('category','c1','u1','user','u1',1,'{}',0,0,0,1,'Waiting',0,NULL,NULL,NULL,'2026-09-28T10:00:00Z')",
            )
            execSQL("PRAGMA user_version = 4")
            close()
        }
        return file
    }

    private fun open(file: File) = Room.databaseBuilder<AppDatabase>(context, file.path)
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        .build()

    @Test
    fun existingRowsSurviveAndTheTransactionsTableWorks() = runTest {
        val db = open(v4Database())
        assertEquals("Sara", db.profileDao().get()!!.displayName)
        assertEquals("Pocket", db.walletDao().get("w1")!!.name)
        assertEquals("Coffee", db.categoryDao().get("c1")!!.name)
        assertNotNull(db.pendingChangeDao().get("category", "c1"))
        assertTrue(db.transactionDao().getAll().isEmpty())

        val stamp = "2026-10-02T10:00:00Z"
        db.transactionDao().upsert(
            TransactionEntity("t1", "u1", "w1", null, "expense", "2026-10-01", 4_550, "EGP", "c1", "Lunch", null, null, false, "u1", null, null, 1, 1, stamp, stamp, null, stamp),
        )
        assertEquals(4_550, db.transactionDao().get("t1")!!.amountMinor)
        db.close()
    }

    @Test
    fun theThreeIndexesExist() = runTest {
        val db = open(v4Database())
        db.transactionDao().getAll()
        db.close()
        // Room validated the schema (including indexes) against 5.json when it opened; the exported file names them too.
        val schema = File("schemas/com.fares.saveme.core.database.AppDatabase/5.json").readText()
        listOf(
            "index_transactions_wallet_id_date_created_at", "index_transactions_counter_wallet_id_date_created_at", "index_transactions_account_id",
        ).forEach { assertTrue(it, it in schema) }
    }
}
