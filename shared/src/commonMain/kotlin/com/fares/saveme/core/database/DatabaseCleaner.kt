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
        database.occurrenceDao().deleteAll()
        database.recurringRuleDao().deleteAll()
        database.transactionDao().deleteAll()
        database.walletMemberDao().deleteAll()
        database.walletDao().deleteAll()
        database.categoryReferenceDao().deleteAll()
        database.platformCategoryDao().deleteAll()
        database.categoryDao().deleteAll()
        database.syncScopeDao().deleteAll()
        // Waiting changes go with the account: they can never be sent under another one (FR-018).
        database.pendingChangeDao().deleteAll()
    }
}
