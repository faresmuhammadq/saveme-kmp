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
    single<SharedBudgetsService> { DefaultSharedBudgetsService(get(), accountId = { (get<SessionManager>().state.value as? AuthState.SignedIn)?.account?.userId }) }
    viewModel { SharedBudgetsViewModel(get<SharedBudgetsService>().observeList { get<ProfileToday>().today() }) }
    viewModel { params ->
        val id = params.get<String>()
        SharedBudgetDetailViewModel(get<SharedBudgetsService>().observeDetail(id) { get<ProfileToday>().today() })
    }
    single { SharedBudgetFeedApplier(get()) } bind FeedApplier::class
    single { SharedBudgetMemberFeedApplier(get()) } bind FeedApplier::class
    single { SharedBudgetLineFeedApplier(get()) } bind FeedApplier::class
    single { SharedBudgetEntryFeedApplier(get()) } bind FeedApplier::class
    single { OwnSharedBudgetMembershipFeedApplier(get()) } bind FeedApplier::class
}
