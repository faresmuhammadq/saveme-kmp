package com.fares.saveme.core.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.database.buildAppDatabase
import com.fares.saveme.core.database.databaseBuilder
import com.fares.saveme.core.platform.AndroidClientInfo
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.core.storage.DataStoreKeyValueStore
import com.fares.saveme.core.storage.KeyValueStore
import com.fares.saveme.core.storage.KeystoreSecureStore
import com.fares.saveme.core.storage.SecureStore
import com.fares.saveme.core.sync.SyncScheduler
import com.fares.saveme.core.sync.WorkManagerSyncScheduler
import org.koin.dsl.module

/** File names must match the exclusions in androidApp's backup rules (P-09.1). */
fun androidPlatformModule(context: Context, appVersion: String) = module {
    single<ClientInfo> { AndroidClientInfo(appVersion) }
    single<SecureStore> {
        KeystoreSecureStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("secure_session") })
    }
    single<KeyValueStore> {
        DataStoreKeyValueStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("app_prefs") })
    }
    single<AppDatabase> { buildAppDatabase(databaseBuilder(context)) }
    single<SyncScheduler> { WorkManagerSyncScheduler(context) }
}
