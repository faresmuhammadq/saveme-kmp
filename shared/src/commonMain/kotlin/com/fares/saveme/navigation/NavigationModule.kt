package com.fares.saveme.navigation

import org.koin.dsl.module

val navigationModule = module {
    single { DeepLinkBus() }
    single { PushTapBus() }
}
