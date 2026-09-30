package com.fares.saveme

import android.app.Application
import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.Environment
import com.fares.saveme.core.di.androidPlatformModule
import com.fares.saveme.core.di.initKoin
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
            ),
            platformModule = androidPlatformModule(this, BuildConfig.VERSION_NAME),
            featureModules = appFeatureModules,
        ) { androidContext(this@MainApplication) }
    }
}
