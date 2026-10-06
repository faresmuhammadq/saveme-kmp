package com.fares.saveme.devtools

import com.fares.saveme.devtools.catalog.ComponentCatalogViewModel
import com.fares.saveme.devtools.pastelink.PasteLinkViewModel
import com.fares.saveme.feature.auth.domain.AuthLinkParser
import com.fares.saveme.feature.sharing.domain.InvitationLinkParser
import com.fares.saveme.devtools.push.PhoneIds
import com.fares.saveme.devtools.push.DevFakePushTokenProvider
import com.fares.saveme.devtools.push.SimulatePushViewModel
import com.fares.saveme.feature.budgets.domain.BudgetsService
import com.fares.saveme.feature.notifications.domain.PushReceiver
import com.fares.saveme.feature.notifications.domain.PushTokenProvider
import com.fares.saveme.feature.recurring.domain.RecurringService
import com.fares.saveme.feature.wallets.domain.WalletsService
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString
import org.koin.core.module.dsl.viewModel
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.push_dev_body
import saveme.shared.generated.resources.push_dev_title
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
    viewModel {
        val receiver = get<PushReceiver>()
        val provider = get<PushTokenProvider>()
        SimulatePushViewModel(
            phoneIds = {
                val occurrence = get<RecurringService>().observeOccurrences(null).first().let { it.due + it.upcoming }.firstOrNull()
                PhoneIds(
                    walletId = occurrence?.occurrence?.walletId ?: get<WalletsService>().observeList().first().active.firstOrNull()?.id,
                    occurrenceId = occurrence?.id,
                    budgetId = get<BudgetsService>().observeOverview().first().active.firstOrNull()?.id,
                )
            },
            texts = { getString(Res.string.push_dev_title) to getString(Res.string.push_dev_body) },
            post = { data, title, body -> receiver.onMessage(data, title, body) },
            rotate = { (provider as? DevFakePushTokenProvider)?.rotate() },
        )
    }
}
