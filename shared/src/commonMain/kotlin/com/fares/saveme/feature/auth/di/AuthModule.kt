package com.fares.saveme.feature.auth.di

import com.fares.saveme.feature.auth.data.AccountSnapshotStore
import com.fares.saveme.feature.auth.data.AuthRepository
import com.fares.saveme.feature.auth.data.IdentityApi
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.auth.domain.usecase.SignInUseCase
import com.fares.saveme.feature.auth.ui.entry.EntryViewModel
import com.fares.saveme.feature.auth.ui.home.HomeViewModel
import com.fares.saveme.feature.auth.ui.signin.SignInArgs
import com.fares.saveme.feature.auth.ui.signin.SignInViewModel
import com.fares.saveme.feature.auth.ui.update.UpdateRequiredViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule = module {
    single { AccountSnapshotStore(get()) }
    single { IdentityApi(get()) }
    single { AuthRepository(get(), get(), get()) }
    single { SessionManager(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    factory { SignInUseCase(get(), get(), get(), get()) }

    viewModelOf(::EntryViewModel)
    viewModel { params ->
        val signIn = get<SignInUseCase>()
        SignInViewModel(params.get<SignInArgs>(), signIn = { e, p, id -> signIn(e, p, id) })
    }
    viewModel { params -> UpdateRequiredViewModel(params.get<String>().ifBlank { null }) }
    viewModel { HomeViewModel(get<SessionManager>().state) }
}
