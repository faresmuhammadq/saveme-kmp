package com.fares.saveme.feature.transactions.di

import com.fares.saveme.core.database.AppDatabase
import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.core.storage.KeyValueStore
import com.fares.saveme.core.sync.FeedApplier
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.core.sync.SyncHandler
import com.fares.saveme.feature.account.domain.ProfileSyncer
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.SessionManager
import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.categories.domain.PendingCategoryUses
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.transactions.data.TransactionApi
import com.fares.saveme.feature.transactions.data.TransactionFeedApplier
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.data.TransactionSyncHandler
import com.fares.saveme.feature.transactions.domain.BalanceReconciler
import com.fares.saveme.feature.transactions.domain.DefaultTransactionsService
import com.fares.saveme.feature.transactions.domain.DefaultOccurrenceTransactions
import com.fares.saveme.feature.transactions.domain.OccurrenceTransactions
import com.fares.saveme.feature.transactions.domain.OwnSpending
import com.fares.saveme.feature.transactions.domain.TransactionOwnSpending
import com.fares.saveme.feature.transactions.domain.NoRecurringLinks
import com.fares.saveme.feature.transactions.domain.RecurringLinks
import com.fares.saveme.feature.transactions.domain.TransactionCategoryUses
import com.fares.saveme.feature.transactions.domain.TransactionPostings
import com.fares.saveme.feature.transactions.domain.TransactionsService
import com.fares.saveme.feature.transactions.domain.TransactionsSyncCoordinator
import com.fares.saveme.feature.transactions.domain.TransactionsWalletCleanup
import com.fares.saveme.feature.transactions.ui.editor.TransactionEditorArgs
import com.fares.saveme.feature.transactions.ui.editor.TransactionEditorViewModel
import com.fares.saveme.feature.transactions.ui.list.TransactionsViewModel
import com.fares.saveme.feature.wallets.domain.PendingPostings
import com.fares.saveme.feature.wallets.domain.SharingGlance
import com.fares.saveme.feature.wallets.domain.WalletCopyParticipant
import com.fares.saveme.feature.wallets.domain.WalletDeletionParticipant
import com.fares.saveme.feature.wallets.domain.WalletsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * "Today" in the profile's time zone, the device's until the profile is read (P-05.3, research R-16).
 * Stored dates never shift; only this default and the month presets follow the zone.
 */
class ProfileToday(profile: ProfileSyncer, private val clientInfo: ClientInfo, scope: CoroutineScope) {
    @kotlin.concurrent.Volatile
    private var zoneId: String = clientInfo.timezoneId()

    init {
        scope.launch { profile.view.collect { zoneId = it?.server?.timezoneId ?: clientInfo.timezoneId() } }
    }

    fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(runCatching { TimeZone.of(zoneId) }.getOrElse { TimeZone.UTC }).date
}

val transactionsModule = module {
    single { TransactionApi(get()) }
    single { ProfileToday(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single {
        val db = get<AppDatabase>()
        val sessions = get<SessionManager>()
        TransactionRepository(
            transactions = db.transactionDao(),
            scopes = db.syncScopeDao(),
            accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId },
            // Resolved on use: the engine is built from every SyncHandler, and the transaction handler needs
            // this repository, so resolving the engine here would loop (Koin stack overflow at start).
            hasQueuedChange = { id -> get<SyncEngine>().get(TransactionSyncHandler.RECORD_TYPE, id) != null },
            walletChangeSeq = { id -> db.walletDao().get(id)?.changeSeq },
        )
    }
    single {
        TransactionSyncHandler(
            repository = get(),
            api = get(),
            // Resolved on use, for the same reason.
            walletOnPhone = { id -> get<WalletsService>().observeWallet(id).first() != null },
        )
    } bind SyncHandler::class
    // The hooks C4 and C5 left (research R-26): their zero/no-op bindings moved here.
    // The transactions side of a recurring confirmation (spec 010 R-07): called by recurring, never imports it.
    single<OccurrenceTransactions> {
        val sessions = get<SessionManager>()
        DefaultOccurrenceTransactions(repository = get(), accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId })
    }
    single<PendingPostings> { TransactionPostings(engine = { get<SyncEngine>() }, repository = get()) }
    // What budgets sum (spec 012 R-05): the person's own expenses with waiting changes applied. Everything it needs from
    // other features is resolved on use, so the graph keeps no cycle.
    single<OwnSpending> {
        val sessions = get<SessionManager>()
        TransactionOwnSpending(
            engine = { get<SyncEngine>() },
            repository = get(),
            accountId = { (sessions.state.value as? AuthState.SignedIn)?.account?.userId },
            memberWalletIds = { get<WalletsService>().observeList().map { list -> (list.active + list.archived).map { it.id }.toSet() } },
            ruleAuthorOf = { ruleId -> get<RecurringLinks>().ruleAuthorOf(ruleId) },
        )
    }
    // One of the sources C5 unions (spec 010 FR-032): bound as a secondary type so recurring's can sit beside it.
    single { TransactionCategoryUses(engine = { get<SyncEngine>() }) } bind PendingCategoryUses::class
    // Default until the recurring module rebinds it (research R-10).
    single<RecurringLinks> { NoRecurringLinks }
    single { BalanceReconciler(repository = get(), runner = get(), scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    // The wallet feed's transactions (spec 009).
    single { TransactionFeedApplier(get()) } bind FeedApplier::class
    single {
        TransactionsWalletCleanup(
            repository = get(),
            engine = { get<SyncEngine>() },
            walletOnPhone = { id -> get<WalletsService>().observeWallet(id).first() != null },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    } binds arrayOf(WalletDeletionParticipant::class, WalletCopyParticipant::class)
    single<TransactionsService> {
        DefaultTransactionsService(
            repository = get(),
            engine = get(),
            wallets = get(),
            authState = get<SessionManager>().state,
            keyValues = get(),
            reconciler = get(),
            runner = get(),
        )
    }
    single(createdAtStart = true) {
        TransactionsSyncCoordinator(
            authState = get<SessionManager>().state,
            reconciler = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ).also { it.start() }
    }
    single {
        val keyValues = get<KeyValueStore>()
        UserDataCleaner { keyValues.remove(DefaultTransactionsService.LAST_WALLET_KEY) }
    } bind UserDataCleaner::class
    viewModel { params ->
        val walletId = params.get<String>()
        val transactions = get<TransactionsService>()
        val wallets = get<WalletsService>()
        val categories = get<CategoriesService>()
        val profile = get<ProfileSyncer>()
        val clientInfo = get<ClientInfo>()
        val sessions = get<SessionManager>()
        TransactionsViewModel(
            walletId = walletId,
            wallet = wallets.observeWallet(walletId),
            list = { query -> transactions.observeList(walletId, query) },
            displays = categories.observeDisplays(walletId),
            filterChoices = combine(
                categories.observeChoices(CategoryKind.Expense),
                categories.observeChoices(CategoryKind.Income),
            ) { expense, income -> expense.own + expense.defaults + income.own + income.defaults },
            refresh = { force -> transactions.refresh(walletId, force) },
            isEmailVerified = { (sessions.state.value as? AuthState.SignedIn)?.account?.emailVerified == true },
            today = { get<ProfileToday>().today() },
            timeZoneId = profile.view.map { it?.server?.timezoneId ?: clientInfo.timezoneId() },
            due = get<RecurringLinks>().observeDue(walletId),
            confirmationStatus = { id -> get<RecurringLinks>().observeConfirmationStatus(id) },
            planLimited = get<SharingGlance>().observePlanLimited(walletId),
        )
    }
    viewModel { params ->
        val args = params.get<TransactionEditorArgs>()
        val transactions = get<TransactionsService>()
        val categories = get<CategoriesService>()
        TransactionEditorViewModel(
            transactionId = args.transactionId?.ifBlank { null },
            walletId = args.walletId?.ifBlank { null },
            observeTransaction = { id, from -> transactions.observeTransaction(id, from) },
            writableWallets = transactions.observeWritableWallets(),
            defaultWalletId = { transactions.defaultWalletId() },
            destinations = { source -> transactions.observeDestinations(source) },
            categoryChoices = { kind, current -> categories.observeChoices(CategoryKind.fromWire(kind.wire) ?: CategoryKind.Expense, current) },
            displays = { wallet -> categories.observeDisplays(wallet) },
            create = { transactions.create(it) },
            save = { id, draft, base, lastSeen -> transactions.save(id, draft, base, lastSeen) },
            delete = { transactions.delete(it) },
            keepMine = { transactions.keepMine(it) },
            useTheirs = { transactions.useTheirs(it) },
            tryAgain = { transactions.tryAgain(it) },
            discard = { transactions.discard(it) },
            today = { get<ProfileToday>().today() },
            matches = { wallet, kind, category, destination, date -> get<RecurringLinks>().observeMatches(wallet, kind, category, destination, date) },
            confirmFromDraft = { occurrenceId, draft -> get<RecurringLinks>().confirmFromDraft(occurrenceId, draft) },
            ruleOnPhone = { ruleId -> get<RecurringLinks>().observeRuleOnPhone(ruleId) },
        )
    }
}
