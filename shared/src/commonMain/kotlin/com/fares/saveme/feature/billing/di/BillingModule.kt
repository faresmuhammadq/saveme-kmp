package com.fares.saveme.feature.billing.di

import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.feature.account.domain.ProfileSyncer
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.billing.data.BillingApi
import com.fares.saveme.feature.billing.data.BillingDataCleaner
import com.fares.saveme.feature.billing.data.CatalogueRepository
import com.fares.saveme.feature.billing.data.SubscriptionRepository
import com.fares.saveme.feature.billing.domain.BillingSyncCoordinator
import com.fares.saveme.feature.billing.domain.Entitlements
import com.fares.saveme.feature.billing.domain.EntitlementsRefresher
import com.fares.saveme.feature.billing.domain.PlanOverview
import com.fares.saveme.feature.billing.domain.RefreshReason
import com.fares.saveme.feature.billing.domain.SessionEntitlements
import com.fares.saveme.feature.billing.ui.plan.PlanViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val billingModule = module {
    single { BillingApi(get()) }
    single { SubscriptionRepository(get(), get()) }
    single { CatalogueRepository(get(), get()) }
    single { BillingDataCleaner(get(), get()) } bind UserDataCleaner::class
    single { EntitlementsRefresher(get<SessionManager>(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single<Entitlements> { SessionEntitlements(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single(createdAtStart = true) {
        BillingSyncCoordinator(get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)).also { it.start() }
    }
    single { PlanOverview(get(), get(), get(), get()) }

    viewModel {
        val overview = get<PlanOverview>()
        val refresher = get<EntitlementsRefresher>()
        val syncer = get<ProfileSyncer>()
        val clientInfo = get<ClientInfo>()
        PlanViewModel(
            plan = overview.current,
            subscription = overview.subscription,
            catalogue = overview.plans,
            // Times show in the profile timezone (P-05); the device's until the profile is known.
            timeZoneId = syncer.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() },
            loadStored = { overview.loadStored() },
            refresh = { refresher.refresh(RefreshReason.PlanScreen) },
            loadCatalogue = { overview.loadCatalogue() },
        )
    }
}
