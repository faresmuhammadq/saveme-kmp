package com.fares.saveme

import android.app.Application
import com.fares.saveme.core.config.AndroidServerAddressSettings
import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.Environment
import com.fares.saveme.core.config.ServerAddress
import com.fares.saveme.core.di.androidPlatformModule
import com.fares.saveme.core.di.initKoin
import com.fares.saveme.devtools.push.devOrUnavailablePushProvider
import org.koin.android.ext.koin.androidContext

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The build's backend, unless the user moved it in the Server address screen (takes effect on this start).
        val serverAddress = AndroidServerAddressSettings(this, default = BuildConfig.DEFAULT_API_HOST)
        val address = ServerAddress.parse(serverAddress.current())
        initKoin(
            config = AppConfig(
                baseUrl = "${BuildConfig.LINK_SCHEME}://${address?.hostPort ?: BuildConfig.DEFAULT_API_HOST}",
                linkHost = address?.host ?: BuildConfig.LINK_HOST,
                linkPort = if (address != null) address.port else BuildConfig.LINK_PORT.toIntOrNull(),
                environment = Environment.valueOf(BuildConfig.ENVIRONMENT),
                isDebugTools = BuildConfig.DEBUG_TOOLS,
                applicationId = BuildConfig.APPLICATION_ID,
                linkScheme = BuildConfig.LINK_SCHEME,
                serverAddressEditable = true,
            ),
            platformModule = androidPlatformModule(
                context = this,
                appVersion = BuildConfig.VERSION_NAME,
                notificationIcon = R.drawable.ic_stat_notification,
                // Development builds get the fake provider, the others none until the gated Firebase phase (spec 017 R-02, FR-046).
                pushTokenProvider = { devOrUnavailablePushProvider() },
                serverAddress = serverAddress,
            ),
            featureModules = appFeatureModules,
        ) { androidContext(this@MainApplication) }
    }
}
