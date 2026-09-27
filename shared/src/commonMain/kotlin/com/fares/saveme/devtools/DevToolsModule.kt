package com.fares.saveme.devtools

import com.fares.saveme.devtools.catalog.ComponentCatalogViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val devToolsModule = module {
    viewModelOf(::DevToolsViewModel)
    viewModelOf(::ComponentCatalogViewModel)
}
