package com.fares.saveme.feature.budgets.data.local

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
 * A budget as the server last said it (research R-03). One made on this phone has a placeholder row with
 * `version = 0`; changes waiting to sync live in the sync queue. The limit is a person's own figure and is
 * never printed (P-09.2).
 */
@Entity(tableName = "budget", indices = [Index(value = ["account_id", "archived_at"])])
data class BudgetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    /** Null = all expenses; immutable. */
    @ColumnInfo(name = "category_id") val categoryId: String?,
    /** `monthly`; another value is kept as the server sent it and shown read-only (research R-15). */
    @ColumnInfo(name = "period_type") val periodType: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "archived_at") val archivedAt: String?,
    val version: Long,
    @ColumnInfo(name = "change_seq") val changeSeq: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "refreshed_at") val refreshedAt: String,
) {
    override fun toString() = "BudgetEntity($id, version=$version)"
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget WHERE account_id = :accountId ORDER BY created_at, id")
    fun observeAll(accountId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budget WHERE account_id = :accountId AND archived_at IS NULL ORDER BY created_at, id")
    fun observeActive(accountId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budget WHERE account_id = :accountId AND archived_at IS NOT NULL ORDER BY created_at, id")
    fun observeArchived(accountId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budget WHERE id = :id")
    fun observe(id: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM budget WHERE id = :id")
    suspend fun get(id: String): BudgetEntity?

    @Query("SELECT * FROM budget")
    suspend fun getAll(): List<BudgetEntity>

    @Upsert
    suspend fun upsert(entity: BudgetEntity)

    @Query("DELETE FROM budget WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM budget_progress WHERE budget_id = :id")
    suspend fun deleteProgress(id: String)

    /** A removed budget takes its stored progress with it, in one transaction (FR-022). */
    @Transaction
    suspend fun deleteWithProgress(id: String) {
        delete(id)
        deleteProgress(id)
    }

    @Query("DELETE FROM budget")
    suspend fun deleteAll()
}
