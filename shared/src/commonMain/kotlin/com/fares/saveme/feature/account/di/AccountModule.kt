package com.fares.saveme.feature.account.di

import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.ServerAddressSettings
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.feature.notifications.domain.PushTokenProvider
import com.fares.saveme.feature.account.data.AccountApi
import com.fares.saveme.feature.account.data.DeviceSessionRepository
import com.fares.saveme.feature.account.data.ExportApi
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
import com.fares.saveme.feature.account.ui.account.AccountPlan
import com.fares.saveme.feature.account.ui.account.AccountPlanStatus
import com.fares.saveme.feature.billing.domain.PlanSummary
import com.fares.saveme.feature.billing.domain.PlanSummaryStatus
import com.fares.saveme.feature.billing.domain.PlanOverview
import com.fares.saveme.feature.account.domain.SessionRefresher
import com.fares.saveme.feature.account.domain.deletion.DefaultDeletionPlanService
import com.fares.saveme.feature.account.domain.deletion.DeletionPlanService
import com.fares.saveme.feature.account.domain.export.DefaultExportService
import com.fares.saveme.feature.account.domain.export.ExportService
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.usecase.CancelDeletionUseCase
import com.fares.saveme.feature.account.domain.usecase.ChangePasswordUseCase
import com.fares.saveme.feature.account.domain.usecase.RequestDeletionUseCase
import com.fares.saveme.feature.account.domain.usecase.EndOtherSessionsUseCase
import com.fares.saveme.feature.account.domain.usecase.EndSessionUseCase
import com.fares.saveme.feature.account.ui.account.AccountViewModel
import com.fares.saveme.feature.account.ui.changepassword.ChangePasswordViewModel
import com.fares.saveme.feature.account.ui.deleteaccount.DeleteAccountViewModel
import com.fares.saveme.feature.account.ui.export.detail.ExportDetailViewModel
import com.fares.saveme.feature.account.ui.export.list.ExportsViewModel
import com.fares.saveme.feature.account.ui.export.request.ExportRequestViewModel
import com.fares.saveme.feature.account.ui.export.request.WalletChoice
import com.fares.saveme.feature.account.ui.profile.ProfileViewModel
import com.fares.saveme.feature.account.ui.sessions.DeviceSessionsViewModel
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.BudgetSummary
import com.fares.saveme.feature.auth.domain.HeldInvitationGlimpse
import com.fares.saveme.feature.auth.domain.DueSummary
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.auth.domain.usecase.ResendVerificationUseCase
import com.fares.saveme.feature.auth.domain.usecase.SignOutUseCase
import com.fares.saveme.feature.auth.domain.usecase.VerifyEmailUseCase
import com.fares.saveme.feature.auth.ui.home.CancelDeletionResult
import com.fares.saveme.feature.auth.ui.home.HomeViewModel
import com.fares.saveme.feature.auth.ui.verify.VerifyEmailViewModel
import com.fares.saveme.feature.wallets.domain.WalletMemberships
import com.fares.saveme.feature.wallets.domain.WalletsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** The budgets Home lists on its card (spec 012 FR-017). */
private const val HOME_BUDGETS = 8

val accountModule = module {
    single { AccountApi(get()) }
    single { ExportApi(get()) }
    single<ExportService> { DefaultExportService(get()) }
    single<DeletionPlanService> { DefaultDeletionPlanService(get(), get(), get(), get()) }
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
        val held = get<HeldInvitationGlimpse>()
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
            invitationHeld = held.observeHeld(),
            invitationDeclined = held.observeDeclined(),
            minuteTicks = minuteTicks(),
            displayName = get<ProfileSyncer>().view.map { it?.effective?.displayName },
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
            email = { (sessions.state.value as? AuthState.SignedIn)?.account?.email },
        )
    }
    viewModel {
        val signOut = get<SignOutUseCase>()
        val engine = get<SyncEngine>()
        val syncer = get<ProfileSyncer>()
        val overview = get<PlanOverview>()
        val sessionList = get<SessionRefresher>()
        AccountViewModel(
            get<SessionManager>().state,
            signOut = { signOut() },
            syncStatus = engine.status,
            syncNow = { engine.syncNow() },
            notificationsAvailable = get<PushTokenProvider>().isAvailable,
            devToolsAvailable = get<AppConfig>().isDebugTools,
            serverAddress = if (get<AppConfig>().serverAddressEditable) get<ServerAddressSettings>().current() else null,
            displayName = syncer.view.map { it?.effective?.displayName },
            plan = flow {
                overview.loadStored()
                emitAll(overview.summary.map { it?.toAccountPlan() })
            },
            deviceCount = sessionList.sessions.map { it.items.size },
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
        val exports = get<ExportService>()
        ExportsViewModel(
            page = { exports.page(it) },
            walletNames = walletNames(get()),
            signedIn = signedInFlow(get()),
            timeZoneId = profileTimeZone(get(), get()),
        )
    }
    viewModel { params ->
        val exports = get<ExportService>()
        val wallets = get<WalletsService>()
        val engine = get<SyncEngine>()
        ExportRequestViewModel(
            request = { scope, walletId -> exports.request(scope, walletId) },
            // A wallet made on this phone has version 0 and is not on the server yet: it cannot be exported (research R-06).
            wallets = wallets.observeList().map { list ->
                (list.active + list.archived).filter { it.server.version > 0 }.map { WalletChoice(it.id, it.shown.name, it.archived, it.shown.icon, it.shown.color, it.shown.walletType) }
            },
            unsentChanges = engine.status.map { it.pending > 0 },
            signedIn = signedInFlow(get()),
            presetWalletId = params.getOrNull<String>()?.takeIf { it.isNotBlank() },
        )
    }
    viewModel { params ->
        val exports = get<ExportService>()
        ExportDetailViewModel(
            exportId = params.get<String>(),
            watch = { id, windowStart -> exports.watch(id, windowStart) },
            openableLink = { exports.openableLink(it) },
            walletNames = walletNames(get()),
            signedIn = signedInFlow(get()),
            timeZoneId = profileTimeZone(get(), get()),
        )
    }
    viewModel {
        val request = get<RequestDeletionUseCase>()
        val cancel = get<CancelDeletionUseCase>()
        val plans = get<DeletionPlanService>()
        val sessions = get<SessionManager>()
        DeleteAccountViewModel(
            readPlan = { plans.read() },
            requestDeletion = { password, successors -> request(password, successors) },
            cancelDeletion = { cancel() },
            pendingOn = {
                (sessions.state.value as? AuthState.SignedIn)?.account
                    ?.takeIf { it.state == AccountState.PendingDeletion }?.deletionScheduledFor
            },
        )
    }
}

/** The present instant, then again at the start of every minute while collected (Home's "being deleted", spec 016 FR-032). */
private fun minuteTicks(clock: Clock = Clock.System) = flow {
    while (true) {
        val now = clock.now()
        emit(now)
        delay(MINUTE_MS - now.toEpochMilliseconds() % MINUTE_MS)
    }
}

private const val MINUTE_MS = 60_000L

/** The wallets by id, active and archived, for naming a wallet export (spec 016 FR-005). */
private fun walletNames(wallets: WalletsService) =
    wallets.observeList().map { list -> (list.active + list.archived).associate { it.id to it.shown.name } }

private fun signedInFlow(sessions: SessionManager) = sessions.state.map { it is AuthState.SignedIn }.distinctUntilChanged()

/** Times are shown in the profile timezone (P-05); the device's until the profile is known. */
private fun profileTimeZone(syncer: ProfileSyncer, clientInfo: ClientInfo) =
    syncer.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() }

/** The plan row's data, from the billing summary. */
private fun PlanSummary.toAccountPlan() = AccountPlan(
    name,
    when (val status = status) {
        is PlanSummaryStatus.Renews -> AccountPlanStatus.Renews(status.on)
        is PlanSummaryStatus.Ends -> AccountPlanStatus.Ends(status.on)
        PlanSummaryStatus.NoEnd -> AccountPlanStatus.NoEnd
        is PlanSummaryStatus.Grace -> AccountPlanStatus.Grace(status.until)
        null -> null
    },
)
