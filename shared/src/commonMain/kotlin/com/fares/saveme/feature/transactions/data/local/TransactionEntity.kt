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
    /** The Shared Budget the transaction counts toward, or null (backend ADR-0027). Only its author sets it. */
    @ColumnInfo(name = "shared_budget_id") val sharedBudgetId: String? = null,
) {
    /** Amounts, notes and people are the person's own; they are never printed (P-09.2). */
    override fun toString() = "TransactionEntity($id, version=$version)"
}

/**
 * The few columns reports read per row (spec 014 R-03): a row of a report is ten values, not the whole transaction, which is
 * what keeps 20,000 rows within a second.
 */
data class ReportRow(
    val id: String,
    @ColumnInfo(name = "wallet_id") val walletId: String,
    @ColumnInfo(name = "counter_wallet_id") val counterWalletId: String?,
    val kind: String,
    val date: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val version: Long,
    @ColumnInfo(name = "pending_confirmation") val pendingConfirmation: Boolean,
) {
    /** Amounts are the person's own; they are never printed (P-09.2). */
    override fun toString() = "ReportRow($id)"
}

/** One day's income or expense sum over some wallets: [kind] is `income` or `expense`. */
data class DaySumRow(val date: String, val kind: String, val total: Long)

/** One wallet's summed balance effect, as [TransactionDao.observeBalancesBefore] answers. */
data class WalletBalanceSum(
    @ColumnInfo(name = "wallet_id") val walletId: String,
    val total: Long,
)

/** How often the person put their own expenses into one Shared Budget. */
data class BudgetUse(
    @ColumnInfo(name = "shared_budget_id") val budgetId: String,
    val uses: Int,
)

@Dao
interface TransactionDao {
    /** The person's own live expenses dated on or after [since], counted per Shared Budget, most used first (frequent picks). */
    @Query(
        """SELECT shared_budget_id, COUNT(*) AS uses FROM transactions WHERE account_id = :accountId AND created_by = :accountId
        AND deleted_at IS NULL AND shared_budget_id IS NOT NULL AND date >= :since
        GROUP BY shared_budget_id ORDER BY uses DESC, MAX(date) DESC""",
    )
    fun observeBudgetUse(accountId: String, since: String): Flow<List<BudgetUse>>

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

    /**
     * What reports need (spec 014 R-03) in one snapshot: the live rows touching [walletIds] (as source or destination)
     * dated in [from]..[to], every row whose id is in [waitingIds] (a waiting change may move it in or out of the range)
     * and every phone-only confirmation, for one account. Other kinds and the overlay are left to the caller.
     */
    @Query(
        """SELECT id, wallet_id, counter_wallet_id, kind, date, amount_minor, currency, category_id, version, pending_confirmation
        FROM transactions WHERE account_id = :accountId AND deleted_at IS NULL AND (
        ((wallet_id IN (:walletIds) OR counter_wallet_id IN (:walletIds)) AND date >= :from AND date <= :to)
        OR id IN (:waitingIds) OR pending_confirmation = 1)""",
    )
    fun observeForReport(accountId: String, walletIds: List<String>, from: String, to: String, waitingIds: List<String>): Flow<List<ReportRow>>

    /**
     * Income and expense sums per day that the server holds (version above zero) for [walletIds], dated in
     * [extraFrom]..[extraTo] but outside [from]..[to] (those days come row by row): what a trend over earlier periods needs
     * without reading every row. Rows the overlay changes are corrected by the caller.
     */
    @Query(
        """SELECT date AS date, kind AS kind, SUM(amount_minor) AS total FROM transactions
        WHERE account_id = :accountId AND deleted_at IS NULL AND version > 0 AND kind IN ('income', 'expense') AND wallet_id IN (:walletIds)
        AND ((date >= :extraFrom AND date < :from) OR (date > :to AND date <= :extraTo)) GROUP BY date, kind""",
    )
    fun observeDailySums(accountId: String, walletIds: List<String>, from: String, to: String, extraFrom: String, extraTo: String): Flow<List<DaySumRow>>

    /**
     * Per wallet, the signed effect of the live rows the server holds (version above zero) dated before [before]: income
     * +, expense -, a transfer - on its source and + on its destination; another kind counts nothing (the same rule as
     * `BalanceEffect.of`). Rows the overlay changes are corrected by the caller.
     */
    @Query(
        """SELECT wallet_id AS wallet_id, SUM(amount) AS total FROM (
        SELECT wallet_id AS wallet_id,
          CASE kind WHEN 'income' THEN amount_minor WHEN 'expense' THEN -amount_minor WHEN 'transfer' THEN -amount_minor ELSE 0 END AS amount
        FROM transactions WHERE account_id = :accountId AND deleted_at IS NULL AND version > 0 AND date < :before AND wallet_id IN (:walletIds)
        UNION ALL
        SELECT counter_wallet_id AS wallet_id, amount_minor AS amount
        FROM transactions WHERE account_id = :accountId AND deleted_at IS NULL AND version > 0 AND date < :before
          AND kind = 'transfer' AND counter_wallet_id IN (:walletIds)
        ) GROUP BY wallet_id""",
    )
    fun observeBalancesBefore(accountId: String, walletIds: List<String>, before: String): Flow<List<WalletBalanceSum>>

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
