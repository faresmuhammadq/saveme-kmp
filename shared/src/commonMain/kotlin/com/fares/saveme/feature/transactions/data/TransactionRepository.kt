package com.fares.saveme.feature.transactions.data

import com.fares.saveme.core.database.SyncScopeDao
import com.fares.saveme.core.sync.ScopeKind
import com.fares.saveme.core.sync.SyncScope
import com.fares.saveme.feature.transactions.data.dto.TransactionDto
import com.fares.saveme.feature.transactions.data.local.TransactionDao
import com.fares.saveme.feature.transactions.data.local.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Clock
import kotlin.time.Instant

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
 * The device's copy of transactions, kept current from the wallets' change feeds (spec 009). No
 * decisions here (P-10.6): the domain decides what to do with the rows.
 *
 * @param hasQueuedChange whether the sync queue still holds a change for a transaction; such a
 *   transaction is never removed by a read (FR-018).
 * @param walletChangeSeq the scope position of the phone's copy of a wallet (`change_seq`), null when absent: a
 *   copy at or past an accepted change's position already counts it, so that change carries nothing for it.
 */
class TransactionRepository(
    private val transactions: TransactionDao,
    private val scopes: SyncScopeDao,
    private val accountId: () -> String?,
    private val hasQueuedChange: suspend (String) -> Boolean,
    private val clock: Clock = Clock.System,
    private val walletChangeSeq: suspend (String) -> Long? = { null },
) {
    fun observeForWallet(accountId: String, walletId: String): Flow<List<TransactionEntity>> =
        transactions.observeForWallet(accountId, walletId)

    fun observe(id: String): Flow<TransactionEntity?> = transactions.observe(id)

    /** The rows budgets sum over: dated in the range, with a waiting change, or a phone-only confirmation (spec 012 R-05). */
    fun observeForSpending(accountId: String, from: String, to: String, waitingIds: List<String>): Flow<List<TransactionEntity>> =
        transactions.observeForSpending(accountId, from, to, waitingIds)

    suspend fun get(id: String): TransactionEntity? = transactions.get(id)

    /** The rows of [ids] and every row still carrying a confirmed effect, from one read of the table. */
    fun observeRelevant(ids: List<String>): Flow<List<TransactionEntity>> =
        if (ids.isEmpty()) transactions.observeCarriedOrPending() else transactions.observeRelevant(ids)

    fun observeCarried(): Flow<List<TransactionEntity>> = transactions.observeCarried()

    /**
     * When the wallet's feed was last read completely; null before the first complete read. An upgraded phone keeps
     * the time of its last whole-list read until its first feed read ends.
     */
    fun observeRefreshedAt(walletId: String): Flow<String?> = combine(
        scopes.observeFeedReadAt(SyncScope(ScopeKind.Wallet, walletId).key),
        scopes.observe(scopeKey(walletId)),
    ) { feed, legacy -> feed ?: legacy?.refreshedAt?.takeIf { at -> at.isNotEmpty() } }

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
        if (old?.pendingConfirmation == true && !own) {
            // The server's copy replaces the phone-only confirmation row in place; its effect, counted until now by
            // the row itself, joins the carry until a wallet read includes it (spec 010 R-07).
            val moved = movedFromPending(dto)
            carrySource = moved.first
            carryCounter = moved.second
            confirmedAt = clock.now().fixedStamp()
        }
        if (own) {
            carrySource += effect(dto, dto.walletId) - effectOf(old, dto.walletId)
            carryCounter += dto.counterWalletId?.let { effect(dto, it) - effectOf(old, it) } ?: 0
            // The source wallet's copy may already include this change (a replayed upload, or a wallet saved after it in
            // the same upload): its balance counts it, so no carry for it (spec 009 FR-013, qa-report.md §4).
            if (dto.changeSeq > 0 && (walletChangeSeq(dto.walletId) ?: 0L) >= dto.changeSeq) carrySource = 0
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

    /** A copy of [walletId] at scope position [changeSeq] was stored: what it already counts carries nothing more. */
    suspend fun clearSourceCarryCoveredBy(walletId: String, changeSeq: Long) {
        if (changeSeq > 0) transactions.clearSourceCarryCoveredBy(walletId, changeSeq)
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

    /**
     * A read of [walletId]'s feed that started at [startedAt] has completed: the effect of every change the
     * server confirmed at or before it on that wallet is in the balance it brought, so that wallet's part of the
     * carry goes, and hidden deletions with no part left go with it (spec 009 R-09).
     */
    suspend fun clearCarryFor(walletId: String, startedAt: Instant) {
        transactions.clearCarryFor(walletId, startedAt.fixedStamp())
        transactions.purgeSettledDeletions()
    }

    suspend fun hasCarry(): Boolean = transactions.getCarried().isNotEmpty()

    /** The wallets whose next completed read settles a carry: every wallet a carried row touches. */
    suspend fun carriedWalletIds(): List<String> =
        transactions.getCarried().flatMap { listOfNotNull(it.walletId, it.counterWalletId) }.distinct()

    /**
     * Applies a transaction from a wallet's change feed (spec 009 FR-004, FR-005, FR-012): when its version is the
     * same or newer; it never adds a balance carry. A deletion removes the row, except while a change to it waits
     * (it stays until settled) and while the row still carries an unsettled balance effect (kept hidden, so the
     * balance does not jump). Returns true when anything changed.
     */
    suspend fun storeFeedCopy(dto: TransactionDto): Boolean {
        val old = transactions.get(dto.id)
        if (old != null && old.version > dto.version) return false
        if (dto.deletedAt != null) {
            if (hasQueuedChange(dto.id)) return false
            if (old == null) return false
            if (old.carryConfirmedAt == null) {
                transactions.delete(dto.id)
                return true
            }
        }
        val moved = if (old?.pendingConfirmation == true) movedFromPending(dto) else null
        transactions.upsert(
            dto.toEntity(accountId().orEmpty(), clock.now().toString()).copy(
                carrySourceMinor = moved?.first ?: old?.carrySourceMinor ?: 0,
                carryCounterMinor = moved?.second ?: old?.carryCounterMinor ?: 0,
                carryConfirmedAt = if (moved != null) clock.now().fixedStamp() else old?.carryConfirmedAt,
            ),
        )
        return true
    }

    /**
     * The effect a replaced phone-only confirmation row moves into the carry (source, destination); the source's part
     * is dropped when the phone's copy of the wallet already counts the transaction (spec 010 R-07, spec 009 FR-013).
     */
    private suspend fun movedFromPending(dto: TransactionDto): Pair<Long, Long> {
        val source = if (dto.changeSeq > 0 && (walletChangeSeq(dto.walletId) ?: 0L) >= dto.changeSeq) 0L else effect(dto, dto.walletId)
        val counter = dto.counterWalletId?.let { effect(dto, it) } ?: 0L
        return source to counter
    }

    /** Inserts a phone-only confirmation row (spec 010 R-07). */
    suspend fun placeConfirmation(row: TransactionEntity) = transactions.upsert(row)

    /** Deletes the confirmation row of [transactionId] while it is still phone-only. */
    suspend fun dropConfirmation(transactionId: String) {
        val row = transactions.get(transactionId)
        if (row != null && row.pendingConfirmation && row.version == 0L) transactions.delete(transactionId)
    }

    /** Deletes the phone-only confirmation row of an occurrence, except the one with id [exceptTransactionId]. */
    suspend fun dropConfirmationOf(occurrenceId: String, exceptTransactionId: String?) {
        transactions.getPendingForOccurrence(occurrenceId).filter { it.id != exceptTransactionId }.forEach { transactions.delete(it.id) }
    }

    /**
     * After a complete read of [walletId]: a confirmation row whose transaction the server recorded ([confirmedIds])
     * but never delivered is dropped, as the transaction was deleted meanwhile (spec 010 R-07).
     */
    suspend fun sweepConfirmations(walletId: String, confirmedIds: Set<String>) {
        transactions.getTouching(walletId)
            .filter { it.pendingConfirmation && it.version == 0L && it.id in confirmedIds }
            .forEach { transactions.delete(it.id) }
    }

    /**
     * After a complete read of [walletId] from the start: a row touching the wallet that reached the server, has no
     * waiting change, carries nothing and was not returned is removed (FR-007).
     */
    suspend fun sweepWallet(walletId: String, seenIds: Set<String>) {
        transactions.getTouching(walletId)
            .filter { it.id !in seenIds && it.version > 0 && it.carryConfirmedAt == null && !hasQueuedChange(it.id) }
            .forEach { transactions.delete(it.id) }
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
