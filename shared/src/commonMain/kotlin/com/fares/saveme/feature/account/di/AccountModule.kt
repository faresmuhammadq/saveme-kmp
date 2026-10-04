package com.fares.saveme.feature.account.di

import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.feature.account.data.AccountApi
import com.fares.saveme.feature.account.data.DeviceSessionRepository
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.account.data.DeviceSessionFeedApplier
import com.fares.saveme.feature.account.data.ProfileFeedApplier
import com.fares.saveme.feature.account.data.ProfileRepository
import com.fares.saveme.feature.account.data.ProfileSyncHandler
import com.fares.saveme.feature.account.domain.AccountRefresher
import com.fares.saveme.feature.account.domain.AccountSyncCoordinator
import com.fares.saveme.feature.account.domain.ProfileSyncer
import com.fares.saveme.feature.account.domain.SessionRefresher
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.usecase.CancelDeletionUseCase
import com.fares.saveme.feature.account.domain.usecase.ChangePasswordUseCase
import com.fares.saveme.feature.account.domain.usecase.RequestDeletionUseCase
import com.fares.saveme.feature.account.domain.usecase.EndOtherSessionsUseCase
import com.fares.saveme.feature.account.domain.usecase.EndSessionUseCase
import com.fares.saveme.feature.account.ui.account.AccountViewModel
import com.fares.saveme.feature.account.ui.changepassword.ChangePasswordViewModel
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountViewModel
import com.fares.saveme.feature.account.ui.profile.ProfileViewModel
import com.fares.saveme.feature.account.ui.sessions.DeviceSessionsViewModel
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.BudgetSummary
import com.fares.saveme.feature.auth.domain.DueSummary
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.usecase.ResendVerificationUseCase
import com.fares.saveme.feature.auth.domain.usecase.SignOutUseCase
import com.fares.saveme.feature.auth.domain.usecase.VerifyEmailUseCase
import com.fares.saveme.feature.auth.ui.home.CancelDeletionResult
import com.fares.saveme.feature.auth.ui.home.HomeViewModel
import com.fares.saveme.feature.auth.ui.verify.VerifyEmailViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** The budgets Home lists on its card (spec 012 FR-017). */
private const val HOME_BUDGETS = 3

val accountModule = module {
    single { AccountApi(get()) }
    single {
        val sessions = get<SessionManager>()
        DeviceSessionRepository(
            get(), get<AppDatabase>().deviceSessionDao(), get<AppDatabase>().syncScopeDao(),
            accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId },
        )
    }
    single { SessionRefresher(get(), get()) }
    single { ProfileRepository(get(), get<AppDatabase>().profileDao(), get<AppDatabase>().syncScopeDao()) }
    single { ProfileSyncHandler(get(), get()) } bind SyncHandler::class
    // Feed appliers of the person's scope (spec 009). The engine and the syncer are resolved on use: both are built from this module's handler.
    single {
        ProfileFeedApplier(get(), get()) { id -> get<SyncEngine>().get(ProfileSyncHandler.RECORD_TYPE, id) != null }
    } bind FeedApplier::class
    single { DeviceSessionFeedApplier(get()) } bind FeedApplier::class
    single { ProfileSyncer(get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default), get()) }
    single { AccountRefresher(get(), get(), get(), get()) }
    single(createdAtStart = true) {
        AccountSyncCoordinator(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default), get(), get()).also { it.start() }
    }
    factory { EndSessionUseCase(get(), get()) }
    factory { EndOtherSessionsUseCase(get(), get()) }
    factory { ChangePasswordUseCase(get(), get()) }
    factory { RequestDeletionUseCase(get(), get()) }
    factory { CancelDeletionUseCase(get(), get()) }

    viewModel {
        val signOut = get<SignOutUseCase>()
        val cancel = get<CancelDeletionUseCase>()
        val resend = get<ResendVerificationUseCase>()
        val engine = get<SyncEngine>()
        val due = get<DueSummary>()
        val budgets = get<BudgetSummary>()
        HomeViewModel(
            authState = get<SessionManager>().state,
            signOut = { signOut() },
            cancelDeletion = {
                when (cancel()) {
                    ActionOutcome.Done -> CancelDeletionResult.Done
                    ActionOutcome.Offline -> CancelDeletionResult.Offline
                    is ActionOutcome.Error -> CancelDeletionResult.Failed
                }
            },
            resendVerification = { resend() },
            syncStatus = engine.status,
            syncNow = { engine.syncNow() },
            dueCount = due.observeDueCount(),
            firstDue = due.observeFirstDue(),
            budgets = budgets.observeTop(HOME_BUDGETS),
        )
    }
    // Registered here, not in authModule: it re-reads the account through this feature's
    // AccountRefresher after a verification (spec 003 FR-011), and auth never imports account.
    viewModel { params ->
        val verify = get<VerifyEmailUseCase>()
        val resend = get<ResendVerificationUseCase>()
        val refresher = get<AccountRefresher>()
        val sessions = get<SessionManager>()
        VerifyEmailViewModel(
            token = params.get<String>(),
            isSignedIn = { sessions.state.value is AuthState.SignedIn },
            verify = { verify(it) },
            resend = { resend() },
            refreshAccount = { refresher.refresh() },
        )
    }
    viewModel {
        val signOut = get<SignOutUseCase>()
        val engine = get<SyncEngine>()
        AccountViewModel(
            get<SessionManager>().state,
            signOut = { signOut() },
            syncStatus = engine.status,
            syncNow = { engine.syncNow() },
        )
    }
    viewModel {
        val refresher = get<SessionRefresher>()
        val endOne = get<EndSessionUseCase>()
        val endOthers = get<EndOtherSessionsUseCase>()
        DeviceSessionsViewModel(
            sessions = refresher.sessions,
            timeZoneId = profileTimeZone(get(), get()),
            refresh = { refresher.refresh() },
            endSession = { endOne(it) },
            endOthers = { endOthers() },
        )
    }
    viewModel {
        val sessions = get<SessionManager>()
        val change = get<ChangePasswordUseCase>()
        ChangePasswordViewModel(
            email = { (sessions.state.value as? AuthState.SignedIn)?.account?.email.orEmpty() },
            changePassword = { current, new -> change(current, new) },
        )
    }
    viewModel {
        val sessions = get<SessionManager>()
        val syncer = get<ProfileSyncer>()
        val refresher = get<AccountRefresher>()
        ProfileViewModel(
            account = sessions.state.filterIsInstance<AuthState.SignedIn>().map { it.account.email to it.account.emailVerified },
            profile = syncer.view,
            refresh = { refresher.refresh() },
            saveLocally = { syncer.saveLocally(it) },
            keepMine = { syncer.keepMine() },
            useTheirs = { syncer.useTheirs() },
            discard = { syncer.discard() },
        )
    }
    viewModel {
        val request = get<RequestDeletionUseCase>()
        DeleteAccountViewModel(requestDeletion = { request(it) })
    }
}

/** Times are shown in the profile timezone (P-05); the device's until the profile is known. */
private fun profileTimeZone(syncer: ProfileSyncer, clientInfo: ClientInfo) =
    syncer.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() }
