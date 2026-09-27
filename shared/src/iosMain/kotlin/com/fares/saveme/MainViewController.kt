package com.fares.saveme

import androidx.compose.ui.window.ComposeUIViewController
import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.Environment
import com.fares.saveme.core.di.initKoin
import com.fares.saveme.core.di.iosPlatformModule

private val koinStarted by lazy {
    // iOS compiles and renders only; live API calls are out of scope (P-15).
    initKoin(
        config = AppConfig("http://localhost:8080", "localhost", Environment.Dev, isDebugTools = true),
        platformModule = iosPlatformModule(),
        featureModules = appFeatureModules,
    )
}

fun MainViewController() = ComposeUIViewController {
    koinStarted
    App()
}
