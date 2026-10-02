package com.fares.saveme.feature.categories.di

import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.locale.AppLanguageStore
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.account.domain.ProfileSyncer
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.categories.data.CategoryApi
import com.fares.saveme.feature.categories.data.CategoryReferenceApi
import com.fares.saveme.feature.categories.data.CategoryReferenceRepository
import com.fares.saveme.feature.categories.data.CategoryRepository
import com.fares.saveme.feature.categories.data.CategorySyncHandler
import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.categories.domain.CategoriesSyncCoordinator
import com.fares.saveme.feature.categories.domain.CategoryReferencesCleanup
import com.fares.saveme.feature.categories.domain.DefaultCategoriesService
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.ui.editor.CategoryEditorViewModel
import com.fares.saveme.feature.categories.ui.list.CategoriesViewModel
import com.fares.saveme.feature.wallets.domain.WalletDeletionParticipant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val categoriesModule = module {
    single { CategoryApi(get()) }
    single { CategoryReferenceApi(get()) }
    single {
        val db = get<AppDatabase>()
        val sessions = get<SessionManager>()
        CategoryRepository(
            api = get(),
            categories = db.categoryDao(),
            platform = db.platformCategoryDao(),
            scopes = db.syncScopeDao(),
            accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId },
            // Resolved on use: the engine is built from every SyncHandler, and the category handler needs
            // this repository, so resolving the engine here would loop (Koin stack overflow at start).
            hasQueuedChange = { id -> get<SyncEngine>().get(CategorySyncHandler.RECORD_TYPE, id) != null },
        )
    }
    single { CategoryReferenceRepository(get(), get<AppDatabase>().categoryReferenceDao()) }
    single { CategorySyncHandler(get(), get()) } bind SyncHandler::class
    single { CategoryReferencesCleanup(get()) } bind WalletDeletionParticipant::class
    single<CategoriesService> {
        val languages = get<AppLanguageStore>()
        DefaultCategoriesService(
            repository = get(),
            references = get(),
            engine = get(),
            authState = get<SessionManager>().state,
            pendingUses = get(),
            language = languages.language.map { it ?: languages.device() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    single(createdAtStart = true) {
        CategoriesSyncCoordinator(
            authState = get<SessionManager>().state,
            categories = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            scheduler = get(),
        ).also { it.start() }
    }
    viewModel {
        val categories = get<CategoriesService>()
        val sessions = get<SessionManager>()
        val profile = get<ProfileSyncer>()
        val clientInfo = get<ClientInfo>()
        CategoriesViewModel(
            overview = { kind -> categories.observeOverview(kind) },
            refresh = { force -> categories.refresh(force) },
            isEmailVerified = { (sessions.state.value as? AuthState.SignedIn)?.account?.emailVerified == true },
            // Times show in the profile timezone (P-05); the device's until the profile is known.
            timeZoneId = profile.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() },
        )
    }
    viewModel { params ->
        val categories = get<CategoriesService>()
        CategoryEditorViewModel(
            categoryId = params.get<String>().ifBlank { null },
            kind = CategoryKind.fromWire(params.get<String>()) ?: CategoryKind.Expense,
            observeCategory = { categories.observeCategory(it) },
            usedLocally = categories.observeUsedLocally(),
            create = { categories.create(it) },
            save = { id, draft, base -> categories.save(id, draft, base) },
            archive = { categories.archive(it) },
            delete = { categories.delete(it) },
            keepMine = { categories.keepMine(it) },
            useTheirs = { categories.useTheirs(it) },
            tryAgain = { categories.tryAgain(it) },
            discard = { categories.discard(it) },
            archiveInstead = { categories.archiveInstead(it) },
            keepCategory = { categories.keepCategory(it) },
        )
    }
}
