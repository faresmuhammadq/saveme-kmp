package com.fares.saveme.core.di

import com.fares.saveme.core.network.ApiClient
import com.fares.saveme.core.network.ApiEvents
import com.fares.saveme.core.network.createHttpClient
import com.fares.saveme.core.network.httpEngine
import com.fares.saveme.core.storage.DeviceIdentityStore
import com.fares.saveme.core.storage.SecureStore
import org.koin.dsl.module

val coreModule = module {
    single { ApiEvents() }
    single {
        val secureStore = get<SecureStore>()
        createHttpClient(
            engine = httpEngine(),
            config = get(),
            clientInfo = get(),
            credentialProvider = { secureStore.read()?.credential },
        )
    }
    single { ApiClient(get(), get()) }
    single { DeviceIdentityStore(get()) }
}
