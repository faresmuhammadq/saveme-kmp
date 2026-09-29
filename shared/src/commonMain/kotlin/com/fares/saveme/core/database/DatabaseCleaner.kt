package com.fares.saveme.core.database

import com.fares.saveme.core.session.UserDataCleaner

/**
 * Empties every table at sign-out. `clearAllTables()` is Android-only in Room KMP, so each table
 * is deleted explicitly: a new table must be added here.
 */
class DatabaseCleaner(private val database: AppDatabase) : UserDataCleaner {
    override suspend fun clear() {
        database.profileDao().deleteAll()
        database.deviceSessionDao().deleteAll()
        database.syncScopeDao().deleteAll()
    }
}
