package com.fares.saveme.core.di

import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.database.DatabaseCleaner
import com.fares.saveme.core.sync.CurrentAccount
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.core.sync.SyncScheduler
import com.fares.saveme.core.locale.AppLanguageStore
import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiEvents
import com.fares.saveme.core.network.createHttpClient
import com.fares.saveme.core.network.httpEngine
import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.core.storage.SecureStore
import org.koin.dsl.bind
import org.koin.dsl.module

val coreModule = module {
    single { ApiEvents() }
    single { AppLanguageStore(get(), get()) } bind UserDataCleaner::class
    single { DatabaseCleaner(get()) } bind UserDataCleaner::class
    single {
        val secureStore = get<SecureStore>()
        val languages = get<AppLanguageStore>()
        createHttpClient(
            engine = httpEngine(),
            config = get(),
            clientInfo = get(),
            credentialProvider = { secureStore.read()?.credential },
            languageProvider = { languages.effective().tag },
        )
    }
    single { ApiClient(get(), get()) }
    single { DeviceIdentityStore(get()) }

    // Sync engine (spec 004). Handlers come from the features; the scheduler from the platform module.
    single { get<AppDatabase>().pendingChangeDao() }
    single {
        val account = get<CurrentAccount>()
        SyncEngine(
            dao = get(),
            scopes = get<AppDatabase>().syncScopeDao(),
            handlers = getAll<SyncHandler>(),
            scheduler = get(),
            currentAccountId = { account.userId() },
            loadAccount = { account.load() },
        )
    }
    // Sign-out stops background sends; DatabaseCleaner empties the queue itself.
    single { UserDataCleaner { get<SyncScheduler>().cancel() } } bind UserDataCleaner::class
}
