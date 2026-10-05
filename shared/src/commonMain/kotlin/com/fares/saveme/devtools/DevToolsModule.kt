package com.fares.saveme.devtools

import com.fares.saveme.devtools.catalog.ComponentCatalogViewModel
import com.fares.saveme.devtools.pastelink.PasteLinkViewModel
import com.fares.saveme.feature.auth.domain.AuthLinkParser
import com.fares.saveme.feature.sharing.domain.InvitationLinkParser
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val devToolsModule = module {
    viewModelOf(::DevToolsViewModel)
    viewModelOf(::ComponentCatalogViewModel)
    viewModel {
        val parser = get<AuthLinkParser>()
        val invitations = get<InvitationLinkParser>()
        // Mailed links and, since spec 013, invitation links: both take the path a tapped link takes.
        PasteLinkViewModel(isAuthLink = { parser.parse(it) != null || invitations.parse(it) != null })
    }
}
