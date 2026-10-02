package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.database.SyncScopeDao
import com.fares.saveme.core.database.SyncScopeEntity
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.transactions.data.dto.TransactionDto
import com.fares.saveme.feature.transactions.data.local.TransactionDao
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

/** What a complete read of one wallet's transactions came to. */
data class WalletRead(val changed: Boolean)

/** A transaction recorded on this phone, before the server has answered (research R-03). */
data class NewTransaction(
    val id: String,
    val accountId: String,
    val walletId: String,
    val counterWalletId: String?,
    val kind: String,
    val date: String,
    val amountMinor: Long,
    val currency: String,
    val categoryId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val cleared: Boolean,
    val createdBy: String,
)

/**
 * API calls and the device's copy of transactions. No decisions here (P-10.6): reads give rows,
 * refreshes give a result, and the domain decides what to do with them.
 *
 * @param hasQueuedChange whether the sync queue still holds a change for a transaction; such a
 *   transaction is never removed by a refresh (FR-018).
 */
class TransactionRepository(
    private val api: TransactionApi,
    private val transactions: TransactionDao,
    private val scopes: SyncScopeDao,
    private val accountId: () -> String?,
    private val hasQueuedChange: suspend (String) -> Boolean,
    private val clock: Clock = Clock.System,
) {
    fun observeForWallet(accountId: String, walletId: String): Flow<List<TransactionEntity>> =
        transactions.observeForWallet(accountId, walletId)

    fun observe(id: String): Flow<TransactionEntity?> = transactions.observe(id)

    suspend fun get(id: String): TransactionEntity? = transactions.get(id)

    /** The rows of [ids] and every row still carrying a confirmed effect, from one read of the table. */
    fun observeRelevant(ids: List<String>): Flow<List<TransactionEntity>> =
        if (ids.isEmpty()) transactions.observeCarried() else transactions.observeRelevant(ids)

    fun observeCarried(): Flow<List<TransactionEntity>> = transactions.observeCarried()

    /** When the wallet's list was last read completely; null before the first complete read. */
    fun observeRefreshedAt(walletId: String): Flow<String?> =
        scopes.observe(scopeKey(walletId)).map { it?.refreshedAt?.takeIf { at -> at.isNotEmpty() } }

    /** A transaction made on this phone: version 0 until the server answers. */
    suspend fun insertPlaceholder(new: NewTransaction) {
        val now = clock.now().toString()
        transactions.upsert(
            TransactionEntity(
                id = new.id, accountId = new.accountId, walletId = new.walletId, counterWalletId = new.counterWalletId,
                kind = new.kind, date = new.date, amountMinor = new.amountMinor, currency = new.currency,
                categoryId = new.categoryId, description = new.description, memo = new.memo,
                counterparty = new.counterparty, cleared = new.cleared, createdBy = new.createdBy,
                recurringRuleId = null, occurrenceId = null, version = 0, changeSeq = 0, createdAt = now,
                updatedAt = now, deletedAt = null, refreshedAt = now,
            ),
        )
    }

    /**
     * Writes the server's copy. [own] is true when it answers this phone's own send: its effect on
     * each touched wallet joins the row's carry until a wallet read confirms it (research R-09), and
     * a deletion stays as a hidden row until then. Any other copy adds no carry and a deletion removes
     * the row at once (analysis A4). Returns true when the row was removed.
     */
    suspend fun storeServerCopy(dto: TransactionDto, own: Boolean = false): Boolean {
        val old = transactions.get(dto.id)
        if (dto.deletedAt != null && !own) {
            transactions.delete(dto.id)
            return true
        }
        val now = clock.now()
        var carrySource = old?.carrySourceMinor ?: 0
        var carryCounter = old?.carryCounterMinor ?: 0
        var confirmedAt = old?.carryConfirmedAt
        if (own) {
            carrySource += effect(dto, dto.walletId) - effectOf(old, dto.walletId)
            carryCounter += dto.counterWalletId?.let { effect(dto, it) - effectOf(old, it) } ?: 0
            confirmedAt = now.fixedStamp()
        }
        transactions.upsert(
            dto.toEntity(accountId().orEmpty(), now.toString()).copy(
                carrySourceMinor = carrySource,
                carryCounterMinor = carryCounter,
                carryConfirmedAt = confirmedAt,
            ),
        )
        return false
    }

    suspend fun remove(id: String) = transactions.delete(id)

    /** Puts back a row the phone no longer holds; a row that came back meanwhile is left as it is. */
    suspend fun reinstate(entity: TransactionEntity) {
        if (transactions.get(entity.id) == null) transactions.upsert(entity)
    }

    /** Removes a placeholder that never reached the server; nothing else holds data for it. */
    suspend fun removePlaceholder(id: String) {
        if (transactions.get(id)?.version == 0L) transactions.delete(id)
    }

    /** Every row (live or kept for its carry) where [walletId] is the source or the destination. */
    suspend fun touching(walletId: String): List<TransactionEntity> = transactions.getTouching(walletId)

    suspend fun clear() = transactions.deleteAll()

    /** Rows whose carry was confirmed at or before [at] are settled: the carry goes, hidden deletions with it. */
    suspend fun clearCarryConfirmedUpTo(at: Instant) {
        transactions.clearCarryUpTo(at.fixedStamp())
        transactions.purgeSettledDeletions()
    }

    suspend fun hasCarry(): Boolean = transactions.getCarried().isNotEmpty()

    /**
     * Reads every page of the wallet's list, storing each page as it arrives so rows show
     * progressively. Only when all pages succeeded does it remove a row that touches the wallet,
     * reached the server, has no waiting change and was not listed (research R-08). A row that still
     * carries a confirmed effect is kept until the carry settles.
     */
    suspend fun refreshWallet(walletId: String): ApiResult<WalletRead> {
        val known = transactions.getTouching(walletId).associateBy { it.id }.toMutableMap()
        val listedIds = mutableSetOf<String>()
        var changed = false
        var cursor: String? = null
        val account = accountId().orEmpty()
        do {
            val page = when (val result = api.list(walletId, cursor)) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> return result
                ApiResult.Offline -> return ApiResult.Offline
                ApiResult.Timeout -> return ApiResult.Timeout
            }
            val now = clock.now().toString()
            val live = page.items.filter { it.deletedAt == null }
            page.items.forEach { listedIds += it.id }
            val entities = live.map { dto ->
                val old = known[dto.id] ?: transactions.get(dto.id)
                if (old == null || old.version != dto.version || old.deletedAt != null) changed = true
                dto.toEntity(account, now).copy(
                    carrySourceMinor = old?.carrySourceMinor ?: 0,
                    carryCounterMinor = old?.carryCounterMinor ?: 0,
                    carryConfirmedAt = old?.carryConfirmedAt,
                )
            }
            transactions.upsertAll(entities)
            entities.forEach { known[it.id] = it }
            cursor = page.nextCursor.takeIf { page.hasMore }
        } while (cursor != null)

        known.values
            .filter { it.id !in listedIds && it.version > 0 && it.carryConfirmedAt == null && !hasQueuedChange(it.id) }
            .forEach {
                transactions.delete(it.id)
                changed = true
            }
        scopes.upsert(SyncScopeEntity(scopeKey(walletId), clock.now().toString()))
        return ApiResult.Success(WalletRead(changed))
    }

    private fun effect(dto: TransactionDto, forWallet: String) =
        BalanceEffect.of(dto.kind, dto.amountMinor, dto.walletId, dto.counterWalletId, dto.deletedAt != null, forWallet)

    /** A placeholder (version 0) or a hidden deletion has no effect in the wallet's balance. */
    private fun effectOf(row: TransactionEntity?, forWallet: String): Long {
        if (row == null || row.version == 0L) return 0
        return BalanceEffect.of(row.kind, row.amountMinor, row.walletId, row.counterWalletId, row.deletedAt != null, forWallet)
    }

    companion object {
        fun scopeKey(walletId: String) = "transactions:$walletId"
    }
}

internal fun TransactionDto.toEntity(accountId: String, refreshedAt: String) = TransactionEntity(
    id = id, accountId = accountId, walletId = walletId, counterWalletId = counterWalletId, kind = kind, date = date,
    amountMinor = amountMinor, currency = currency, categoryId = categoryId, description = description, memo = memo,
    counterparty = counterparty, cleared = cleared, createdBy = createdBy, recurringRuleId = recurringRuleId,
    occurrenceId = occurrenceId, version = version, changeSeq = changeSeq, createdAt = createdAt, updatedAt = updatedAt,
    deletedAt = deletedAt, refreshedAt = refreshedAt,
)

/** A fixed-width UTC instant (milliseconds), so stored times compare as strings in the DAO. */
internal fun Instant.fixedStamp(): String {
    val text = Instant.fromEpochMilliseconds(toEpochMilliseconds()).toString()
    return if ('.' in text) text else text.removeSuffix("Z") + ".000Z"
}
