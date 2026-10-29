package com.fares.saveme.feature.budgets.di

import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.account.domain.ProfileSyncer
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.BudgetSummary
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.budgets.data.BudgetApi
import com.fares.saveme.feature.budgets.data.BudgetFeedApplier
import com.fares.saveme.feature.budgets.data.BudgetProgressRepository
import com.fares.saveme.feature.budgets.data.BudgetRepository
import com.fares.saveme.feature.budgets.data.BudgetSyncHandler
import com.fares.saveme.feature.budgets.domain.BudgetCategoryUses
import com.fares.saveme.feature.budgets.domain.BudgetsHomeSummary
import com.fares.saveme.feature.budgets.domain.BudgetsService
import com.fares.saveme.feature.budgets.domain.BudgetsSyncCoordinator
import com.fares.saveme.feature.budgets.domain.DefaultBudgetsService
import com.fares.saveme.feature.budgets.ui.editor.BudgetEditorViewModel
import com.fares.saveme.feature.budgets.ui.list.BudgetsViewModel
import com.fares.saveme.feature.categories.domain.PendingCategoryUses
import com.fares.saveme.feature.transactions.domain.OwnSpending
import com.fares.saveme.feature.wallets.domain.WalletsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Budgets (spec 012). Listed after the auth, categories, transactions and recurring modules so its rebinding of
 * [BudgetSummary] wins. The engine is resolved on use wherever a budgets class could otherwise close a cycle (the engine is
 * built from every sync handler; spec 006 lesson).
 */
val budgetsModule = module {
    single { BudgetApi(get()) }
    single {
        val sessions = get<SessionManager>()
        BudgetRepository(
            budgets = get<AppDatabase>().budgetDao(),
            accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId },
            // Resolved on use: the engine is built from every SyncHandler, and the budget handler needs this repository.
            hasQueuedChange = { id -> get<SyncEngine>().get(BudgetSyncHandler.RECORD_TYPE, id) != null },
        )
    }
    single { BudgetProgressRepository(get<AppDatabase>().budgetProgressDao()) }
    // The person's own budgets from their change feed (spec 009), and their way through the sync engine.
    single { BudgetFeedApplier(get()) } bind FeedApplier::class
    single { BudgetSyncHandler(get(), get()) } bind SyncHandler::class
    // One more source of the union C5 reads (spec 010 FR-032): the categories a budget names are in use.
    single { BudgetCategoryUses(get(), { get<SyncEngine>() }, get<SessionManager>().state) } bind PendingCategoryUses::class
    single<BudgetsService> {
        DefaultBudgetsService(
            repository = get(),
            progress = get(),
            api = get(),
            engine = { get<SyncEngine>() },
            authState = get<SessionManager>().state,
            categories = get(),
            wallets = get<WalletsService>(),
            spending = get<OwnSpending>(),
            profile = get<ProfileSyncer>().view,
            deviceZoneId = { get<ClientInfo>().timezoneId() },
            runner = get(),
        )
    }
    single<BudgetSummary> { BudgetsHomeSummary(get()) }
    single(createdAtStart = true) {
        BudgetsSyncCoordinator(
            authState = get<SessionManager>().state,
            budgets = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            scheduler = get(),
        ).also { it.start() }
    }
    viewModel {
        val budgets = get<BudgetsService>()
        val sessions = get<SessionManager>()
        val profile = get<ProfileSyncer>()
        val clientInfo = get<ClientInfo>()
        BudgetsViewModel(
            overview = budgets.observeOverview(),
            refresh = { force -> budgets.refresh(force) },
            isEmailVerified = { (sessions.state.value as? AuthState.SignedIn)?.account?.emailVerified == true },
            // Times show in the profile timezone (P-05); the device's until the profile is known.
            timeZoneId = profile.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() },
        )
    }
    viewModel { params ->
        val budgets = get<BudgetsService>()
        BudgetEditorViewModel(
            budgetId = params.get<String>().ifBlank { null },
            observeBudget = { budgets.observeBudget(it) },
            observeChoices = { currency, current -> budgets.observeChoices(currency, current) },
            observeStartDay = budgets.observeStartDay(),
            create = { budgets.create(it) },
            saveLimit = { id, limit, base -> budgets.saveLimit(id, limit, base) },
            archive = { budgets.archive(it) },
            delete = { budgets.delete(it) },
            keepMine = { budgets.keepMine(it) },
            useTheirs = { budgets.useTheirs(it) },
            tryAgain = { budgets.tryAgain(it) },
            discard = { budgets.discard(it) },
            refreshOne = { budgets.refreshOne(it) },
            observeHistory = { category, currency -> budgets.observeSpendHistory(category, currency) },
        )
    }
}
