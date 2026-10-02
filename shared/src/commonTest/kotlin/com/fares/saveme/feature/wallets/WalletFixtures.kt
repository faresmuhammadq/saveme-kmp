package com.fares.saveme.feature.wallets

import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.core.sync.FakeClock
import com.fares.saveme.core.sync.FakePendingChangeDao
import com.fares.saveme.core.sync.FakeSyncScheduler
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.account.FakeSyncScopeDao
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.auth.domain.AuthState
import com.fares.saveme.feature.auth.domain.model.AccountSnapshot
import com.fares.saveme.feature.auth.domain.model.AccountState
import com.fares.saveme.feature.auth.domain.model.FeatureValue
import com.fares.saveme.feature.auth.domain.model.RefreshOutcome
import com.fares.saveme.feature.billing.domain.EntitlementSet
import com.fares.saveme.feature.billing.domain.Entitlements
import com.fares.saveme.feature.billing.domain.FlagFeature
import com.fares.saveme.feature.billing.domain.QuotaFeature
import com.fares.saveme.feature.billing.domain.QuotaLimit
import com.fares.saveme.feature.wallets.data.WalletApi
import com.fares.saveme.feature.wallets.data.WalletRepository
import com.fares.saveme.feature.wallets.data.WalletSyncHandler
import com.fares.saveme.feature.wallets.data.local.WalletDao
import com.fares.saveme.feature.wallets.data.local.WalletEntity
import com.fares.saveme.feature.wallets.data.local.WalletMemberDao
import com.fares.saveme.feature.wallets.data.local.WalletMemberEntity
import com.fares.saveme.feature.wallets.domain.DefaultWalletsService
import com.fares.saveme.feature.wallets.domain.PendingPostings
import com.fares.saveme.feature.wallets.domain.ZeroPendingPostings
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

class FakeWalletDao : WalletDao {
    val rows = MutableStateFlow<Map<String, WalletEntity>>(emptyMap())

    override fun observeAll(accountId: String): Flow<List<WalletEntity>> = rows.map { all ->
        all.values.filter { it.accountId == accountId }
            .sortedWith(compareBy({ it.archivedAt != null }, { it.createdAt }, { it.id }))
    }

    override fun observe(id: String): Flow<WalletEntity?> = rows.map { it[id] }
    override suspend fun get(id: String): WalletEntity? = rows.value[id]
    override suspend fun getAll(): List<WalletEntity> = rows.value.values.toList()
    override suspend fun upsert(entity: WalletEntity) { rows.value = rows.value + (entity.id to entity) }
    override suspend fun upsertAll(entities: List<WalletEntity>) { rows.value = rows.value + entities.associateBy { it.id } }
    override suspend fun delete(id: String) { rows.value = rows.value - id }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

class FakeWalletMemberDao : WalletMemberDao {
    val rows = MutableStateFlow<List<WalletMemberEntity>>(emptyList())
    override fun observe(walletId: String): Flow<List<WalletMemberEntity>> =
        rows.map { all -> all.filter { it.walletId == walletId }.sortedWith(compareBy({ it.createdAt }, { it.id })) }
    override suspend fun get(walletId: String): List<WalletMemberEntity> = rows.value.filter { it.walletId == walletId }
    override suspend fun insertAll(items: List<WalletMemberEntity>) { rows.value = rows.value + items }
    override suspend fun deleteFor(walletId: String) { rows.value = rows.value.filterNot { it.walletId == walletId } }
    override suspend fun deleteAll() { rows.value = emptyList() }
}

fun walletJson(
    id: String = "w1",
    type: String = "cash",
    name: String = "Pocket",
    currency: String = "EGP",
    opening: Long = 25_000,
    balance: Long = opening,
    icon: String? = null,
    color: String? = null,
    archivedAt: String? = null,
    locked: Boolean = false,
    role: String = "owner",
    version: Long = 1,
    deletedAt: String? = null,
    createdAt: String = "2026-09-01T00:00:00Z",
) = """
    {"id":"$id","wallet_id":"$id","type":"$type","name":"$name","currency":"$currency","opening_balance_minor":$opening,
     "balance_minor":$balance,"icon":${icon?.let { "\"$it\"" } ?: "null"},"color":${color?.let { "\"$it\"" } ?: "null"},
     "archived_at":${archivedAt?.let { "\"$it\"" } ?: "null"},"currency_locked":$locked,"my_role":"$role","version":$version,
     "change_seq":7,"created_at":"$createdAt","updated_at":"$createdAt","deleted_at":${deletedAt?.let { "\"$it\"" } ?: "null"},"something_new":1}
""".trimIndent()

fun walletBody(json: String = walletJson()) = """{"data":$json,"meta":{}}"""

fun memberJson(id: String, userId: String, name: String?, role: String = "owner", createdAt: String = "2026-09-01T00:00:00Z", walletId: String = "w1") =
    """{"id":"$id","wallet_id":"$walletId","member_user_id":"$userId","display_name":${name?.let { "\"$it\"" } ?: "null"},"role":"$role","version":1,"created_at":"$createdAt","updated_at":"$createdAt","deleted_at":null,"something_new":true}"""

fun pageOf(items: List<String>, nextCursor: String? = null) =
    """{"data":[${items.joinToString(",")}],"meta":{"next_cursor":${nextCursor?.let { "\"$it\"" } ?: "null"},"has_more":${nextCursor != null},"limit":50}}"""

val signedInAccount = AccountSnapshot("u1", "sara@example.com", true, AccountState.Active, null, "free", emptyMap())

/** Entitlements whose `wallets.max` is set by the test (`null` = unlimited). */
class FakeEntitlements(var walletLimit: Long? = null) : Entitlements {
    override val state: StateFlow<EntitlementSet?> = MutableStateFlow(null)
    override fun can(flag: FlagFeature) = false
    override fun quota(quota: QuotaFeature): QuotaLimit = walletLimit?.let { QuotaLimit.Limited(it) } ?: QuotaLimit.Unlimited
    override fun allowsAnother(quota: QuotaFeature, currentCount: Int) =
        walletLimit?.let { currentCount < it } ?: true
    override suspend fun refreshAfterBillingAction() = RefreshOutcome.Done
}

class FakePostings : PendingPostings {
    val delta = MutableStateFlow(emptyMap<String, Long>())
    val pending = MutableStateFlow(emptySet<String>())
    override fun deltaFor(walletId: String): Flow<Long> = delta.map { it[walletId] ?: 0L }
    override fun hasPending(walletId: String): Flow<Boolean> = pending.map { walletId in it }
}

/** The wallet stack over a Ktor MockEngine and in-memory DAOs; one signed-in account, `u1`. */
class WalletTestEnv(
    val scope: CoroutineScope,
    val postings: PendingPostings = ZeroPendingPostings,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val auth = AuthTestEnv(handler)
    val api = WalletApi(auth.client)
    val walletDao = FakeWalletDao()
    val memberDao = FakeWalletMemberDao()
    val scopeDao = FakeSyncScopeDao()
    val pendingDao = FakePendingChangeDao()
    val scheduler = FakeSyncScheduler()
    val clock = FakeClock()
    val removed = mutableListOf<String>()
    val repository: WalletRepository = WalletRepository(
        api, walletDao, memberDao, scopeDao,
        accountId = { "u1" },
        hasQueuedChange = { id -> engine.get(WalletSyncHandler.RECORD_TYPE, id) != null },
        onWalletRemoved = { removed += it },
        clock = clock,
    )
    val syncHandler: WalletSyncHandler = WalletSyncHandler(repository, api)
    val engine: SyncEngine = SyncEngine(pendingDao, scopeDao, listOf(syncHandler), scheduler, currentAccountId = { "u1" }, clock = clock)
    val authState = MutableStateFlow<AuthState>(AuthState.SignedIn(signedInAccount))
    val entitlements = FakeEntitlements()
    private var nextId = 0
    val service = DefaultWalletsService(
        repository, engine, authState, entitlements, postings,
        defaultCurrency = flowOf("EGP"), scope = scope, clock = clock, newId = { "w-new-${++nextId}" },
    )
    val requests get() = auth.requests

    suspend fun signedIn() = auth.secureStore.write(StoredSession(CREDENTIAL, "a", Instant.parse("2026-09-26T10:00:00Z")))

    /** A synced wallet row, as a refresh would have written it. */
    suspend fun seed(json: String = walletJson()) {
        repository.storeServerCopy(com.fares.saveme.core.network.AppJson.decodeFromString(com.fares.saveme.feature.wallets.data.dto.WalletDto.serializer(), json))
    }
}
