package com.fares.saveme.feature.recurring

import com.fares.saveme.core.network.AppJson
import com.fares.saveme.core.storage.StoredSession
import com.fares.saveme.core.sync.FakeClock
import com.fares.saveme.core.sync.FakePendingChangeDao
import com.fares.saveme.core.sync.FakeSyncScheduler
import com.fares.saveme.core.sync.SyncEngine
import com.fares.saveme.feature.account.FakeSyncScopeDao
import com.fares.saveme.feature.auth.AuthTestEnv
import com.fares.saveme.feature.auth.CREDENTIAL
import com.fares.saveme.feature.recurring.data.OccurrenceRepository
import com.fares.saveme.feature.recurring.data.OccurrenceSyncHandler
import com.fares.saveme.feature.recurring.data.RecurringApi
import com.fares.saveme.feature.recurring.data.RecurringRepository
import com.fares.saveme.feature.recurring.data.RecurringRuleSyncHandler
import com.fares.saveme.feature.recurring.data.dto.OccurrenceDto
import com.fares.saveme.feature.recurring.data.dto.RecurringRuleDto
import com.fares.saveme.feature.recurring.data.local.OccurrenceDao
import com.fares.saveme.feature.recurring.data.local.OccurrenceEntity
import com.fares.saveme.feature.recurring.data.local.RecurringRuleDao
import com.fares.saveme.feature.recurring.data.local.RecurringRuleEntity
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/** In-memory stand-in that follows [RecurringRuleDao]'s SQL (the SQL itself: RecurringDaoTest). */
class FakeRecurringRuleDao : RecurringRuleDao {
    val rows = MutableStateFlow<Map<String, RecurringRuleEntity>>(emptyMap())

    override fun observeForWallet(accountId: String, walletId: String): Flow<List<RecurringRuleEntity>> =
        rows.map { all -> all.values.filter { it.accountId == accountId && it.walletId == walletId }.sortedWith(compareBy({ it.createdAt }, { it.id })) }

    override fun observeAll(accountId: String): Flow<List<RecurringRuleEntity>> =
        rows.map { all -> all.values.filter { it.accountId == accountId }.sortedWith(compareBy({ it.createdAt }, { it.id })) }

    override fun observe(id: String): Flow<RecurringRuleEntity?> = rows.map { it[id] }
    override suspend fun get(id: String) = rows.value[id]
    override suspend fun getForWallet(walletId: String) = rows.value.values.filter { it.walletId == walletId }
    override suspend fun getFollowing(ruleId: String) = rows.value.values.filter { it.followsRuleId == ruleId }
    override suspend fun upsert(entity: RecurringRuleEntity) { rows.value = rows.value + (entity.id to entity) }
    override suspend fun delete(id: String) { rows.value = rows.value - id }
    override suspend fun deleteForWallet(walletId: String) { rows.value = rows.value.filterValues { it.walletId != walletId } }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

/** In-memory stand-in that follows [OccurrenceDao]'s SQL. */
class FakeOccurrenceDao : OccurrenceDao {
    val rows = MutableStateFlow<Map<String, OccurrenceEntity>>(emptyMap())

    private fun sorted(list: Collection<OccurrenceEntity>) = list.sortedWith(compareBy({ it.date }, { it.ordinal }, { it.id }))

    override fun observeForWallet(accountId: String, walletId: String): Flow<List<OccurrenceEntity>> =
        rows.map { all -> sorted(all.values.filter { it.accountId == accountId && it.walletId == walletId }) }

    override fun observeAll(accountId: String): Flow<List<OccurrenceEntity>> =
        rows.map { all -> sorted(all.values.filter { it.accountId == accountId }) }

    override fun observeForRule(ruleId: String): Flow<List<OccurrenceEntity>> =
        rows.map { all -> sorted(all.values.filter { it.recurringRuleId == ruleId }) }

    override fun observe(id: String): Flow<OccurrenceEntity?> = rows.map { it[id] }
    override suspend fun get(id: String) = rows.value[id]
    override suspend fun getByTransactionId(transactionId: String) = rows.value.values.filter { it.transactionId == transactionId }
    override suspend fun getForRule(ruleId: String) = rows.value.values.filter { it.recurringRuleId == ruleId }
    override suspend fun getForWallet(walletId: String) = rows.value.values.filter { it.walletId == walletId }
    override suspend fun upsert(entity: OccurrenceEntity) { rows.value = rows.value + (entity.id to entity) }
    override suspend fun setNotice(id: String, notice: String?) { rows.value[id]?.let { rows.value = rows.value + (id to it.copy(localNotice = notice)) } }
    override suspend fun delete(id: String) { rows.value = rows.value - id }
    override suspend fun deleteForRule(ruleId: String) { rows.value = rows.value.filterValues { it.recurringRuleId != ruleId } }
    override suspend fun deleteForWallet(walletId: String) { rows.value = rows.value.filterValues { it.walletId != walletId } }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

private fun String?.q() = this?.let { "\"$it\"" } ?: "null"

/** A rule as the server answers it (recurring.yaml `RecurringRule`, with a member the client does not know). */
fun ruleJson(
    id: String = "r1",
    walletId: String = "w1",
    version: Long = 1,
    preset: String = "custom",
    status: String = "active",
    kind: String = "expense",
    amount: Long = 450_000,
    basis: String = "per_occurrence",
    currency: String = "EGP",
    categoryId: String? = "c1",
    counterWalletId: String? = null,
    description: String? = "Rent",
    autoConfirm: Boolean = false,
    frequency: String = "monthly",
    interval: Int = 1,
    anchorDay: Int? = 1,
    startDate: String = "2026-11-01",
    endDate: String? = null,
    count: Int? = null,
    payoutRound: Int? = null,
    payoutCategoryId: String? = null,
    deletedAt: String? = null,
    changeSeq: Long = 901,
) = """
    {"id":"$id","wallet_id":"$walletId","version":$version,"change_seq":$changeSeq,"created_at":"2026-10-02T09:00:00.000000Z",
     "updated_at":"2026-10-02T09:00:00.000000Z","deleted_at":${deletedAt.q()},"preset":"$preset","status":"$status","kind":"$kind",
     "amount_minor":$amount,"amount_basis":"$basis","currency":"$currency","category_id":${categoryId.q()},
     "counter_wallet_id":${counterWalletId.q()},"description":${description.q()},"memo":null,"counterparty":null,
     "auto_confirm":$autoConfirm,"frequency":"$frequency","interval":$interval,"anchor_day":${anchorDay ?: "null"},
     "start_date":"$startDate","end_date":${endDate.q()},"occurrence_count":${count ?: "null"},"payout_round":${payoutRound ?: "null"},
     "payout_category_id":${payoutCategoryId.q()},"timezone":"Africa/Cairo","created_by":"u1","something_new":1}
""".trimIndent()

/** An occurrence as the server answers it (recurring.yaml `Occurrence`). */
fun occurrenceJson(
    id: String = "o1",
    walletId: String = "w1",
    ruleId: String = "r1",
    version: Long = 1,
    ordinal: Int = 1,
    isPayout: Boolean = false,
    scheduledDate: String = "2026-11-01",
    date: String = scheduledDate,
    status: String = "scheduled",
    kind: String = "expense",
    amount: Long = 450_000,
    currency: String = "EGP",
    categoryId: String? = "c1",
    description: String? = "Rent",
    edited: Boolean = false,
    transactionId: String? = null,
    refusal: String? = null,
    deletedAt: String? = null,
    changeSeq: Long = 902,
) = """
    {"id":"$id","wallet_id":"$walletId","version":$version,"change_seq":$changeSeq,"created_at":"2026-10-02T09:00:00.000000Z",
     "updated_at":"2026-10-02T09:00:00.000000Z","deleted_at":${deletedAt.q()},"recurring_rule_id":"$ruleId","ordinal":$ordinal,
     "is_payout":$isPayout,"scheduled_date":"$scheduledDate","date":"$date","status":"$status","kind":"$kind","amount_minor":$amount,
     "currency":"$currency","category_id":${categoryId.q()},"counter_wallet_id":null,"description":${description.q()},"memo":null,
     "counterparty":null,"edited":$edited,"transaction_id":${transactionId.q()},"auto_confirm_refusal":${refusal.q()},"something_new":1}
""".trimIndent()

fun ruleBody(json: String = ruleJson()) = """{"data":$json,"meta":{}}"""

fun occurrenceBody(json: String = occurrenceJson()) = """{"data":$json,"meta":{}}"""

fun ruleDto(json: String): RecurringRuleDto = AppJson.decodeFromString(RecurringRuleDto.serializer(), json)

fun occurrenceDto(json: String): OccurrenceDto = AppJson.decodeFromString(OccurrenceDto.serializer(), json)

/** Records what the handlers asked the transactions side to drop. */
class DroppedConfirmations {
    val calls = mutableListOf<Pair<String, String?>>()
    val drop: suspend (String, String?) -> Unit = { occurrence, except -> calls += occurrence to except }
}

/**
 * The recurring data stack over a Ktor MockEngine and in-memory DAOs: both repositories, both handlers and
 * the sync engine; one signed-in account, `u1`.
 */
class RecurringTestEnv(
    val scope: CoroutineScope,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { throw kotlinx.io.IOException("offline") },
) {
    val auth = AuthTestEnv(handler)
    val api = RecurringApi(auth.client)
    val ruleDao = FakeRecurringRuleDao()
    val occurrenceDao = FakeOccurrenceDao()
    val scopeDao = FakeSyncScopeDao()
    val pendingDao = FakePendingChangeDao()
    val scheduler = FakeSyncScheduler()
    val clock = FakeClock()
    val dropped = DroppedConfirmations()
    val discardedRules = mutableListOf<String>()
    var walletCurrencies = mutableMapOf("w1" to "EGP")

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
    val ruleHandler = RecurringRuleSyncHandler(rules, api, walletCurrency = { walletCurrencies[it] }, discardRule = { discardedRules += it })
    val occurrenceHandler = OccurrenceSyncHandler(
        occurrences, api,
        queuedPayload = { id ->
            engine.get(OccurrenceSyncHandler.RECORD_TYPE, id)?.let {
                AppJson.decodeFromString(com.fares.saveme.feature.recurring.data.dto.OccurrencePayload.serializer(), it.payloadJson)
            }
        },
        dropConfirmation = dropped.drop,
    )
    val engine: SyncEngine = SyncEngine(
        pendingDao, scopeDao, listOf(ruleHandler, occurrenceHandler), scheduler, currentAccountId = { "u1" }, clock = clock,
    )
    val requests get() = auth.requests

    suspend fun signedIn() = auth.secureStore.write(StoredSession(CREDENTIAL, "a", Instant.parse("2026-09-26T10:00:00Z")))

    suspend fun seedRule(json: String = ruleJson()) { rules.storeCopy(ruleDto(json), respectQueued = false) }

    suspend fun seedOccurrence(json: String = occurrenceJson()) { occurrences.storeCopy(occurrenceDto(json), respectQueued = false) }
}
