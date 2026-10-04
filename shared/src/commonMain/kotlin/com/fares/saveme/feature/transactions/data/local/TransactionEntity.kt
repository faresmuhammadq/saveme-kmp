package com.fares.saveme.feature.transactions.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A transaction as the server last said it (research R-03). One recorded on this phone has a
 * placeholder row with `version = 0`; changes waiting to sync live in the sync queue. The carry
 * columns hold the balance effect the server confirmed that no wallet read has included yet (R-09).
 * The table is `transactions` because `TRANSACTION` is an SQLite keyword.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["wallet_id", "date", "created_at"]),
        Index(value = ["counter_wallet_id", "date", "created_at"]),
        Index(value = ["account_id"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "wallet_id") val walletId: String,
    @ColumnInfo(name = "counter_wallet_id") val counterWalletId: String?,
    val kind: String,
    val date: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val description: String?,
    val memo: String?,
    val counterparty: String?,
    val cleared: Boolean,
    @ColumnInfo(name = "created_by") val createdBy: String,
    @ColumnInfo(name = "recurring_rule_id") val recurringRuleId: String?,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: String?,
    val version: Long,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String?,
    @ColumnInfo(name = "refreshed_at") val refreshedAt: String,
    @ColumnInfo(name = "carry_source_minor") val carrySourceMinor: Long = 0,
    @ColumnInfo(name = "carry_counter_minor") val carryCounterMinor: Long = 0,
    @ColumnInfo(name = "carry_confirmed_at") val carryConfirmedAt: String? = null,
    /** Phone-only row showing a recurring confirmation the server has not answered yet (spec 010 R-07). */
    @ColumnInfo(name = "pending_confirmation", defaultValue = "0") val pendingConfirmation: Boolean = false,
) {
    /** Amounts, notes and people are the person's own; they are never printed (P-09.2). */
    override fun toString() = "TransactionEntity($id, version=$version)"
}

@Dao
interface TransactionDao {
    /** Live rows where the wallet is the source or the destination, newest first (research R-13). */
    @Query(
        """SELECT * FROM transactions WHERE account_id = :accountId AND deleted_at IS NULL
        AND (wallet_id = :walletId OR counter_wallet_id = :walletId) ORDER BY date DESC, created_at DESC, id DESC""",
    )
    fun observeForWallet(accountId: String, walletId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: String): Flow<TransactionEntity?>

    /**
     * What budgets need (spec 012 R-05) in one snapshot: the live expenses dated in [from]..[to] (ISO dates compare in
     * time order), every row whose id is in [waitingIds] (a waiting change may move it in or out of the range) and every
     * phone-only confirmation, for one account. Income, transfers and other people's rows are left to the caller.
     */
    @Query(
        """SELECT * FROM transactions WHERE account_id = :accountId AND deleted_at IS NULL AND (
        (kind = 'expense' AND date >= :from AND date <= :to) OR id IN (:waitingIds) OR pending_confirmation = 1)""",
    )
    fun observeForSpending(accountId: String, from: String, to: String, waitingIds: List<String>): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    /** The rows of the given ids and every row whose carry still waits for a wallet read, in one snapshot (research R-09). */
    @Query("SELECT * FROM transactions WHERE id IN (:ids) OR carry_confirmed_at IS NOT NULL OR pending_confirmation = 1")
    fun observeRelevant(ids: List<String>): Flow<List<TransactionEntity>>

    /** [observeRelevant] with no ids: rows carrying a balance effect the wallets' copies lack, and phone-only confirmations. */
    @Query("SELECT * FROM transactions WHERE carry_confirmed_at IS NOT NULL OR pending_confirmation = 1")
    fun observeCarriedOrPending(): Flow<List<TransactionEntity>>

    /** The phone-only confirmation row of an occurrence, if the server has not replaced it (spec 010 R-07). */
    @Query("SELECT * FROM transactions WHERE occurrence_id = :occurrenceId AND pending_confirmation = 1 AND version = 0")
    suspend fun getPendingForOccurrence(occurrenceId: String): List<TransactionEntity>

    /** Every row (live or kept for its carry) where the wallet is the source or the destination. */
    @Query("SELECT * FROM transactions WHERE wallet_id = :walletId OR counter_wallet_id = :walletId")
    suspend fun getTouching(walletId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<TransactionEntity>

    /** Rows whose carry still waits for a wallet read. */
    @Query("SELECT * FROM transactions WHERE carry_confirmed_at IS NOT NULL")
    fun observeCarried(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE carry_confirmed_at IS NOT NULL")
    suspend fun getCarried(): List<TransactionEntity>

    /**
     * A read of [walletId]'s feed that started at [at] includes the effect of every change the server confirmed
     * at or before it on that wallet: its part of the carry goes (ISO-8601 UTC strings of fixed width compare in
     * time order). The other wallet of a transfer keeps its part until its own read.
     */
    @Query(
        """UPDATE transactions SET carry_source_minor = 0
        WHERE wallet_id = :walletId AND carry_confirmed_at IS NOT NULL AND carry_confirmed_at <= :at""",
    )
    suspend fun clearSourceCarry(walletId: String, at: String)

    @Query(
        """UPDATE transactions SET carry_counter_minor = 0
        WHERE counter_wallet_id = :walletId AND carry_confirmed_at IS NOT NULL AND carry_confirmed_at <= :at""",
    )
    suspend fun clearCounterCarry(walletId: String, at: String)

    /** A row with no part left is settled, as far as [walletId] is concerned. */
    @Query(
        """UPDATE transactions SET carry_confirmed_at = NULL
        WHERE (wallet_id = :walletId OR counter_wallet_id = :walletId) AND carry_confirmed_at IS NOT NULL
        AND carry_confirmed_at <= :at AND carry_source_minor = 0 AND carry_counter_minor = 0""",
    )
    suspend fun settleClearedCarry(walletId: String, at: String)

    /**
     * A copy of the source wallet at scope position [changeSeq] already counts every change of that scope up to it:
     * the source part of a carry for a row at or below that position is settled (spec 009 FR-013, qa-report.md §4).
     * The destination part stays for the destination's read (a transfer's position is the source scope's).
     */
    @Query(
        """UPDATE transactions SET carry_source_minor = 0
        WHERE wallet_id = :walletId AND carry_confirmed_at IS NOT NULL AND change_seq > 0 AND change_seq <= :changeSeq""",
    )
    suspend fun clearSourceCarryCoveredBy(walletId: String, changeSeq: Long)

    @Transaction
    suspend fun clearCarryFor(walletId: String, at: String) {
        clearSourceCarry(walletId, at)
        clearCounterCarry(walletId, at)
        settleClearedCarry(walletId, at)
    }

    /** A confirmed deletion kept only for its carry goes once the carry is gone. */
    @Query("DELETE FROM transactions WHERE deleted_at IS NOT NULL AND carry_confirmed_at IS NULL")
    suspend fun purgeSettledDeletions()

    @Upsert
    suspend fun upsert(entity: TransactionEntity)

    @Upsert
    suspend fun upsertAll(entities: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
