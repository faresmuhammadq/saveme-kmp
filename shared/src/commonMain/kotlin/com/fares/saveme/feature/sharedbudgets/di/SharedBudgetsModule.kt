package com.fares.saveme.feature.sharedbudgets.di

import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.SharedBudgetScopes
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.sharedbudgets.data.OwnSharedBudgetMembershipFeedApplier
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetEntryFeedApplier
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetFeedApplier
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetLineFeedApplier
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetMemberFeedApplier
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetRepository
import com.fares.saveme.feature.sharedbudgets.domain.DefaultSharedBudgetScopes
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetLinksImpl
import com.fares.saveme.feature.transactions.domain.SharedBudgetLinks
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import com.fares.saveme.feature.sharedbudgets.domain.DefaultSharedBudgetsService
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetsService
import com.fares.saveme.feature.sharedbudgets.ui.detail.SharedBudgetDetailViewModel
import com.fares.saveme.feature.sharedbudgets.ui.list.SharedBudgetsViewModel
import com.fares.saveme.feature.transactions.di.ProfileToday
import org.koin.core.module.dsl.viewModel
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetApi
import com.fares.saveme.feature.sharedbudgets.data.SharedBudgetSyncHandler
import com.fares.saveme.feature.sharedbudgets.domain.DefaultSharedBudgetsManager
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.fares.saveme.feature.budgets.domain.BudgetsService
import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.sharedbudgets.ui.editor.SharedBudgetEditorViewModel
import com.fares.saveme.core.config.AppConfig
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.feature.sharedbudgets.domain.HeldSharedBudgetInvitation
import com.fares.saveme.feature.sharedbudgets.domain.SharedBudgetLinkParser
import com.fares.saveme.feature.sharedbudgets.ui.invitation.SharedBudgetInvitationViewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Shared Budgets (backend ADR-0027): the budgets a person tracks with others, whichever wallets pay. A third kind of feed scope,
 * read only as a member; the person's own membership rows on their user feed say which budgets to read.
 */
val sharedBudgetsModule = module {
    single {
        val sessions = get<SessionManager>()
        SharedBudgetRepository(
            dao = get<AppDatabase>().sharedBudgetDao(),
            scopes = get<AppDatabase>().syncScopeDao(),
            accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId },
            // Resolved on use: the engine is built from every SyncHandler (spec 006 lesson).
            hasQueuedChange = { id -> get<SyncEngine>().get("shared_budget", id) != null },
        )
    }
    single {
        val sessions = get<SessionManager>()
        DefaultSharedBudgetScopes(get(), accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId })
    } bind SharedBudgetScopes::class
    // Rebinds transactions' default (as the recurring module does); frequent picks are the person's own last 90 days on this phone.
    single<SharedBudgetLinks> {
        SharedBudgetLinksImpl(
            repository = get(),
            accountId = { (get<SessionManager>().state.value as? AuthState.SignedIn)?.account?.userId },
            recentBudgetUse = { userId ->
                val since = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.minus(DatePeriod(days = 90))
                get<AppDatabase>().transactionDao().observeBudgetUse(userId, since.toString()).map { rows -> rows.map { it.budgetId } }
            },
        )
    }
    single {
        val config = get<AppConfig>()
        SharedBudgetLinkParser(config.linkHost, config.linkPort, scheme = config.linkScheme)
    }
    single { HeldSharedBudgetInvitation() } bind UserDataCleaner::class
    viewModel { params ->
        val held = get<HeldSharedBudgetInvitation>()
        SharedBudgetInvitationViewModel(
            token = params.get<String>(),
            signedIn = { get<SessionManager>().state.value is AuthState.SignedIn },
            manager = get(),
            onAnswered = held::drop,
        )
    }
    single { SharedBudgetApi(get()) }
    single { SharedBudgetSyncHandler(get(), get()) } bind SyncHandler::class
    single<SharedBudgetsManager> {
        DefaultSharedBudgetsManager(
            repository = get(),
            api = get(),
            engine = { get<SyncEngine>() },
            runner = get(),
            authState = get<SessionManager>().state,
            deviceZoneId = { get<ClientInfo>().timezoneId() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    single<SharedBudgetsService> { DefaultSharedBudgetsService(get(), accountId = { (get<SessionManager>().state.value as? AuthState.SignedIn)?.account?.userId }) }
    viewModel { params ->
        val id = params.get<String>().ifBlank { null }
        SharedBudgetEditorViewModel(
            budgetId = id,
            manager = get(),
            budget = { get<SharedBudgetsService>().observeEditor(it) },
            currencies = get<BudgetsService>().observeChoices("", null).map { it.currencies },
            categoryChoices = get<CategoriesService>().observeChoices(CategoryKind.Expense, null),
        )
    }
    viewModel { SharedBudgetsViewModel(get<SharedBudgetsService>().observeList { get<ProfileToday>().today() }) }
    viewModel { params ->
        val id = params.get<String>()
        SharedBudgetDetailViewModel(id, { back -> get<SharedBudgetsService>().observeDetail(id, { get<ProfileToday>().today() }, back) }, get())
    }
    single { SharedBudgetFeedApplier(get()) } bind FeedApplier::class
    single { SharedBudgetMemberFeedApplier(get()) } bind FeedApplier::class
    single { SharedBudgetLineFeedApplier(get()) } bind FeedApplier::class
    single { SharedBudgetEntryFeedApplier(get()) } bind FeedApplier::class
    single { OwnSharedBudgetMembershipFeedApplier(get()) } bind FeedApplier::class
}
