package com.fares.saveme

import android.app.Application
import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.Environment
import com.fares.saveme.core.di.androidPlatformModule
import com.fares.saveme.core.di.initKoin
import com.fares.saveme.devtools.push.devOrUnavailablePushProvider
import org.koin.android.ext.koin.androidContext

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            config = AppConfig(
                baseUrl = BuildConfig.API_BASE_URL,
                linkHost = BuildConfig.LINK_HOST,
                linkPort = BuildConfig.LINK_PORT.toIntOrNull(),
                environment = Environment.valueOf(BuildConfig.ENVIRONMENT),
                isDebugTools = BuildConfig.DEBUG_TOOLS,
                applicationId = BuildConfig.APPLICATION_ID,
            ),
            platformModule = androidPlatformModule(
                context = this,
                appVersion = BuildConfig.VERSION_NAME,
                notificationIcon = R.drawable.ic_stat_notification,
                // Development builds get the fake provider, the others none until the gated Firebase phase (spec 017 R-02, FR-046).
                pushTokenProvider = { devOrUnavailablePushProvider() },
            ),
            featureModules = appFeatureModules,
        ) { androidContext(this@MainApplication) }
    }
}
