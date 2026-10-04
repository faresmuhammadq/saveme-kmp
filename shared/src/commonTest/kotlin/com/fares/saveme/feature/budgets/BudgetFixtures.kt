package com.fares.saveme.feature.budgets

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.core.sync.FakeClock
import com.fares.saveme.core.sync.FakePendingChangeDao
import com.fares.saveme.core.sync.FakeSyncRunner
import com.fares.saveme.core.sync.FakeSyncScheduler
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.account.FakeSyncScopeDao
import com.fares.saveme.feature.account.domain.model.Profile
import com.fares.saveme.feature.account.domain.model.ProfileView
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.AccountSnapshot
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.budgets.data.BudgetApi
import com.fares.saveme.feature.budgets.data.BudgetProgressRepository
import com.fares.saveme.feature.budgets.data.BudgetRepository
import com.fares.saveme.feature.budgets.data.BudgetSyncHandler
import com.fares.saveme.feature.budgets.data.dto.BudgetDto
import com.fares.saveme.feature.budgets.data.local.BudgetDao
import com.fares.saveme.feature.budgets.data.local.BudgetEntity
import com.fares.saveme.feature.budgets.data.local.BudgetProgressDao
import com.fares.saveme.feature.budgets.data.local.BudgetProgressEntity
import com.fares.saveme.feature.budgets.domain.DefaultBudgetsService
import com.fares.saveme.feature.categories.domain.CategoriesService
import com.fares.saveme.feature.categories.domain.model.CategoriesOverview
import com.fares.saveme.feature.categories.domain.model.CategoryChoices
import com.fares.saveme.feature.categories.domain.model.CategoryDisplay
import com.fares.saveme.feature.categories.domain.model.CategoryDisplays
import com.fares.saveme.feature.categories.domain.model.CategoryDraft
import com.fares.saveme.feature.categories.domain.model.CategoryKind
import com.fares.saveme.feature.categories.domain.model.CategorySource
import com.fares.saveme.feature.categories.domain.model.CategoryView
import com.fares.saveme.feature.transactions.domain.OwnExpense
import com.fares.saveme.feature.transactions.domain.OwnSpending
import com.fares.saveme.feature.transactions.domain.SpendingKey
import com.fares.saveme.feature.transactions.domain.SpendingSnapshot
import com.fares.saveme.feature.wallets.domain.WalletsService
import com.fares.saveme.feature.wallets.domain.model.CreateResult as WalletCreateResult
import com.fares.saveme.feature.wallets.domain.model.Member
import com.fares.saveme.feature.wallets.domain.model.SaveResult as WalletSaveResult
import com.fares.saveme.feature.wallets.domain.model.TransferResult
import com.fares.saveme.feature.wallets.domain.model.Wallet
import com.fares.saveme.feature.wallets.domain.model.WalletDraft
import com.fares.saveme.feature.wallets.domain.model.WalletList
import com.fares.saveme.feature.wallets.domain.model.WalletRole
import com.fares.saveme.feature.wallets.domain.model.SyncState as WalletSyncState
import com.fares.saveme.feature.wallets.domain.model.WalletView
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** In-memory stand-in that follows [BudgetDao]'s SQL (the SQL itself: BudgetDaoTest). */
class FakeBudgetDao(private val progress: FakeBudgetProgressDao = FakeBudgetProgressDao()) : BudgetDao {
    val rows = MutableStateFlow<Map<String, BudgetEntity>>(emptyMap())
    val progressDao: FakeBudgetProgressDao get() = progress

    override fun observeAll(accountId: String): Flow<List<BudgetEntity>> = rows.map { all ->
        all.values.filter { it.accountId == accountId }.sortedWith(compareBy({ it.createdAt }, { it.id }))
    }
    override fun observeActive(accountId: String): Flow<List<BudgetEntity>> = observeAll(accountId).map { l -> l.filter { it.archivedAt == null } }
    override fun observeArchived(accountId: String): Flow<List<BudgetEntity>> = observeAll(accountId).map { l -> l.filter { it.archivedAt != null } }
    override fun observe(id: String): Flow<BudgetEntity?> = rows.map { it[id] }
    override suspend fun get(id: String): BudgetEntity? = rows.value[id]
    override suspend fun getAll(): List<BudgetEntity> = rows.value.values.toList()
    override suspend fun upsert(entity: BudgetEntity) { rows.value = rows.value + (entity.id to entity) }
    override suspend fun delete(id: String) { rows.value = rows.value - id }
    override suspend fun deleteProgress(id: String) = progress.delete(id)
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

class FakeBudgetProgressDao : BudgetProgressDao {
    val rows = MutableStateFlow<Map<String, BudgetProgressEntity>>(emptyMap())
    override fun observeAll(accountId: String): Flow<List<BudgetProgressEntity>> = rows.map { all -> all.values.filter { it.accountId == accountId } }
    override suspend fun get(budgetId: String): BudgetProgressEntity? = rows.value[budgetId]
    override suspend fun getAll(): List<BudgetProgressEntity> = rows.value.values.toList()
    override suspend fun upsertAll(rows: List<BudgetProgressEntity>) { this.rows.value = this.rows.value + rows.associateBy { it.budgetId } }
    override suspend fun delete(budgetId: String) { rows.value = rows.value - budgetId }
    override suspend fun deleteExcept(accountId: String, keepIds: List<String>) {
        rows.value = rows.value.filterValues { it.accountId != accountId || it.budgetId in keepIds }
    }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

fun budgetJson(
    id: String = "b1",
    categoryId: String? = "c1",
    amount: Long = 600_000,
    currency: String = "EGP",
    periodType: String = "monthly",
    archivedAt: String? = null,
    version: Long = 1,
    deletedAt: String? = null,
    createdAt: String = "2026-09-01T00:00:00Z",
) = """
    {"id":"$id","user_id":"u1","version":$version,"change_seq":7,"created_at":"$createdAt","updated_at":"$createdAt",
     "deleted_at":${deletedAt.q()},"category_id":${categoryId.q()},"period_type":"$periodType","amount_minor":$amount,
     "currency":"$currency","archived_at":${archivedAt.q()},"something_new":1}
""".trimIndent()

private fun String?.q() = this?.let { "\"$it\"" } ?: "null"

fun budgetBody(json: String = budgetJson()) = """{"data":$json,"meta":{}}"""

fun progressJson(
    budgetId: String = "b1",
    start: String = "2026-10-01",
    end: String = "2026-10-31",
    days: Int = 31,
    elapsed: Int? = 2,
    limit: Long = 600_000,
    spent: Long = 45_000,
    currency: String = "EGP",
    projected: Long? = 696_000,
    others: List<String> = emptyList(),
) = """
    {"budget_id":"$budgetId","period":{"start":"$start","end":"$end","days_in_period":$days,"days_elapsed":${elapsed ?: "null"}},
     "is_current":true,"limit":{"amount_minor":$limit,"currency":"$currency"},"spent":{"amount_minor":$spent,"currency":"$currency"},
     "remaining":{"amount_minor":${limit - spent},"currency":"$currency"},
     "projected_end":${projected?.let { """{"amount_minor":$it,"currency":"$currency"}""" } ?: "null"},
     "over_limit":${spent > limit},"other_currencies":[${others.joinToString(",") { "\"$it\"" }}],"something_new":true}
""".trimIndent()

fun progressPage(items: List<String>, nextCursor: String? = null, limit: Int = 200) =
    """{"data":[${items.joinToString(",")}],"meta":{"next_cursor":${nextCursor.q()},"has_more":${nextCursor != null},"limit":$limit}}"""

fun progressBody(json: String = progressJson()) = """{"data":$json,"meta":{}}"""

fun budgetDto(json: String = budgetJson()): BudgetDto = AppJson.decodeFromString(BudgetDto.serializer(), json)

val budgetAccount = AccountSnapshot("u1", "sara@example.com", true, AccountState.Active, null, "free", emptyMap())

fun expense(
    id: String,
    date: String,
    amount: Long,
    currency: String = "EGP",
    category: String? = "c1",
    waiting: Boolean = false,
) = OwnExpense(id, LocalDate.parse(date), amount, currency, category, waiting)

fun touch(date: String, currency: String = "EGP", category: String? = "c1") = SpendingKey(LocalDate.parse(date), currency, category)

/** Transactions' hook stand-in: a scripted snapshot, whatever the range. */
class FakeSpending(initial: SpendingSnapshot = SpendingSnapshot(emptyList(), emptyList(), emptyList())) : OwnSpending {
    val snapshot = MutableStateFlow(initial)
    val ranges = mutableListOf<Pair<LocalDate, LocalDate>>()
    override fun observe(from: LocalDate, to: LocalDate): Flow<SpendingSnapshot> {
        ranges += from to to
        return snapshot
    }

    /** Sets the same expenses as both the shown and the synced list, without a waiting change. */
    fun set(vararg expenses: OwnExpense) {
        snapshot.value = SpendingSnapshot(expenses.toList(), expenses.toList(), emptyList())
    }
}

fun categoryDisplay(id: String, name: String, source: CategorySource = CategorySource.Own) =
    CategoryDisplay(id, name, "cart", "#2E7D32", "expense", source, false)

/** C5's service stand-in: scripted choices and displays. */
class FakeBudgetCategories : CategoriesService {
    val displays = mutableMapOf("c1" to categoryDisplay("c1", "Groceries"), "c2" to categoryDisplay("c2", "Transport"))
    val own = MutableStateFlow(listOf(categoryDisplay("c1", "Groceries"), categoryDisplay("c2", "Transport")))
    val defaults = MutableStateFlow(listOf(categoryDisplay("d1", "Dining", CategorySource.Default)))

    override fun observeChoices(kind: CategoryKind, currentCategoryId: String?): Flow<CategoryChoices> =
        kotlinx.coroutines.flow.combine(own, defaults) { o, d ->
            val current = currentCategoryId?.takeIf { id -> o.none { it.id == id } && d.none { it.id == id } }?.let { displays[it] }
            CategoryChoices(current, o, d)
        }

    override fun observeDisplays(walletId: String?): Flow<CategoryDisplays> = displays.let { map ->
        flowOf(CategoryDisplays(lookup = { id -> map[id] ?: CategoryDisplay(id, "", null, null, null, CategorySource.Unavailable, false) }))
    }

    override fun observeOverview(kind: CategoryKind): Flow<CategoriesOverview> = error("not used")
    override fun observeCategory(id: String): Flow<CategoryView?> = error("not used")
    override fun observeUsedLocally(): Flow<Set<String>> = flowOf(emptySet())
    override suspend fun create(draft: CategoryDraft): com.fares.saveme.feature.categories.domain.model.CreateResult = error("not used")
    override suspend fun save(id: String, draft: CategoryDraft, baseVersion: Long): com.fares.saveme.feature.categories.domain.model.SaveResult = error("not used")
    override suspend fun archive(id: String): com.fares.saveme.feature.categories.domain.model.SaveResult = error("not used")
    override suspend fun delete(id: String): com.fares.saveme.feature.categories.domain.model.SaveResult = error("not used")
    override suspend fun archiveInstead(id: String) = error("not used")
    override suspend fun keepCategory(id: String) = error("not used")
    override suspend fun keepMine(id: String) = error("not used")
    override suspend fun useTheirs(id: String) = error("not used")
    override suspend fun tryAgain(id: String) = error("not used")
    override suspend fun discard(id: String) = error("not used")
    override suspend fun refresh(force: Boolean): RefreshOutcome = RefreshOutcome.Done
    override suspend fun refreshReferences(walletId: String, force: Boolean): RefreshOutcome = RefreshOutcome.Done
}

private val stamp = Instant.parse("2026-09-01T00:00:00Z")

fun walletView(id: String, currency: String, archived: Boolean = false) = WalletView(
    server = Wallet(id, "cash", "Wallet $id", currency, 0, 0, null, null, if (archived) stamp else null, false, WalletRole.Owner, 1, stamp),
    pending = null, pendingDelete = false, syncState = WalletSyncState.None, displayedBalanceMinor = 0,
)

/** C4's service stand-in: a scripted wallet list. */
class FakeBudgetWallets(vararg currencies: String) : WalletsService {
    val list = MutableStateFlow(WalletList(currencies.mapIndexed { i, c -> walletView("w$i", c) }, emptyList(), emptyList(), null))
    override fun observeList(): Flow<WalletList> = list
    override fun observeWallet(id: String): Flow<WalletView?> = error("not used")
    override fun observeMembers(id: String): Flow<List<Member>> = error("not used")
    override suspend fun canCreateAnother(): Boolean = error("not used")
    override suspend fun create(draft: WalletDraft): WalletCreateResult = error("not used")
    override suspend fun save(id: String, draft: WalletDraft, baseVersion: Long): WalletSaveResult = error("not used")
    override suspend fun archive(id: String): WalletSaveResult = error("not used")
    override suspend fun delete(id: String): WalletSaveResult = error("not used")
    override suspend fun keepMine(id: String) = error("not used")
    override suspend fun useTheirs(id: String) = error("not used")
    override suspend fun tryAgain(id: String) = error("not used")
    override suspend fun discard(id: String) = error("not used")
    override suspend fun refresh(force: Boolean): RefreshOutcome = RefreshOutcome.Done
    override suspend fun refreshMembers(id: String): RefreshOutcome = RefreshOutcome.Done
    override suspend fun transferOwnership(id: String, toUserId: String): TransferResult = error("not used")
}

fun profileView(
    startDay: Int = 1,
    from: LocalDate? = null,
    zone: String = "Africa/Cairo",
    currency: String = "EGP",
) = ProfileView(
    server = Profile("Sara", "en", zone, currency, false, startDay, from, 1),
    pending = null, conflict = null, rejection = emptyMap(), refreshedAt = null,
)

/**
 * The budgets stack over a Ktor MockEngine, in-memory DAOs, the real sync engine with the budget handler, scripted
 * categories, wallets and spending, a fixed clock (2026-10-02 10:00 UTC) and one signed-in verified account `u1`.
 */
class BudgetTestEnv(
    val scope: CoroutineScope,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { throw kotlinx.io.IOException("offline") },
) {
    val auth = AuthTestEnv(handler)
    val api = BudgetApi(auth.client)
    val progressDao = FakeBudgetProgressDao()
    val budgetDao = FakeBudgetDao(progressDao)
    val scopeDao = FakeSyncScopeDao()
    val pendingDao = FakePendingChangeDao()
    val scheduler = FakeSyncScheduler()
    val clock = FakeClock()
    val categories = FakeBudgetCategories()
    val wallets = FakeBudgetWallets("EGP", "USD")
    val spending = FakeSpending()
    val profile = MutableStateFlow<ProfileView?>(profileView())
    val now = MutableStateFlow(clock.now)
    val authState = MutableStateFlow<AuthState>(AuthState.SignedIn(budgetAccount))

    val repository = BudgetRepository(
        budgetDao, accountId = { "u1" },
        hasQueuedChange = { id -> engine.get(BudgetSyncHandler.RECORD_TYPE, id) != null },
        clock = clock,
    )
    val progress = BudgetProgressRepository(progressDao)
    val syncHandler = BudgetSyncHandler(repository, api)
    val engine: SyncEngine = SyncEngine(pendingDao, scopeDao, listOf(syncHandler), scheduler, currentAccountId = { "u1" }, clock = clock)
    val runner = FakeSyncRunner(engine = engine, scope = scope)
    private var nextId = 0
    val service = DefaultBudgetsService(
        repository = repository, progress = progress, api = api, engine = { engine }, authState = authState,
        categories = categories, wallets = wallets, spending = spending, profile = profile, deviceZoneId = { "Africa/Cairo" },
        runner = runner, now = now, clock = clock, newId = { "b-new-${++nextId}" },
    )
    val requests get() = auth.requests

    suspend fun signedIn() = auth.secureStore.write(StoredSession(CREDENTIAL, "a", Instant.parse("2026-09-26T10:00:00Z")))

    /** A synced budget, as a feed read would have written it. */
    suspend fun seed(json: String = budgetJson()) {
        repository.storeServerCopy(budgetDto(json))
    }

    /** A stored progress row, as a completed walk would have written it. */
    suspend fun seedProgress(
        budgetId: String = "b1",
        start: String = "2026-10-01",
        end: String = "2026-10-31",
        limit: Long = 600_000,
        spent: Long = 45_000,
        local: Long = 45_000,
        others: String = "",
        localOthers: String = "",
        currency: String = "EGP",
    ) = progressDao.upsertAll(
        listOf(BudgetProgressEntity(budgetId, "u1", start, end, 31, limit, spent, currency, others, local, localOthers, "2026-10-02T09:00:00Z")),
    )
}
