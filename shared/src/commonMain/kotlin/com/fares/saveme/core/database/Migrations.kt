package com.fares.saveme.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1 → v2 (spec 004, research R-09): the profile's waiting edit moves into the shared sync queue,
 * so an edit made before the update still syncs after it (FR-005).
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS `pending_change` (`record_type` TEXT NOT NULL, `record_id` TEXT NOT NULL,
            `account_id` TEXT NOT NULL, `scope_kind` TEXT NOT NULL, `scope_id` TEXT NOT NULL,
            `base_version` INTEGER NOT NULL, `payload_json` TEXT NOT NULL, `is_delete` INTEGER NOT NULL,
            `restore` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `created_order` INTEGER NOT NULL,
            `state` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `next_attempt_at` TEXT, `clash_json` TEXT,
            `refusal_json` TEXT, `updated_at` TEXT NOT NULL, PRIMARY KEY(`record_type`, `record_id`))""",
        )
        // A rejection was stored as {field: code}; the queue keeps {code, fields} (data-model §1).
        connection.execSQL(
            """INSERT INTO `pending_change` (`record_type`, `record_id`, `account_id`, `scope_kind`, `scope_id`,
            `base_version`, `payload_json`, `is_delete`, `restore`, `revision`, `created_order`, `state`, `attempts`,
            `next_attempt_at`, `clash_json`, `refusal_json`, `updated_at`)
            SELECT 'profile', `id`, `user_id`, 'user', `user_id`, COALESCE(`pending_base_version`, `version`),
            `pending_json`, 0, 0, 0, 1,
            CASE WHEN `conflict_json` IS NOT NULL THEN 'Clash' WHEN `rejection_json` IS NOT NULL THEN 'Refused' ELSE 'Waiting' END,
            0, NULL, `conflict_json`,
            CASE WHEN `rejection_json` IS NOT NULL THEN '{"code":"VALIDATION_FAILED","fields":' || `rejection_json` || '}' END,
            `refreshed_at`
            FROM `profile` WHERE `pending_json` IS NOT NULL""",
        )
        connection.execSQL(
            """CREATE TABLE `profile_new` (`user_id` TEXT NOT NULL, `id` TEXT NOT NULL, `version` INTEGER NOT NULL,
            `display_name` TEXT, `locale` TEXT NOT NULL, `timezone` TEXT NOT NULL, `default_currency` TEXT NOT NULL,
            `default_currency_locked` INTEGER NOT NULL, `budget_period_start_day` INTEGER NOT NULL,
            `budget_period_start_day_from` TEXT, `refreshed_at` TEXT NOT NULL, PRIMARY KEY(`user_id`))""",
        )
        connection.execSQL(
            """INSERT INTO `profile_new` SELECT `user_id`, `id`, `version`, `display_name`, `locale`, `timezone`,
            `default_currency`, `default_currency_locked`, `budget_period_start_day`, `budget_period_start_day_from`,
            `refreshed_at` FROM `profile`""",
        )
        connection.execSQL("DROP TABLE `profile`")
        connection.execSQL("ALTER TABLE `profile_new` RENAME TO `profile`")
        connection.execSQL("ALTER TABLE `sync_scope` ADD COLUMN `last_pushed_at` TEXT")
    }
}

/**
 * v2 → v3 (spec 006, research R-02): the wallet tables. Nothing else is touched, so every existing
 * row, including waiting changes in `pending_change`, survives the update.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS `wallet` (`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `type` TEXT NOT NULL,
            `name` TEXT NOT NULL, `currency` TEXT NOT NULL, `opening_balance_minor` INTEGER NOT NULL,
            `balance_minor` INTEGER NOT NULL, `icon` TEXT, `color` TEXT, `archived_at` TEXT,
            `currency_locked` INTEGER NOT NULL, `my_role` TEXT NOT NULL, `version` INTEGER NOT NULL,
            `change_seq` INTEGER NOT NULL, `created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL,
            `refreshed_at` TEXT NOT NULL, PRIMARY KEY(`id`))""",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_wallet_account_id_archived_at_created_at` ON `wallet` (`account_id`, `archived_at`, `created_at`)",
        )
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS `wallet_member` (`id` TEXT NOT NULL, `wallet_id` TEXT NOT NULL,
            `member_user_id` TEXT NOT NULL, `display_name` TEXT, `role` TEXT NOT NULL, `version` INTEGER NOT NULL,
            `created_at` TEXT NOT NULL, PRIMARY KEY(`id`))""",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_wallet_member_wallet_id` ON `wallet_member` (`wallet_id`)")
    }
}

/**
 * v3 → v4 (spec 007, research R-02): the category tables (own categories, the cached default set,
 * partners' references). Nothing else is touched, so every existing row, including waiting
 * changes in `pending_change`, survives the update.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS `category` (`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `kind` TEXT NOT NULL,
            `name` TEXT NOT NULL, `icon` TEXT, `color` TEXT, `archived_at` TEXT, `in_use` INTEGER NOT NULL,
            `version` INTEGER NOT NULL, `change_seq` INTEGER NOT NULL, `created_at` TEXT NOT NULL,
            `updated_at` TEXT NOT NULL, `refreshed_at` TEXT NOT NULL, PRIMARY KEY(`id`))""",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_category_account_id_kind_archived_at` ON `category` (`account_id`, `kind`, `archived_at`)",
        )
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS `platform_category` (`id` TEXT NOT NULL, `system_key` TEXT NOT NULL,
            `kind` TEXT NOT NULL, `name_ar` TEXT NOT NULL, `name_en` TEXT NOT NULL, `icon` TEXT NOT NULL,
            `color` TEXT NOT NULL, `position` INTEGER NOT NULL, `archived_at` TEXT, `updated_at` TEXT NOT NULL,
            PRIMARY KEY(`id`))""",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_platform_category_kind_position` ON `platform_category` (`kind`, `position`)")
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS `category_reference` (`id` TEXT NOT NULL, `wallet_id` TEXT NOT NULL,
            `category_id` TEXT NOT NULL, `owner_user_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL,
            `icon` TEXT, `color` TEXT, `version` INTEGER NOT NULL, `change_seq` INTEGER NOT NULL,
            `updated_at` TEXT NOT NULL, PRIMARY KEY(`id`))""",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_category_reference_wallet_id` ON `category_reference` (`wallet_id`)")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_category_reference_wallet_id_category_id` ON `category_reference` (`wallet_id`, `category_id`)",
        )
    }
}
