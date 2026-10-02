package com.fares.saveme.core.di

import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.database.DatabaseCleaner
import com.fares.saveme.core.sync.CurrentAccount
import com.fares.saveme.core.sync.DefaultSyncRunner
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.FeedPuller
import com.fares.saveme.core.sync.HttpSyncApi
import com.fares.saveme.core.sync.SyncApi
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.core.sync.SyncLock
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.core.sync.SyncScheduler
import com.fares.saveme.core.sync.SyncSignOutCleaner
import com.fares.saveme.core.sync.WalletScopes
import com.fares.saveme.core.locale.AppLanguageStore
import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiEvents
import com.fares.saveme.core.network.createHttpClient
import com.fares.saveme.core.network.httpEngine
import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.core.storage.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    // Sync engine (spec 004) and its feeds and batch upload (spec 009). Handlers and appliers come
    // from the features, the scheduler from the platform module. Everything the engine builds from
    // the features is resolved here once; what a handler or applier needs of the engine, the runner
    // or a service it resolves on use (spec 006 lesson: a cycle overflows the stack at start).
    single { get<AppDatabase>().pendingChangeDao() }
    single<SyncApi> { HttpSyncApi(get(), get()) }
    single { SyncLock() }
    single {
        val account = get<CurrentAccount>()
        SyncEngine(
            dao = get(),
            scopes = get<AppDatabase>().syncScopeDao(),
            handlers = getAll<SyncHandler>(),
            scheduler = get(),
            currentAccountId = { account.userId() },
            loadAccount = { account.load() },
            syncApi = get(),
            lock = get(),
        )
    }
    single {
        val account = get<CurrentAccount>()
        FeedPuller(
            api = get(),
            scopes = get<AppDatabase>().syncScopeDao(),
            appliers = { getAll<FeedApplier>() },
            walletScopes = { getOrNull<WalletScopes>() },
            lock = get(),
            currentAccountId = { account.userId() },
        )
    }
    single<SyncRunner> {
        val account = get<CurrentAccount>()
        DefaultSyncRunner(
            engine = get(),
            puller = get(),
            walletScopes = { getOrNull<WalletScopes>() },
            currentAccountId = { account.userId() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    // Sign-out stops the run in progress, so nothing is sent or applied under another account (FR-028).
    single { SyncSignOutCleaner { get<SyncRunner>() } } bind UserDataCleaner::class
    // Sign-out stops background sends; DatabaseCleaner empties the queue itself.
    single { UserDataCleaner { get<SyncScheduler>().cancel() } } bind UserDataCleaner::class
}
