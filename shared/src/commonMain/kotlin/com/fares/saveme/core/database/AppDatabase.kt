package com.fares.saveme.core.database

import com.fares.saveme.core.sync.PendingChangeDao
import com.fares.saveme.core.sync.PendingChangeEntity
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.fares.saveme.feature.account.data.local.DeviceSessionDao
import com.fares.saveme.feature.account.data.local.DeviceSessionEntity
import com.fares.saveme.feature.account.data.local.ProfileDao
import com.fares.saveme.feature.account.data.local.ProfileEntity
import com.fares.saveme.feature.budgets.data.local.BudgetDao
import com.fares.saveme.feature.budgets.data.local.BudgetEntity
import com.fares.saveme.feature.budgets.data.local.BudgetProgressDao
import com.fares.saveme.feature.budgets.data.local.BudgetProgressEntity
import com.fares.saveme.feature.categories.data.local.CategoryDao
import com.fares.saveme.feature.categories.data.local.CategoryEntity
import com.fares.saveme.feature.categories.data.local.CategoryReferenceDao
import com.fares.saveme.feature.categories.data.local.CategoryReferenceEntity
import com.fares.saveme.feature.categories.data.local.PlatformCategoryDao
import com.fares.saveme.feature.categories.data.local.PlatformCategoryEntity
import com.fares.saveme.feature.recurring.data.local.OccurrenceDao
import com.fares.saveme.feature.recurring.data.local.OccurrenceEntity
import com.fares.saveme.feature.recurring.data.local.RecurringRuleDao
import com.fares.saveme.feature.recurring.data.local.RecurringRuleEntity
import com.fares.saveme.feature.transactions.data.local.TransactionDao
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import com.fares.saveme.feature.wallets.data.local.WalletDao
import com.fares.saveme.feature.wallets.data.local.WalletEntity
import com.fares.saveme.feature.wallets.data.local.WalletMemberDao
import com.fares.saveme.feature.wallets.data.local.WalletMemberEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** The on-device source of truth for syncable entities (P-04.1). Each feature owns its tables. */
@Database(
    entities = [
        ProfileEntity::class, DeviceSessionEntity::class, SyncScopeEntity::class, PendingChangeEntity::class,
        WalletEntity::class, WalletMemberEntity::class,
        CategoryEntity::class, PlatformCategoryEntity::class, CategoryReferenceEntity::class,
        TransactionEntity::class, RecurringRuleEntity::class, OccurrenceEntity::class,
        BudgetEntity::class, BudgetProgressEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun deviceSessionDao(): DeviceSessionDao
    abstract fun syncScopeDao(): SyncScopeDao
    abstract fun pendingChangeDao(): PendingChangeDao
    abstract fun walletDao(): WalletDao
    abstract fun walletMemberDao(): WalletMemberDao
    abstract fun categoryDao(): CategoryDao
    abstract fun platformCategoryDao(): PlatformCategoryDao
    abstract fun categoryReferenceDao(): CategoryReferenceDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun budgetDao(): BudgetDao
    abstract fun budgetProgressDao(): BudgetProgressDao
}

@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun buildAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
