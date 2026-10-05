package com.fares.saveme.feature.transactions

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
import com.fares.saveme.feature.auth.domain.model.AccountSnapshot
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.categories.domain.model.CategoriesOverview
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.categories.domain.model.CategoryView
import com.fares.saveme.feature.categories.domain.model.CreateResult as CategoryCreateResult
import com.fares.saveme.feature.categories.domain.model.SaveResult as CategorySaveResult
import com.fares.saveme.feature.transactions.data.TransactionApi
import com.fares.saveme.feature.transactions.data.TransactionRepository
import com.fares.saveme.feature.transactions.data.TransactionSyncHandler
import com.fares.saveme.feature.transactions.data.dto.TransactionDto
import com.fares.saveme.feature.transactions.data.local.TransactionDao
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import com.fares.saveme.feature.transactions.data.local.WalletBalanceSum
import com.fares.saveme.feature.transactions.data.local.DaySumRow
import com.fares.saveme.feature.transactions.data.local.ReportRow
import com.fares.saveme.feature.transactions.domain.BalanceReconciler
import com.fares.saveme.feature.transactions.domain.DefaultTransactionsService
import com.fares.saveme.feature.transactions.domain.TransactionPostings
import com.fares.saveme.feature.transactions.domain.TransactionsWalletCleanup
import com.fares.saveme.feature.wallets.FakeEntitlements
import com.fares.saveme.feature.wallets.FakeWalletDao
import com.fares.saveme.feature.wallets.FakeWalletMemberDao
import com.fares.saveme.feature.wallets.data.WalletApi
import com.fares.saveme.feature.wallets.data.WalletRepository
import com.fares.saveme.feature.wallets.data.WalletSyncHandler
import com.fares.saveme.feature.wallets.data.dto.WalletDto
import com.fares.saveme.feature.wallets.domain.DefaultWalletsService
import com.fares.saveme.feature.wallets.memberJson
import com.fares.saveme.feature.wallets.walletJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Duration
import kotlin.time.Instant

/** In-memory stand-in that follows [TransactionDao]'s SQL (the SQL itself: TransactionDaoTest). */
class FakeTransactionDao : TransactionDao {
    val rows = MutableStateFlow<Map<String, TransactionEntity>>(emptyMap())

    override fun observeForWallet(accountId: String, walletId: String): Flow<List<TransactionEntity>> = rows.map { all ->
        all.values
            .filter { it.accountId == accountId && it.deletedAt == null && (it.walletId == walletId || it.counterWalletId == walletId) }
            .sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.createdAt }.thenByDescending { it.id })
    }

    override fun observe(id: String): Flow<TransactionEntity?> = rows.map { it[id] }
    override fun observeForSpending(accountId: String, from: String, to: String, waitingIds: List<String>): Flow<List<TransactionEntity>> = rows.map { all ->
        all.values.filter {
            it.accountId == accountId && it.deletedAt == null &&
                ((it.kind == "expense" && it.date >= from && it.date <= to) || it.id in waitingIds || it.pendingConfirmation)
        }
    }
    override fun observeForReport(accountId: String, walletIds: List<String>, from: String, to: String, waitingIds: List<String>): Flow<List<ReportRow>> = rows.map { all ->
        all.values.filter {
            it.accountId == accountId && it.deletedAt == null &&
                (((it.walletId in walletIds || (it.counterWalletId != null && it.counterWalletId in walletIds)) && it.date >= from && it.date <= to) ||
                    it.id in waitingIds || it.pendingConfirmation)
        }.map { ReportRow(it.id, it.walletId, it.counterWalletId, it.kind, it.date, it.amountMinor, it.currency, it.categoryId, it.version, it.pendingConfirmation) }
    }

    override fun observeDailySums(accountId: String, walletIds: List<String>, from: String, to: String, extraFrom: String, extraTo: String): Flow<List<DaySumRow>> = rows.map { all ->
        all.values
            .filter {
                it.accountId == accountId && it.deletedAt == null && it.version > 0 && (it.kind == "income" || it.kind == "expense") && it.walletId in walletIds &&
                    ((it.date >= extraFrom && it.date < from) || (it.date > to && it.date <= extraTo))
            }
            .groupBy { it.date to it.kind }
            .map { (key, group) -> DaySumRow(key.first, key.second, group.sumOf { it.amountMinor }) }
    }

    override fun observeBalancesBefore(accountId: String, walletIds: List<String>, before: String): Flow<List<WalletBalanceSum>> = rows.map { all ->
        val sums = HashMap<String, Long>()
        all.values.filter { it.accountId == accountId && it.deletedAt == null && it.version > 0 && it.date < before }.forEach { row ->
            if (row.walletId in walletIds) {
                val effect = when (row.kind) { "income" -> row.amountMinor; "expense", "transfer" -> -row.amountMinor; else -> 0L }
                sums[row.walletId] = (sums[row.walletId] ?: 0L) + effect
            }
            val counter = row.counterWalletId
            if (row.kind == "transfer" && counter != null && counter in walletIds) sums[counter] = (sums[counter] ?: 0L) + row.amountMinor
        }
        sums.map { (wallet, total) -> WalletBalanceSum(wallet, total) }
    }

    override suspend fun get(id: String): TransactionEntity? = rows.value[id]
    override fun observeRelevant(ids: List<String>): Flow<List<TransactionEntity>> =
        rows.map { all -> all.values.filter { it.id in ids || it.carryConfirmedAt != null || it.pendingConfirmation } }
    override fun observeCarriedOrPending(): Flow<List<TransactionEntity>> =
        rows.map { all -> all.values.filter { it.carryConfirmedAt != null || it.pendingConfirmation } }
    override suspend fun getPendingForOccurrence(occurrenceId: String) =
        rows.value.values.filter { it.occurrenceId == occurrenceId && it.pendingConfirmation && it.version == 0L }
    override suspend fun getTouching(walletId: String) = rows.value.values.filter { it.walletId == walletId || it.counterWalletId == walletId }
    override suspend fun getAll() = rows.value.values.toList()

    override fun observeCarried(): Flow<List<TransactionEntity>> = rows.map { all -> all.values.filter { it.carryConfirmedAt != null } }
    override suspend fun getCarried() = rows.value.values.filter { it.carryConfirmedAt != null }

    override suspend fun clearSourceCarryCoveredBy(walletId: String, changeSeq: Long) {
        rows.value = rows.value.mapValues { (_, r) ->
            if (r.walletId == walletId && r.carryConfirmedAt != null && r.changeSeq > 0 && r.changeSeq <= changeSeq) r.copy(carrySourceMinor = 0) else r
        }
    }

    override suspend fun clearSourceCarry(walletId: String, at: String) {
        rows.value = rows.value.mapValues { (_, r) ->
            if (r.walletId == walletId && r.carryConfirmedAt != null && r.carryConfirmedAt <= at) r.copy(carrySourceMinor = 0) else r
        }
    }

    override suspend fun clearCounterCarry(walletId: String, at: String) {
        rows.value = rows.value.mapValues { (_, r) ->
            if (r.counterWalletId == walletId && r.carryConfirmedAt != null && r.carryConfirmedAt <= at) r.copy(carryCounterMinor = 0) else r
        }
    }

    override suspend fun settleClearedCarry(walletId: String, at: String) {
        rows.value = rows.value.mapValues { (_, r) ->
            if ((r.walletId == walletId || r.counterWalletId == walletId) && r.carryConfirmedAt != null && r.carryConfirmedAt <= at &&
                r.carrySourceMinor == 0L && r.carryCounterMinor == 0L
            ) r.copy(carryConfirmedAt = null) else r
        }
    }

    override suspend fun purgeSettledDeletions() {
        rows.value = rows.value.filterValues { !(it.deletedAt != null && it.carryConfirmedAt == null) }
    }

    override suspend fun upsert(entity: TransactionEntity) { rows.value = rows.value + (entity.id to entity) }
    override suspend fun upsertAll(entities: List<TransactionEntity>) { rows.value = rows.value + entities.associateBy { it.id } }
    override suspend fun delete(id: String) { rows.value = rows.value - id }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

fun transactionJson(
    id: String = "t1",
    walletId: String = "w1",
    kind: String = "expense",
    date: String = "2026-10-01",
    amount: Long = 4_550,
    currency: String = "EGP",
    categoryId: String? = "c1",
    counterWalletId: String? = null,
    description: String? = null,
    memo: String? = null,
    counterparty: String? = null,
    cleared: Boolean = false,
    createdBy: String = "u1",
    recurringRuleId: String? = null,
    occurrenceId: String? = null,
    version: Long = 1,
    deletedAt: String? = null,
    createdAt: String = "2026-10-01T09:00:00Z",
) = """
    {"id":"$id","wallet_id":"$walletId","kind":"$kind","date":"$date","amount_minor":$amount,"currency":"$currency",
     "category_id":${categoryId.q()},"counter_wallet_id":${counterWalletId.q()},"description":${description.q()},
     "memo":${memo.q()},"counterparty":${counterparty.q()},"cleared":$cleared,"created_by":"$createdBy",
     "recurring_rule_id":${recurringRuleId.q()},"occurrence_id":${occurrenceId.q()},"version":$version,"change_seq":9,
     "created_at":"$createdAt","updated_at":"$createdAt","deleted_at":${deletedAt.q()},"something_new":1}
""".trimIndent()

private fun String?.q() = this?.let { "\"$it\"" } ?: "null"

fun transactionBody(json: String = transactionJson()) = """{"data":$json,"meta":{}}"""

fun transactionPage(items: List<String>, nextCursor: String? = null, limit: Int = 200) =
    """{"data":[${items.joinToString(",")}],"meta":{"next_cursor":${nextCursor.q()},"has_more":${nextCursor != null},"limit":$limit}}"""

fun dto(json: String): TransactionDto = AppJson.decodeFromString(TransactionDto.serializer(), json)

val txAccount = AccountSnapshot("u1", "sara@example.com", true, AccountState.Active, null, "free", emptyMap())

fun display(id: String, name: String, kind: String = "expense", source: CategorySource = CategorySource.Own, archived: Boolean = false) =
    CategoryDisplay(id, name, "cart", "#2E7D32", kind, source, archived)

/** C5's service stand-in: scripted choices and displays, records the reference refreshes it is asked for. */
class FakeCategories : CategoriesService {
    val displays = mutableMapOf("c1" to display("c1", "Groceries"), "c2" to display("c2", "Transport"), "ci" to display("ci", "Salary", "income"))
    val own = MutableStateFlow(listOf(display("c1", "Groceries"), display("c2", "Transport")))
    val defaults = MutableStateFlow(listOf(display("d1", "Dining", source = CategorySource.Default)))
    val incomeOwn = MutableStateFlow(listOf(display("ci", "Salary", "income")))
    val referenceRefreshes = mutableListOf<Pair<String, Boolean>>()
    var missed = mutableListOf<String>()

    override fun observeChoices(kind: CategoryKind, currentCategoryId: String?): Flow<CategoryChoices> =
        if (kind == CategoryKind.Income) incomeOwn.map { CategoryChoices(null, it, emptyList()) }
        else kotlinx.coroutines.flow.combine(own, defaults) { o, d ->
            val current = currentCategoryId?.takeIf { id -> o.none { it.id == id } && d.none { it.id == id } }?.let { displays[it] }
            CategoryChoices(current, o, d)
        }

    override fun observeDisplays(walletId: String?): Flow<CategoryDisplays> = flowOf(
        CategoryDisplays(
            lookup = { id -> displays[id] ?: CategoryDisplay(id, "", null, null, null, CategorySource.Unavailable, false) },
            onMiss = { },
        ),
    )

    override suspend fun refreshReferences(walletId: String, force: Boolean): RefreshOutcome {
        referenceRefreshes += walletId to force
        return RefreshOutcome.Done
    }

    override fun observeOverview(kind: CategoryKind): Flow<CategoriesOverview> = error("not used")
    override fun observeCategory(id: String): Flow<CategoryView?> = error("not used")
    override fun observeUsedLocally(): Flow<Set<String>> = flowOf(emptySet())
    override suspend fun create(draft: CategoryDraft): CategoryCreateResult = error("not used")
    override suspend fun save(id: String, draft: CategoryDraft, baseVersion: Long): CategorySaveResult = error("not used")
    override suspend fun archive(id: String): CategorySaveResult = error("not used")
    override suspend fun delete(id: String): CategorySaveResult = error("not used")
    override suspend fun archiveInstead(id: String) = error("not used")
    override suspend fun keepCategory(id: String) = error("not used")
    override suspend fun keepMine(id: String) = error("not used")
    override suspend fun useTheirs(id: String) = error("not used")
    override suspend fun tryAgain(id: String) = error("not used")
    override suspend fun discard(id: String) = error("not used")
    override suspend fun refresh(force: Boolean): RefreshOutcome = RefreshOutcome.Done
}

/**
 * The ledger stack over a Ktor MockEngine and in-memory DAOs: real wallets service, sync engine with
 * both handlers, transaction repository, postings, reconciler and service; one signed-in account, `u1`.
 */
class TransactionTestEnv(
    val scope: CoroutineScope,
    /** The ids new transactions get, in order; `t-new-1`, `t-new-2`, … when not given. */
    private val newIds: List<String> = emptyList(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { throw kotlinx.io.IOException("offline") },
) {
    val auth = AuthTestEnv(handler)
    val walletApi = WalletApi(auth.client)
    val api = TransactionApi(auth.client)
    val walletDao = FakeWalletDao()
    val memberDao = FakeWalletMemberDao()
    val transactionDao = FakeTransactionDao()
    val scopeDao = FakeSyncScopeDao()
    val pendingDao = FakePendingChangeDao()
    val scheduler = FakeSyncScheduler()
    val clock = FakeClock()
    val categories = FakeCategories()
    val authState = MutableStateFlow<AuthState>(AuthState.SignedIn(txAccount))

    val walletRepository: WalletRepository = WalletRepository(
        walletApi, walletDao, memberDao, scopeDao,
        accountId = { "u1" },
        hasQueuedChange = { id -> engine.get("wallet", id) != null },
        onWalletRemoved = { id -> cleanup.onWalletDeleted(id) },
        clock = clock,
        onWalletCopy = { id, changeSeq -> cleanup.onWalletCopy(id, changeSeq) },
    )
    val repository: TransactionRepository = TransactionRepository(
        transactionDao, scopeDao,
        accountId = { "u1" },
        hasQueuedChange = { id -> engine.get(TransactionSyncHandler.RECORD_TYPE, id) != null },
        clock = clock,
        walletChangeSeq = { id -> walletDao.get(id)?.changeSeq },
    )
    val walletHandler = WalletSyncHandler(walletRepository, walletApi, accountId = { "u1" })
    val syncHandler = TransactionSyncHandler(repository, api, walletOnPhone = { id -> walletDao.get(id) != null })
    val engine: SyncEngine = SyncEngine(
        pendingDao, scopeDao, listOf(walletHandler, syncHandler), scheduler, currentAccountId = { "u1" }, clock = clock,
    )
    val postings = TransactionPostings({ engine }, repository)
    val occurrenceTransactions = com.fares.saveme.feature.transactions.domain.DefaultOccurrenceTransactions(repository, accountId = { "u1" }, clock = clock)
    val runner = FakeSyncRunner(engine = engine, scope = scope)
    private var nextWalletId = 0
    val wallets = DefaultWalletsService(
        walletRepository, engine, authState, FakeEntitlements(), postings,
        defaultCurrency = flowOf("EGP"), runner = runner, newId = { "w-new-${++nextWalletId}" },
    )
    val reconciler = BalanceReconciler(repository, runner, scope, clock, debounce = Duration.ZERO)
    val cleanup = TransactionsWalletCleanup(repository, { engine }, { id -> walletDao.get(id) != null }, scope)
    private var nextId = 0
    val service = DefaultTransactionsService(
        repository, engine, wallets, authState, auth.keyValues, reconciler, runner, clock,
        newId = { newIds.getOrNull(nextId++) ?: "t-new-$nextId" },
    )
    val requests get() = auth.requests

    suspend fun signedIn() = auth.secureStore.write(StoredSession(CREDENTIAL, "a", Instant.parse("2026-09-26T10:00:00Z")))

    /** A synced wallet row, as a refresh would have written it. */
    suspend fun seedWallet(json: String = walletJson()) {
        walletRepository.storeServerCopy(AppJson.decodeFromString(WalletDto.serializer(), json))
    }

    suspend fun seedMember(userId: String, name: String?, role: String = "editor", walletId: String = "w1") {
        memberDao.insertAll(
            listOf(
                com.fares.saveme.feature.wallets.data.local.WalletMemberEntity(
                    "m-$walletId-$userId", walletId, userId, name, role, 1, "2026-09-01T00:00:00Z",
                ),
            ),
        )
    }

    /** The carry a wallet would show on top of the server's balance, summed over every row. */
    suspend fun carryFor(walletId: String): Long = transactionDao.getAll().sumOf {
        (if (it.walletId == walletId) it.carrySourceMinor else 0L) + (if (it.counterWalletId == walletId) it.carryCounterMinor else 0L)
    }

    /** A synced transaction row, as a read would have written it. */
    suspend fun seed(json: String = transactionJson()) {
        repository.storeServerCopy(dto(json), own = false)
    }
}

/** Ktor's mock engine answers on its own dispatcher, so a test that does not suspend on the call polls in real time. */
suspend fun awaitReal(condition: () -> Boolean) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        repeat(200) {
            if (condition()) return@withContext
            kotlinx.coroutines.delay(25)
        }
    }
}

/** A transaction as a screen sees it, for the pure list rules. */
fun viewOf(
    id: String = "t1",
    date: String = "2026-10-01",
    amount: Long = 1_000,
    kind: String = "expense",
    walletId: String = "w1",
    counterWalletId: String? = null,
    category: String? = "c1",
    description: String? = null,
    memo: String? = null,
    counterparty: String? = null,
    createdAt: String = "2026-10-01T09:00:00Z",
    viewedFrom: String = walletId,
    sync: com.fares.saveme.feature.transactions.domain.model.TransactionSyncState = com.fares.saveme.feature.transactions.domain.model.TransactionSyncState.None,
    pendingDelete: Boolean = false,
): com.fares.saveme.feature.transactions.domain.model.TransactionView {
    val draft = com.fares.saveme.feature.transactions.domain.model.TransactionDraft(
        walletId, kind, kotlinx.datetime.LocalDate.parse(date), amount, "EGP", category, counterWalletId, description, memo, counterparty, false,
    )
    return com.fares.saveme.feature.transactions.domain.model.TransactionView(
        id = id, server = null, shown = draft, createdAt = Instant.parse(createdAt), pendingDelete = pendingDelete, syncState = sync,
        direction = com.fares.saveme.feature.transactions.domain.directionIn(draft, viewedFrom), counterpart = null,
        addedBy = com.fares.saveme.feature.transactions.domain.model.AddedBy.You, fromRecurringRule = false, canEdit = true, canDelete = true,
        walletCurrency = "EGP",
    )
}
