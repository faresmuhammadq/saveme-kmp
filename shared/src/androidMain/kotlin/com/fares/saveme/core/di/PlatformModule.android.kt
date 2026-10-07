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
import com.fares.saveme.feature.sharing.AndroidLinkSharer
import com.fares.saveme.feature.sharing.domain.LinkSharer
import com.fares.saveme.devtools.playbilling.devOrRealPlayClient
import com.fares.saveme.devtools.push.devOrUnavailablePushProvider
import com.fares.saveme.feature.billing.data.play.PlayBillingClient
import com.fares.saveme.feature.notifications.AndroidNotificationDisplay
import com.fares.saveme.feature.notifications.AndroidNotificationPermissionState
import com.fares.saveme.feature.notifications.WorkManagerPushRegistrationScheduler
import com.fares.saveme.feature.notifications.domain.NotificationDisplay
import com.fares.saveme.feature.notifications.domain.NotificationPermissionState
import com.fares.saveme.feature.notifications.domain.PushRegistrationScheduler
import com.fares.saveme.feature.notifications.domain.PushTokenProvider
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * File names must match the exclusions in androidApp's backup rules (P-09.1). [notificationIcon] is the host's monochrome
 * small icon; [pushTokenProvider] is chosen per build by the host (spec 017 R-02): the fake in development builds, none
 * in staging and production until the Firebase phase swaps it in `:androidApp` only.
 */
fun androidPlatformModule(
    context: Context,
    appVersion: String,
    notificationIcon: Int = android.R.drawable.stat_notify_more,
    pushTokenProvider: Scope.() -> PushTokenProvider = { devOrUnavailablePushProvider() },
) = module {
    single<ClientInfo> { AndroidClientInfo(appVersion) }
    single<SecureStore> {
        KeystoreSecureStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("secure_session") })
    }
    single<KeyValueStore> {
        DataStoreKeyValueStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("app_prefs") })
    }
    single<AppDatabase> { buildAppDatabase(databaseBuilder(context)) }
    single<SyncScheduler> { WorkManagerSyncScheduler(context) }
    single<LinkSharer> { AndroidLinkSharer(context) }
    single<PushTokenProvider> { pushTokenProvider() }
    single<NotificationPermissionState> { AndroidNotificationPermissionState(context) }
    single<NotificationDisplay> { AndroidNotificationDisplay(context, notificationIcon, get()) }
    single<PushRegistrationScheduler> { WorkManagerPushRegistrationScheduler(context) }
    // Google Play: the unavailable port until the Play Billing wiring is approved (spec 018 G1); development builds wrap it with the scripted one.
    single<PlayBillingClient> { devOrRealPlayClient() }
}
