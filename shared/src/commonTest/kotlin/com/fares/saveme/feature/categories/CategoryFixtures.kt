package com.fares.saveme.feature.categories

import com.fares.saveme.core.locale.AppLanguage
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
import com.fares.saveme.feature.categories.data.CategoryApi
import com.fares.saveme.feature.categories.data.CategoryReferenceRepository
import com.fares.saveme.feature.categories.data.CategoryRepository
import com.fares.saveme.feature.categories.data.CategorySyncHandler
import com.fares.saveme.feature.categories.data.dto.CategoryDto
import com.fares.saveme.feature.categories.data.local.CategoryDao
import com.fares.saveme.feature.categories.data.local.CategoryEntity
import com.fares.saveme.feature.categories.data.local.CategoryReferenceDao
import com.fares.saveme.feature.categories.data.local.CategoryReferenceEntity
import com.fares.saveme.feature.categories.data.local.PlatformCategoryDao
import com.fares.saveme.feature.categories.data.local.PlatformCategoryEntity
import com.fares.saveme.feature.categories.domain.DefaultCategoriesService
import com.fares.saveme.feature.categories.domain.NoPendingCategoryUses
import com.fares.saveme.feature.categories.domain.PendingCategoryUses
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

class FakeCategoryDao : CategoryDao {
    val rows = MutableStateFlow<Map<String, CategoryEntity>>(emptyMap())

    override fun observeAll(accountId: String): Flow<List<CategoryEntity>> = rows.map { all ->
        all.values.filter { it.accountId == accountId }.sortedWith(compareBy({ it.createdAt }, { it.id }))
    }

    override fun observe(id: String): Flow<CategoryEntity?> = rows.map { it[id] }
    override suspend fun get(id: String): CategoryEntity? = rows.value[id]
    override suspend fun getAll(): List<CategoryEntity> = rows.value.values.toList()
    override suspend fun upsert(entity: CategoryEntity) { rows.value = rows.value + (entity.id to entity) }
    override suspend fun upsertAll(entities: List<CategoryEntity>) { rows.value = rows.value + entities.associateBy { it.id } }
    override suspend fun markInUse(id: String) { rows.value[id]?.let { rows.value = rows.value + (id to it.copy(inUse = true)) } }
    override suspend fun delete(id: String) { rows.value = rows.value - id }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

class FakePlatformCategoryDao : PlatformCategoryDao {
    val rows = MutableStateFlow<List<PlatformCategoryEntity>>(emptyList())
    override fun observeAll(): Flow<List<PlatformCategoryEntity>> = rows.map { it.sortedWith(compareBy({ r -> r.position }, { r -> r.id })) }
    override suspend fun getAll(): List<PlatformCategoryEntity> = rows.value
    override suspend fun insertAll(items: List<PlatformCategoryEntity>) { rows.value = rows.value + items }
    override suspend fun deleteAll() { rows.value = emptyList() }
}

class FakeCategoryReferenceDao : CategoryReferenceDao {
    val rows = MutableStateFlow<List<CategoryReferenceEntity>>(emptyList())
    override fun observe(walletId: String): Flow<List<CategoryReferenceEntity>> = rows.map { all -> all.filter { it.walletId == walletId } }
    override fun observeAll(): Flow<List<CategoryReferenceEntity>> = rows
    override suspend fun get(walletId: String): List<CategoryReferenceEntity> = rows.value.filter { it.walletId == walletId }
    override suspend fun insertAll(items: List<CategoryReferenceEntity>) { rows.value = rows.value + items }
    override suspend fun getById(id: String): CategoryReferenceEntity? = rows.value.firstOrNull { it.id == id }
    override suspend fun upsert(entity: CategoryReferenceEntity) { rows.value = rows.value.filterNot { it.id == entity.id } + entity }
    override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
    override suspend fun deleteOthers(walletId: String, categoryId: String, keepId: String) {
        rows.value = rows.value.filterNot { it.walletId == walletId && it.categoryId == categoryId && it.id != keepId }
    }
    override suspend fun deleteFor(walletId: String) { rows.value = rows.value.filterNot { it.walletId == walletId } }
    override suspend fun deleteAll() { rows.value = emptyList() }
}

fun categoryJson(
    id: String = "c1",
    kind: String = "expense",
    name: String = "Coffee",
    icon: String? = null,
    color: String? = null,
    archivedAt: String? = null,
    inUse: Boolean = false,
    version: Long = 1,
    deletedAt: String? = null,
    createdAt: String = "2026-09-01T00:00:00Z",
) = """
    {"id":"$id","user_id":"u1","kind":"$kind","name":"$name","icon":${icon?.let { "\"$it\"" } ?: "null"},"color":${color?.let { "\"$it\"" } ?: "null"},
     "archived_at":${archivedAt?.let { "\"$it\"" } ?: "null"},"in_use":$inUse,"version":$version,
     "change_seq":7,"created_at":"$createdAt","updated_at":"$createdAt","deleted_at":${deletedAt?.let { "\"$it\"" } ?: "null"},"something_new":1}
""".trimIndent()

fun categoryBody(json: String = categoryJson()) = """{"data":$json,"meta":{}}"""

fun platformJson(
    id: String = "d1",
    key: String = "groceries",
    kind: String = "expense",
    en: String = "Groceries",
    ar: String = "بقالة",
    icon: String = "cart",
    color: String = "#2E7D32",
    position: Int = 1,
    archivedAt: String? = null,
) = """
    {"id":"$id","system_key":"$key","kind":"$kind","names":{"ar":"$ar","en":"$en"},"icon":"$icon","color":"$color","position":$position,
     "archived_at":${archivedAt?.let { "\"$it\"" } ?: "null"},"updated_at":"2026-09-01T00:00:00Z","something_new":true}
""".trimIndent()

fun referenceJson(
    id: String = "r1",
    walletId: String = "w1",
    categoryId: String = "p1",
    owner: String = "u2",
    kind: String = "expense",
    name: String = "Partner's coffee",
    icon: String? = "restaurant",
    color: String? = "#1F7F8C",
) = """
    {"id":"$id","wallet_id":"$walletId","category_id":"$categoryId","owner_user_id":"$owner","kind":"$kind","name":"$name",
     "icon":${icon?.let { "\"$it\"" } ?: "null"},"color":${color?.let { "\"$it\"" } ?: "null"},"version":1,"change_seq":3,
     "created_at":"2026-09-01T00:00:00Z","updated_at":"2026-09-01T00:00:00Z","deleted_at":null,"something_new":true}
""".trimIndent()

fun categoryPage(items: List<String>, nextCursor: String? = null, limit: Int = 200) =
    """{"data":[${items.joinToString(",")}],"meta":{"next_cursor":${nextCursor?.let { "\"$it\"" } ?: "null"},"has_more":${nextCursor != null},"limit":$limit}}"""

val signedInAccount = AccountSnapshot("u1", "sara@example.com", true, AccountState.Active, null, "free", emptyMap())

class FakePendingUses : PendingCategoryUses {
    val used = MutableStateFlow(emptySet<String>())
    override fun usedLocally(): Flow<Set<String>> = used
}

/** The category stack over a Ktor MockEngine and in-memory DAOs; one signed-in account, `u1`. */
class CategoryTestEnv(
    val scope: CoroutineScope,
    val uses: PendingCategoryUses = NoPendingCategoryUses,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val auth = AuthTestEnv(handler)
    val api = CategoryApi(auth.client)
    val categoryDao = FakeCategoryDao()
    val platformDao = FakePlatformCategoryDao()
    val referenceDao = FakeCategoryReferenceDao()
    val scopeDao = FakeSyncScopeDao()
    val pendingDao = FakePendingChangeDao()
    val scheduler = FakeSyncScheduler()
    val clock = FakeClock()
    val repository: CategoryRepository = CategoryRepository(
        api, categoryDao, platformDao, scopeDao,
        accountId = { "u1" },
        hasQueuedChange = { id -> engine.get(CategorySyncHandler.RECORD_TYPE, id) != null },
        clock = clock,
    )
    val references = CategoryReferenceRepository(referenceDao)
    val syncHandler = CategorySyncHandler(repository, api)
    val engine: SyncEngine = SyncEngine(pendingDao, scopeDao, listOf(syncHandler), scheduler, currentAccountId = { "u1" }, clock = clock)
    val runner = FakeSyncRunner(engine = engine, scope = scope)
    val authState = MutableStateFlow<AuthState>(AuthState.SignedIn(signedInAccount))
    val language = MutableStateFlow(AppLanguage.En)
    private var nextId = 0
    val service = DefaultCategoriesService(
        repository, references, engine, authState, uses, language,
        scope = scope, runner = runner, clock = clock, newId = { "c-new-${++nextId}" },
    )
    val requests get() = auth.requests

    suspend fun signedIn() = auth.secureStore.write(StoredSession(CREDENTIAL, "a", Instant.parse("2026-09-26T10:00:00Z")))

    /** A synced own category, as a refresh would have written it. */
    suspend fun seed(json: String = categoryJson()) {
        repository.storeServerCopy(AppJson.decodeFromString(CategoryDto.serializer(), json))
    }

    /** The default set as the platform sends it. */
    suspend fun seedDefaults(vararg json: String = arrayOf(platformJson())) {
        platformDao.rows.value = json.map {
            AppJson.decodeFromString(com.fares.saveme.feature.categories.data.dto.PlatformCategoryDto.serializer(), it).let { dto ->
                PlatformCategoryEntity(dto.id, dto.systemKey, dto.kind, dto.names.ar, dto.names.en, dto.icon, dto.color, dto.position, dto.archivedAt, dto.updatedAt)
            }
        }
    }

    fun seedReference(walletId: String = "w1", categoryId: String = "p1", name: String = "Partner's coffee") {
        referenceDao.rows.value = referenceDao.rows.value + CategoryReferenceEntity(
            "r-$walletId-$categoryId", walletId, categoryId, "u2", "expense", name, "restaurant", "#1F7F8C", 1, 3, "2026-09-01T00:00:00Z",
        )
    }
}
