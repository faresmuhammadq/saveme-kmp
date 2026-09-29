package com.fares.saveme.feature.account.di

import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.feature.account.data.AccountApi
import com.fares.saveme.feature.account.data.DeviceSessionRepository
import com.fares.saveme.feature.account.data.ProfileRepository
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
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.usecase.SignOutUseCase
import com.fares.saveme.feature.auth.ui.home.CancelDeletionResult
import com.fares.saveme.feature.auth.ui.home.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val accountModule = module {
    single { AccountApi(get()) }
    single { DeviceSessionRepository(get(), get<AppDatabase>().deviceSessionDao(), get<AppDatabase>().syncScopeDao()) }
    single { SessionRefresher(get()) }
    single { ProfileRepository(get(), get<AppDatabase>().profileDao(), get<AppDatabase>().syncScopeDao()) }
    single { ProfileSyncer(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single { AccountRefresher(get(), get(), get(), get()) }
    single(createdAtStart = true) {
        AccountSyncCoordinator(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)).also { it.start() }
    }
    factory { EndSessionUseCase(get(), get()) }
    factory { EndOtherSessionsUseCase(get(), get()) }
    factory { ChangePasswordUseCase(get(), get()) }
    factory { RequestDeletionUseCase(get(), get()) }
    factory { CancelDeletionUseCase(get(), get()) }

    viewModel {
        val signOut = get<SignOutUseCase>()
        val cancel = get<CancelDeletionUseCase>()
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
        )
    }
    viewModel {
        val signOut = get<SignOutUseCase>()
        AccountViewModel(get<SessionManager>().state, signOut = { signOut() })
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
