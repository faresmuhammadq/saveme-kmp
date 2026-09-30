package com.fares.saveme.devtools

import com.fares.saveme.devtools.catalog.ComponentCatalogViewModel
import com.fares.saveme.devtools.pastelink.PasteLinkViewModel
import com.fares.saveme.feature.auth.domain.AuthLinkParser
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val devToolsModule = module {
    viewModelOf(::DevToolsViewModel)
    viewModelOf(::ComponentCatalogViewModel)
    viewModel {
        val parser = get<AuthLinkParser>()
        PasteLinkViewModel(isAuthLink = { parser.parse(it) != null })
    }
}
