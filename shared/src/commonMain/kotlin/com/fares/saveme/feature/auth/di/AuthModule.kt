package com.fares.saveme.feature.auth.di

import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.config.ServerAddressSettings
import com.fares.saveme.core.session.SignOutParticipant
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.core.sync.CurrentAccount
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.BudgetSummary
import com.fares.saveme.feature.auth.domain.DueSummary
import com.fares.saveme.feature.auth.domain.HomeOverviewSummary
import com.fares.saveme.feature.auth.domain.NoHomeOverview
import com.fares.saveme.feature.auth.domain.HeldInvitationGlimpse
import com.fares.saveme.feature.auth.domain.NoBudgetSummary
import com.fares.saveme.feature.auth.domain.NoHeldInvitation
import com.fares.saveme.feature.auth.domain.NoDueSummary
import com.fares.saveme.feature.auth.data.AccountSnapshotStore
import com.fares.saveme.feature.auth.data.AuthRepository
import com.fares.saveme.feature.auth.data.EntitlementsApi
import com.fares.saveme.feature.auth.data.IdentityApi
import com.fares.saveme.feature.auth.domain.AuthLinkParser
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.usecase.ForgotPasswordUseCase
import com.fares.saveme.feature.auth.domain.usecase.RegisterUseCase
import com.fares.saveme.feature.auth.domain.usecase.ResetPasswordUseCase
import com.fares.saveme.feature.auth.domain.usecase.ResendVerificationUseCase
import com.fares.saveme.feature.auth.domain.usecase.VerifyEmailUseCase
import com.fares.saveme.feature.auth.domain.usecase.SignInUseCase
import com.fares.saveme.feature.auth.domain.usecase.SignOutUseCase
import com.fares.saveme.feature.auth.ui.checkinbox.CheckInboxKind
import com.fares.saveme.feature.auth.ui.checkinbox.CheckInboxViewModel
import com.fares.saveme.feature.auth.ui.entry.EntryViewModel
import com.fares.saveme.feature.auth.ui.password.forgot.ForgotPasswordViewModel
import com.fares.saveme.feature.auth.ui.password.reset.ResetPasswordViewModel
import com.fares.saveme.feature.auth.ui.register.RegisterViewModel
import com.fares.saveme.feature.auth.ui.verify.EmailNotVerifiedViewModel
import com.fares.saveme.feature.auth.ui.signin.SignInArgs
import com.fares.saveme.feature.auth.ui.signin.SignInViewModel
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.feature.auth.ui.update.UpdateRequiredViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule = module {
    // Default until the recurring module rebinds it (spec 010 research R-10).
    single<DueSummary> { NoDueSummary }
    single<BudgetSummary> { NoBudgetSummary }
    single<HomeOverviewSummary> { NoHomeOverview }
    // Default until the sharing module rebinds it (spec 013 R-16).
    single<HeldInvitationGlimpse> { NoHeldInvitation }
    single { AccountSnapshotStore(get()) }
    single { IdentityApi(get()) }
    single { EntitlementsApi(get()) }
    single { AuthRepository(get(), get(), get()) }
    single {
        SessionManager(
            repository = get(),
            entitlementsApi = get(),
            apiEvents = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            cleaners = getAll<UserDataCleaner>(),
        )
    }
    single {
        val config = get<AppConfig>()
        AuthLinkParser(config.linkHost, config.linkPort, scheme = config.linkScheme)
    }
    single<CurrentAccount> {
        val sessions = get<SessionManager>()
        object : CurrentAccount {
            override fun userId() = (sessions.state.value as? AuthState.SignedIn)?.account?.userId
            override suspend fun load() {
                if (sessions.state.value == AuthState.Unknown) sessions.restore()
            }
        }
    }
    factory { SignInUseCase(get(), get(), get(), get()) }
    factory { SignOutUseCase(get(), get(), participants = { getAll<SignOutParticipant>() }) }
    factory { RegisterUseCase(get(), get()) }
    factory { VerifyEmailUseCase(get()) }
    factory { ResendVerificationUseCase(get()) }
    factory { ForgotPasswordUseCase(get()) }
    factory { ResetPasswordUseCase(get()) }

    viewModel {
        val config = get<AppConfig>()
        EntryViewModel(config, serverAddress = if (config.serverAddressEditable) get<ServerAddressSettings>().current() else null)
    }
    viewModel { params ->
        val signIn = get<SignInUseCase>()
        SignInViewModel(params.get<SignInArgs>(), signIn = { e, p, id -> signIn(e, p, id) })
    }
    viewModel { params -> UpdateRequiredViewModel(params.get<String>().ifBlank { null }, get<ClientInfo>().appVersion) }
    viewModel { params ->
        val register = get<RegisterUseCase>()
        RegisterViewModel(params.get<String>().ifBlank { null }, register = { e, p, n -> register(e, p, n) })
    }
    viewModel { params -> CheckInboxViewModel(params.get<String>(), params.get<CheckInboxKind>()) }
    viewModel {
        val resend = get<ResendVerificationUseCase>()
        EmailNotVerifiedViewModel(resend = { resend() })
    }
    viewModel { params ->
        val forgot = get<ForgotPasswordUseCase>()
        ForgotPasswordViewModel(params.get<String>().ifBlank { null }, requestReset = { forgot(it) })
    }
    viewModel { params ->
        val reset = get<ResetPasswordUseCase>()
        ResetPasswordViewModel(params.get<String>(), reset = { t, p -> reset(t, p) })
    }
}
