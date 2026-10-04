package com.fares.saveme.feature.account

import com.fares.saveme.core.database.FeedPlace
import com.fares.saveme.core.database.SyncScopeDao
import com.fares.saveme.core.database.SyncScopeEntity
import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.feature.account.data.AccountApi
import com.fares.saveme.core.locale.AppLanguageStore
import com.fares.saveme.feature.account.data.DeviceSessionRepository
import com.fares.saveme.core.sync.FakePendingChangeDao
import com.fares.saveme.core.sync.FakeSyncRunner
import com.fares.saveme.core.sync.FakeSyncScheduler
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.account.data.ProfileRepository
import com.fares.saveme.feature.account.data.ProfileSyncHandler
import com.fares.saveme.feature.account.data.local.DeviceSessionDao
import com.fares.saveme.feature.account.data.local.DeviceSessionEntity
import com.fares.saveme.feature.account.data.local.ProfileDao
import com.fares.saveme.feature.account.data.local.ProfileEntity
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

/** In-memory stand-ins that follow the DAOs' SQL (the SQL itself is tested in AccountDaoTest). */
class FakeDeviceSessionDao : DeviceSessionDao {
    val rows = MutableStateFlow<List<DeviceSessionEntity>>(emptyList())
    override fun observeAll(): Flow<List<DeviceSessionEntity>> = rows.map { list -> list.sortedByDescending { it.signedInAt } }
    override suspend fun deleteAll() { rows.value = emptyList() }
    override suspend fun insertAll(items: List<DeviceSessionEntity>) { rows.value = rows.value + items }
    override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
    override suspend fun getAll(): List<DeviceSessionEntity> = rows.value
    override suspend fun upsert(entity: DeviceSessionEntity) { rows.value = rows.value.filterNot { it.id == entity.id } + entity }
}

class FakeSyncScopeDao : SyncScopeDao {
    val rows = MutableStateFlow<Map<String, SyncScopeEntity>>(emptyMap())
    override suspend fun upsert(entity: SyncScopeEntity) { rows.value = rows.value + (entity.scope to entity) }
    override suspend fun deleteAll() { rows.value = emptyMap() }
    override fun observe(scope: String): Flow<SyncScopeEntity?> = rows.map { it[scope] }
    override suspend fun markPushed(scope: String, at: String) {
        val current = rows.value[scope] ?: SyncScopeEntity(scope, "")
        rows.value = rows.value + (scope to current.copy(lastPushedAt = at))
    }
    override fun observeLastPushed(): Flow<String?> = rows.map { r -> r.values.mapNotNull { it.lastPushedAt }.maxOrNull() }
    override suspend fun feedCursor(scope: String): String? = rows.value[scope]?.feedCursor
    override suspend fun feedPlace(scope: String): FeedPlace? = rows.value[scope]?.let { FeedPlace(it.feedCursor, it.feedPosition) }
    override suspend fun storeFeedPage(scope: String, cursor: String, position: Long?) {
        val current = rows.value[scope] ?: SyncScopeEntity(scope, "")
        rows.value = rows.value + (scope to current.copy(feedCursor = cursor, feedPosition = position))
    }
    override suspend fun markFeedRead(scope: String, startedAt: String) {
        val current = rows.value[scope] ?: SyncScopeEntity(scope, "")
        rows.value = rows.value + (scope to current.copy(feedReadAt = startedAt))
    }
    override suspend fun clearFeed(scope: String) {
        rows.value[scope]?.let { rows.value = rows.value + (scope to it.copy(feedCursor = null, feedPosition = null, feedReadAt = null)) }
    }
    override fun observeFeedReadAt(scope: String): Flow<String?> = rows.map { it[scope]?.feedReadAt }
}

class FakeProfileDao : ProfileDao {
    val row = MutableStateFlow<ProfileEntity?>(null)
    override fun observe(): Flow<ProfileEntity?> = row
    override suspend fun get(): ProfileEntity? = row.value
    override suspend fun upsert(entity: ProfileEntity) { row.value = entity }
    override suspend fun deleteAll() { row.value = null }
    override suspend fun updateServerCopy(
        userId: String, id: String, version: Long, displayName: String?, locale: String, timezone: String,
        defaultCurrency: String, defaultCurrencyLocked: Boolean, budgetPeriodStartDay: Int,
        budgetPeriodStartDayFrom: String?, refreshedAt: String,
    ): Int {
        val current = row.value?.takeIf { it.userId == userId } ?: return 0
        row.value = current.copy(
            id = id, version = version, displayName = displayName, locale = locale, timezone = timezone,
            defaultCurrency = defaultCurrency, defaultCurrencyLocked = defaultCurrencyLocked,
            budgetPeriodStartDay = budgetPeriodStartDay, budgetPeriodStartDayFrom = budgetPeriodStartDayFrom,
            refreshedAt = refreshedAt,
        )
        return 1
    }
}

fun profileJson(
    version: Long = 3,
    displayName: String? = "Sara",
    locale: String = "en",
    currencyLocked: Boolean = false,
    budgetDay: Int = 1,
    budgetFrom: String? = null,
) = """
    {"id":"p1","user_id":"u1","version":$version,"change_seq":9,"created_at":"2026-09-01T00:00:00Z","updated_at":"2026-09-01T00:00:00Z",
     "deleted_at":null,"display_name":${displayName?.let { "\"$it\"" } ?: "null"},"locale":"$locale","timezone":"Africa/Cairo",
     "default_currency":"EGP","default_currency_locked":$currencyLocked,"budget_period_start_day":$budgetDay,
     "budget_period_start_day_from":${budgetFrom?.let { "\"$it\"" } ?: "null"},"something_new":1}
""".trimIndent()

fun meBody(profile: String = profileJson(), state: String = "active", scheduled: String? = null) = """
    {"data":{"account":{"id":"u1","email":"sara@example.com","email_verified":true,"state":"$state",
      "deletion_scheduled_for":${scheduled?.let { "\"$it\"" } ?: "null"}},"profile":$profile},"meta":{}}
""".trimIndent()

fun sessionJson(id: String, current: Boolean = false, signedInAt: String = "2026-09-20T08:30:00Z", lastUsedAt: String? = null) = """
    {"id":"$id","user_id":"u1","version":1,"change_seq":4,"created_at":"$signedInAt","updated_at":"$signedInAt","deleted_at":null,
     "device_name":"Device $id","platform":"android","app_version":"1.0.0","state":"active","end_reason":null,
     "signed_in_at":"$signedInAt","last_used_at":${lastUsedAt?.let { "\"$it\"" } ?: "null"},"ended_at":null,"current":$current,
     "something_new":true}
""".trimIndent()

fun pageBody(items: List<String>, nextCursor: String? = null) =
    """{"data":[${items.joinToString(",")}],"meta":{"next_cursor":${nextCursor?.let { "\"$it\"" } ?: "null"},"has_more":${nextCursor != null},"limit":50}}"""

/** The JSON members a request sent, read from its body. */
fun HttpRequestData.jsonBody(): JsonObject =
    Json.parseToJsonElement((body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

class AccountTestEnv(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
    val auth = AuthTestEnv(handler)
    val sessionDao = FakeDeviceSessionDao()
    val scopeDao = FakeSyncScopeDao()
    val profileDao = FakeProfileDao()
    val api = AccountApi(auth.client)
    val sessions = DeviceSessionRepository(api, sessionDao, scopeDao, accountId = { "u1" })
    val profiles = ProfileRepository(api, profileDao, scopeDao)
    val languages = AppLanguageStore(auth.keyValues, auth.clientInfo)
    val pendingDao = FakePendingChangeDao()
    val scheduler = FakeSyncScheduler()
    val profileHandler = ProfileSyncHandler(profiles, languages)
    val engine = SyncEngine(pendingDao, scopeDao, listOf(profileHandler), scheduler, currentAccountId = { "u1" })
    val runner = FakeSyncRunner(engine = engine)
    val requests get() = auth.requests
    val events get() = auth.events

    suspend fun signedIn() = auth.secureStore.write(StoredSession(CREDENTIAL, "a", Instant.parse("2026-09-26T10:00:00Z")))
}
