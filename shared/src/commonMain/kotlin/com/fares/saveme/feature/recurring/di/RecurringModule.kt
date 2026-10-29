package com.fares.saveme.feature.recurring.di

import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.feature.account.domain.ProfileSyncer
import com.fares.saveme.feature.auth.domain.DueSummary
import com.fares.saveme.feature.categories.domain.PendingCategoryUses
import com.fares.saveme.feature.recurring.domain.DefaultRecurringService
import com.fares.saveme.feature.recurring.domain.RecurringCategoryUses
import com.fares.saveme.feature.recurring.domain.RecurringDueSummary
import com.fares.saveme.feature.recurring.domain.RecurringLinksImpl
import com.fares.saveme.feature.recurring.domain.RecurringService
import com.fares.saveme.feature.recurring.domain.RecurringWalletCleanup
import com.fares.saveme.feature.recurring.domain.OccurrenceScheduledMoney
import com.fares.saveme.feature.recurring.domain.ScheduledMoney
import com.fares.saveme.feature.transactions.domain.RecurringLinks
import com.fares.saveme.feature.wallets.domain.WalletDeletionParticipant
import kotlinx.coroutines.flow.map
import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.recurring.data.OccurrenceFeedApplier
import com.fares.saveme.feature.recurring.data.OccurrenceRepository
import com.fares.saveme.feature.recurring.data.OccurrenceSyncHandler
import com.fares.saveme.feature.recurring.data.RecurringApi
import com.fares.saveme.feature.recurring.data.RecurringRepository
import com.fares.saveme.feature.recurring.data.RecurringRuleFeedApplier
import com.fares.saveme.feature.recurring.data.RecurringRuleSyncHandler
import com.fares.saveme.feature.recurring.data.dto.OccurrencePayload
import com.fares.saveme.feature.transactions.domain.OccurrenceTransactions
import com.fares.saveme.feature.wallets.domain.WalletsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.recurring.ui.editor.RuleEditorArgs
import com.fares.saveme.feature.recurring.ui.editor.RuleEditorViewModel
import com.fares.saveme.feature.recurring.ui.list.RecurringArgs
import com.fares.saveme.feature.recurring.ui.list.RecurringViewModel
import com.fares.saveme.feature.recurring.ui.occurrence.OccurrenceArgs
import com.fares.saveme.feature.recurring.ui.rule.RuleDetailArgs
import com.fares.saveme.feature.recurring.ui.rule.RuleDetailViewModel
import com.fares.saveme.feature.recurring.ui.occurrence.OccurrenceViewModel
import com.fares.saveme.feature.recurring.domain.model.RecurringRecord
import com.fares.saveme.feature.transactions.di.ProfileToday
import com.fares.saveme.feature.transactions.domain.TransactionsService
import com.fares.saveme.feature.wallets.domain.model.WalletRole

/**
 * The recurring context (spec 010). Everything that needs the sync engine resolves it on use: the engine is
 * built from every [SyncHandler], so resolving it while a handler or repository is built would loop (spec
 * 006 lesson).
 */
val recurringModule = module {
    single { RecurringApi(get()) }
    single {
        val db = get<AppDatabase>()
        val sessions = get<SessionManager>()
        RecurringRepository(
            rules = db.recurringRuleDao(),
            occurrences = db.occurrenceDao(),
            accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId },
            hasQueuedChange = { id -> get<SyncEngine>().get(RecurringRuleSyncHandler.RECORD_TYPE, id) != null },
            hasQueuedOccurrenceChange = { id -> get<SyncEngine>().get(OccurrenceSyncHandler.RECORD_TYPE, id) != null },
        )
    }
    single {
        val db = get<AppDatabase>()
        val sessions = get<SessionManager>()
        OccurrenceRepository(
            occurrences = db.occurrenceDao(),
            accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId },
            hasQueuedChange = { id -> get<SyncEngine>().get(OccurrenceSyncHandler.RECORD_TYPE, id) != null },
        )
    }
    single {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        RecurringRuleSyncHandler(
            repository = get(),
            api = get(),
            walletCurrency = { id -> get<WalletsService>().observeWallet(id).first()?.server?.currency },
            // Launched: the handler runs inside the engine's lock, and a discard takes it (spec 008 lesson).
            discardRule = { id -> scope.launch { get<SyncEngine>().discard(RecurringRuleSyncHandler.RECORD_TYPE, id) } },
        )
    } bind SyncHandler::class
    single {
        OccurrenceSyncHandler(
            repository = get(),
            api = get(),
            queuedPayload = { id ->
                get<SyncEngine>().get(OccurrenceSyncHandler.RECORD_TYPE, id)
                    ?.let { runCatching { AppJson.decodeFromString(OccurrencePayload.serializer(), it.payloadJson) }.getOrNull() }
            },
            dropConfirmation = { occurrenceId, except -> get<OccurrenceTransactions>().dropConfirmationOf(occurrenceId, except) },
        )
    } bind SyncHandler::class
    single { RecurringRuleFeedApplier(get()) } bind FeedApplier::class
    single {
        OccurrenceFeedApplier(
            repository = get(),
            dropConfirmation = { occurrenceId, except -> get<OccurrenceTransactions>().dropConfirmationOf(occurrenceId, except) },
            sweepConfirmations = { walletId, ids -> get<OccurrenceTransactions>().sweepConfirmations(walletId, ids) },
        )
    } bind FeedApplier::class
    // The domain (spec 010 contracts/recurring-domain.md).
    single<RecurringService> {
        val profile = get<ProfileSyncer>()
        val clientInfo = get<ClientInfo>()
        DefaultRecurringService(
            rules = get(),
            occurrences = get(),
            engine = get(),
            api = get(),
            wallets = get(),
            occurrenceTransactions = get(),
            authState = get<SessionManager>().state,
            runner = get(),
            profileZone = profile.view.map { it?.server?.timezoneId },
            deviceZone = { clientInfo.timezoneId() },
        )
    }
    // Hooks the lower features own, rebound here (research R-10): the latest definition wins.
    single<RecurringLinks> { RecurringLinksImpl(service = get(), engine = { get<SyncEngine>() }) }
    single<DueSummary> { RecurringDueSummary(get()) }
    // What reports read (spec 014 R-05): occurrences still scheduled, with the queue laid over them.
    single<ScheduledMoney> {
        val sessions = get<SessionManager>()
        OccurrenceScheduledMoney(
            engine = { get<SyncEngine>() },
            repository = get(),
            accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId },
        )
    }
    single { RecurringCategoryUses(engine = { get<SyncEngine>() }) } bind PendingCategoryUses::class
    single {
        RecurringWalletCleanup(
            rules = get(),
            occurrences = get(),
            occurrenceTransactions = get(),
            engine = { get<SyncEngine>() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    } bind WalletDeletionParticipant::class
    // The screens (P-16): one store each, domain lambdas only.
    viewModel { params ->
        val args = params.get<RecurringArgs>()
        val service = get<RecurringService>()
        val wallets = get<WalletsService>()
        val transactions = get<TransactionsService>()
        val sessions = get<SessionManager>()
        val walletId = args.walletId
        RecurringViewModel(
            walletId = walletId,
            walletName = if (walletId == null) kotlinx.coroutines.flow.flowOf(null) else wallets.observeWallet(walletId).map { it?.shown?.name },
            occurrences = service.observeOccurrences(walletId),
            rules = service.observeRules(walletId),
            canAdd = if (walletId == null) {
                transactions.observeWritableWallets().map { it.isNotEmpty() }
            } else {
                wallets.observeWallet(walletId).map { view ->
                    view != null && !view.archived && (view.server.myRole == WalletRole.Owner || view.server.myRole == WalletRole.Editor)
                }
            },
            refresh = { force -> service.refresh(walletId, force) },
            isEmailVerified = { (sessions.state.value as? AuthState.SignedIn)?.account?.emailVerified == true },
            confirm = { id, draft -> service.confirm(id, draft) },
            skip = { id -> service.skip(id) },
        )
    }
    viewModel { params ->
        val args = params.get<RuleEditorArgs>()
        val service = get<RecurringService>()
        val wallets = get<WalletsService>()
        val transactions = get<TransactionsService>()
        val categories = get<CategoriesService>()
        RuleEditorViewModel(
            args = args,
            observeRule = { id -> service.observeRule(id) },
            writableWallets = transactions.observeWritableWallets(),
            defaultWalletId = { transactions.defaultWalletId() },
            destinations = { source -> transactions.observeDestinations(source) },
            categoryChoices = { kind, current -> categories.observeChoices(CategoryKind.fromWire(kind.wire) ?: CategoryKind.Expense, current) },
            displays = { wallet -> categories.observeDisplays(wallet) },
            walletName = { id -> wallets.observeWallet(id).map { it?.shown?.name } },
            preview = { draft -> service.preview(draft) },
            create = { wallet, draft -> service.create(wallet, draft) },
            save = { id, draft, base -> service.save(id, draft, base) },
            changeFrom = { id, from, draft, base -> service.changeFrom(id, from, draft, base) },
            today = { get<ProfileToday>().today() },
        )
    }
    viewModel { params ->
        val args = params.get<OccurrenceArgs>()
        val service = get<RecurringService>()
        val wallets = get<WalletsService>()
        val categories = get<CategoriesService>()
        OccurrenceViewModel(
            occurrenceId = args.occurrenceId,
            openConfirm = args.confirm,
            observe = { id -> service.observeOccurrence(id) },
            displays = { wallet -> categories.observeDisplays(wallet) },
            categoryChoices = { kind, current -> categories.observeChoices(CategoryKind.fromWire(kind.wire) ?: CategoryKind.Expense, current) },
            walletName = { id -> wallets.observeWallet(id).map { it?.shown?.name } },
            confirm = { id, draft -> service.confirm(id, draft) },
            edit = { id, draft -> service.edit(id, draft) },
            skip = { id -> service.skip(id) },
            unskip = { id -> service.unskip(id) },
            cancelConfirmation = { id -> service.cancelConfirmation(id) },
            dismissNotice = { id -> service.dismissNotice(id) },
            keepMine = { id -> service.keepMine(RecurringRecord.Occurrence, id) },
            useTheirs = { id -> service.useTheirs(RecurringRecord.Occurrence, id) },
            tryAgain = { id -> service.tryAgain(RecurringRecord.Occurrence, id) },
            discard = { id -> service.discard(RecurringRecord.Occurrence, id) },
        )
    }
    viewModel { params ->
        val args = params.get<RuleDetailArgs>()
        val service = get<RecurringService>()
        val categories = get<CategoriesService>()
        RuleDetailViewModel(
            ruleId = args.ruleId,
            observe = { id -> service.observeRule(id) },
            observeOccurrences = { wallet, rule -> service.observeOccurrences(wallet, rule) },
            ruleTitle = { id -> service.observeRule(id).map { it?.shown?.description } },
            preview = { draft -> service.preview(draft, limit = 12) },
            setStatus = { id, status, base -> service.setStatus(id, status, base) },
            delete = { id -> service.delete(id) },
            catchUp = { id -> service.catchUp(id) },
            keepMine = { id -> service.keepMine(RecurringRecord.Rule, id) },
            useTheirs = { id -> service.useTheirs(RecurringRecord.Rule, id) },
            tryAgain = { id -> service.tryAgain(RecurringRecord.Rule, id) },
            discard = { id -> service.discard(RecurringRecord.Rule, id) },
            displays = { wallet -> categories.observeDisplays(wallet) },
        )
    }
}
