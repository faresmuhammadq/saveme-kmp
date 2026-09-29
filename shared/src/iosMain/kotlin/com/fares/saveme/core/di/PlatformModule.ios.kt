package com.fares.saveme.core.di

import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.database.buildAppDatabase
import com.fares.saveme.core.database.databaseBuilder
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.core.platform.IosClientInfo
import com.fares.saveme.core.storage.InMemoryKeyValueStore
import com.fares.saveme.core.storage.KeyValueStore
import com.fares.saveme.core.storage.SecureStore
import com.fares.saveme.core.storage.StubSecureStore
import org.koin.dsl.module

fun iosPlatformModule() = module {
    single<ClientInfo> { IosClientInfo() }
    single<SecureStore> { StubSecureStore() }
    single<KeyValueStore> { InMemoryKeyValueStore() }
    single<AppDatabase> { buildAppDatabase(databaseBuilder()) }
}
