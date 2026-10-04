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

/**
 * v4 → v5 (spec 008, research R-02): the transactions table with its three indexes. Nothing else
 * is touched, so every existing row, including waiting changes in `pending_change`, survives.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `account_id` TEXT NOT NULL,
            `wallet_id` TEXT NOT NULL, `counter_wallet_id` TEXT, `kind` TEXT NOT NULL, `date` TEXT NOT NULL,
            `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `category_id` TEXT, `description` TEXT,
            `memo` TEXT, `counterparty` TEXT, `cleared` INTEGER NOT NULL, `created_by` TEXT NOT NULL,
            `recurring_rule_id` TEXT, `occurrence_id` TEXT, `version` INTEGER NOT NULL, `change_seq` INTEGER NOT NULL,
            `created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL, `deleted_at` TEXT, `refreshed_at` TEXT NOT NULL,
            `carry_source_minor` INTEGER NOT NULL, `carry_counter_minor` INTEGER NOT NULL, `carry_confirmed_at` TEXT,
            PRIMARY KEY(`id`))""",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_wallet_id_date_created_at` ON `transactions` (`wallet_id`, `date`, `created_at`)")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_counter_wallet_id_date_created_at` ON `transactions` (`counter_wallet_id`, `date`, `created_at`)",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_account_id` ON `transactions` (`account_id`)")
    }
}

/**
 * v5 → v6 (spec 009, research R-11): each sync scope keeps its change feed position and the start
 * of its last completed read. Nothing else is touched, and every position starts empty, so the
 * first run after the update reads every scope from the start (FR-029).
 */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `sync_scope` ADD COLUMN `feed_cursor` TEXT")
        connection.execSQL("ALTER TABLE `sync_scope` ADD COLUMN `feed_read_at` TEXT")
    }
}

/**
 * v6 → v7 (spec 010, research R-02): the recurring rule and occurrence tables, the phone-only
 * `pending_confirmation` flag on transactions, and every wallet scope's feed position cleared once, so the
 * rule and occurrence rows spec 009 skipped are read from the start (FR-030). Every existing row and the
 * user scope's position are kept.
 */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS `recurring_rule` (`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `wallet_id` TEXT NOT NULL,
            `preset` TEXT NOT NULL, `status` TEXT NOT NULL, `kind` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL,
            `amount_basis` TEXT NOT NULL, `currency` TEXT NOT NULL, `category_id` TEXT, `counter_wallet_id` TEXT,
            `description` TEXT, `memo` TEXT, `counterparty` TEXT, `auto_confirm` INTEGER NOT NULL, `frequency` TEXT NOT NULL,
            `interval` INTEGER NOT NULL, `anchor_day` INTEGER, `start_date` TEXT NOT NULL, `end_date` TEXT,
            `occurrence_count` INTEGER, `payout_round` INTEGER, `payout_category_id` TEXT, `timezone` TEXT, `created_by` TEXT,
            `version` INTEGER NOT NULL, `change_seq` INTEGER NOT NULL, `created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL,
            `refreshed_at` TEXT NOT NULL, `follows_rule_id` TEXT, PRIMARY KEY(`id`))""",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rule_account_id_wallet_id_status` ON `recurring_rule` (`account_id`, `wallet_id`, `status`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rule_follows_rule_id` ON `recurring_rule` (`follows_rule_id`)")
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS `occurrence` (`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `wallet_id` TEXT NOT NULL,
            `recurring_rule_id` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, `is_payout` INTEGER NOT NULL,
            `scheduled_date` TEXT NOT NULL, `date` TEXT NOT NULL, `status` TEXT NOT NULL, `kind` TEXT NOT NULL,
            `amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `category_id` TEXT, `counter_wallet_id` TEXT,
            `description` TEXT, `memo` TEXT, `counterparty` TEXT, `edited` INTEGER NOT NULL, `transaction_id` TEXT,
            `auto_confirm_refusal` TEXT, `version` INTEGER NOT NULL, `change_seq` INTEGER NOT NULL, `created_at` TEXT NOT NULL,
            `updated_at` TEXT NOT NULL, `refreshed_at` TEXT NOT NULL, `local_notice` TEXT, PRIMARY KEY(`id`))""",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_occurrence_account_id_wallet_id_date` ON `occurrence` (`account_id`, `wallet_id`, `date`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_occurrence_recurring_rule_id_date` ON `occurrence` (`recurring_rule_id`, `date`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_occurrence_transaction_id` ON `occurrence` (`transaction_id`)")
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `pending_confirmation` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("UPDATE `sync_scope` SET `feed_cursor` = NULL, `feed_read_at` = NULL WHERE `scope` LIKE 'wallet:%'")
    }
}
