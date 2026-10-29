package com.fares.saveme.core.di

import com.fares.saveme.core.config.ServerAddressSettings
import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.database.buildAppDatabase
import com.fares.saveme.core.database.databaseBuilder
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.core.platform.IosClientInfo
import com.fares.saveme.core.storage.InMemoryKeyValueStore
import com.fares.saveme.core.storage.KeyValueStore
import com.fares.saveme.core.storage.SecureStore
import com.fares.saveme.core.storage.StubSecureStore
import com.fares.saveme.core.sync.NoopSyncScheduler
import com.fares.saveme.core.sync.SyncScheduler
import com.fares.saveme.feature.billing.data.play.PlayBillingClient
import com.fares.saveme.feature.billing.data.play.UnavailablePlayBillingClient
import com.fares.saveme.feature.sharing.NoLinkSharer
import com.fares.saveme.feature.sharing.domain.LinkSharer
import com.fares.saveme.feature.notifications.IosNotificationPermissionState
import com.fares.saveme.feature.notifications.NoopNotificationDisplay
import com.fares.saveme.feature.notifications.NoopPushRegistrationScheduler
import com.fares.saveme.feature.notifications.domain.NotificationDisplay
import com.fares.saveme.feature.notifications.domain.NotificationPermissionState
import com.fares.saveme.feature.notifications.domain.PushRegistrationScheduler
import com.fares.saveme.feature.notifications.domain.PushTokenProvider
import com.fares.saveme.feature.notifications.domain.UnavailablePushTokenProvider
import org.koin.dsl.module

fun iosPlatformModule() = module {
    // The server address is not editable on iOS (AppConfig.serverAddressEditable stays false).
    single<ServerAddressSettings> {
        object : ServerAddressSettings {
            override val default = ""
            override fun current() = ""
            override fun save(hostPort: String) = Unit
            override fun reset() = Unit
            override fun restartApp() = Unit
        }
    }
    single<ClientInfo> { IosClientInfo() }
    single<SecureStore> { StubSecureStore() }
    single<KeyValueStore> { InMemoryKeyValueStore() }
    single<AppDatabase> { buildAppDatabase(databaseBuilder()) }
    single<SyncScheduler> { NoopSyncScheduler() }
    single<LinkSharer> { NoLinkSharer() }
    // No push on iOS (FR-042): no provider, no channels, no permission, no display.
    single<PushTokenProvider> { UnavailablePushTokenProvider }
    single<NotificationPermissionState> { IosNotificationPermissionState() }
    single<NotificationDisplay> { NoopNotificationDisplay() }
    single<PushRegistrationScheduler> { NoopPushRegistrationScheduler() }
    // No Google Play on iOS (FR-055).
    single<PlayBillingClient> { UnavailablePlayBillingClient(platformSupported = false) }
}
