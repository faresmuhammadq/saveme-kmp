package com.fares.saveme.core.database

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.fares.saveme.feature.recurring.data.local.OccurrenceEntity
import com.fares.saveme.feature.recurring.data.local.RecurringRuleEntity
import org.junit.Assert.assertFalse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
 * v6 → v7 (spec 010, research R-02): the recurring rule and occurrence tables, `transactions.pending_confirmation`, and
 * every wallet scope's feed position cleared once. The v6 tables are the exported schemas/…/6.json definitions; Room
 * validates the result against v7.
 */
@RunWith(RobolectricTestRunner::class)
class Migration6To7Test {

    private val context = RuntimeEnvironment.getApplication()

    private fun v6Database(): File {
        val file = File(context.cacheDir, "migration67-${System.nanoTime()}.db")
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
            execSQL("CREATE TABLE `sync_scope` (`scope` TEXT NOT NULL, `refreshedAt` TEXT NOT NULL, `last_pushed_at` TEXT, `feed_cursor` TEXT, `feed_read_at` TEXT, PRIMARY KEY(`scope`))")
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
            execSQL(
                "CREATE TABLE `transactions` (`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `wallet_id` TEXT NOT NULL, `counter_wallet_id` TEXT, " +
                    "`kind` TEXT NOT NULL, `date` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `category_id` TEXT, " +
                    "`description` TEXT, `memo` TEXT, `counterparty` TEXT, `cleared` INTEGER NOT NULL, `created_by` TEXT NOT NULL, " +
                    "`recurring_rule_id` TEXT, `occurrence_id` TEXT, `version` INTEGER NOT NULL, `change_seq` INTEGER NOT NULL, " +
                    "`created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL, `deleted_at` TEXT, `refreshed_at` TEXT NOT NULL, " +
                    "`carry_source_minor` INTEGER NOT NULL, `carry_counter_minor` INTEGER NOT NULL, `carry_confirmed_at` TEXT, PRIMARY KEY(`id`))",
            )
            execSQL("CREATE INDEX `index_transactions_wallet_id_date_created_at` ON `transactions` (`wallet_id`, `date`, `created_at`)")
            execSQL("CREATE INDEX `index_transactions_counter_wallet_id_date_created_at` ON `transactions` (`counter_wallet_id`, `date`, `created_at`)")
            execSQL("CREATE INDEX `index_transactions_account_id` ON `transactions` (`account_id`)")
            execSQL(
                "INSERT INTO transactions VALUES ('t1','u1','w1',NULL,'expense','2026-09-28',4550,'EGP','c1',NULL,NULL,NULL,0,'u1',NULL,NULL,1,1," +
                    "'2026-09-28T10:00:00Z','2026-09-28T10:00:00Z',NULL,'2026-09-28T10:00:00Z',0,0,NULL)",
            )
            execSQL("INSERT INTO sync_scope VALUES ('wallets','2026-09-28T10:00:00Z',NULL,NULL,NULL)")
            execSQL("INSERT INTO sync_scope VALUES ('user:u1','2026-09-28T10:00:00Z','2026-09-28T09:00:00Z','user-pos','2026-10-01T08:00:00.000Z')")
            execSQL("INSERT INTO sync_scope VALUES ('wallet:w1','2026-09-28T10:00:00Z','2026-09-28T09:30:00Z','wallet-pos','2026-10-01T08:30:00.000Z')")
            execSQL("PRAGMA user_version = 6")
            close()
        }
        return file
    }

    private fun open(file: File) = Room.databaseBuilder<AppDatabase>(context, file.path)
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
        .build()

    @Test
    fun existingRowsSurviveAndOldTransactionsAreNotPending() = runTest {
        val db = open(v6Database())
        assertEquals("Sara", db.profileDao().get()!!.displayName)
        assertEquals("Pocket", db.walletDao().get("w1")!!.name)
        assertEquals("Coffee", db.categoryDao().get("c1")!!.name)
        val transaction = db.transactionDao().get("t1")!!
        assertEquals(4_550L, transaction.amountMinor)
        assertFalse(transaction.pendingConfirmation)
        assertNotNull(db.pendingChangeDao().get("category", "c1"))
        db.close()
    }

    @Test
    fun walletScopesLosePositionsOnceAndEverythingElseKeepsItsOwn() = runTest {
        val db = open(v6Database())
        val wallet = db.syncScopeDao().observe("wallet:w1").first()!!
        assertNull(wallet.feedCursor)
        assertNull(wallet.feedReadAt)
        assertEquals("2026-09-28T09:30:00Z", wallet.lastPushedAt)
        assertEquals("2026-09-28T10:00:00Z", wallet.refreshedAt)
        val user = db.syncScopeDao().observe("user:u1").first()!!
        // The database opens at the current version, so 8 -> 9 also ran: it forgets the person's own place once (spec 012 FR-020).
        assertNull(user.feedCursor)
        assertNull(user.feedReadAt)
        assertNull(db.syncScopeDao().observe("wallets").first()!!.feedCursor)
        db.close()
    }

    @Test
    fun theNewTablesWorkAndTheirIndexesAndColumnsAreInTheExportedSchema() = runTest {
        val db = open(v6Database())
        val stamp = "2026-10-02T09:00:00Z"
        db.recurringRuleDao().upsert(
            RecurringRuleEntity(
                "r1", "u1", "w1", "custom", "active", "expense", 450_000, "per_occurrence", "EGP", "c1", null, "Rent", null, null, true,
                "monthly", 1, 1, "2026-11-01", null, null, null, null, "Africa/Cairo", "u1", 1, 901, stamp, stamp, stamp,
            ),
        )
        db.occurrenceDao().upsert(
            OccurrenceEntity(
                "o1", "u1", "w1", "r1", 1, false, "2026-11-01", "2026-11-01", "scheduled", "expense", 450_000, "EGP", "c1", null, "Rent",
                null, null, false, null, null, 1, 902, stamp, stamp, stamp,
            ),
        )
        assertEquals("Rent", db.recurringRuleDao().get("r1")!!.description)
        assertEquals(listOf("o1"), db.occurrenceDao().getForRule("r1").map { it.id })
        db.transactionDao().upsert(db.transactionDao().get("t1")!!.copy(pendingConfirmation = true))
        assertTrue(db.transactionDao().get("t1")!!.pendingConfirmation)
        db.close()
        val schema = File("schemas/com.fares.saveme.core.database.AppDatabase/7.json").readText()
        listOf(
            "recurring_rule", "occurrence", "pending_confirmation", "follows_rule_id", "local_notice",
            "index_recurring_rule_account_id_wallet_id_status", "index_occurrence_recurring_rule_id_date", "index_occurrence_transaction_id",
        ).forEach { assertTrue(it, it in schema) }
    }
}
