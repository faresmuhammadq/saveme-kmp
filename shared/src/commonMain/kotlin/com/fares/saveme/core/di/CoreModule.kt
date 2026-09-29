package com.fares.saveme.core.di

import com.fares.saveme.core.database.DatabaseCleaner
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
}
