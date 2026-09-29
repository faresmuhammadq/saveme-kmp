package com.fares.saveme.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.fares.saveme.feature.account.data.local.DeviceSessionDao
import com.fares.saveme.feature.account.data.local.DeviceSessionEntity
import com.fares.saveme.feature.account.data.local.ProfileDao
import com.fares.saveme.feature.account.data.local.ProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** The on-device source of truth for syncable entities (P-04.1). Each feature owns its tables. */
@Database(
    entities = [ProfileEntity::class, DeviceSessionEntity::class, SyncScopeEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun deviceSessionDao(): DeviceSessionDao
    abstract fun syncScopeDao(): SyncScopeDao
}

@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun buildAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
