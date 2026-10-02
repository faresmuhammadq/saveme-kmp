package com.fares.saveme.feature.transactions.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
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

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    /** The rows of the given ids and every row whose carry still waits for a wallet read, in one snapshot (research R-09). */
    @Query("SELECT * FROM transactions WHERE id IN (:ids) OR carry_confirmed_at IS NOT NULL")
    fun observeRelevant(ids: List<String>): Flow<List<TransactionEntity>>

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

    /** ISO-8601 UTC strings of fixed width compare in time order. */
    @Query(
        """UPDATE transactions SET carry_source_minor = 0, carry_counter_minor = 0, carry_confirmed_at = NULL
        WHERE carry_confirmed_at IS NOT NULL AND carry_confirmed_at <= :at""",
    )
    suspend fun clearCarryUpTo(at: String)

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
