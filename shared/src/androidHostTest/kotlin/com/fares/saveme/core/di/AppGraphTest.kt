package com.fares.saveme.core.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.fares.saveme.appFeatureModules
import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.Environment
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.wallets.domain.WalletsService
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

/**
 * The app's real Koin graph starts and resolves the sync engine and the wallets service. A
 * dependency cycle between a feature's sync handler and the engine overflows the stack at start
 * (the 006 E2E run crashed this way), which no feature-level test with hand-built objects sees.
 */
@RunWith(RobolectricTestRunner::class)
class AppGraphTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun theAppGraphStartsAndResolvesTheSyncEngine() {
        initKoin(
            config = AppConfig(baseUrl = "http://localhost/api/v1/", linkHost = "localhost", environment = Environment.Dev, isDebugTools = true),
            platformModule = androidPlatformModule(context, "test"),
            featureModules = appFeatureModules,
        ) { androidContext(context) }

        val koin = GlobalContext.get()
        assertNotNull(koin.get<SyncEngine>())
        assertNotNull(koin.get<WalletsService>())
    }
}
