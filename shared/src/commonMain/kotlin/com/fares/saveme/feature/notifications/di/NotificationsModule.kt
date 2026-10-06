package com.fares.saveme.feature.notifications.di

import com.fares.saveme.core.session.SignOutParticipant
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.core.sync.SyncRunner
import com.fares.saveme.feature.account.domain.AccountRefresher
import com.fares.saveme.feature.account.domain.ProfileSyncer
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.budgets.domain.BudgetsService
import com.fares.saveme.feature.notifications.data.NotificationsApi
import com.fares.saveme.feature.notifications.data.PromptFlags
import com.fares.saveme.feature.notifications.data.PushRegistrationStore
import com.fares.saveme.feature.notifications.data.ShownNotificationsStore
import com.fares.saveme.feature.notifications.domain.DefaultRecordPresence
import com.fares.saveme.feature.notifications.domain.NotificationOpenService
import com.fares.saveme.feature.notifications.domain.NotificationPreferencesService
import com.fares.saveme.feature.notifications.domain.NotificationPromptGate
import com.fares.saveme.feature.notifications.domain.PushCoordinator
import com.fares.saveme.feature.notifications.domain.PushDataCleaner
import com.fares.saveme.feature.notifications.domain.PushReceiver
import com.fares.saveme.feature.notifications.domain.PushReconciler
import com.fares.saveme.feature.notifications.domain.PushRegistrationService
import com.fares.saveme.feature.notifications.domain.PushRoute
import com.fares.saveme.feature.notifications.domain.PushSignOutParticipant
import com.fares.saveme.feature.notifications.domain.RecordPresence
import com.fares.saveme.feature.notifications.ui.invitation.InvitationNoticeViewModel
import com.fares.saveme.feature.notifications.ui.open.NotificationOpenViewModel
import com.fares.saveme.feature.notifications.ui.resolveChannelLabels
import com.fares.saveme.feature.notifications.ui.settings.NotificationSettingsViewModel
import com.fares.saveme.feature.recurring.domain.RecurringService
import com.fares.saveme.feature.wallets.domain.WalletsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Push notifications (spec 017). The platform module binds the provider, the display, the permission state and the
 * scheduler; this module resolves the sync runner and the other features' services on use (spec 006 lesson: a
 * cycle through the session manager's cleaners overflows the stack at start).
 */
val notificationsModule = module {
    single { NotificationsApi(get()) }
    single { PushRegistrationStore(get()) }
    single { ShownNotificationsStore(get()) }
    single { PromptFlags(get()) }
    single { NotificationPreferencesService(get()) }
    single { NotificationPromptGate(get(), get()) }

    single { PushRegistrationService(get(), get(), get(), get<SessionManager>(), get(), get()) } bind PushReconciler::class
    single(createdAtStart = true) {
        PushCoordinator(get<SessionManager>(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)).also { it.start() }
    }
    single { PushSignOutParticipant(get(), get(), get()) } bind SignOutParticipant::class
    single {
        PushDataCleaner(get(), get(), get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    } bind UserDataCleaner::class
    single {
        PushReceiver(
            sessions = get<SessionManager>(),
            shown = get(),
            display = get(),
            labels = { resolveChannelLabels() },
            coordinator = { get<PushCoordinator>() },
            runner = { get<SyncRunner>() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    single<RecordPresence> { DefaultRecordPresence({ get<WalletsService>() }, { get<RecurringService>() }, { get<BudgetsService>() }) }
    single {
        NotificationOpenService(
            runner = { get<SyncRunner>() },
            refreshEntitlements = { get<SessionManager>().refreshEntitlements() },
            refreshAccount = { get<AccountRefresher>().refresh() },
            presence = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    viewModel {
        val preferences = get<NotificationPreferencesService>()
        val coordinator = get<PushCoordinator>()
        val syncer = get<ProfileSyncer>()
        NotificationSettingsViewModel(
            load = { preferences.load() },
            save = { edited, version -> preferences.save(edited, version) },
            timeZone = { syncer.view.first()?.server?.timezoneId },
            permissionChanged = { coordinator.onPermissionChanged() },
        )
    }
    viewModel { params ->
        val open = get<NotificationOpenService>()
        NotificationOpenViewModel(params.get<PushRoute>()) { open.open(it) }
    }
    viewModel {
        val sessions = get<SessionManager>()
        InvitationNoticeViewModel(address = { (sessions.state.value as? AuthState.SignedIn)?.account?.email.orEmpty() })
    }
}
