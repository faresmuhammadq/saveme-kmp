package com.fares.saveme.feature.recurring

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.core.sync.FakeClock
import com.fares.saveme.core.sync.FakePendingChangeDao
import com.fares.saveme.core.sync.FakeSyncRunner
import com.fares.saveme.core.sync.FakeSyncScheduler
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.account.FakeSyncScopeDao
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.recurring.data.OccurrenceRepository
import com.fares.saveme.feature.recurring.data.OccurrenceSyncHandler
import com.fares.saveme.feature.recurring.data.RecurringApi
import com.fares.saveme.feature.recurring.data.RecurringRepository
import com.fares.saveme.feature.recurring.data.RecurringRuleSyncHandler
import com.fares.saveme.feature.recurring.data.dto.OccurrencePayload
import com.fares.saveme.feature.recurring.domain.DefaultRecurringService
import com.fares.saveme.feature.recurring.domain.RecurringLinksImpl
import com.fares.saveme.feature.recurring.domain.schedule.TodayTicker
import com.fares.saveme.feature.transactions.FakeTransactionDao
import com.fares.saveme.feature.transactions.data.TransactionApi
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.data.TransactionSyncHandler
import com.fares.saveme.feature.transactions.domain.BalanceReconciler
import com.fares.saveme.feature.transactions.domain.DefaultOccurrenceTransactions
import com.fares.saveme.feature.transactions.domain.TransactionPostings
import com.fares.saveme.feature.transactions.domain.TransactionsWalletCleanup
import com.fares.saveme.feature.transactions.txAccount
import com.fares.saveme.feature.wallets.FakeEntitlements
import com.fares.saveme.feature.wallets.FakeWalletDao
import com.fares.saveme.feature.wallets.FakeWalletMemberDao
import com.fares.saveme.feature.wallets.data.WalletApi
import com.fares.saveme.feature.wallets.data.WalletRepository
import com.fares.saveme.feature.wallets.data.WalletSyncHandler
import com.fares.saveme.feature.wallets.data.dto.WalletDto
import com.fares.saveme.feature.wallets.domain.DefaultWalletsService
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The recurring service over the real wallets, transactions and recurring data stacks, one sync engine with all four
 * handlers, a mock HTTP engine and in-memory DAOs; one signed-in verified account `u1`, now `2026-10-02T10:00Z`.
 */
class RecurringServiceEnv(
    val scope: CoroutineScope,
    /** The ids the service mints, in order; `new-1`, `new-2`, … when not given. */
    private val newIds: List<String> = emptyList(),
    /** Uploads go as batches through the real [HttpSyncApi] instead of one request per record (spec 009). */
    batch: Boolean = false,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { throw kotlinx.io.IOException("offline") },
) {
    val auth = AuthTestEnv(handler)
    val authState = MutableStateFlow<AuthState>(AuthState.SignedIn(txAccount))
    val clock = FakeClock()
    val scopeDao = FakeSyncScopeDao()
    val pendingDao = FakePendingChangeDao()
    val scheduler = FakeSyncScheduler()
    val ruleDao = FakeRecurringRuleDao()
    val occurrenceDao = FakeOccurrenceDao()
    val walletDao = FakeWalletDao()
    val memberDao = FakeWalletMemberDao()
    val transactionDao = FakeTransactionDao()

    val recurringApi = RecurringApi(auth.client)
    private val walletApi = WalletApi(auth.client)
    private val transactionApi = TransactionApi(auth.client)

    val walletRepository: WalletRepository = WalletRepository(
        walletApi, walletDao, memberDao, scopeDao,
        accountId = { "u1" },
        hasQueuedChange = { id -> engine.get("wallet", id) != null },
        onWalletRemoved = { id -> transactionCleanup.onWalletDeleted(id) },
        clock = clock,
        onWalletCopy = { id, changeSeq -> transactionCleanup.onWalletCopy(id, changeSeq) },
    )
    val transactionRepository: TransactionRepository = TransactionRepository(
        transactionDao, scopeDao,
        accountId = { "u1" },
        hasQueuedChange = { id -> engine.get(TransactionSyncHandler.RECORD_TYPE, id) != null },
        clock = clock,
        walletChangeSeq = { id -> walletDao.get(id)?.changeSeq },
    )
    val rules = RecurringRepository(
        ruleDao, occurrenceDao, accountId = { "u1" },
        hasQueuedChange = { id -> engine.get(RecurringRuleSyncHandler.RECORD_TYPE, id) != null },
        hasQueuedOccurrenceChange = { id -> engine.get(OccurrenceSyncHandler.RECORD_TYPE, id) != null },
        clock = clock,
    )
    val occurrences = OccurrenceRepository(
        occurrenceDao, accountId = { "u1" },
        hasQueuedChange = { id -> engine.get(OccurrenceSyncHandler.RECORD_TYPE, id) != null },
        clock = clock,
    )
    val occurrenceTransactions = DefaultOccurrenceTransactions(transactionRepository, accountId = { "u1" }, clock = clock)
    val discardedRules = mutableListOf<String>()
    var walletCurrencies = mutableMapOf("w1" to "EGP")

    val ruleHandler = RecurringRuleSyncHandler(rules, recurringApi, walletCurrency = { walletCurrencies[it] }, discardRule = { id ->
        discardedRules += id
        scope.launchDiscard(id)
    })
    val occurrenceHandler = OccurrenceSyncHandler(
        occurrences, recurringApi,
        queuedPayload = { id ->
            engine.get(OccurrenceSyncHandler.RECORD_TYPE, id)?.let { AppJson.decodeFromString(OccurrencePayload.serializer(), it.payloadJson) }
        },
        dropConfirmation = { occurrenceId, except -> occurrenceTransactions.dropConfirmationOf(occurrenceId, except) },
    )
    private val walletHandler = WalletSyncHandler(walletRepository, walletApi, accountId = { "u1" })
    private val transactionHandler = TransactionSyncHandler(transactionRepository, transactionApi, walletOnPhone = { id -> walletDao.get(id) != null })
    val engine: SyncEngine = SyncEngine(
        pendingDao, scopeDao, listOf(walletHandler, transactionHandler, ruleHandler, occurrenceHandler), scheduler,
        currentAccountId = { "u1" }, clock = clock,
        syncApi = if (batch) com.fares.saveme.core.sync.HttpSyncApi(auth.client, auth.events, batchTimeoutMillis = null) else null,
    )
    /** Uploads are only counted; a test drains the engine itself so scripted answers go where it expects. */
    val runner = FakeSyncRunner(engine = engine, scope = null)
    private val postings = TransactionPostings({ engine }, transactionRepository)
    private val transactionCleanup = TransactionsWalletCleanup(transactionRepository, { engine }, { id -> walletDao.get(id) != null }, scope)
    val wallets = DefaultWalletsService(
        walletRepository, engine, authState, FakeEntitlements(), postings,
        defaultCurrency = flowOf("EGP"), runner = runner, newId = { "w-new" },
    )
    val reconciler = BalanceReconciler(transactionRepository, runner, scope, clock, debounce = Duration.ZERO)

    private var nextId = 0
    val service = DefaultRecurringService(
        rules = rules, occurrences = occurrences, engine = engine, api = recurringApi, wallets = wallets,
        occurrenceTransactions = occurrenceTransactions, authState = authState, runner = runner,
        profileZone = flowOf("Africa/Cairo"), deviceZone = { "UTC" }, ticker = TodayTicker(clock), clock = clock,
        newId = { newIds.getOrNull(nextId++) ?: "new-${nextId}" },
    )
    val links = RecurringLinksImpl(service) { engine }

    val requests get() = auth.requests

    private fun CoroutineScope.launchDiscard(id: String) {
        launch { engine.discard(RecurringRuleSyncHandler.RECORD_TYPE, id) }
    }

    suspend fun signedIn() = auth.secureStore.write(StoredSession(CREDENTIAL, "a", Instant.parse("2026-09-26T10:00:00Z")))

    suspend fun seedWallet(json: String = walletJson()) {
        walletRepository.storeServerCopy(AppJson.decodeFromString(WalletDto.serializer(), json))
    }

    suspend fun seedRule(json: String = ruleJson()) { rules.storeCopy(ruleDto(json), respectQueued = false) }

    suspend fun seedOccurrence(json: String = occurrenceJson()) { occurrences.storeCopy(occurrenceDto(json), respectQueued = false) }
}

/** A rule as the editor would hand it over: monthly rent of 4,500.00 from 1 November 2026. */
fun ruleDraft(
    preset: com.fares.saveme.feature.recurring.domain.model.Preset = com.fares.saveme.feature.recurring.domain.model.Preset.Custom,
    kind: com.fares.saveme.feature.transactions.domain.model.TransactionKind = com.fares.saveme.feature.transactions.domain.model.TransactionKind.Expense,
    amount: Long = 450_000,
    basis: com.fares.saveme.feature.recurring.domain.model.AmountBasis = com.fares.saveme.feature.recurring.domain.model.AmountBasis.PerOccurrence,
    category: String? = "c1",
    counter: String? = null,
    description: String? = "Rent",
    frequency: com.fares.saveme.feature.recurring.domain.model.Frequency = com.fares.saveme.feature.recurring.domain.model.Frequency.Monthly,
    interval: Int = 1,
    anchorDay: Int? = null,
    start: kotlinx.datetime.LocalDate = kotlinx.datetime.LocalDate(2026, 11, 1),
    end: kotlinx.datetime.LocalDate? = null,
    count: Int? = null,
    payoutRound: Int? = null,
    payoutCategory: String? = null,
    autoConfirm: Boolean = false,
) = com.fares.saveme.feature.recurring.domain.model.RuleDraft(
    preset = preset, kind = kind, amountMinor = amount, amountBasis = basis, categoryId = category, counterWalletId = counter,
    description = description, frequency = frequency, interval = interval, anchorDay = anchorDay, startDate = start,
    endDate = end, occurrenceCount = count, payoutRound = payoutRound, payoutCategoryId = payoutCategory, autoConfirm = autoConfirm,
)

fun confirmDraft(
    date: kotlinx.datetime.LocalDate = kotlinx.datetime.LocalDate(2026, 11, 1),
    amount: Long = 450_000,
    category: String? = "c1",
    description: String? = "Rent",
) = com.fares.saveme.feature.recurring.domain.model.ConfirmDraft(date, amount, category, description, null, null)
