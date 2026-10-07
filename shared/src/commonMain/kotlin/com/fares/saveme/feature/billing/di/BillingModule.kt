package com.fares.saveme.feature.billing.di

import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.feature.account.domain.ProfileSyncer
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.billing.data.BillingApi
import com.fares.saveme.feature.billing.data.BillingDataCleaner
import com.fares.saveme.feature.billing.data.CatalogueRepository
import com.fares.saveme.feature.billing.data.SubscriptionRepository
import com.fares.saveme.feature.billing.data.play.PlayBillingClient
import com.fares.saveme.feature.billing.domain.BillingAnswerApplier
import com.fares.saveme.feature.billing.domain.BillingSyncCoordinator
import com.fares.saveme.feature.billing.domain.Entitlements
import com.fares.saveme.feature.billing.domain.EntitlementsRefresher
import com.fares.saveme.feature.billing.domain.PlanOverview
import com.fares.saveme.feature.billing.domain.RedeemService
import com.fares.saveme.feature.billing.domain.RefreshReason
import com.fares.saveme.feature.billing.domain.SessionEntitlements
import com.fares.saveme.feature.billing.domain.play.PassTrigger
import com.fares.saveme.feature.billing.domain.play.PlayBillingCleaner
import com.fares.saveme.feature.billing.domain.play.PlayBillingNotes
import com.fares.saveme.feature.billing.domain.play.PlayPassLock
import com.fares.saveme.feature.billing.domain.play.PlayPriceLoader
import com.fares.saveme.feature.billing.domain.play.PlayPurchaseVerifier
import com.fares.saveme.feature.billing.domain.play.PlayRestoreService
import com.fares.saveme.feature.billing.domain.play.PlayStoreLinks
import com.fares.saveme.feature.billing.domain.play.RestoreTrigger
import com.fares.saveme.feature.billing.ui.plan.PlanViewModel
import com.fares.saveme.feature.billing.ui.redeem.RedeemViewModel
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
    single { PlanOverview(get(), get(), get(), get()) }
    single { BillingAnswerApplier(get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single { RedeemService(get(), get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default), applier = get()) }

    // Google Play (spec 018). The port is bound per platform (and per build): none of these resolves it while the graph is
    // built, so the Koin cycle of spec 006 cannot recur.
    single { PlayBillingNotes() }
    single { PlayPassLock() }
    single { PlayPriceLoader(get<PlayBillingClient>()) }
    single {
        val overview = get<PlanOverview>()
        PlayPurchaseVerifier(
            port = get(),
            api = get(),
            sessions = get(),
            applier = get(),
            notes = get(),
            reloadCatalogue = { overview.loadCatalogue() },
            lock = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    single {
        PlayRestoreService(get(), get(), get(), get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
    single { PlayBillingCleaner(get(), get()) { get<PlayPurchaseVerifier>().cancelTimers() } } bind UserDataCleaner::class
    single(createdAtStart = true) {
        BillingSyncCoordinator(
            sessions = get(),
            refresher = get(),
            apiEvents = get(),
            verifier = get(),
            restorer = get(),
            purchaseUpdates = get<PlayBillingClient>().purchaseUpdates,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ).also { it.start() }
    }

    viewModel {
        val overview = get<PlanOverview>()
        val refresher = get<EntitlementsRefresher>()
        val syncer = get<ProfileSyncer>()
        val clientInfo = get<ClientInfo>()
        val port = get<PlayBillingClient>()
        val notes = get<PlayBillingNotes>()
        val verifier = get<PlayPurchaseVerifier>()
        val restorer = get<PlayRestoreService>()
        val prices = get<PlayPriceLoader>()
        val applicationId = get<AppConfig>().applicationId
        PlanViewModel(
            plan = overview.current,
            subscription = overview.subscription,
            catalogue = overview.plans,
            // Times show in the profile timezone (P-05); the device's until the profile is known.
            timeZoneId = syncer.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() },
            loadStored = { overview.loadStored() },
            refresh = { refresher.refresh(RefreshReason.PlanScreen) },
            loadCatalogue = { overview.loadCatalogue() },
            playSupported = port.platformSupported,
            notes = notes.notes,
            runPurchasePass = { verifier.pass(PassTrigger.PlanScreen) },
            loadPlayPrices = { ids -> prices.load(ids) },
            restorePurchases = { retry -> restorer.restore(RestoreTrigger.Tap, retry) },
            subscriptionsUrl = { productId -> PlayStoreLinks.subscriptions(applicationId, productId) },
            consumeActivated = { notes.consumeActivated() },
            screenOpened = { notes.planScreenOpened() },
            screenClosed = { notes.planScreenClosed() },
        )
    }

    viewModel {
        val sessions = get<SessionManager>()
        val syncer = get<ProfileSyncer>()
        val clientInfo = get<ClientInfo>()
        val service = get<RedeemService>()
        RedeemViewModel(
            redeem = { code, attempt -> service.redeem(code, attempt) },
            isEmailVerified = { (sessions.state.value as? AuthState.SignedIn)?.account?.emailVerified == true },
            signedInEmail = { (sessions.state.value as? AuthState.SignedIn)?.account?.email },
            signedIn = sessions.state.map { it is AuthState.SignedIn },
            timeZoneId = syncer.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() },
        )
    }
}
