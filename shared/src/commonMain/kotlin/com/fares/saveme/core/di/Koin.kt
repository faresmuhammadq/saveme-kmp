package com.fares.saveme.core.di

import com.fares.saveme.core.config.AppConfig
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

fun initKoin(
    config: AppConfig,
    platformModule: Module,
    featureModules: List<Module>,
    appDeclaration: KoinApplication.() -> Unit = {},
): KoinApplication = startKoin {
    appDeclaration()
    modules(module { single { config } }, platformModule, coreModule)
    modules(featureModules)
}
