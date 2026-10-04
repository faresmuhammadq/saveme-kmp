package com.fares.saveme.feature.budgets.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The server's last progress figures for a budget and the phone's synced-only figures at that moment (research R-06).
 * A phone-side cache, never synced. Remaining, over-limit, percentage and projection are not stored: they are derived
 * on the phone, so a stored row never goes stale against "today" (data-model §2). Amounts are never printed (P-09.2).
 */
@Entity(tableName = "budget_progress")
data class BudgetProgressEntity(
    @PrimaryKey @ColumnInfo(name = "budget_id") val budgetId: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "period_start") val periodStart: String,
    @ColumnInfo(name = "period_end") val periodEnd: String,
    @ColumnInfo(name = "days_in_period") val daysInPeriod: Int,
    @ColumnInfo(name = "limit_minor") val limitMinor: Long,
    @ColumnInfo(name = "spent_minor") val spentMinor: Long,
    val currency: String,
    /** Sorted codes, comma-separated; empty when none. */
    @ColumnInfo(name = "other_currencies") val otherCurrencies: String,
    @ColumnInfo(name = "local_spent_minor") val localSpentMinor: Long,
    @ColumnInfo(name = "local_other_currencies") val localOtherCurrencies: String,
    @ColumnInfo(name = "read_at") val readAt: String,
) {
    override fun toString() = "BudgetProgressEntity($budgetId)"
}

@Dao
interface BudgetProgressDao {
    @Query("SELECT * FROM budget_progress WHERE account_id = :accountId")
    fun observeAll(accountId: String): Flow<List<BudgetProgressEntity>>

    @Query("SELECT * FROM budget_progress WHERE budget_id = :budgetId")
    suspend fun get(budgetId: String): BudgetProgressEntity?

    @Query("SELECT * FROM budget_progress")
    suspend fun getAll(): List<BudgetProgressEntity>

    @Upsert
    suspend fun upsertAll(rows: List<BudgetProgressEntity>)

    @Query("DELETE FROM budget_progress WHERE budget_id = :budgetId")
    suspend fun delete(budgetId: String)

    @Query("DELETE FROM budget_progress WHERE account_id = :accountId AND budget_id NOT IN (:keepIds)")
    suspend fun deleteExcept(accountId: String, keepIds: List<String>)

    @Query("DELETE FROM budget_progress")
    suspend fun deleteAll()

    /**
     * A completed walk: stores [rows] and removes the stored rows of every budget it did not return, except the
     * archived ones in [archivedIds] (the list omits them, so their rows come from one-by-one reads; research R-09).
     */
    @Transaction
    suspend fun replaceWalk(accountId: String, rows: List<BudgetProgressEntity>, archivedIds: List<String>) {
        upsertAll(rows)
        deleteExcept(accountId, rows.map { it.budgetId } + archivedIds)
    }
}
